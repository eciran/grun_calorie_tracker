#!/usr/bin/env python3
"""Build the versioned TR private-test rich v2 overlay from frozen OFF evidence.

The builder never mutates v1. It excludes duplicate-GTIN candidate groups and
secondary/unknown-primary identity conflicts from the import artifact, while
preserving those candidates in explicit review queues.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest().upper()


def assert_hash(path: Path, expected: str) -> str:
    actual = sha256(path)
    if actual != expected.upper():
        raise SystemExit(f"Checksum mismatch for {path}: expected {expected}, got {actual}")
    return actual


def read_csv(path: Path) -> tuple[list[str], list[dict[str, str]]]:
    with path.open("r", encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        return list(reader.fieldnames or []), list(reader)


def write_csv(path: Path, fields: list[str], rows: list[dict[str, object]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(
            stream,
            fieldnames=fields,
            quoting=csv.QUOTE_ALL,
            lineterminator="\n",
            extrasaction="ignore",
        )
        writer.writeheader()
        writer.writerows(rows)


def write_tsv(path: Path, fields: list[str], rows: list[dict[str, object]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields, delimiter="\t", lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def number(value: object) -> str:
    if value is None or value == "":
        return ""
    return format(float(value), ".12g")


def plausible(row: dict[str, object]) -> bool:
    try:
        calories, protein, fat, carbs = (
            float(row[field]) for field in ("calories", "protein", "fat", "carbs")
        )
    except (KeyError, TypeError, ValueError):
        return False
    return (
        0 <= calories <= 1000
        and all(0 <= value <= 100 for value in (protein, fat, carbs))
        and protein + fat + carbs <= 110
    )


def valid_gtin(value: str) -> bool:
    gtin = str(value or "").strip()
    if len(gtin) not in {8, 12, 13, 14} or not gtin.isdigit():
        return False
    total = 0
    weight = 3
    for digit in reversed(gtin[:-1]):
        total += int(digit) * weight
        weight = 1 if weight == 3 else 3
    return (10 - total % 10) % 10 == int(gtin[-1])


def source_urls(raw: str) -> dict[str, object]:
    try:
        parsed = json.loads(raw or "{}")
    except json.JSONDecodeError:
        parsed = {}
    return parsed if isinstance(parsed, dict) else {}


def enriched_row(base: dict[str, str], candidate: dict[str, object]) -> dict[str, str]:
    result = dict(base)
    off = candidate["off"]
    nutrition = off["nutrition"]
    result.update({
        "nutrition_basis": "SOURCE_REPORTED",
        "calories": number(nutrition.get("calories")),
        "protein": number(nutrition.get("protein")),
        "fat": number(nutrition.get("fat")),
        "carbs": number(nutrition.get("carbs")),
        "fiber": number(nutrition.get("fiber")),
        "sugar": number(nutrition.get("sugar")),
        "sodium": number(nutrition.get("sodium")),
        "source_salt_per_100g": number(nutrition.get("salt")),
    })
    providers = {item.strip() for item in str(base.get("source_providers") or "").split("|") if item.strip()}
    providers.add("OPEN_FOOD_FACTS")
    result["source_providers"] = "|".join(sorted(providers))
    urls = source_urls(base.get("source_urls_json", ""))
    urls["open_food_facts_nutrition"] = off.get("sourceUrl")
    result["source_urls_json"] = json.dumps(urls, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
    if not result.get("image_url") and off.get("imageUrl"):
        result["image_url"] = str(off["imageUrl"])
        result["external_image_url"] = str(off["imageUrl"])
    return result


def review_row(candidate: dict[str, object], blocker: str, primary_barcode: str = "") -> dict[str, object]:
    off = candidate["off"]
    return {
        "matchedBarcode": candidate.get("matchedBarcode", ""),
        "catalogId": candidate.get("catalogId", ""),
        "retailerName": candidate.get("retailerName", ""),
        "retailerBrand": candidate.get("retailerBrand", ""),
        "selectedPrimaryBarcode": primary_barcode,
        "identityMatch": candidate.get("identityMatch", ""),
        "offSourceUrl": off.get("sourceUrl", ""),
        "offSourceRow": off.get("sourceRow", ""),
        "reviewDecision": "PENDING_ADMIN_REVIEW",
        "promotionStatus": "BLOCKED",
        "promotionBlocker": blocker,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--v1-manifest", required=True)
    parser.add_argument("--v1-rich", required=True)
    parser.add_argument("--v1-max", required=True)
    parser.add_argument("--candidates", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--expected-v1-manifest-sha256", required=True)
    parser.add_argument("--expected-v1-rich-sha256", required=True)
    parser.add_argument("--expected-v1-max-sha256", required=True)
    parser.add_argument("--expected-candidates-sha256", required=True)
    parser.add_argument("--generated-at")
    args = parser.parse_args()

    v1_manifest_path = Path(args.v1_manifest).resolve()
    v1_rich_path = Path(args.v1_rich).resolve()
    v1_max_path = Path(args.v1_max).resolve()
    candidate_path = Path(args.candidates).resolve()
    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    input_hashes = {
        "v1Manifest": assert_hash(v1_manifest_path, args.expected_v1_manifest_sha256),
        "v1Rich": assert_hash(v1_rich_path, args.expected_v1_rich_sha256),
        "v1Max": assert_hash(v1_max_path, args.expected_v1_max_sha256),
        "offCandidates": assert_hash(candidate_path, args.expected_candidates_sha256),
    }
    v1_manifest = json.loads(v1_manifest_path.read_text(encoding="utf-8"))
    fields, v1_rich = read_csv(v1_rich_path)
    max_fields, v1_max = read_csv(v1_max_path)
    if fields != max_fields:
        raise SystemExit("v1 rich/max CSV schemas differ")
    candidate_doc = json.loads(candidate_path.read_text(encoding="utf-8"))
    candidates = candidate_doc.get("products", [])
    source = candidate_doc.get("source", {})
    if source.get("id") != "OPEN_FOOD_FACTS" or source.get("license") != "ODbL-1.0":
        raise SystemExit("OFF source/license contract is missing")
    if not source.get("attributionRequired") or not source.get("shareAlikeReviewRequired"):
        raise SystemExit("OFF attribution/share-alike gates are missing")

    rich_by_barcode = {row["barcode"]: row for row in v1_rich}
    max_by_barcode = {row["barcode"]: row for row in v1_max}
    max_by_catalog_id = {row["source_catalog_id"]: row for row in v1_max if row.get("source_catalog_id")}
    grouped: defaultdict[str, list[dict[str, object]]] = defaultdict(list)
    for candidate in candidates:
        grouped[str(candidate.get("matchedBarcode") or "")].append(candidate)

    duplicate_rows: list[dict[str, object]] = []
    identity_review_rows: list[dict[str, object]] = []
    provenance_rows: list[dict[str, object]] = []
    additions: list[dict[str, str]] = []
    already_rich = 0

    for barcode in sorted(grouped):
        group = grouped[barcode]
        if len(group) > 1:
            for candidate in group:
                selected = max_by_catalog_id.get(str(candidate.get("catalogId") or ""), {})
                duplicate_rows.append(review_row(candidate, "DUPLICATE_GTIN_GROUP", selected.get("barcode", "")))
            continue
        candidate = group[0]
        if barcode in rich_by_barcode:
            already_rich += 1
            continue
        base = max_by_barcode.get(barcode)
        if base is None:
            selected = max_by_catalog_id.get(str(candidate.get("catalogId") or ""), {})
            identity_review_rows.append(
                review_row(candidate, "MATCHED_GTIN_IS_NOT_SELECTED_PRIMARY_GTIN", selected.get("barcode", ""))
            )
            continue
        row = enriched_row(base, candidate)
        if not valid_gtin(row["barcode"]) or not plausible(row):
            raise SystemExit(f"Enriched row failed identity/nutrition validation: {barcode}")
        additions.append(row)
        off = candidate["off"]
        nutrition = off["nutrition"]
        provenance_rows.append({
            "barcode": barcode,
            "catalogId": candidate.get("catalogId", ""),
            "identityMatch": "EXACT_SELECTED_PRIMARY_GTIN",
            "nutritionSource": "OPEN_FOOD_FACTS",
            "nutritionSourceUrl": off.get("sourceUrl", ""),
            "nutritionSourceRow": off.get("sourceRow", ""),
            "nutritionSourceLastModified": off.get("lastModified", ""),
            "sourceSnapshotSha256": source.get("sha256", ""),
            "sourceLicense": "ODbL-1.0",
            "attributionRequired": "true",
            "shareAlikeReviewRequired": "true",
            "calories": number(nutrition.get("calories")),
            "protein": number(nutrition.get("protein")),
            "fat": number(nutrition.get("fat")),
            "carbs": number(nutrition.get("carbs")),
            "fiber": number(nutrition.get("fiber")),
            "sugar": number(nutrition.get("sugar")),
            "sodium": number(nutrition.get("sodium")),
            "salt": number(nutrition.get("salt")),
        })

    v2_rows = sorted(v1_rich + additions, key=lambda row: row["barcode"])
    barcode_counts = Counter(row["barcode"] for row in v2_rows)
    duplicate_output_barcodes = [barcode for barcode, count in barcode_counts.items() if count != 1]
    invalid_gtins = [row["barcode"] for row in v2_rows if not valid_gtin(row["barcode"])]
    invalid_nutrition = [row["barcode"] for row in v2_rows if not plausible(row)]
    if duplicate_output_barcodes or invalid_gtins or invalid_nutrition:
        raise SystemExit(
            f"v2 validation failed: duplicate={len(duplicate_output_barcodes)} "
            f"invalidGtin={len(invalid_gtins)} invalidNutrition={len(invalid_nutrition)}"
        )

    rich_v2_path = output_dir / "tr-retailer-test-rich-v2-import.csv"
    duplicate_path = output_dir / "tr-off-nutrition-duplicate-gtin-review.tsv"
    identity_path = output_dir / "tr-off-nutrition-primary-identity-review.tsv"
    provenance_path = output_dir / "tr-off-nutrition-field-provenance.tsv"
    validation_path = output_dir / "validation-report.json"
    manifest_path = output_dir / "manifest.json"
    write_csv(rich_v2_path, fields, v2_rows)
    review_fields = list((duplicate_rows or identity_review_rows)[0])
    write_tsv(duplicate_path, review_fields, duplicate_rows)
    write_tsv(identity_path, review_fields, identity_review_rows)
    provenance_fields = list(provenance_rows[0])
    write_tsv(provenance_path, provenance_fields, provenance_rows)

    generated_at = args.generated_at or datetime.now(timezone.utc).isoformat()
    validation = {
        "schemaVersion": 1,
        "generatedAt": generated_at,
        "status": "PASS",
        "checks": {
            "inputChecksums": "PASS",
            "v1FilesUnchanged": (
                sha256(v1_manifest_path) == input_hashes["v1Manifest"]
                and sha256(v1_rich_path) == input_hashes["v1Rich"]
                and sha256(v1_max_path) == input_hashes["v1Max"]
            ),
            "exactSelectedPrimaryGtinOnly": True,
            "duplicateCandidateGroupsExcluded": len(duplicate_rows) == 92,
            "identityConflictCandidatesExcluded": len(identity_review_rows) == 53,
            "uniqueOutputGtins": not duplicate_output_barcodes,
            "validOutputGtins": not invalid_gtins,
            "completePlausibleCoreNutrition": not invalid_nutrition,
            "offAttributionRecorded": True,
            "offShareAlikeReviewRequired": True,
            "productionImportAuthorized": False,
        },
        "counts": {
            "candidateRows": len(candidates),
            "candidateUniqueGtins": len(grouped),
            "duplicateGtinGroups": sum(1 for group in grouped.values() if len(group) > 1),
            "duplicateReviewRows": len(duplicate_rows),
            "primaryIdentityReviewRows": len(identity_review_rows),
            "alreadyRichRowsUnchanged": already_rich,
            "v1RichRows": len(v1_rich),
            "v2AddedRows": len(additions),
            "v2RichRows": len(v2_rows),
        },
    }
    if not all(value is True or value == "PASS" or value is False and key == "productionImportAuthorized"
               for key, value in validation["checks"].items()):
        raise SystemExit("One or more v2 validation gates failed")
    validation_path.write_text(json.dumps(validation, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    manifest = {
        "schemaVersion": 2,
        "manifestId": "tr-retailer-test-v2-off-nutrition",
        "generatedAt": generated_at,
        "marketRegion": "TR",
        "releaseClassification": "PRIVATE_TEST_ONLY_BLOCKED_FOR_PRODUCTION_RETAILER_RIGHTS_AND_ODBL_REVIEW",
        "requiredImportMode": "RAW_EXTERNAL",
        "requiredImportFormat": "GRUN_STANDARD",
        "base": {
            "manifestId": v1_manifest.get("manifestId"),
            "manifest": str(v1_manifest_path),
            "manifestSha256": input_hashes["v1Manifest"],
            "richImport": str(v1_rich_path),
            "richImportSha256": input_hashes["v1Rich"],
            "richRows": len(v1_rich),
        },
        "nutritionEvidence": {
            "provider": "Open Food Facts",
            "sourceSnapshot": source.get("path"),
            "sourceSnapshotSha256": source.get("sha256"),
            "candidateArtifact": str(candidate_path),
            "candidateArtifactSha256": input_hashes["offCandidates"],
            "license": "ODbL-1.0",
            "attributionRequired": True,
            "shareAlikeReviewRequired": True,
        },
        "selectionPolicy": {
            "identity": "Exact candidate GTIN must equal the selected v1 max primary GTIN.",
            "duplicates": "All 46 duplicate-GTIN groups are excluded and routed to admin review.",
            "secondaryGtins": "Candidates whose matched GTIN is not the selected primary GTIN are excluded and routed to identity review.",
            "nutrition": "Complete plausible OFF per-100g calories/protein/fat/carbs; optional fields copied only when source-reported.",
            "sodium": "OFF source-reported sodium is copied; salt remains separately preserved.",
            "v1": "The v1 files are immutable inputs and are not overwritten.",
        },
        "counts": validation["counts"],
        "artifacts": [
            {"role": "TEST_DEFAULT_RICH_V2", "file": rich_v2_path.name, "rows": len(v2_rows), "sha256": sha256(rich_v2_path)},
            {"role": "OFF_NUTRITION_FIELD_PROVENANCE", "file": provenance_path.name, "rows": len(provenance_rows), "sha256": sha256(provenance_path)},
            {"role": "DUPLICATE_GTIN_REVIEW", "file": duplicate_path.name, "rows": len(duplicate_rows), "groups": 46, "sha256": sha256(duplicate_path)},
            {"role": "PRIMARY_IDENTITY_REVIEW", "file": identity_path.name, "rows": len(identity_review_rows), "sha256": sha256(identity_path)},
            {"role": "VALIDATION", "file": validation_path.name, "status": "PASS", "sha256": sha256(validation_path)},
        ],
        "blockers": [
            "Retailer identity/page commercial-use and persistent-storage rights are not recorded.",
            "OFF ODbL attribution is recorded, but distribution/share-alike compliance requires owner review.",
            "Duplicate and secondary-GTIN identity review queues are not approved.",
            "No production database import is authorized by this manifest.",
        ],
    }
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({
        "status": "PASS",
        "manifest": str(manifest_path),
        "manifestSha256": sha256(manifest_path),
        "counts": validation["counts"],
    }, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

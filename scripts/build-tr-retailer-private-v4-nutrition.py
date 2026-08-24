#!/usr/bin/env python3
"""Build PRIVATE_TEST_ONLY TR rich v4 from exact-GTIN frozen nutrition evidence."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
from collections import Counter, defaultdict
from pathlib import Path


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest().upper()


def valid_gtin(gtin: str) -> bool:
    if not gtin.isdigit() or len(gtin) not in {8, 12, 13, 14}:
        return False
    total, weight = 0, 3
    for digit in reversed(gtin[:-1]):
        total += int(digit) * weight
        weight = 1 if weight == 3 else 3
    return (10 - total % 10) % 10 == int(gtin[-1])


def num(value: object) -> str:
    return "" if value in (None, "") else format(float(value), ".12g")


def plausible(row: dict[str, str]) -> bool:
    try:
        kcal, protein, fat, carbs = (float(row[k]) for k in ("calories", "protein", "fat", "carbs"))
    except (KeyError, ValueError, TypeError):
        return False
    return 0 <= kcal <= 1000 and all(0 <= x <= 100 for x in (protein, fat, carbs)) and protein + fat + carbs <= 110


def load_csv(path: Path) -> tuple[list[str], list[dict[str, str]]]:
    with path.open("r", encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        return list(reader.fieldnames or []), list(reader)


def build_row(fields: list[str], item: dict, evidence: dict) -> dict[str, str]:
    gtin = str(evidence["gtin"])
    nutrition = evidence["nutrition"]
    values = {
        "catalog_type": "BRANDED_PRODUCT", "data_source": "ADMIN_IMPORT",
        "nutrition_basis": "SOURCE_REPORTED", "barcode": gtin, "source_key": f"barcode:{gtin}",
        "name": str(item.get("name") or evidence.get("sourceName") or ""),
        "brand": str(item.get("brand") or evidence.get("sourceBrand") or ""),
        "calories": num(nutrition.get("calories")), "protein": num(nutrition.get("protein")),
        "fat": num(nutrition.get("fat")), "carbs": num(nutrition.get("carbs")),
        "fiber": num(nutrition.get("fiber")), "sugar": num(nutrition.get("sugar")), "sodium": "",
        "serving_size_grams": "", "serving_unit": "", "market_region": "TR", "market_regions": "TR",
        "display_name_tr": str(item.get("name") or ""), "short_display_name_tr": str(item.get("name") or ""),
        "aliases_tr": "", "image_url": "", "external_image_url": "", "display_image_url": "",
        "allergens": "", "nutri_score": "unknown", "source_catalog_id": str(item.get("catalogId") or ""),
        "source_providers": str(evidence.get("source") or "FREE_INDEX"),
        "source_urls_json": json.dumps({"free_nutrition_evidence": evidence.get("sourceUrl")}, ensure_ascii=False, separators=(",", ":"), sort_keys=True),
        "source_salt_per_100g": num(nutrition.get("salt")), "serving_options_json": "",
    }
    row = {field: values.get(field, "") for field in fields}
    if not valid_gtin(gtin) or not plausible(row) or not row["name"] or not row["brand"]:
        raise SystemExit(f"Invalid v4 row {gtin} {row['name']}")
    return row


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--v3-rich", type=Path, required=True)
    parser.add_argument("--candidates", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    fields, base = load_csv(args.v3_rich)
    candidates = json.loads(args.candidates.read_text(encoding="utf-8-sig"))
    base_gtins = {row["barcode"] for row in base}
    singles = [item for item in candidates if item.get("status") == "EXACT_GTIN_SINGLE_NUTRITION"]
    by_gtin: dict[str, list[dict]] = defaultdict(list)
    for item in singles:
        by_gtin[str(item["candidates"][0]["gtin"])].append(item)
    additions, provenance, review = [], [], []
    overlap = 0
    for gtin in sorted(by_gtin):
        items = by_gtin[gtin]
        if len(items) != 1:
            review.append({"gtin": gtin, "catalogIds": sorted(str(x.get("catalogId")) for x in items), "reason": "MULTIPLE_TARGET_PRODUCTS_FOR_GTIN"})
            continue
        if gtin in base_gtins:
            overlap += 1
            continue
        item, evidence = items[0], items[0]["candidates"][0]
        additions.append(build_row(fields, item, evidence))
        provenance.append({
            "gtin": gtin, "catalogId": item.get("catalogId"), "matchMethod": "EXACT_GTIN_SINGLE_NUTRITION",
            "source": evidence.get("source"), "sourceFile": evidence.get("sourceFile"),
            "sourceUrl": evidence.get("sourceUrl"), "sourceProductId": evidence.get("sourceProductId"),
            "nutritionBasis": evidence.get("nutritionBasis"), "nutrition": evidence.get("nutrition"),
        })
    output = sorted(base + additions, key=lambda row: row["barcode"])
    counts = Counter(row["barcode"] for row in output)
    if any(count != 1 for count in counts.values()) or any(not valid_gtin(row["barcode"]) or not plausible(row) for row in output):
        raise SystemExit("v4 output validation failed")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    csv_path = args.output_dir / "tr-retailer-test-rich-v4-import.csv"
    with csv_path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fields, quoting=csv.QUOTE_ALL, lineterminator="\n")
        writer.writeheader(); writer.writerows(output)
    provenance_path = args.output_dir / "tr-free-nutrition-provenance.json"
    review_path = args.output_dir / "tr-free-nutrition-duplicate-target-review.json"
    provenance_path.write_text(json.dumps(provenance, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    review_path.write_text(json.dumps(review, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    report = {
        "status": "PASS", "classification": "PRIVATE_TEST_ONLY", "baseRows": len(base),
        "exactSingleRows": len(singles), "uniqueCandidateGtins": len(by_gtin),
        "duplicateTargetGtins": len(review), "baseOverlapGtins": overlap,
        "netNewRows": len(additions), "outputRows": len(output), "remainingTo10000": max(0, 10000 - len(output)),
        "inputs": {"v3Sha256": sha256(args.v3_rich), "candidatesSha256": sha256(args.candidates)},
        "outputs": {"csvSha256": sha256(csv_path), "provenanceSha256": sha256(provenance_path), "reviewSha256": sha256(review_path)},
    }
    report_path = args.output_dir / "report.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Prioritize nutrition-ready TR products that still require an authorized GTIN.

This file-only job never assigns a barcode. Existing web/OFF/fuzzy candidates
remain review references unless source rights, content checksum and explicit
admin approval are all present.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlparse


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest().upper()


def assert_hash(path: Path, expected: str) -> str:
    actual = sha256(path)
    if actual != expected.strip().upper():
        raise SystemExit(f"Checksum mismatch for {path}: expected {expected}, got {actual}")
    return actual


def load(path: Path) -> object:
    return json.loads(path.read_text(encoding="utf-8"))


def rows(document: object, key: str = "products") -> list[dict[str, object]]:
    if isinstance(document, list):
        return document
    if isinstance(document, dict):
        value = document.get(key, [])
        return value if isinstance(value, list) else []
    return []


def decimal(value: object) -> float | None:
    if value is None or str(value).strip() == "":
        return None
    try:
        return float(str(value).replace(",", "."))
    except ValueError:
        return None


def plausible_core(product: dict[str, object]) -> bool:
    nutrition = product.get("nutrition") or {}
    core = (
        decimal(nutrition.get("energyKcal", nutrition.get("calories"))),
        decimal(nutrition.get("protein")),
        decimal(nutrition.get("fat")),
        decimal(nutrition.get("carbohydrate", nutrition.get("carbs"))),
    )
    if any(value is None for value in core):
        return False
    calories, protein, fat, carbs = core
    return (
        0 <= calories <= 1000
        and all(0 <= value <= 100 for value in (protein, fat, carbs))
        and protein + fat + carbs <= 110
    )


def valid_gtin(value: object) -> bool:
    gtin = str(value or "").strip()
    if len(gtin) not in {8, 12, 13, 14} or not gtin.isdigit():
        return False
    total = 0
    weight = 3
    for digit in reversed(gtin[:-1]):
        total += int(digit) * weight
        weight = 1 if weight == 3 else 3
    return (10 - total % 10) % 10 == int(gtin[-1])


def valid_url(value: object) -> bool:
    parsed = urlparse(str(value or ""))
    return parsed.scheme == "https" and bool(parsed.netloc)


def write_tsv(path: Path, fields: list[str], output_rows: list[dict[str, object]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields, delimiter="\t", lineterminator="\n")
        writer.writeheader()
        writer.writerows(output_rows)


def index_by(items: list[dict[str, object]], field: str) -> dict[str, dict[str, object]]:
    return {str(item.get(field) or "").strip(): item for item in items if str(item.get(field) or "").strip()}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--queue", required=True)
    parser.add_argument("--catalog", required=True)
    parser.add_argument("--excluded", required=True)
    parser.add_argument("--enrichment", required=True)
    parser.add_argument("--confirmed", required=True)
    parser.add_argument("--external-verified", required=True)
    parser.add_argument("--manual-review", required=True)
    parser.add_argument("--web-search-state", required=True)
    parser.add_argument("--source-registry", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--expected-queue-sha256", required=True)
    parser.add_argument("--expected-catalog-sha256", required=True)
    parser.add_argument("--expected-excluded-sha256", required=True)
    parser.add_argument("--expected-enrichment-sha256", required=True)
    parser.add_argument("--expected-confirmed-sha256", required=True)
    parser.add_argument("--expected-external-sha256", required=True)
    parser.add_argument("--expected-manual-sha256", required=True)
    parser.add_argument("--expected-web-state-sha256", required=True)
    parser.add_argument("--expected-registry-sha256", required=True)
    parser.add_argument("--generated-at")
    args = parser.parse_args()

    input_paths = {
        "queue": Path(args.queue).resolve(),
        "catalog": Path(args.catalog).resolve(),
        "excluded": Path(args.excluded).resolve(),
        "enrichment": Path(args.enrichment).resolve(),
        "confirmed": Path(args.confirmed).resolve(),
        "externalVerified": Path(args.external_verified).resolve(),
        "manualReview": Path(args.manual_review).resolve(),
        "webSearchState": Path(args.web_search_state).resolve(),
        "sourceRegistry": Path(args.source_registry).resolve(),
    }
    expected = {
        "queue": args.expected_queue_sha256,
        "catalog": args.expected_catalog_sha256,
        "excluded": args.expected_excluded_sha256,
        "enrichment": args.expected_enrichment_sha256,
        "confirmed": args.expected_confirmed_sha256,
        "externalVerified": args.expected_external_sha256,
        "manualReview": args.expected_manual_sha256,
        "webSearchState": args.expected_web_state_sha256,
        "sourceRegistry": args.expected_registry_sha256,
    }
    input_hashes = {name: assert_hash(path, expected[name]) for name, path in input_paths.items()}

    queue_document = load(input_paths["queue"])
    catalog_document = load(input_paths["catalog"])
    excluded_document = load(input_paths["excluded"])
    enrichment_document = load(input_paths["enrichment"])
    confirmed_document = load(input_paths["confirmed"])
    external_document = load(input_paths["externalVerified"])
    manual_document = load(input_paths["manualReview"])
    web_state_document = load(input_paths["webSearchState"])
    registry = load(input_paths["sourceRegistry"])

    source_registry = {source["id"]: source for source in registry.get("sources", [])}
    if source_registry.get("PUBLIC_RETAILER_OR_MANUFACTURER_PAGE", {}).get("directImport") is not False:
        raise SystemExit("Public page evidence registry gate is missing")

    catalog = rows(catalog_document)
    catalog_by_id = index_by(catalog, "catalogId")
    excluded_ids = {str(item.get("catalogId") or "") for item in rows(excluded_document)}
    queued = rows(queue_document)
    target = []
    for item in queued:
        catalog_id = str(item.get("catalogId") or "")
        product = catalog_by_id.get(catalog_id)
        if (
            product
            and catalog_id not in excluded_ids
            and not product.get("isMultipack")
            and str(product.get("name") or "").strip()
            and str(product.get("brand") or "").strip()
            and plausible_core(product)
        ):
            target.append((item, product))
    if len(queued) != 2581 or len(target) != 1925:
        raise SystemExit(f"Queue contract changed: queued={len(queued)} nutritionReady={len(target)}")

    enrichment = index_by(rows(enrichment_document), "migrosSourceProductId")
    confirmed = index_by(rows(confirmed_document), "migrosSourceProductId")
    external = index_by(rows(external_document), "migrosSourceProductId")
    web_state = index_by(rows(web_state_document), "migrosSourceProductId")
    manual_accepted = index_by(rows(manual_document, "accepted"), "catalogId")
    brand_counts = Counter(str(product.get("brand") or "").strip() for _, product in target)

    output_rows: list[dict[str, object]] = []
    evidence_rows: list[dict[str, object]] = []
    brand_sources: defaultdict[str, Counter[str]] = defaultdict(Counter)
    brand_categories: defaultdict[str, Counter[str]] = defaultdict(Counter)
    priority_counts: Counter[str] = Counter()
    channel_counts: Counter[str] = Counter()
    enrichment_status_counts: Counter[str] = Counter()

    for queued_item, product in target:
        catalog_id = str(queued_item.get("catalogId") or "")
        brand = str(product.get("brand") or "").strip()
        source = str(queued_item.get("source") or product.get("source") or "UNKNOWN").strip().upper()
        source_ids = queued_item.get("sourceProductIds") or {}
        migros_id = str(source_ids.get("migros") or "").strip()
        evidence_candidates: list[tuple[str, dict[str, object]]] = []
        for evidence_class, indexed, lookup in (
            ("CONFIRMED_HISTORY", confirmed, migros_id),
            ("EXTERNAL_VERIFIED_HISTORY", external, migros_id),
            ("WEB_SEARCH_HISTORY", web_state, migros_id),
            ("MANUAL_REVIEW_HISTORY", manual_accepted, catalog_id),
            ("ENRICHMENT_REVIEW_HISTORY", enrichment, migros_id),
        ):
            if lookup and lookup in indexed:
                evidence_candidates.append((evidence_class, indexed[lookup]))

        candidate_gtins: set[str] = set()
        candidate_methods: set[str] = set()
        candidate_urls: set[str] = set()
        official_manufacturer_reference = False
        enrichment_status = "NO_EXISTING_EVIDENCE"
        for evidence_class, evidence in evidence_candidates:
            if evidence_class == "ENRICHMENT_REVIEW_HISTORY":
                enrichment_status = str(evidence.get("status") or "UNKNOWN")
                enrichment_status_counts[enrichment_status] += 1
            raw_gtins = [evidence.get("barcode"), evidence.get("primaryBarcode")]
            raw_gtins.extend(evidence.get("candidateBarcodes") or [])
            raw_gtins.extend(evidence.get("validBarcodes") or [])
            for gtin in raw_gtins:
                if valid_gtin(gtin):
                    candidate_gtins.add(str(gtin).strip())
            for method in evidence.get("methods") or []:
                candidate_methods.add(str(method))
            if evidence.get("method"):
                candidate_methods.add(str(evidence["method"]))
            nested_evidence = list(evidence.get("evidence") or [])
            nested_evidence.extend(evidence.get("evidenceSources") or [])
            for record in nested_evidence:
                if record.get("method"):
                    candidate_methods.add(str(record["method"]))
                url = record.get("sourceUrl") or record.get("url")
                if valid_url(url):
                    candidate_urls.add(str(url))
                official_manufacturer_reference = official_manufacturer_reference or bool(record.get("officialManufacturer"))
                for nested in record.get("evidenceSources") or []:
                    url = nested.get("url")
                    if valid_url(url):
                        candidate_urls.add(str(url))
                    official_manufacturer_reference = official_manufacturer_reference or bool(nested.get("officialManufacturer"))

        if evidence_candidates and candidate_gtins:
            priority_score = 95 if official_manufacturer_reference else 85
            priority_level = "P0"
            recommended_channel = "ADMIN_REVIEW_EXISTING_CANDIDATE_AND_OBTAIN_AUTHORIZED_CONFIRMATION"
            blocker = "EXISTING_GTIN_CANDIDATE_IS_REVIEW_ONLY"
        elif brand_counts[brand] >= 20:
            priority_score = 80
            priority_level = "P1"
            recommended_channel = "BRAND_OWNER_OR_GS1_BATCH_FEED"
            blocker = "MISSING_AUTHORIZED_GTIN_EVIDENCE"
        elif brand_counts[brand] >= 5:
            priority_score = 70
            priority_level = "P2"
            recommended_channel = "BRAND_OWNER_OR_AUTHORIZED_SUPPLIER_FEED"
            blocker = "MISSING_AUTHORIZED_GTIN_EVIDENCE"
        else:
            priority_score = 60
            priority_level = "P3"
            recommended_channel = "AUTHORIZED_SUPPLIER_OR_USER_LABEL_CAPTURE"
            blocker = "MISSING_AUTHORIZED_GTIN_EVIDENCE"
        if product.get("packageAmount") is not None and str(product.get("packageUnit") or "").strip():
            priority_score += 5

        priority_counts[priority_level] += 1
        channel_counts[recommended_channel] += 1
        brand_sources[brand][source] += 1
        brand_categories[brand][str(product.get("mainCategory") or "UNKNOWN")] += 1
        blockers = [
            blocker,
            "MISSING_EVIDENCE_CONTENT_CHECKSUM",
            "COMMERCIAL_USE_AND_STORAGE_RIGHTS_NOT_RECORDED",
            "ADMIN_REVIEW_NOT_APPROVED",
        ]
        output_rows.append({
            "priorityScore": priority_score,
            "priorityLevel": priority_level,
            "catalogId": catalog_id,
            "source": source,
            "sourceProductId": migros_id or str(source_ids.get(source.lower()) or ""),
            "name": product.get("name", ""),
            "brand": brand,
            "brandBatchRows": brand_counts[brand],
            "mainCategory": product.get("mainCategory", ""),
            "subcategory": product.get("subcategory", ""),
            "packageAmount": product.get("packageAmount", ""),
            "packageUnit": product.get("packageUnit", ""),
            "nutritionStatus": "COMPLETE_PLAUSIBLE_CORE",
            "recommendedEvidenceChannel": recommended_channel,
            "existingEvidenceClassifications": ";".join(sorted(item[0] for item in evidence_candidates)),
            "existingEnrichmentStatus": enrichment_status,
            "candidateGtinsReviewOnly": ";".join(sorted(candidate_gtins)),
            "candidateMethods": ";".join(sorted(candidate_methods)),
            "candidateUrls": ";".join(sorted(candidate_urls)),
            "officialManufacturerReference": str(official_manufacturer_reference).lower(),
            "assignedBarcode": "",
            "promotionStatus": "BLOCKED_REVIEW_QUEUE_ONLY",
            "promotionBlockers": ";".join(blockers),
        })
        for evidence_class, evidence in evidence_candidates:
            evidence_rows.append({
                "catalogId": catalog_id,
                "evidenceClassification": evidence_class,
                "sourceRecordStatus": evidence.get("status", ""),
                "candidateGtins": ";".join(sorted(candidate_gtins)),
                "candidateMethods": ";".join(sorted(candidate_methods)),
                "candidateUrls": ";".join(sorted(candidate_urls)),
                "officialManufacturerReference": str(official_manufacturer_reference).lower(),
                "evidenceContentChecksum": "",
                "commercialUseAllowed": "UNVERIFIED",
                "persistentStorageAllowed": "UNVERIFIED",
                "reviewDecision": "PENDING_ADMIN_REVIEW",
                "promotionStatus": "BLOCKED",
            })

    output_rows.sort(key=lambda row: (-int(row["priorityScore"]), -int(row["brandBatchRows"]), str(row["brand"]), str(row["name"]), str(row["catalogId"])))
    brand_rows = []
    for brand, count in sorted(brand_counts.items(), key=lambda item: (-item[1], item[0])):
        channel = "BRAND_OWNER_OR_GS1_BATCH_FEED" if count >= 20 else (
            "BRAND_OWNER_OR_AUTHORIZED_SUPPLIER_FEED" if count >= 5 else "AUTHORIZED_SUPPLIER_OR_USER_LABEL_CAPTURE"
        )
        brand_rows.append({
            "brand": brand,
            "nutritionReadyBarcodeGapRows": count,
            "sources": ";".join(f"{name}:{rows}" for name, rows in sorted(brand_sources[brand].items())),
            "topCategories": ";".join(f"{name}:{rows}" for name, rows in brand_categories[brand].most_common(5)),
            "recommendedEvidenceChannel": channel,
            "promotionStatus": "ACQUISITION_PLAN_ONLY",
        })

    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    queue_path = output_dir / "tr-nutrition-ready-barcode-evidence-priority.tsv"
    evidence_path = output_dir / "tr-existing-barcode-evidence-review.tsv"
    brand_path = output_dir / "tr-brand-evidence-acquisition-plan.tsv"
    report_path = output_dir / "report.json"
    manifest_path = output_dir / "manifest.json"
    write_tsv(queue_path, list(output_rows[0]), output_rows)
    evidence_fields = list(evidence_rows[0]) if evidence_rows else [
        "catalogId", "evidenceClassification", "sourceRecordStatus", "candidateGtins",
        "candidateMethods", "candidateUrls", "officialManufacturerReference",
        "evidenceContentChecksum", "commercialUseAllowed", "persistentStorageAllowed",
        "reviewDecision", "promotionStatus",
    ]
    write_tsv(evidence_path, evidence_fields, evidence_rows)
    write_tsv(brand_path, list(brand_rows[0]), brand_rows)

    assigned_count = sum(1 for row in output_rows if str(row["assignedBarcode"]).strip())
    promotable_count = sum(1 for row in output_rows if row["promotionStatus"] == "PROMOTABLE")
    invalid_candidates = sum(
        1 for row in output_rows
        for value in str(row["candidateGtinsReviewOnly"]).split(";")
        if value and not valid_gtin(value)
    )
    generated_at = args.generated_at or datetime.now(timezone.utc).isoformat()
    report = {
        "schemaVersion": 1,
        "generatedAt": generated_at,
        "status": "PASS",
        "classification": "IDENTITY_EVIDENCE_ACQUISITION_QUEUE_NOT_CATALOG_IMPORT",
        "counts": {
            "requiredBarcodeQueueRows": len(queued),
            "nutritionReadyPriorityRows": len(output_rows),
            "sourceRows": dict(Counter(row["source"] for row in output_rows)),
            "priorityLevels": dict(sorted(priority_counts.items())),
            "recommendedChannels": dict(sorted(channel_counts.items())),
            "rowsWithExistingEvidenceHistory": sum(1 for row in output_rows if row["existingEvidenceClassifications"]),
            "existingEvidenceReviewRows": len(evidence_rows),
            "rowsWithReviewOnlyCandidateGtins": sum(1 for row in output_rows if row["candidateGtinsReviewOnly"]),
            "officialManufacturerReferenceRows": sum(1 for row in output_rows if row["officialManufacturerReference"] == "true"),
            "assignedBarcodes": assigned_count,
            "promotableRows": promotable_count,
            "blockedRows": len(output_rows) - promotable_count,
            "invalidReviewCandidateGtins": invalid_candidates,
            "brandAcquisitionBatches": len(brand_rows),
        },
        "enrichmentStatusCounts": dict(sorted(enrichment_status_counts.items())),
        "gates": {
            "noBarcodeManufactured": assigned_count == 0,
            "noFuzzyPromotion": promotable_count == 0,
            "allRowsNutritionReady": len(output_rows) == 1925,
            "allReviewCandidateGtinsChecksumValid": invalid_candidates == 0,
            "productionImportAuthorized": False,
        },
        "decision": "Acquire authorized GTIN evidence in priority order; existing web/OFF candidates remain review-only and no barcode is assigned by this artifact.",
    }
    if not all(value is True or value is False and key == "productionImportAuthorized" for key, value in report["gates"].items()):
        raise SystemExit("Priority queue validation failed")
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    manifest = {
        "schemaVersion": 1,
        "manifestId": "tr-nutrition-ready-barcode-evidence-priority-v1",
        "generatedAt": generated_at,
        "releaseClassification": "REVIEW_AND_ACQUISITION_QUEUE_ONLY_NOT_IMPORTABLE",
        "inputHashes": input_hashes,
        "sourcePolicy": {
            "acceptedForFuturePromotion": ["GS1_OR_GDSN_FEED", "BRAND_OWNER_FEED", "AUTHORIZED_SUPPLIER_FEED", "USER_SUBMITTED_LABEL"],
            "reviewOnly": ["PUBLIC_RETAILER_PAGE", "PUBLIC_DISTRIBUTOR_PAGE", "OFF_NAME_OR_PACKAGE_MATCH", "FUZZY_MATCH"],
            "rule": "No GTIN may be created or assigned without checksum-valid authorized evidence and explicit admin approval.",
        },
        "counts": report["counts"],
        "artifacts": [
            {"role": "PRODUCT_PRIORITY_QUEUE", "file": queue_path.name, "rows": len(output_rows), "sha256": sha256(queue_path)},
            {"role": "EXISTING_EVIDENCE_REVIEW", "file": evidence_path.name, "rows": len(evidence_rows), "sha256": sha256(evidence_path)},
            {"role": "BRAND_ACQUISITION_PLAN", "file": brand_path.name, "rows": len(brand_rows), "sha256": sha256(brand_path)},
            {"role": "VALIDATION_REPORT", "file": report_path.name, "status": "PASS", "sha256": sha256(report_path)},
        ],
        "productionImportAuthorized": False,
    }
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({
        "status": "PASS",
        "manifest": str(manifest_path),
        "manifestSha256": sha256(manifest_path),
        "counts": report["counts"],
    }, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

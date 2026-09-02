#!/usr/bin/env python3
"""Build review-only queues for barcode/nutrition completion.

This script never imports or promotes products. It joins the current catalog
gap queue to previously collected exact-match evidence and emits deterministic
review artifacts.
"""

import csv
import json
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "outputs" / "catalog-completion-research-aug29"
CURRENT_GAP = ROOT / "outputs" / "product-catalog-aug21" / "development-queues" / "nutrition-gap.tsv"
NUTRITION_CANDIDATES = ROOT / "outputs" / "product-catalog-aug08" / "tr-free-nutrition-index-match-v1-20260805" / "exact-gtin-free-nutrition-candidates.json"
BARCODE_CANDIDATES = ROOT / "outputs" / "product-catalog-aug08" / "tr-all-nutrition-only-v1-20260807" / "exact-free-index-barcode-candidates.json"


def read_tsv(path):
    with path.open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle, delimiter="\t"))


def write_tsv(path, fields, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields, delimiter="\t", extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def main():
    gaps = read_tsv(CURRENT_GAP)
    nutrition = json.loads(NUTRITION_CANDIDATES.read_text(encoding="utf-8-sig"))
    barcodes = json.loads(BARCODE_CANDIDATES.read_text(encoding="utf-8-sig"))
    nutrition_by_id = {row["catalogId"]: row for row in nutrition}

    nutrition_review = []
    current_status = Counter()
    for gap in gaps:
        candidate = nutrition_by_id.get(gap["source_catalog_id"])
        if candidate is None and gap.get("barcode"):
            candidate = nutrition_by_id.get(f"GTIN:{gap['barcode']}")
        status = candidate["status"] if candidate else "NOT_IN_PRIOR_SCAN"
        current_status[status] += 1
        if status not in {"EXACT_GTIN_SINGLE_NUTRITION", "EXACT_GTIN_NUTRITION_CONFLICT"}:
            continue
        evidence = candidate.get("candidates", [])
        nutrition_review.append({
            "decision": "REVIEW_EXACT_GTIN_NUTRITION" if status == "EXACT_GTIN_SINGLE_NUTRITION" else "QUARANTINE_CONFLICT",
            "priority": gap["priority"],
            "category": gap["category"],
            "barcode": gap["barcode"],
            "name": gap["name"],
            "brand": gap["brand"],
            "missing_fields": gap["missing_fields"],
            "source_catalog_id": gap["source_catalog_id"],
            "match_status": status,
            "candidate_count": len(evidence),
            "evidence_json": json.dumps(evidence, ensure_ascii=False, separators=(",", ":")),
        })

    barcode_review = []
    barcode_status = Counter(row["matchStatus"] for row in barcodes)
    exact_methods = Counter()
    for row in barcodes:
        if row["matchStatus"] not in {"EXACT_SINGLE_GTIN", "EXACT_COLLISION_REVIEW"}:
            continue
        method = row.get("matchMethod", "")
        if row["matchStatus"] == "EXACT_SINGLE_GTIN":
            exact_methods[method] += 1
        barcode_review.append({
            "decision": "REVIEW_EXACT_BARCODE" if row["matchStatus"] == "EXACT_SINGLE_GTIN" else "QUARANTINE_COLLISION",
            "catalog_id": row["catalogId"],
            "name": row["name"],
            "brand": row.get("brand", ""),
            "package_key": row.get("packageKey", ""),
            "match_status": row["matchStatus"],
            "match_method": method,
            "candidate_gtins": ";".join(row.get("candidateGtins", [])),
            "evidence_json": json.dumps(row.get("evidence", []), ensure_ascii=False, separators=(",", ":")),
        })

    nutrition_review.sort(key=lambda row: (row["decision"], row["priority"], row["category"], row["name"]))
    barcode_review.sort(key=lambda row: (row["decision"], row["match_method"], row["name"]))
    write_tsv(OUT / "barcode-present-nutrition-review.tsv", list(nutrition_review[0].keys()), nutrition_review)
    write_tsv(OUT / "nutrition-present-barcode-review.tsv", list(barcode_review[0].keys()), barcode_review)

    exact_nutrition = [row for row in nutrition_review if row["decision"] == "REVIEW_EXACT_GTIN_NUTRITION"]
    exact_barcode = [row for row in barcode_review if row["decision"] == "REVIEW_EXACT_BARCODE"]
    report = {
        "schemaVersion": 1,
        "classification": "REVIEW_ONLY_NOT_IMPORTABLE",
        "policy": {
            "noAutomaticDatabaseWrites": True,
            "barcodeNutritionRequiresExactGtin": True,
            "barcodeCompletionRequiresIdentityAndPackageReview": True,
            "conflictsRemainQuarantined": True,
        },
        "currentNutritionGapRows": len(gaps),
        "currentNutritionGapMatchStatus": dict(current_status),
        "exactNutritionReviewRows": len(exact_nutrition),
        "exactNutritionByPriority": dict(Counter(row["priority"] for row in exact_nutrition)),
        "exactNutritionByCategory": dict(Counter(row["category"] for row in exact_nutrition)),
        "nutritionConflictRows": sum(row["decision"] == "QUARANTINE_CONFLICT" for row in nutrition_review),
        "nutritionReadyMissingBarcodeStatus": dict(barcode_status),
        "exactBarcodeReviewRows": len(exact_barcode),
        "exactBarcodeMatchMethods": dict(exact_methods),
        "barcodeCollisionRows": sum(row["decision"] == "QUARANTINE_COLLISION" for row in barcode_review),
        "outputs": {
            "barcodePresentNutritionReview": str(OUT / "barcode-present-nutrition-review.tsv"),
            "nutritionPresentBarcodeReview": str(OUT / "nutrition-present-barcode-review.tsv"),
        },
    }
    (OUT / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()

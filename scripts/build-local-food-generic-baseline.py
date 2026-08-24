#!/usr/bin/env python3
"""Normalize and triage the approved USDA generic baseline for the D2 pipeline."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import unicodedata
from collections import Counter
from pathlib import Path


OUTPUT_FIELDS = [
    "catalog_type", "data_source", "source_registry_id", "source_key", "name",
    "display_name_en", "display_name_tr", "alias_en", "alias_tr", "market_region",
    "preparation_state", "nutrition_basis", "calories", "protein", "fat", "carbs",
    "fiber", "sugar", "sodium", "potassium", "cholesterol", "calcium", "iron",
    "magnesium", "zinc", "vitamin_a", "vitamin_c", "vitamin_d", "vitamin_e",
    "vitamin_b12", "serving_size_grams", "serving_unit", "serving_options_json",
    "source_ref", "source_version", "license_id", "provenance_note", "source_record_id",
    "calculation_method", "calculation_version", "ingredient_links_json",
    "estimation_method", "confidence_score", "approval_status", "approved_by",
    "approved_at", "dish_family_key", "dish_variant_key", "barcode", "review_reasons",
]


def slug(value: str) -> str:
    value = unicodedata.normalize("NFKD", value)
    value = "".join(c for c in value if not unicodedata.combining(c)).lower()
    return re.sub(r"[^a-z0-9]+", "_", value).strip("_") or "unnamed"


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def normalize(row: dict[str, str], alias_review: dict[str, str] | None = None, prep_review: dict[str, str] | None = None, source_version: str = "baseline-export-2026-07-24") -> tuple[dict[str, str], list[str]]:
    fdc_id = row["fdc_id"].strip()
    prep_review = prep_review or {}
    reviewed_prep = prep_review.get("preparation_state", "").strip().upper() if prep_review.get("decision") == "APPROVE" else ""
    prep = reviewed_prep or row["preparation_state"].strip().upper()
    reasons: list[str] = []
    if prep == "UNSPECIFIED" or not prep:
        reasons.append("PREPARATION_STATE_REVIEW")
    if not row.get("fiber", "").strip():
        reasons.append("MISSING_REQUIRED_FIBER")
    if not row.get("sodium", "").strip():
        reasons.append("MISSING_REQUIRED_SODIUM")
    micro_fields = ("potassium", "calcium", "iron", "magnesium", "zinc", "vitamin_a", "vitamin_c", "vitamin_d", "vitamin_e", "vitamin_b12")
    if sum(bool(row.get(field, "").strip()) for field in micro_fields) < 2:
        reasons.append("INSUFFICIENT_MICRONUTRIENTS")
    alias_review = alias_review or {}
    decision = alias_review.get("decision", "")
    if decision == "QUARANTINE":
        reasons.append("SOURCE_IDENTITY_MISMATCH")
    elif decision != "APPROVE" or not alias_review.get("alias_tr", "").strip() or not alias_review.get("display_name_tr", "").strip():
        reasons.append("MISSING_REVIEWED_TR_ALIAS")
    serving = float(row["serving_size_grams"])
    serving_json = json.dumps([{
        "label": "100_g", "gramWeight": serving, "defaultOption": True,
        "labels": {"EN": "100 g", "TR": "100 g"},
    }], ensure_ascii=False, separators=(",", ":"))
    result = {field: "" for field in OUTPUT_FIELDS}
    for field in ("catalog_type", "data_source", "name", "alias_en", "market_region", "preparation_state",
                  "calories", "protein", "fat", "carbs", "fiber", "sugar", "sodium", "potassium",
                  "cholesterol", "calcium", "iron", "magnesium", "zinc", "vitamin_a", "vitamin_c",
                  "vitamin_d", "vitamin_e", "vitamin_b12", "serving_size_grams", "serving_unit"):
        result[field] = row.get(field, "").strip()
    result.update({
        "source_registry_id": "USDA_FOODDATA_PUBLIC",
        "preparation_state": prep,
        "source_key": f"USDA_FOODDATA:GENERIC_INGREDIENT:GLOBAL:fdc_{fdc_id}_{slug(prep)}",
        "display_name_en": row["name"].strip(),
        "display_name_tr": alias_review.get("display_name_tr", "").strip(),
        "alias_tr": alias_review.get("alias_tr", "").strip(),
        "nutrition_basis": "SOURCE_REPORTED",
        "serving_options_json": serving_json,
        "source_ref": f"https://fdc.nal.usda.gov/fdc-app.html#/food-details/{fdc_id}/nutrients",
        "source_version": source_version,
        "license_id": "CC0-1.0",
        "provenance_note": row.get("source_note", "").strip() + "; nutrient units normalized as g or mg per 100 g",
        "source_record_id": fdc_id,
        "review_reasons": "|".join(sorted(set(reasons))),
    })
    return result, sorted(set(reasons))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--staging", required=True, type=Path)
    parser.add_argument("--release", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--alias-review", type=Path)
    parser.add_argument("--preparation-review", type=Path)
    parser.add_argument("--source-version", default="baseline-export-2026-07-24")
    args = parser.parse_args()
    with args.input.open(encoding="utf-8-sig", newline="") as handle:
        source_rows = list(csv.DictReader(handle))
    alias_reviews: dict[str, dict[str, str]] = {}
    if args.alias_review:
        with args.alias_review.open(encoding="utf-8-sig", newline="") as handle:
            review_rows = list(csv.DictReader(handle))
        alias_reviews = {row["fdc_id"].strip(): row for row in review_rows}
        if len(alias_reviews) != len(review_rows):
            raise ValueError("Alias review contains duplicate fdc_id values")
    prep_reviews: dict[str, dict[str, str]] = {}
    if args.preparation_review:
        with args.preparation_review.open(encoding="utf-8-sig", newline="") as handle:
            prep_rows = list(csv.DictReader(handle))
        prep_reviews = {row["fdc_id"].strip(): row for row in prep_rows}
        if len(prep_reviews) != len(prep_rows):
            raise ValueError("Preparation review contains duplicate fdc_id values")
    normalized = [normalize(row, alias_reviews.get(row["fdc_id"].strip()), prep_reviews.get(row["fdc_id"].strip()), args.source_version) for row in source_rows]
    staging_rows = [row for row, _ in normalized]
    release_rows = [row for row, reasons in normalized if not reasons]
    for path, rows in ((args.staging, staging_rows), (args.release, release_rows)):
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=OUTPUT_FIELDS)
            writer.writeheader(); writer.writerows(rows)
    reason_counts = Counter(reason for _, reasons in normalized for reason in reasons)
    report = {
        "result": "PASS", "stage": "D2_BASELINE_NORMALIZATION",
        "inputRows": len(source_rows), "stagingRows": len(staging_rows), "releaseReadyRows": len(release_rows),
        "reviewReasonCounts": dict(sorted(reason_counts.items())),
        "aliasReviewRows": len(alias_reviews),
        "aliasApprovedRows": sum(row.get("decision") == "APPROVE" for row in alias_reviews.values()),
        "aliasQuarantinedRows": sum(row.get("decision") == "QUARANTINE" for row in alias_reviews.values()),
        "unitPolicy": {"macros": "g_per_100g", "sodiumAndMinerals": "mg_per_100g"},
        "preparationReviewRows": len(prep_reviews),
        "preparationApprovedRows": sum(row.get("decision") == "APPROVE" for row in prep_reviews.values()),
        "hashes": {"inputSha256": sha256(args.input), "stagingSha256": sha256(args.staging), "releaseSha256": sha256(args.release)},
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

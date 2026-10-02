#!/usr/bin/env python3
"""Fail-closed validation for generated CoFID candidate artifacts."""

from __future__ import annotations

import argparse
import csv
import json
import re
from collections import Counter
from pathlib import Path


BLOCKED_REASONS = {
    "MISSING_CALORIES", "INCOMPLETE_MACROS", "NON_USER_FACING_VARIANT",
    "PREPARATION_STATE_REVIEW", "DUPLICATE_SOURCE_RECORD_ID",
    "DUPLICATE_SOURCE_NAME", "DISPLAY_NAME_REVIEW",
}


def load(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def require(condition: bool, message: str, failures: list[str]) -> None:
    if not condition:
        failures.append(message)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--all", required=True, type=Path)
    parser.add_argument("--generic-safe", required=True, type=Path)
    parser.add_argument("--generic-review", required=True, type=Path)
    parser.add_argument("--prepared-new", required=True, type=Path)
    parser.add_argument("--decisions", required=True, type=Path)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    args = parser.parse_args()

    all_rows = load(args.all)
    generic_safe = load(args.generic_safe)
    generic_review = load(args.generic_review)
    prepared_new = load(args.prepared_new)
    decisions = load(args.decisions)
    manifest = load(args.manifest)
    report = json.loads(args.report.read_text(encoding="utf-8"))
    failures: list[str] = []
    energy_warnings: list[str] = []

    require(len(all_rows) == report["importCandidateRows"], "All-candidate count differs from report", failures)
    require(len(all_rows) == len({row["source_key"] for row in all_rows}), "Duplicate source_key found", failures)
    require(len(all_rows) == len(generic_safe) + len(generic_review) + len(decisions), "Candidate partitions are not exhaustive", failures)
    require(not ({row["source_key"] for row in generic_safe} & {row["source_key"] for row in generic_review}), "Generic partitions overlap", failures)

    for row in all_rows:
        require(bool(row["calories"]), f"Missing calories: {row['source_key']}", failures)
        require(bool(row["display_name_en"]), f"Missing display name: {row['source_key']}", failures)
        require(row["display_name"] == row["display_name_en"], f"Primary/EN display name mismatch: {row['source_key']}", failures)
        require(row["short_display_name"] == row["display_name_en"], f"Short/EN display name mismatch: {row['source_key']}", failures)
        require(row["approval_status"] == "PENDING_REVIEW", f"Unexpected approval status: {row['source_key']}", failures)
        require(row["market_region"] == "UK_IE", f"Unexpected market: {row['source_key']}", failures)
        require(row["license_id"] == "OGL-3.0", f"Unexpected license: {row['source_key']}", failures)
        require(row["source_registry_id"] == "UK_COFID_2021", f"Unexpected source registry: {row['source_key']}", failures)
        require(row["nutrition_basis"] == "SOURCE_REPORTED", f"Unexpected nutrition basis: {row['source_key']}", failures)
        require(row["nutrition_reference_unit"] in {"PER_100G", "PER_100ML"}, f"Unexpected nutrition reference unit: {row['source_key']}", failures)
        try:
            serving_options = json.loads(row["serving_options_json"])
            require(isinstance(serving_options, list) and len(serving_options) == 1, f"Invalid serving options: {row['source_key']}", failures)
            if isinstance(serving_options, list) and serving_options:
                expected_key = "mlVolume" if row["nutrition_reference_unit"] == "PER_100ML" else "gramWeight"
                require(expected_key in serving_options[0], f"Serving basis mismatch: {row['source_key']}", failures)
                require(serving_options[0].get("unitType") == "SERVING", f"Serving unit mismatch: {row['source_key']}", failures)
                require(float(serving_options[0].get("quantity", 0)) > 0, f"Serving quantity missing: {row['source_key']}", failures)
                require(float(serving_options[0].get(expected_key, 0)) > 0, f"Serving conversion missing: {row['source_key']}", failures)
        except (TypeError, json.JSONDecodeError):
            failures.append(f"Invalid serving JSON: {row['source_key']}")
        for field in ("calories", "protein", "fat", "carbs"):
            if row[field]:
                try:
                    require(float(row[field]) >= 0, f"Negative {field}: {row['source_key']}", failures)
                except ValueError:
                    failures.append(f"Non-numeric {field}: {row['source_key']}")
        if all(row[field] for field in ("calories", "protein", "fat", "carbs")):
            calories = float(row["calories"])
            protein = float(row["protein"])
            fat = float(row["fat"])
            carbs = float(row["carbs"])
            require(calories <= 1000, f"Implausible calories: {row['source_key']}", failures)
            require(all(value <= 100 for value in (protein, fat, carbs)), f"Implausible macro: {row['source_key']}", failures)
            require(protein + fat + carbs <= 110, f"Implausible macro sum: {row['source_key']}", failures)
            estimated = protein * 4 + fat * 9 + carbs * 4
            alcohol_match = re.search(r"alcohol_g=([0-9.]+)", row["provenance_note"])
            alcohol = float(alcohol_match.group(1)) if alcohol_match else 0.0
            if alcohol <= 0 and calories >= 20 and abs(calories - estimated) > max(80, calories * 0.45):
                energy_warnings.append(row["source_key"])

    for row in generic_safe:
        reasons = set(filter(None, row["review_reasons"].split("|")))
        require(not reasons & BLOCKED_REASONS, f"Blocked generic row in safe output: {row['source_key']}", failures)
        require(row["catalog_type"] == "GENERIC_INGREDIENT", f"Non-generic row in generic output: {row['source_key']}", failures)
        require(len(row["display_name"]) <= 80 and "," not in row["display_name"] and ";" not in row["display_name"], f"Unsafe generic mobile display name: {row['source_key']}", failures)

    decisions_by_id = {row["source_record_id"]: row for row in decisions}
    require(len(decisions_by_id) == len(decisions), "Prepared decision source IDs are not unique", failures)
    for row in prepared_new:
        decision = decisions_by_id.get(row["source_record_id"])
        require(decision is not None, f"Prepared new row lacks decision: {row['source_key']}", failures)
        require(decision is not None and decision["decision"] == "NEW_PRODUCT_REVIEW", f"Merge/variant leaked into new-product output: {row['source_key']}", failures)
        reasons = set(filter(None, row["review_reasons"].split("|")))
        require(not reasons & BLOCKED_REASONS, f"Blocked prepared row in new-product output: {row['source_key']}", failures)
        require(len(row["display_name"]) <= 80 and "," not in row["display_name"] and ";" not in row["display_name"], f"Unsafe prepared mobile display name: {row['source_key']}", failures)

    manifest_by_key = {row["item_key"]: row for row in manifest}
    for row in decisions:
        if row["decision"] != "MERGE_AS_EVIDENCE":
            continue
        canonical = manifest_by_key.get(row["matched_item_key"])
        require(canonical is not None, f"Merge target is missing: {row['source_record_id']}", failures)
        if canonical:
            require(row["canonical_display_name_en"] == canonical["display_name_en"], f"Canonical EN name drift: {row['source_record_id']}", failures)
            require(row["canonical_display_name_tr"] == canonical["display_name_tr"], f"Canonical TR name drift: {row['source_record_id']}", failures)
            require(row["portion_profile"] == canonical["portion_profile"], f"Serving profile drift: {row['source_record_id']}", failures)

    decision_counts = Counter(row["decision"] for row in decisions)
    result = {
        "result": "FAIL" if failures else "PASS",
        "allCandidates": len(all_rows),
        "genericSafe": len(generic_safe),
        "genericReview": len(generic_review),
        "preparedNewSafe": len(prepared_new),
        "decisionCounts": dict(decision_counts),
        "failureCount": len(failures),
        "failures": failures[:100],
        "energyConsistencyWarningCount": len(energy_warnings),
        "energyConsistencyWarningSample": energy_warnings[:20],
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())

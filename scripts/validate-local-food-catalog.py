#!/usr/bin/env python3
"""Preflight validator for the August 8 generic/local-food release contract."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import re
import unicodedata
from collections import Counter
from pathlib import Path
from typing import Any


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest().upper()


def slug(value: str) -> str:
    ascii_value = unicodedata.normalize("NFD", value or "")
    ascii_value = "".join(character for character in ascii_value if unicodedata.category(character) != "Mn")
    normalized = re.sub(r"[^a-z0-9]+", "_", ascii_value.lower()).strip("_")
    return normalized or "unnamed"


def nonblank(row: dict[str, str], field: str) -> str:
    return (row.get(field) or "").strip()


def parse_number(row: dict[str, str], field: str, row_number: int, errors: list[dict[str, Any]]) -> float | None:
    raw = nonblank(row, field)
    if not raw:
        return None
    try:
        value = float(raw)
    except ValueError:
        errors.append(issue(row_number, "INVALID_NUMBER", field, f"{field} must be numeric."))
        return None
    if not math.isfinite(value):
        errors.append(issue(row_number, "INVALID_NUMBER", field, f"{field} must be finite."))
        return None
    return value


def issue(row_number: int, code: str, field: str, message: str, severity: str = "ERROR") -> dict[str, Any]:
    return {
        "row": row_number,
        "severity": severity,
        "code": code,
        "field": field,
        "message": message,
    }


def validate_servings(
    row: dict[str, str],
    row_number: int,
    catalog_type: str,
    contract: dict[str, Any],
    errors: list[dict[str, Any]],
) -> None:
    serving = parse_number(row, "serving_size_grams", row_number, errors)
    bounds = contract["serving_grams"].get(catalog_type)
    if serving is None:
        errors.append(issue(row_number, "MISSING_DEFAULT_SERVING", "serving_size_grams", "A default serving in grams is required."))
    elif bounds and not bounds[0] <= serving <= bounds[1]:
        errors.append(issue(row_number, "IMPLAUSIBLE_DEFAULT_SERVING", "serving_size_grams", f"Serving must be within {bounds[0]}-{bounds[1]} g."))

    raw_options = nonblank(row, "serving_options_json")
    if not raw_options:
        errors.append(issue(row_number, "MISSING_SERVING_OPTIONS", "serving_options_json", "At least one structured serving option is required."))
        return
    try:
        options = json.loads(raw_options)
    except json.JSONDecodeError:
        errors.append(issue(row_number, "INVALID_SERVING_OPTIONS_JSON", "serving_options_json", "Serving options must be valid JSON."))
        return
    if not isinstance(options, list) or not options:
        errors.append(issue(row_number, "EMPTY_SERVING_OPTIONS", "serving_options_json", "Serving options must be a non-empty array."))
        return

    defaults = [option for option in options if option.get("defaultOption") is True]
    if len(defaults) != 1:
        errors.append(issue(row_number, "INVALID_DEFAULT_SERVING_COUNT", "serving_options_json", "Exactly one serving option must be default."))
    seen_labels: set[str] = set()
    for option in options:
        label = str(option.get("label") or "").strip()
        normalized_label = slug(label)
        if not label or normalized_label in seen_labels:
            errors.append(issue(row_number, "INVALID_SERVING_LABEL", "serving_options_json", "Serving labels must be non-empty and unique."))
        seen_labels.add(normalized_label)
        gram_weight = option.get("gramWeight")
        ml_volume = option.get("mlVolume")
        positive_gram = isinstance(gram_weight, (int, float)) and 0 < gram_weight <= 1000
        positive_ml = isinstance(ml_volume, (int, float)) and 0 < ml_volume <= 2000
        if not positive_gram and not positive_ml:
            errors.append(issue(row_number, "IMPLAUSIBLE_SERVING_CONVERSION", "serving_options_json", "Every option needs a plausible gramWeight or mlVolume."))
        labels = option.get("labels") or {}
        if not str(labels.get("EN") or "").strip() or not str(labels.get("TR") or "").strip():
            errors.append(issue(row_number, "MISSING_SERVING_LOCALIZATION", "serving_options_json", "Every serving option needs EN and TR labels."))

    if serving is not None and len(defaults) == 1:
        default_weight = defaults[0].get("gramWeight")
        if isinstance(default_weight, (int, float)) and abs(default_weight - serving) > 1:
            errors.append(issue(row_number, "DEFAULT_SERVING_MISMATCH", "serving_options_json", "Default option gramWeight must match serving_size_grams."))


def validate_catalog(
    input_path: Path,
    contract_path: Path,
    registry_path: Path,
    release_class: str,
) -> dict[str, Any]:
    contract = json.loads(contract_path.read_text(encoding="utf-8"))
    registry_document = json.loads(registry_path.read_text(encoding="utf-8"))
    registry = {source["source_registry_id"]: source for source in registry_document["sources"]}

    errors: list[dict[str, Any]] = []
    warnings: list[dict[str, Any]] = []
    catalog_counts: Counter[str] = Counter()
    basis_counts: Counter[str] = Counter()
    source_counts: Counter[str] = Counter()
    seen_source_keys: set[str] = set()
    seen_canonical_identities: set[str] = set()

    with input_path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        headers = reader.fieldnames or []
        missing_headers = sorted(set(contract["required_columns"]) - set(headers))
        for field in missing_headers:
            errors.append(issue(1, "MISSING_COLUMN", field, f"Required column {field} is absent."))
        rows = list(reader)

    for index, row in enumerate(rows, start=2):
        catalog_type = nonblank(row, "catalog_type")
        source_key = nonblank(row, "source_key")
        preparation_state = nonblank(row, "preparation_state")
        market_region = nonblank(row, "market_region")
        nutrition_basis = nonblank(row, "nutrition_basis")
        source_registry_id = nonblank(row, "source_registry_id")

        catalog_counts[catalog_type or "MISSING"] += 1
        basis_counts[nutrition_basis or "MISSING"] += 1
        source_counts[source_registry_id or "MISSING"] += 1

        if catalog_type not in contract["allowed_catalog_types"]:
            errors.append(issue(index, "FORBIDDEN_CATALOG_TYPE", "catalog_type", "Only GENERIC_INGREDIENT and LOCAL_DISH are accepted."))
        for field in ("source_key", "name", "display_name_en", "display_name_tr", "alias_en", "alias_tr",
                      "market_region", "preparation_state", "nutrition_basis", "source_ref",
                      "source_version", "license_id", "provenance_note", "source_registry_id"):
            if not nonblank(row, field):
                errors.append(issue(index, "MISSING_REQUIRED_VALUE", field, f"{field} is required."))

        if source_key:
            if source_key in seen_source_keys:
                errors.append(issue(index, "DUPLICATE_SOURCE_KEY", "source_key", "source_key must be unique and idempotent."))
            seen_source_keys.add(source_key)
            if not re.fullmatch(contract["stable_key_pattern"], source_key):
                errors.append(issue(index, "INVALID_SOURCE_KEY", "source_key", "source_key does not match the stable local-food key contract."))

        barcode = nonblank(row, "barcode")
        if barcode:
            errors.append(issue(index, "BARCODE_FORBIDDEN", "barcode", "Generic ingredients and local dishes must be barcode-free."))

        family = nonblank(row, "dish_family_key")
        variant = nonblank(row, "dish_variant_key")
        segment_pattern = contract["identity_segment_pattern"]
        for field, value in (("dish_family_key", family), ("dish_variant_key", variant)):
            if value and not re.fullmatch(segment_pattern, value):
                errors.append(issue(index, "INVALID_IDENTITY_SEGMENT", field, f"{field} must be lowercase snake_case."))

        if catalog_type == "LOCAL_DISH":
            if preparation_state != "PREPARED":
                errors.append(issue(index, "LOCAL_DISH_NOT_PREPARED", "preparation_state", "LOCAL_DISH must use PREPARED."))
            if market_region not in contract["allowed_local_dish_regions"]:
                errors.append(issue(index, "INVALID_LOCAL_DISH_REGION", "market_region", "Local-dish region is not enabled by the release contract."))
            if ":LOCAL_DISH:" not in source_key:
                errors.append(issue(index, "LOCAL_DISH_SOURCE_KEY_TYPE_MISMATCH", "source_key", "LOCAL_DISH source_key must include its catalog type."))
            display_identity = slug(nonblank(row, "display_name_tr") or nonblank(row, "name"))
            canonical_identity = ":".join((market_region, "LOCAL_DISH", preparation_state, family or display_identity, variant or (display_identity if family else ""))).rstrip(":")
            if canonical_identity in seen_canonical_identities:
                errors.append(issue(index, "DUPLICATE_CANONICAL_IDENTITY", "dish_variant_key", "A dish family/variant identity may occur only once; servings stay on the same record."))
            seen_canonical_identities.add(canonical_identity)
        elif catalog_type == "GENERIC_INGREDIENT":
            if preparation_state == "UNSPECIFIED" or not preparation_state:
                errors.append(issue(index, "GENERIC_PREPARATION_REQUIRED", "preparation_state", "Generic ingredient preparation state must be explicit."))
            if family or variant:
                errors.append(issue(index, "DISH_IDENTITY_ON_GENERIC", "dish_family_key", "Generic ingredients cannot carry dish family/variant keys."))
            if ":GENERIC_INGREDIENT:" not in source_key:
                errors.append(issue(index, "GENERIC_SOURCE_KEY_TYPE_MISMATCH", "source_key", "Generic source_key must include its catalog type."))

        source = registry.get(source_registry_id)
        if source is None:
            errors.append(issue(index, "UNKNOWN_SOURCE", "source_registry_id", "Source is not present in the verified registry."))
        else:
            if nonblank(row, "data_source") != source["data_source"]:
                errors.append(issue(index, "DATA_SOURCE_MISMATCH", "data_source", "Row data_source does not match the source registry."))
            if catalog_type and catalog_type not in source["catalog_types"]:
                errors.append(issue(index, "SOURCE_CATALOG_TYPE_MISMATCH", "catalog_type", "Source is not approved for this catalog type."))
            if nonblank(row, "license_id") != source["license_id"]:
                errors.append(issue(index, "LICENSE_MISMATCH", "license_id", "Row license_id must match the registry."))
            if not source.get("review_allowed"):
                errors.append(issue(index, "SOURCE_NOT_INGESTIBLE", "source_registry_id", "Source rights do not permit ingestion even into the review queue."))
            if release_class == "PRODUCTION" and not source.get("production_allowed"):
                record_approval = source.get("production_allowed_with_record_approval") and nonblank(row, "approval_status") == "APPROVED"
                if not record_approval:
                    errors.append(issue(index, "SOURCE_NOT_PRODUCTION_ALLOWED", "source_registry_id", "Source is not production-allowed without explicit record approval."))

        numeric_values: dict[str, float] = {}
        for field in set(contract["required_nutrients"]) | set(contract["micronutrient_fields"]) | {"sugar"}:
            value = parse_number(row, field, index, errors)
            if value is not None:
                numeric_values[field] = value
                bounds = contract["ranges"].get(field)
                if bounds and not bounds[0] <= value <= bounds[1]:
                    errors.append(issue(index, "NUTRIENT_OUT_OF_RANGE", field, f"{field} must be within {bounds[0]}-{bounds[1]} per 100 g."))

        for field in contract["required_nutrients"]:
            if field not in numeric_values:
                errors.append(issue(index, "MISSING_REQUIRED_NUTRIENT", field, f"{field} per 100 g is required."))
        micro_count = sum(field in numeric_values for field in contract["micronutrient_fields"])
        if micro_count < contract["minimum_micronutrient_count"]:
            errors.append(issue(index, "INSUFFICIENT_MICRONUTRIENTS", "nutrition", f"At least {contract['minimum_micronutrient_count']} micronutrients are required."))

        if all(field in numeric_values for field in ("calories", "protein", "fat", "carbs")):
            calculated = numeric_values["protein"] * 4 + numeric_values["fat"] * 9 + numeric_values["carbs"] * 4
            reported = numeric_values["calories"]
            difference = abs(reported - calculated)
            policy = contract["energy_consistency"]
            if difference > max(policy["error_absolute_kcal"], reported * policy["error_relative"]):
                errors.append(issue(index, "MACRO_ENERGY_INCONSISTENT", "calories", f"Reported energy differs from 4/9/4 macro energy by {difference:.1f} kcal."))
            elif difference > max(policy["warning_absolute_kcal"], reported * policy["warning_relative"]):
                warnings.append(issue(index, "MACRO_ENERGY_REVIEW", "calories", f"Macro energy difference is {difference:.1f} kcal.", "WARNING"))

        if nutrition_basis == "SOURCE_REPORTED":
            if not nonblank(row, "source_record_id"):
                errors.append(issue(index, "SOURCE_RECORD_ID_REQUIRED", "source_record_id", "SOURCE_REPORTED requires a stable source record id."))
        elif nutrition_basis == "CALCULATED":
            if not nonblank(row, "calculation_method") or not nonblank(row, "calculation_version"):
                errors.append(issue(index, "CALCULATION_METHOD_REQUIRED", "calculation_method", "CALCULATED requires a versioned method; ingredient links remain optional."))
            raw_links = nonblank(row, "ingredient_links_json")
            if raw_links:
                try:
                    links = json.loads(raw_links)
                    if not isinstance(links, list):
                        raise ValueError
                except (json.JSONDecodeError, ValueError):
                    errors.append(issue(index, "INVALID_INGREDIENT_LINKS", "ingredient_links_json", "Ingredient links must be an optional JSON array."))
        elif nutrition_basis == "ESTIMATED":
            if not nonblank(row, "estimation_method"):
                errors.append(issue(index, "ESTIMATION_METHOD_REQUIRED", "estimation_method", "ESTIMATED requires an explicit method."))
            confidence = parse_number(row, "confidence_score", index, errors)
            if confidence is None or not 0 <= confidence <= contract["estimated_max_confidence"]:
                errors.append(issue(index, "ESTIMATED_CONFIDENCE_TOO_HIGH", "confidence_score", "ESTIMATED confidence must remain in the low-confidence band."))
            if release_class == "PRODUCTION":
                if nonblank(row, "approval_status") != "APPROVED" or not nonblank(row, "approved_by") or not nonblank(row, "approved_at"):
                    errors.append(issue(index, "ESTIMATED_APPROVAL_REQUIRED", "approval_status", "Production ESTIMATED rows require named, dated approval."))
        else:
            errors.append(issue(index, "INVALID_NUTRITION_BASIS", "nutrition_basis", "nutrition_basis must be SOURCE_REPORTED, CALCULATED, or ESTIMATED."))

        validate_servings(row, index, catalog_type, contract, errors)

    errors.sort(key=lambda value: (value["row"], value["code"], value["field"]))
    warnings.sort(key=lambda value: (value["row"], value["code"], value["field"]))
    return {
        "schemaVersion": contract["schema_version"],
        "releaseClass": release_class,
        "result": "PASS" if not errors else "FAIL",
        "input": str(input_path),
        "hashes": {
            "inputSha256": sha256(input_path),
            "contractSha256": sha256(contract_path),
            "sourceRegistrySha256": sha256(registry_path),
        },
        "metrics": {
            "rows": len(rows),
            "catalogTypeCounts": dict(sorted(catalog_counts.items())),
            "nutritionBasisCounts": dict(sorted(basis_counts.items())),
            "sourceCounts": dict(sorted(source_counts.items())),
            "uniqueSourceKeys": len(seen_source_keys),
            "uniqueCanonicalLocalDishes": len(seen_canonical_identities),
            "errors": len(errors),
            "warnings": len(warnings),
        },
        "errors": errors,
        "warnings": warnings,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--contract", default=Path("automation/local-food-catalog-aug08/local-food-catalog-v1.json"), type=Path)
    parser.add_argument("--source-registry", default=Path("automation/local-food-catalog-aug08/source-registry-v1.json"), type=Path)
    parser.add_argument("--release-class", choices=("REVIEW", "PRODUCTION"), default="REVIEW")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()

    report = validate_catalog(args.input, args.contract, args.source_registry, args.release_class)
    serialized = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(serialized, encoding="utf-8")
    print(serialized, end="")
    return 0 if report["result"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())

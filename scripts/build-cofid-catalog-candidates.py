#!/usr/bin/env python3
"""Build review-only G-Run catalog candidates from the official CoFID 2021 workbook."""

from __future__ import annotations

import argparse
import csv
import json
import re
import unicodedata
from collections import Counter
from difflib import SequenceMatcher
from pathlib import Path

import openpyxl


OFFICIAL_SOURCE = "https://www.gov.uk/government/publications/composition-of-foods-integrated-dataset-cofid"
OUTPUT_FIELDS = [
    "catalog_type", "data_source", "source_registry_id", "source_key", "name",
    "display_name", "short_display_name", "display_name_en", "display_name_tr",
    "alias_en", "alias_tr", "market_region",
    "preparation_state", "nutrition_basis", "nutrition_reference_unit", "calories", "protein", "fat", "carbs",
    "fiber", "sugar", "sodium", "serving_size_grams", "serving_unit",
    "serving_options_json", "source_ref", "source_version", "license_id",
    "provenance_note", "source_record_id", "approval_status", "review_reasons",
]

PREPARED_TERMS = re.compile(
    r"\b(homemade|ready to eat|takeaway|cappuccino|latte|sandwich|burger|pizza|soup|"
    r"curry|casserole|stew|pie|pudding|omelette|porridge|salad|smoothie|milkshake|"
    r"fish and chips|breakfast|lasagne|risotto|chow mein|kebab|wrap|toastie)\b",
    re.IGNORECASE,
)
REVIEW_TERMS = re.compile(
    r"\b(weighed with|with bone|with bones|including waste|inedible|skin and bone|"
    r"as purchased|leftover)\b",
    re.IGNORECASE,
)

CANONICAL_DISPLAY_NAMES = {
    "coffee irish": "Irish Coffee",
    "coffee instant made up with water": "Instant Coffee",
    "tea black infusion average": "Black Tea",
    "tea green infusion": "Green Tea",
    "tea herbal infusion": "Herbal Tea",
    "lemonade": "Lemonade",
    "lemonade homemade": "Lemonade",
    "fruit juice mixed": "Mixed Fruit Juice",
    "wine red": "Red Wine",
    "wine white dry": "White Wine",
    "wine white medium": "White Wine",
    "wine white sparkling": "Sparkling Wine",
    "wine rose medium": "Rose Wine",
}
PREPARATION_WORDS = {
    "raw", "cooked", "boiled", "steamed", "grilled", "fried", "roasted",
    "baked", "stewed", "braised", "dried", "chilled", "frozen", "smoked",
}
DISPLAY_NOISE = {"average", "homemade"}
MATCH_STOP_WORDS = {"with", "and", "the", "of", "made", "homemade", "average"}


def text(value: object) -> str:
    return "" if value is None else str(value).strip()


def number(value: object) -> str:
    raw = text(value)
    if not raw or raw.upper() in {"N", "ND", "N/A"}:
        return ""
    if raw.lower() in {"tr", "trace"}:
        return "0"
    try:
        return f"{float(raw):g}"
    except ValueError:
        return ""


def normalized_name(value: str) -> str:
    value = unicodedata.normalize("NFKD", value)
    value = "".join(char for char in value if not unicodedata.combining(char)).lower()
    return " ".join(re.findall(r"[a-z0-9]+", value))


def display_name(name: str) -> tuple[str, bool]:
    """Return a user-facing English name and whether manual review is advisable."""
    canonical_key = normalized_name(name)
    if canonical_key in CANONICAL_DISPLAY_NAMES:
        return CANONICAL_DISPLAY_NAMES[canonical_key], False
    if "," not in name:
        return name.strip().title(), False

    parts = [part.strip() for part in name.split(",") if part.strip()]
    base = parts[0]
    if len(parts) >= 4:
        return name.strip().title(), True
    descriptors = [part for part in parts[1:] if normalized_name(part) not in DISPLAY_NOISE]
    preparation: list[str] = []
    qualifiers: list[str] = []
    suffixes: list[str] = []
    for descriptor in descriptors:
        normalized = normalized_name(descriptor)
        words = set(normalized.split())
        if normalized.startswith(("stuffed with ", "with ", "without ", "made with ", "served with ")):
            suffixes.append(descriptor)
        elif words & PREPARATION_WORDS:
            preparation.append(descriptor)
        else:
            qualifiers.append(descriptor)
    rendered = " ".join(preparation + qualifiers + [base] + suffixes).strip().title()
    return rendered, bool(REVIEW_TERMS.search(name))


def preparation_state(name: str) -> str:
    lowered = name.lower()
    for term, state in (
        ("raw", "RAW"), ("boiled", "BOILED"), ("steamed", "STEAMED"),
        ("grilled", "GRILLED"), ("fried", "FRIED"), ("roasted", "ROASTED"),
        ("baked", "BAKED"), ("cooked", "COOKED"), ("homemade", "PREPARED"),
    ):
        if re.search(rf"\b{term}\b", lowered):
            return state
    return "PREPARED" if PREPARED_TERMS.search(name) else "UNSPECIFIED"


def catalog_type(name: str, group: str) -> str:
    lowered = name.lower()
    source_base_terms = ("powder", "concentrate", "undiluted", "dry leaves", "tea leaves")
    ready_beverage = (
        group.startswith("Q")
        or (group in {"PAC", "FC"} and not any(term in lowered for term in source_base_terms))
        or (group == "PCC" and "diluted" in lowered and "undiluted" not in lowered)
        or ("coffee" in lowered and ("infusion" in lowered or "made up" in lowered))
        or ("tea" in lowered and "infusion" in lowered)
    )
    return "STANDARD_PREPARED_ITEM" if ready_beverage or PREPARED_TERMS.search(name) else "GENERIC_INGREDIENT"


def load_manifest(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def closest_manifest(name: str, manifest: list[dict[str, str]]) -> tuple[str, float]:
    source = normalized_name(name)
    best_key, best_score = "", 0.0
    for item in manifest:
        candidate = normalized_name(item["display_name_en"])
        score = SequenceMatcher(None, source, candidate).ratio()
        if source == candidate:
            return item["item_key"], 1.0
        if score > best_score:
            best_key, best_score = item["item_key"], score
    return best_key, best_score


def match_tokens(value: str) -> set[str]:
    tokens = []
    for token in normalized_name(value).split():
        if token in MATCH_STOP_WORDS:
            continue
        tokens.append(token[:-1] if token.endswith("s") and len(token) > 4 else token)
    return set(tokens)


def token_overlap(left: str, right: str) -> float:
    left_tokens, right_tokens = match_tokens(left), match_tokens(right)
    union = left_tokens | right_tokens
    return len(left_tokens & right_tokens) / len(union) if union else 0.0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--workbook", required=True, type=Path)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--prepared-output", required=True, type=Path)
    parser.add_argument("--decision-output", required=True, type=Path)
    parser.add_argument("--generic-output", required=True, type=Path)
    parser.add_argument("--generic-review-output", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    args = parser.parse_args()

    manifest = load_manifest(args.manifest)
    manifest_by_display = {normalized_name(item["display_name_en"]): item for item in manifest}
    workbook = openpyxl.load_workbook(args.workbook, read_only=True, data_only=True)
    sheet = workbook["1.3 Proximates"]
    source_rows = [values for values in sheet.iter_rows(min_row=4, values_only=True) if values[0] and values[1]]
    code_counts = Counter(text(values[0]) for values in source_rows)
    name_counts = Counter(normalized_name(text(values[1])) for values in source_rows)
    rows: list[dict[str, str]] = []
    overlaps: list[dict[str, object]] = []
    reason_counts: Counter[str] = Counter()
    type_counts: Counter[str] = Counter()

    for values in source_rows:
        code, name, description, group = map(text, values[:4])
        if not code or not name:
            continue
        calories, protein, fat, carbs = map(number, (values[12], values[9], values[10], values[11]))
        sugar = number(values[16])
        alcohol = number(values[23])
        row_type = catalog_type(name, group)
        prep = preparation_state(name)
        user_display_name, display_needs_review = display_name(name)
        canonical_item = manifest_by_display.get(normalized_name(user_display_name))
        if canonical_item:
            user_display_name = canonical_item["display_name_en"]
        reasons: list[str] = []
        if not calories:
            reasons.append("MISSING_CALORIES")
        if not all((protein, fat, carbs)):
            reasons.append("INCOMPLETE_MACROS")
        if all((calories, protein, fat, carbs)):
            calorie_value = float(calories)
            protein_value = float(protein)
            fat_value = float(fat)
            carbs_value = float(carbs)
            if (
                calorie_value > 1000
                or any(value > 100 for value in (protein_value, fat_value, carbs_value))
                or protein_value + fat_value + carbs_value > 110
            ):
                reasons.append("IMPLAUSIBLE_CORE_NUTRITION")
            estimated_energy = protein_value * 4 + fat_value * 9 + carbs_value * 4
            alcohol_value = float(alcohol) if alcohol else 0.0
            if alcohol_value <= 0 and calorie_value >= 20 and abs(calorie_value - estimated_energy) > max(80, calorie_value * 0.45):
                reasons.append("ENERGY_COMPONENT_MISMATCH")
        if prep == "UNSPECIFIED":
            reasons.append("PREPARATION_STATE_REVIEW")
        if REVIEW_TERMS.search(name):
            reasons.append("NON_USER_FACING_VARIANT")
        if display_needs_review:
            reasons.append("DISPLAY_NAME_REVIEW")
        if len(user_display_name) > 80 or "," in user_display_name or ";" in user_display_name:
            reasons.append("DISPLAY_NAME_REVIEW")
        if code_counts[code] > 1:
            reasons.append("DUPLICATE_SOURCE_RECORD_ID")
        if name_counts[normalized_name(name)] > 1:
            reasons.append("DUPLICATE_SOURCE_NAME")
        match_key, match_score = closest_manifest(name, manifest)
        if match_score == 1.0:
            reasons.append("EXACT_STANDARD_200_OVERLAP")
        elif match_score >= 0.88:
            reasons.append("POSSIBLE_STANDARD_200_OVERLAP")
        if match_score >= 0.88:
            overlaps.append({"foodCode": code, "foodName": name, "itemKey": match_key, "score": round(match_score, 4)})

        is_alcoholic_beverage = group.startswith("Q")
        serving_unit = "ml" if is_alcoholic_beverage else "g"
        serving_option = {
            "label": f"100 {serving_unit}",
            "unitType": "SERVING",
            "quantity": 1,
            "defaultOption": True,
            "labels": {"EN": f"100 {serving_unit}", "TR": f"100 {serving_unit}"},
        }
        serving_option["mlVolume" if is_alcoholic_beverage else "gramWeight"] = 100
        serving_options = [serving_option]
        result = {field: "" for field in OUTPUT_FIELDS}
        result.update({
            "catalog_type": row_type,
            "data_source": "COFID",
            "source_registry_id": "UK_COFID_2021",
            "source_key": f"COFID_2021:{code}:{normalized_name(name).replace(' ', '_')}" if code_counts[code] > 1 else f"COFID_2021:{code}",
            "name": name,
            "display_name": user_display_name,
            "short_display_name": user_display_name,
            "display_name_en": user_display_name,
            "market_region": "UK_IE",
            "preparation_state": prep,
            "nutrition_basis": "SOURCE_REPORTED",
            "nutrition_reference_unit": "PER_100ML" if is_alcoholic_beverage else "PER_100G",
            "calories": calories,
            "protein": protein,
            "fat": fat,
            "carbs": carbs,
            "sugar": sugar,
            "serving_size_grams": "100",
            "serving_unit": serving_unit,
            "serving_options_json": json.dumps(serving_options, ensure_ascii=False, separators=(",", ":")),
            "source_ref": OFFICIAL_SOURCE,
            "source_version": "CoFID 2021",
            "license_id": "OGL-3.0",
            "provenance_note": f"CoFID group={group}; description={description}; alcohol_g={alcohol}",
            "source_record_id": code,
            "approval_status": "PENDING_REVIEW",
            "review_reasons": "|".join(sorted(reasons)),
        })
        rows.append(result)
        type_counts[row_type] += 1
        reason_counts.update(reasons)

    exclusion_reasons = {"MISSING_CALORIES", "IMPLAUSIBLE_CORE_NUTRITION", "ENERGY_COMPONENT_MISMATCH"}
    excluded_rows = [
        row for row in rows if set(row["review_reasons"].split("|")) & exclusion_reasons
    ]
    import_candidate_rows = [
        row for row in rows if not (set(row["review_reasons"].split("|")) & exclusion_reasons)
    ]
    excluded_reason_counts = Counter(
        reason
        for row in excluded_rows
        for reason in row["review_reasons"].split("|")
        if reason in exclusion_reasons
    )

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=OUTPUT_FIELDS)
        writer.writeheader()
        writer.writerows(import_candidate_rows)

    blocking_reasons = {
        "MISSING_CALORIES", "INCOMPLETE_MACROS", "NON_USER_FACING_VARIANT",
        "PREPARATION_STATE_REVIEW",
        "DUPLICATE_SOURCE_RECORD_ID", "DUPLICATE_SOURCE_NAME",
        "EXACT_STANDARD_200_OVERLAP", "POSSIBLE_STANDARD_200_OVERLAP",
        "DISPLAY_NAME_REVIEW",
    }
    prepared_rows = [
        row for row in import_candidate_rows
        if row["catalog_type"] == "STANDARD_PREPARED_ITEM"
        and not (set(row["review_reasons"].split("|")) & blocking_reasons)
    ]
    import_type_counts = Counter(row["catalog_type"] for row in import_candidate_rows)
    decision_fields = [
        "source_record_id", "source_name", "proposed_display_name_en", "decision",
        "matched_item_key", "canonical_display_name_en", "canonical_display_name_tr",
        "portion_profile", "match_score", "review_reasons",
    ]
    decisions: list[dict[str, object]] = []
    for row in import_candidate_rows:
        if row["catalog_type"] != "STANDARD_PREPARED_ITEM":
            continue
        exact = manifest_by_display.get(normalized_name(row["display_name_en"]))
        matched_key, matched_score = closest_manifest(row["display_name_en"], manifest)
        matched = next((item for item in manifest if item["item_key"] == matched_key), None)
        if exact:
            decision = "MERGE_AS_EVIDENCE"
            matched = exact
            matched_score = 1.0
        elif matched and matched_score >= 0.82 and token_overlap(row["display_name_en"], matched["display_name_en"]) >= 0.5:
            decision = "POSSIBLE_VARIANT_REVIEW"
        else:
            decision = "NEW_PRODUCT_REVIEW"
            matched = None
        decisions.append({
            "source_record_id": row["source_record_id"],
            "source_name": row["name"],
            "proposed_display_name_en": row["display_name_en"],
            "decision": decision,
            "matched_item_key": matched["item_key"] if matched else "",
            "canonical_display_name_en": matched["display_name_en"] if matched else "",
            "canonical_display_name_tr": matched["display_name_tr"] if matched else "",
            "portion_profile": matched["portion_profile"] if matched else "",
            "match_score": f"{matched_score:.4f}",
            "review_reasons": row["review_reasons"],
        })
    with args.decision_output.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=decision_fields)
        writer.writeheader()
        writer.writerows(decisions)
    decision_counts = Counter(str(row["decision"]) for row in decisions)
    non_new_source_ids = {
        str(row["source_record_id"])
        for row in decisions
        if row["decision"] in {"MERGE_AS_EVIDENCE", "POSSIBLE_VARIANT_REVIEW"}
    }
    prepared_new_rows = [
        row for row in prepared_rows if row["source_record_id"] not in non_new_source_ids
    ]
    args.prepared_output.parent.mkdir(parents=True, exist_ok=True)
    with args.prepared_output.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=OUTPUT_FIELDS)
        writer.writeheader()
        writer.writerows(prepared_new_rows)

    generic_blocking_reasons = {
        "MISSING_CALORIES", "INCOMPLETE_MACROS", "NON_USER_FACING_VARIANT",
        "PREPARATION_STATE_REVIEW", "DUPLICATE_SOURCE_RECORD_ID",
        "DUPLICATE_SOURCE_NAME", "DISPLAY_NAME_REVIEW",
    }
    generic_rows = [row for row in import_candidate_rows if row["catalog_type"] == "GENERIC_INGREDIENT"]
    generic_safe_rows = [
        row for row in generic_rows
        if not (set(row["review_reasons"].split("|")) & generic_blocking_reasons)
    ]
    generic_review_rows = [row for row in generic_rows if row not in generic_safe_rows]
    for path, output_rows in (
        (args.generic_output, generic_safe_rows),
        (args.generic_review_output, generic_review_rows),
    ):
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=OUTPUT_FIELDS)
            writer.writeheader()
            writer.writerows(output_rows)

    report = {
        "result": "PASS",
        "source": "CoFID 2021 official workbook",
        "sourceUrl": OFFICIAL_SOURCE,
        "sourceRows": len(rows),
        "excludedRows": len(excluded_rows),
        "excludedReasonCounts": dict(excluded_reason_counts),
        "importCandidateRows": len(import_candidate_rows),
        "importCandidateTypeCounts": dict(import_type_counts),
        "uniqueSourceKeys": len({row["source_key"] for row in import_candidate_rows}),
        "uniqueNames": len({normalized_name(row["name"]) for row in import_candidate_rows}),
        "catalogTypeCounts": dict(type_counts),
        "reviewReasonCounts": dict(reason_counts),
        "exactStandard200Overlaps": reason_counts["EXACT_STANDARD_200_OVERLAP"],
        "possibleStandard200Overlaps": reason_counts["POSSIBLE_STANDARD_200_OVERLAP"],
        "additiveCandidatesAfterExactOverlap": len(import_candidate_rows) - reason_counts["EXACT_STANDARD_200_OVERLAP"],
        "preparedQualityGateRows": len(prepared_rows),
        "preparedNewProductSafetyRows": len(prepared_new_rows),
        "preparedCandidatesNeedingReview": import_type_counts["STANDARD_PREPARED_ITEM"] - len(prepared_rows),
        "preparedDecisionCounts": dict(decision_counts),
        "genericSafetyGateRows": len(generic_safe_rows),
        "genericReviewRows": len(generic_review_rows),
        "overlapCandidates": overlaps,
        "releasePolicy": "Review-only output; no row is production-approved automatically.",
    }
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key != "overlapCandidates"}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

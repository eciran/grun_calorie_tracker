"""Resolve the exact 123/10/5 review queues without modifying a catalogue.

Reviewed identity equivalence is not a fuzzy-name matcher. Rounded values prove
compatibility with an internal import, not independent nutrient correctness.
"""
import argparse
import copy
import csv
import importlib.util
import json
from collections import Counter
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation, ROUND_HALF_UP
from pathlib import Path

spec = importlib.util.spec_from_file_location("everyday", Path(__file__).with_name("review-r6-everyday-categories.py"))
everyday = importlib.util.module_from_spec(spec)
spec.loader.exec_module(everyday)
ROOT = everyday.ROOT
FOLDER = ROOT / "outputs/r6-everyday-categories-20261012"
MACROS = {"calories", "protein", "fat", "carbs", "fiber", "sugar", "saturated_fat", "trans_fat", "sugar_alcohol"}
FIELDS = ["calories", "protein", "fat", "carbs", "fiber", "sugar", "sodium", "potassium", "cholesterol",
          "calcium", "iron", "magnesium", "zinc", "vitamin_a", "vitamin_c", "vitamin_d", "vitamin_e",
          "vitamin_b12", "saturated_fat", "trans_fat", "sugar_alcohol"]
VERSION = "R6_EXACT_EVERYDAY_RESOLUTION_V1"
PRECISION_CONTRACT_SHA256 = "82e0db1ac22f2bea4156554113a3bcf778e2d743e490c047615838d0f8dd29d2"


def rules_index(registry):
    result = {}
    if registry.get("version") != "R6_EXACT_EVERYDAY_REVIEW_V1":
        raise ValueError("Explicit reviewed registry required")
    for group in registry["ruleGroups"]:
        if not group.get("basis") or not group.get("target") or not group.get("keys"):
            raise ValueError("Missing exact-rule rationale/target/identity")
        for suffix in group["keys"]:
            key = group["prefix"] + suffix
            if key in result:
                raise ValueError("Duplicate category decision for " + key)
            result[key] = group
    return result


def compare_profile(food, source):
    if (food.get("source_key") != source.get("source_key") or food.get("catalog_type") != "LOCAL_DISH"
            or food.get("data_source") != "LOCAL_CURATED" or source.get("data_source") != "LOCAL_CURATED"
            or food.get("nutrition_basis") != "CALCULATED" or source.get("nutrition_basis") != "CALCULATED"
            or food.get("nutrition_reference_unit") != "PER_100G"
            or food.get("preparation_state") != source.get("preparation_state")
            or food.get("market_region") != source.get("market_region")
            or everyday.normalized(food.get("name")) != everyday.normalized(source.get("name"))):
        raise ValueError("Exact internal calculated profile identity/basis required")
    fields = []
    for field in FIELDS:
        current, raw = food.get(field), source.get(field)
        absent = raw is None or raw == ""
        if current is None and absent:
            fields.append({"field": field, "current": None, "source": None, "status": "UNKNOWN_BOTH"})
            continue
        status, normalized = "MISMATCH", None
        if current is not None and not absent and not isinstance(current, bool):
            try:
                value, actual = Decimal(str(raw)), Decimal(str(current))
                if value.is_finite() and actual.is_finite() and min(value, actual) >= 0:
                    normalized = value.quantize(Decimal("0.1" if field in MACROS else "0.001"), rounding=ROUND_HALF_UP)
                    if actual == normalized:
                        status = "IMPORT_PRECISION_MATCH"
            except InvalidOperation:
                pass
        fields.append({"field": field, "current": current, "source": raw,
                       "normalizedSource": str(normalized) if normalized is not None else None, "status": status})
    known = sum(f["status"] == "IMPORT_PRECISION_MATCH" for f in fields)
    return {"compatible": known >= 4 and all(f["status"] != "MISMATCH" for f in fields),
            "knownMatches": known, "unknownBoth": sum(f["status"] == "UNKNOWN_BOTH" for f in fields),
            "independentNutritionVerified": False, "nutritionWriteAllowed": False, "fields": fields}


def target_active(target, categories):
    if target not in categories:
        return False
    by_id = {c["id"]: c for c in categories.values()}
    node, seen = categories[target], set()
    while node:
        if not node["active"] or node["id"] in seen:
            return False
        seen.add(node["id"])
        parent = node.get("parent_id")
        if parent is not None and parent not in by_id:
            return False
        node = by_id.get(parent)
    return True


def resolve(original, current, rule, name_decision, source, calculated, categories):
    result = copy.deepcopy(original)
    if original["beforeSha256"] != everyday.fingerprint(current):
        raise ValueError("Current record/category/evidence drift")
    result["previousReasons"] = original["reasons"]
    reasons = set(original["reasons"])
    target = original["targetSlug"]
    if rule:
        if original["catalogType"] == "GENERIC_INGREDIENT":
            expected = {"USDA_FOODDATA:fdc:172233": ("0200", "Dill weed, fresh"),
                        "USDA_FOODDATA:fdc:169640": ("1900", "Honey")}.get(original["sourceKey"])
            if not expected or not source or (source.get("foodCategory", {}).get("code"), source.get("description")) != expected:
                raise ValueError("Exact official herb/sweet category identity proof required")
        target = rule["target"]
        result["reviewedCategoryRationale"] = rule["basis"]
        result["reviewedSourceUrl"] = rule.get("reviewedUrl")
        reasons.discard("AMBIGUOUS_OR_MISSING_SOURCE_CATEGORY")
    if name_decision:
        if (original["id"] != name_decision["id"] or original["sourceKey"] != name_decision["sourceKey"]
                or current["food"]["name"] != name_decision["currentName"] or not source
                or source["description"] != name_decision["sourceDescription"]
                or "USDA_FOODDATA:fdc:" + str(source["fdcId"]) != original["sourceKey"]):
            raise ValueError("Reviewed USDA identity has drifted")
        target = name_decision["target"]
        if source.get("dataType") != "Survey (FNDDS)" and everyday.usda_target(source) != target:
            raise ValueError("Reviewed name target contradicts source food category")
        result["nameDecision"] = name_decision
        result["sourceBodySha256"] = everyday.fingerprint(source)
        if source.get("dataType") == "Survey (FNDDS)":
            numbers = [str(a.get("value")) for a in source.get("foodAttributes", []) if a.get("name") == "WWEIA Category number"]
            descriptions = [a.get("value") for a in source.get("foodAttributes", []) if a.get("name") == "WWEIA Category description"]
            if numbers != ["2502"] or descriptions != ["Eggs and omelets"] or target != "eggs":
                raise ValueError("Explicit FNDDS attribute proof required; wweiaFoodCategoryCode is not the stable category number")
            result["categoryAttributeProof"] = {"number": numbers[0], "description": descriptions[0]}
            reasons.discard("AMBIGUOUS_OR_MISSING_SOURCE_CATEGORY")
        if name_decision["decision"] == "EQUIVALENT_IDENTITY":
            reasons.discard("USDA_CURRENT_NAME_IDENTITY_REVIEW")
        elif name_decision["decision"] == "NAME_CORRECTION_REQUIRED":
            reasons.add("R4_REVIEWED_NAME_PATCH_NOT_APPLIED")
        else:
            raise ValueError("Unknown identity review decision")
    if calculated:
        result["profileComparison"] = compare_profile(current["food"], calculated)
        if result["profileComparison"]["compatible"]:
            reasons.discard("MANIFEST_CORE_PROFILE_DRIFT")
            result["warnings"] = sorted(set(result["warnings"] + ["CALCULATED_INTERNAL_PROFILE_NOT_LAB_VERIFIED", "RECIPE_YIELD_APPROVAL_REMAINS_SEPARATE", "FOUR_UNKNOWN_NUTRIENTS_NOT_ZERO"]))
    if not target_active(target, categories):
        reasons.add("TARGET_CATEGORY_MISSING_OR_INACTIVE")
    result["targetSlug"] = target
    result["reasons"] = sorted(reasons)
    result["status"] = "SAFE_CATEGORY_NOMINATION" if not reasons else "REVIEW_REQUIRED"
    result["primaryQueue"] = everyday.primary_review_queue(result)
    result["applyAllowed"] = False
    return result


def prepare_plan(decisions, current_by_id, categories, snapshot_time, hashes):
    safe = [r for r in decisions if r["status"] == "SAFE_CATEGORY_NOMINATION"]
    if len({r["id"] for r in safe}) != len(safe):
        raise ValueError("Duplicate plan ID")
    rows = []
    for decision in safe:
        before = current_by_id[decision["id"]]
        if decision["applyAllowed"] or decision["reasons"] or before["assignments"] or not target_active(decision["targetSlug"], categories):
            raise ValueError("Plan cannot promote a held/already assigned/inactive candidate")
        if decision["beforeSha256"] != everyday.fingerprint(before):
            raise ValueError("Plan before-image mismatch")
        rows.append({"id": decision["id"], "sourceKey": decision["sourceKey"], "targetSlug": decision["targetSlug"],
                     "targetCategory": categories[decision["targetSlug"]], "expectedBefore": before,
                     "beforeSha256": decision["beforeSha256"], "sourceIdentitySha256": decision["sourceIdentitySha256"]})
    return {"version": "R6_CATEGORY_APPLICATION_PREPARATION_V1", "catalogWrites": 0, "liveApplyAllowed": False,
            "approval": None, "backup": None, "snapshotTime": snapshot_time, "inputHashes": hashes,
            "expectedProducts": len(rows), "expectedNewAssignments": len(rows), "allowedTable": "food_item_categories",
            "forbiddenWrites": ["food_items", "nutrition", "names", "aliases", "servings", "source_evidence", "merges", "deletes"],
            "requiredReleaseGates": ["FRESH_EXACT_BEFORE_IMAGES_AND_TAXONOMY", "EXPLICIT_ADMIN_CATEGORY_APPROVAL",
                                     "ENCRYPTED_BACKUP_AVAILABLE", "BOUNDED_ATOMIC_INSERT_AND_EXACT_COUNT",
                                     "OWNED_ASSIGNMENT_ROLLBACK_REHEARSAL", "CANONICAL_FILTER_SEARCH_ACCEPTANCE"],
            "rollbackPolicy": "Remove only exact unchanged assignments owned by the approved operation; abort on later admin edits. Never restore food/nutrition/serving data for this category-only operation.",
            "rows": rows}


def build(output, now):
    base_path = FOLDER / "review/category-dry-run.json"
    registry_path = ROOT / "data/catalog/everyday-category-review-r6-v1.json"
    calculated_path = ROOT / "outputs/local-food-catalog-aug25/db-import/local-dish-calculated-v2.csv"
    normalizer_path = ROOT / "src/main/java/com/grun/calorietracker/service/support/NutritionValueNormalizer.java"
    if everyday.sha(normalizer_path) != PRECISION_CONTRACT_SHA256:
        raise ValueError("Java precision contract changed; explicitly review rounding before clearing drift")
    base = json.loads(base_path.read_text(encoding="utf-8"))
    # All prior source/rule hashes must still refer to the input files reviewed.
    paths = {"catalog": FOLDER / "aws/catalog.csv", "proof": FOLDER / "aws/snapshot-proof.json",
             "rules": Path(__file__).with_name("review-r6-everyday-categories.py")}
    for key in ("r3_catalog", "r3_proof", "usda", "local", "local_baseline", "prepared", "prepared_ready", "portion_profiles", "r4", "r5"):
        fixed = {"r3_catalog": "outputs/r3-aws-source-review-20261011/catalog.csv", "r3_proof": "outputs/r3-aws-source-review-20261011/snapshot-proof.json",
                 "usda": "outputs/r3-aws-source-review-20261011/usda/evidence.json", "local": "outputs/local-food-catalog-aug08/staging/d4-local-dish-taxonomy-frozen-v2.csv",
                 "local_baseline": "outputs/local-food-catalog-aug25/local-dish-nutrition-baseline-v2.csv", "prepared": "sample-data/standard-prepared-catalog-200-v1.csv",
                 "prepared_ready": "outputs/standard-prepared-ready/standard-prepared-catalog-200-ready.csv", "portion_profiles": "sample-data/standard-prepared-portion-profiles-v1.csv",
                 "r4": "src/main/resources/catalog/source-name-decisions-r4-v1.json", "r5": "outputs/r5-held-followup-20261012/final-review/release-review.json"}
        paths[key] = ROOT / fixed[key]
    if any(everyday.sha(path) != base["inputHashes"][key] for key, path in paths.items()):
        raise ValueError("Frozen baseline inputs drifted; rebuild review explicitly")
    current, proof, categories = everyday.verify_snapshot(paths["catalog"], paths["proof"], now)
    current_by_id = everyday.unique_index(current, "id")
    original_by_id = everyday.unique_index(base["rows"], "id")
    if set(current_by_id) != set(original_by_id):
        raise ValueError("Current/base scope mismatch")
    spec = importlib.util.spec_from_file_location("closure", Path(__file__).with_name("close-r3-usda-review.py"))
    closure = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(closure)
    _, _, usda = closure.verify_inputs(paths["r3_catalog"], paths["r3_proof"], paths["usda"])
    if not everyday.fresh(json.loads(paths["usda"].read_text(encoding="utf-8"))["fetchedAt"], now):
        raise ValueError("USDA evidence refresh required")
    registry = json.loads(registry_path.read_text(encoding="utf-8"))
    if registry.get("baselineManifestSha256") != everyday.sha(base_path):
        raise ValueError("Reviewed registry belongs to another baseline manifest")
    rules = rules_index(registry)
    names = everyday.unique_index(registry["usdaNames"], "sourceKey")
    calculated = everyday.unique_index(everyday.read_csv(calculated_path), "source_key")
    selected = [r for r in base["rows"] if r["primaryQueue"] in {"CATEGORY_EVIDENCE_REVIEW", "SOURCE_IDENTITY_REVIEW", "MANIFEST_CURRENT_PROFILE_DRIFT"}]
    if Counter(r["primaryQueue"] for r in selected) != {"CATEGORY_EVIDENCE_REVIEW": 123, "SOURCE_IDENTITY_REVIEW": 10, "MANIFEST_CURRENT_PROFILE_DRIFT": 5}:
        raise ValueError("Exact 123/10/5 target partition changed")
    expected_rules = {r["sourceKey"] for r in selected if r["primaryQueue"] != "SOURCE_IDENTITY_REVIEW"}
    expected_names = {r["sourceKey"] for r in selected if r["primaryQueue"] == "SOURCE_IDENTITY_REVIEW"}
    if set(rules) != expected_rules or set(names) != expected_names:
        raise ValueError("Exact decision coverage required; no extra/fuzzy rules")
    if set(calculated) != {r["sourceKey"] for r in selected if r["primaryQueue"] == "MANIFEST_CURRENT_PROFILE_DRIFT"}:
        raise ValueError("Exact five calculated-profile keys required")
    updated = []
    for original in base["rows"]:
        if original not in selected:
            updated.append(copy.deepcopy(original))
            continue
        key = original["sourceKey"]
        updated.append(resolve(original, current_by_id[original["id"]], rules.get(key), names.get(key),
                               usda.get(key.rsplit(":", 1)[-1]), calculated.get(key), categories))
    safe = [r for r in updated if r["status"] == "SAFE_CATEGORY_NOMINATION"]
    held = [r for r in updated if r["status"] == "REVIEW_REQUIRED"]
    changed = [r for r in updated if r["id"] in {t["id"] for t in selected}]
    hashes = {"baselineManifest": everyday.sha(base_path), "reviewRegistry": everyday.sha(registry_path),
              "calculatedImport": everyday.sha(calculated_path), "javaPrecisionContract": everyday.sha(normalizer_path),
              "producer": everyday.sha(Path(__file__)), **{key: everyday.sha(path) for key, path in paths.items()}}
    summary = {"version": VERSION, "generatedAt": now.isoformat(), "snapshotTime": proof["time"], "rows": len(updated),
               "reviewedRows": len(changed), "newSafeNominations": sum(r["status"] == "SAFE_CATEGORY_NOMINATION" for r in changed),
               "safeCategoryNominations": len(safe), "reviewRequired": len(held),
               "reviewQueues": dict(Counter(r["primaryQueue"] for r in held)),
               "byPreviousQueue": {q: dict(Counter(r["status"] for r in changed if original_by_id[r["id"]]["primaryQueue"] == q)) for q in ("CATEGORY_EVIDENCE_REVIEW", "SOURCE_IDENTITY_REVIEW", "MANIFEST_CURRENT_PROFILE_DRIFT")},
               "profileComparisons": {r["id"]: {k: v for k, v in r["profileComparison"].items() if k != "fields"} for r in changed if "profileComparison" in r},
               "databaseWrites": 0, "liveApplyAllowed": False, "allNutritionVerified": False}
    output.mkdir(parents=True, exist_ok=True)
    name_proposals = {"version": "R6_REVIEWED_NAME_CORRECTION_PROPOSALS_V1", "liveApplyAllowed": False,
                      "localizationsAndAliasesMeasured": False,
                      "requiredBeforeApply": "Fresh exact EN/TR localization and active-alias before-images, explicit R4 approval, backup, compatible preparation/servings and rollback; do not keep misleading cut/treatment aliases active.",
                      "rows": [r for r in changed if r.get("nameDecision", {}).get("decision") == "NAME_CORRECTION_REQUIRED"]}
    for name, value in (("resolved-category-dry-run.json", {"summary": summary, "inputHashes": hashes, "rows": updated}),
                        ("application-plan.json", prepare_plan(updated, current_by_id, categories, proof["time"], hashes)),
                        ("name-correction-proposals.json", name_proposals),
                        ("summary.json", summary)):
        (output / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    fields = ["id", "name", "sourceKey", "catalogType", "targetSlug", "status", "primaryQueue", "reasons", "warnings"]
    for name, rows in (("reviewed-138.csv", changed), ("safe-category-nominations.csv", safe), ("review-required.csv", held)):
        with (output / name).open("w", encoding="utf-8-sig", newline="") as stream:
            writer = csv.DictWriter(stream, fieldnames=fields)
            writer.writeheader()
            for row in rows:
                values = {k: " | ".join(row[k]) if isinstance(row[k], list) else row[k] for k in fields}
                writer.writerow({k: "'" + v if isinstance(v, str) and v.lstrip().startswith(("=", "+", "-", "@")) else v for k,v in values.items()})
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=FOLDER / "resolved-review")
    args = parser.parse_args()
    build(args.output, datetime.now(timezone.utc))

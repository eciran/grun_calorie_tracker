"""Source-bound category nominations, never an import or live approval.

USDA categories are independent source evidence. Internal dish/prepared manifests
prove an intended catalogue identity, NOT independently verified nutrition.
"""
import argparse
import csv
import hashlib
import importlib.util
import json
import math
import unicodedata
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

csv.field_size_limit(16 * 1024 * 1024)
ROOT = Path(__file__).resolve().parents[1]
VERSION = "R6_EVERYDAY_CATEGORY_NOMINATIONS_V1"
TYPES = {"GENERIC_INGREDIENT": 229, "LOCAL_DISH": 267, "STANDARD_PREPARED_ITEM": 200}
# Broad source groups nominate a root unless a narrower group is unambiguous.
USDA_GROUPS = {"0500": "poultry", "1000": "red-meat", "1300": "red-meat",
               "1700": "red-meat", "1500": "fish-seafood", "1800": "bread-bakery-staples",
               "0800": "breakfast-cereals", "2500": "savoury-snacks"}
LOCAL_GROUPS = {"SOUP": "soups", "SANDWICH": "sandwiches-wraps", "DESSERT": "desserts-ice-cream",
                "MAIN_DISH": "prepared-meals", "VEGETABLE_DISH": "prepared-meals",
                "LEGUME_DISH": "prepared-meals", "RICE_DISH": "prepared-meals",
                "GRAIN_DISH": "prepared-meals", "PASTA_DISH": "prepared-meals",
                "NOODLE_DISH": "prepared-meals", "STUFFED_DISH": "prepared-meals",
                "DUMPLING": "prepared-meals", "SIDE_DISH": "prepared-meals",
                "SALAD": "prepared-meals", "SAUCE_DISH": "sauces-condiments"}
PREPARED_GROUPS = {"COFFEE": "hot-drinks", "HOT_DRINK": "hot-drinks", "ALCOHOL": "beverages",
                   "BREAKFAST": "prepared-meals", "LIGHT_MEAL": "prepared-meals",
                   "SIDE_FAST": "prepared-meals"}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def fingerprint(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def read_csv(path):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def unique_index(rows, field):
    keys = [r.get(field) for r in rows]
    if any(not key for key in keys) or len(keys) != len(set(keys)):
        raise ValueError("Missing/duplicate source identity: " + field)
    return dict(zip(keys, rows))


def normalized(value):
    return " ".join(unicodedata.normalize("NFC", str(value or "")).casefold().split())


def fresh(timestamp, now):
    stamp = datetime.fromisoformat(timestamp.replace("Z", "+00:00"))
    if stamp.tzinfo is None:
        return False
    return 0 <= (now - stamp).total_seconds() <= 86400


def verify_snapshot(catalog, proof, now):
    rows = read_csv(catalog)
    p = json.loads(proof.read_text(encoding="utf-8"))
    if (p.get("origin") != "AWS_READ_ONLY_SNAPSHOT" or p.get("transactionReadOnly") is not True
            or p.get("exitCode") != 0 or p.get("scope") != "r6-everyday"
            or p.get("sha256") != sha(catalog) or p.get("rows") != len(rows)
            or p.get("sqlSha256") != sha(ROOT / "scripts/sql/export-r6-everyday-category-review.sql")
            or not fresh(p["time"], now)):
        raise ValueError("Fresh, intact, scope-bound AWS read proof required")
    unique_index(rows, "id")
    parsed = [{"id": r["id"], **{k: json.loads(r[k]) for k in ("food", "assignments", "source_tags", "evidence")}} for r in rows]
    if len(p["metrics"]) != 1 or p["metrics"][0]["orphanAssignments"] != 0:
        raise ValueError("Unambiguous live category integrity proof required")
    if dict(Counter(r["food"]["catalog_type"] for r in parsed)) != TYPES or p["metrics"][0]["types"] != TYPES:
        raise ValueError("696-product cohort drift; investigate rather than silently truncate")
    if any(str(r["food"]["id"]) != r["id"] or r["food"].get("is_custom")
           or r["food"].get("publication_status") != "PUBLISHED" for r in parsed):
        raise ValueError("Invalid published/non-custom scope")
    unique_index([r["food"] for r in parsed], "source_key")
    categories = unique_index(p["metrics"][0]["categories"], "slug")
    ids = unique_index(list(categories.values()), "id")
    for category in categories.values():
        seen = set()
        node = category
        while node:
            if node["id"] in seen:
                raise ValueError("Category hierarchy cycle")
            seen.add(node["id"])
            parent = node.get("parent_id")
            if parent is not None and parent not in ids:
                raise ValueError("Unknown category parent")
            node = ids.get(parent)
    return parsed, p, categories


def usda_target(source):
    """Use source category AND source identity qualifiers, never a DB name keyword."""
    code = str(source.get("foodCategory", {}).get("code", ""))
    description = normalized(source.get("description"))
    base = description.split(",")[0]
    if code in USDA_GROUPS:
        return USDA_GROUPS[code]
    if code == "0900":
        if base == "orange juice":
            return "juice"
        fruit_bases = {"watermelon", "raisins", "pears", "dates", "olives", "pomegranates", "plums",
                       "apricots", "cherries", "figs", "blackberries", "peaches", "melons", "oranges",
                       "bananas", "apples", "pineapple", "strawberries", "raspberries", "blueberries",
                       "grapes", "kiwifruit (kiwi)", "mandarin", "mango"}
        return "fruit" if base in fruit_bases else None
    if code == "1100":
        if base in {"edamame", "soybeans"}:
            return "legumes-pulses"
        if base in {"potatoes", "sweet potato"}:
            return "potatoes-starchy-vegetables"
        if base == "tomato" and description.startswith("tomato, paste,"):
            return "sauces-condiments"
        vegetable_bases = {"pumpkin", "spinach", "squash", "beets", "artichokes", "eggplant", "kale",
                           "leeks", "mushrooms", "okra", "sweet potato leaves", "broccoli", "brussels sprouts",
                           "cabbage", "chard", "onions", "parsnips", "tomatoes", "carrots", "cauliflower",
                           "peas", "garlic", "peppers", "lettuce", "beans", "celery", "cucumber"}
        return "vegetables" if base in vegetable_bases else None
    if code == "0100":
        return {"milk": "milk", "egg": "eggs", "cheese": "cheese", "cottage cheese": "cheese",
                "cream cheese": "cheese", "yogurt": "yogurt-cultured-dairy", "kefir": "yogurt-cultured-dairy",
                "butter": "solid-fats", "cream": "dairy-eggs"}.get(base)
    if code == "0400":
        return {"oil": "cooking-oils", "mayonnaise": "sauces-condiments"}.get(base)
    if code in {"1200", "1600"}:
        if "butter" in base or (base in {"nuts", "seeds"} and " butter," in description):
            return "spreads-dips"
        return "nuts-seeds" if code == "1200" else "legumes-pulses"
    if code == "2000":
        if base == "chia seeds":
            return "nuts-seeds"
        return "pasta-noodles" if base == "pasta" else "grains-starches"
    if code == "1400":
        if base == "water":
            return "water"
        if description.startswith(("beverages, coffee,", "beverages, tea,")):
            return "hot-drinks"
    # Sugars, spices, unknown groups need a taxonomy decision; no catch-all.
    return None


def manifest_drift(food, source):
    reasons = []
    names = [source.get(k) for k in ("name", "display_name_en", "display_name_tr")]
    if normalized(food["name"]) not in {normalized(n) for n in names if n}:
        reasons.append("CURRENT_NAME_MANIFEST_IDENTITY_REVIEW")
    for field in ("preparation_state", "market_region", "nutrition_basis", "nutrition_reference_unit"):
        if source.get(field) and food.get(field) != source[field]:
            reasons.append("MANIFEST_" + field.upper() + "_DRIFT")
    for field in ("calories", "protein", "fat", "carbs"):
        current, expected = food.get(field), source.get(field)
        try:
            valid = current is not None and not isinstance(current, bool) and math.isfinite(float(current)) and math.isfinite(float(expected))
            same = valid and abs(float(current) - float(expected)) <= 0.000001
        except (ValueError, TypeError):
            same = False
        if not same:
            reasons.append("MANIFEST_CORE_PROFILE_DRIFT")
            break
    return reasons


def prepared_target(source):
    group, portion = source.get("category"), source.get("portion_profile")
    if group in {"COFFEE", "HOT_DRINK"}:
        if portion in {"COLD_DRINK", "BLENDED_DRINK", "COCKTAIL_GLASS"}:
            return "beverages"
        if portion == "DESSERT_CUP" and source.get("item_key") == "affogato":
            return "desserts-ice-cream"
        if portion in {"ESPRESSO", "HOT_DRINK", "MILK_COFFEE", "TURKISH_COFFEE",
                       "TEA_CUP", "TEA_MUG", "TURKISH_TEA_GLASS", "MILK_DRINK"}:
            return "hot-drinks"
        return None
    if group in {"BREAKFAST", "LIGHT_MEAL", "SIDE_FAST"}:
        if portion == "EGG_PORTION":
            return "eggs"
        if portion in {"SANDWICH", "WRAP", "TOAST_PORTION", "ROLL"}:
            return "sandwiches-wraps"
        # Exact curated identifiers, not arbitrary current product-name regex.
        if source.get("item_key") in {"margherita_pizza_slice", "pepperoni_pizza_slice"}:
            return "pizza-pies"
    return PREPARED_GROUPS.get(group)


def nominate(row, sources, categories, r4_keys, r5_holds, source_fresh=True):
    food = row["food"]
    kind, key = food["catalog_type"], food["source_key"]
    reasons, warnings = [], []
    target = None
    source = None
    origin = None
    if row["assignments"]:
        reasons.append("EXISTING_ASSIGNMENT_PRESERVE_ADMIN_REVIEW")
    if key in r4_keys:
        reasons.append("R4_NAMING_PREPARATION_GATE")
    if row["id"] in r5_holds:
        reasons.extend("R5_" + r for r in r5_holds[row["id"]])
    if kind == "GENERIC_INGREDIENT":
        source = sources["usda"].get(key)
        origin = "OFFICIAL_USDA_FOOD_CATEGORY"
        if source and food.get("data_source") == "USDA_FOODDATA":
            target = usda_target(source)
            if not source_fresh:
                reasons.append("USDA_EVIDENCE_STALE")
            # R3's frozen identity is checked again for drift, without treating
            # unchanged values as independent verification of all nutrients.
            prior = sources["prior"].get(key)
            if not prior or prior["name"] != food["name"] or prior["preparation_state"] != food["preparation_state"]:
                reasons.append("R3_CURRENT_IDENTITY_DRIFT")
            if normalized(food["name"]) != normalized(source["description"]):
                reasons.append("USDA_CURRENT_NAME_IDENTITY_REVIEW")
        else:
            reasons.append("EXACT_USDA_SOURCE_IDENTITY_MISSING")
    elif kind == "LOCAL_DISH":
        source = sources["local"].get(key)
        baseline = sources["local_baseline"].get(key)
        origin = "INTERNAL_LOCAL_DISH_TAXONOMY_NOT_LAB_VERIFICATION"
        warnings.append("INTERNAL_TAXONOMY_ONLY_NOT_NUTRITION_CERTIFICATION")
        if source and baseline and food.get("data_source") == "LOCAL_CURATED":
            reasons.extend(manifest_drift(food, baseline))
            target = LOCAL_GROUPS.get(source.get("category"))
            if baseline.get("nutrition_basis") == "ESTIMATED":
                warnings.append("ESTIMATED_NUTRITION_REQUIRES_SEPARATE_REVIEW")
        else:
            reasons.append("EXACT_LOCAL_MANIFEST_IDENTITY_MISSING")
    else:
        source = sources["prepared"].get(key)
        baseline = sources["prepared_ready"].get(key)
        origin = "INTERNAL_PREPARED_MANIFEST_NOT_INDEPENDENT_SOURCE_VALIDATION"
        warnings.append("INTERNAL_TAXONOMY_ONLY_NOT_NUTRITION_CERTIFICATION")
        if source and baseline and food.get("data_source") == "LOCAL_CURATED":
            reasons.extend(manifest_drift(food, baseline))
            target = prepared_target(source)
        else:
            reasons.append("EXACT_PREPARED_MANIFEST_IDENTITY_MISSING")
    if not target:
        reasons.append("AMBIGUOUS_OR_MISSING_SOURCE_CATEGORY")
    elif target not in categories or not categories[target]["active"]:
        reasons.append("TARGET_CATEGORY_MISSING_OR_INACTIVE")
    else:
        by_id = {c["id"]: c for c in categories.values()}
        node = categories[target]
        while node:
            if not node["active"]:
                reasons.append("TARGET_ANCESTOR_INACTIVE")
            node = by_id.get(node.get("parent_id"))
    reasons = sorted(set(reasons))
    status = "SAFE_CATEGORY_NOMINATION" if not reasons else "REVIEW_REQUIRED"
    return {"id": row["id"], "sourceKey": key, "name": food["name"], "catalogType": kind,
            "market": food.get("market_region"), "targetSlug": target, "status": status,
            "reasons": reasons, "warnings": warnings, "origin": origin,
            "sourceCategory": source.get("foodCategory", source.get("category")) if source else None,
            "sourceIdentitySha256": fingerprint(source) if source else None,
            "beforeSha256": fingerprint(row), "oldAssignments": row["assignments"],
            "applyAllowed": False, "nutritionVerified": False}


def primary_review_queue(row):
    reasons = row["reasons"]
    if row["status"] == "SAFE_CATEGORY_NOMINATION":
        return "CATEGORY_NOMINATION_ONLY"
    if "EXISTING_ASSIGNMENT_PRESERVE_ADMIN_REVIEW" in reasons:
        return "PRESERVE_EXISTING_CATEGORY"
    if any(r.startswith(("R3_", "R4_", "R5_")) for r in reasons):
        return "PREREQUISITE_R3_R4_R5"
    if "MANIFEST_CORE_PROFILE_DRIFT" in reasons:
        return "MANIFEST_CURRENT_PROFILE_DRIFT"
    if any("IDENTITY" in r for r in reasons):
        return "SOURCE_IDENTITY_REVIEW"
    return "CATEGORY_EVIDENCE_REVIEW"


def build(output, now):
    folder = ROOT / "outputs/r6-everyday-categories-20261012"
    paths = {
        "catalog": folder / "aws/catalog.csv", "proof": folder / "aws/snapshot-proof.json",
        "r3_catalog": ROOT / "outputs/r3-aws-source-review-20261011/catalog.csv",
        "r3_proof": ROOT / "outputs/r3-aws-source-review-20261011/snapshot-proof.json",
        "usda": ROOT / "outputs/r3-aws-source-review-20261011/usda/evidence.json",
        "local": ROOT / "outputs/local-food-catalog-aug08/staging/d4-local-dish-taxonomy-frozen-v2.csv",
        "local_baseline": ROOT / "outputs/local-food-catalog-aug25/local-dish-nutrition-baseline-v2.csv",
        "prepared": ROOT / "sample-data/standard-prepared-catalog-200-v1.csv",
        "prepared_ready": ROOT / "outputs/standard-prepared-ready/standard-prepared-catalog-200-ready.csv",
        "portion_profiles": ROOT / "sample-data/standard-prepared-portion-profiles-v1.csv",
        "r4": ROOT / "src/main/resources/catalog/source-name-decisions-r4-v1.json",
        "r5": ROOT / "outputs/r5-held-followup-20261012/final-review/release-review.json",
        "rules": Path(__file__).resolve(),
    }
    rows, proof, categories = verify_snapshot(paths["catalog"], paths["proof"], now)
    spec = importlib.util.spec_from_file_location("r3_closure", Path(__file__).with_name("close-r3-usda-review.py"))
    closure = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(closure)
    prior, _, usda = closure.verify_inputs(paths["r3_catalog"], paths["r3_proof"], paths["usda"])
    evidence = json.loads(paths["usda"].read_text(encoding="utf-8"))
    sources = {"usda": {"USDA_FOODDATA:fdc:" + key: value for key, value in usda.items()},
               "prior": unique_index(prior, "source_key")}
    for key in ("local", "local_baseline", "prepared_ready"):
        sources[key] = unique_index(read_csv(paths[key]), "source_key")
    sources["prepared"] = {"GRUN_PREPARED_V1:" + key: value for key, value in unique_index(read_csv(paths["prepared"]), "item_key").items()}
    profiles = unique_index(read_csv(paths["portion_profiles"]), "profile_key")
    if any(r["portion_profile"] not in profiles for r in sources["prepared"].values()):
        raise ValueError("Unknown prepared portion profile")
    r4_keys = {r["sourceKey"] for r in json.loads(paths["r4"].read_text(encoding="utf-8"))["entries"]}
    r5 = json.loads(paths["r5"].read_text(encoding="utf-8"))
    held = {str(r["id"]): r["holds"] for r in r5["rows"] if r["status"] == "HOLD"}
    decisions = [nominate(r, sources, categories, r4_keys, held, fresh(evidence["fetchedAt"], now)) for r in rows]
    for row in decisions:
        row["primaryQueue"] = primary_review_queue(row)
    safe = [r for r in decisions if r["status"] == "SAFE_CATEGORY_NOMINATION"]
    review = [r for r in decisions if r["status"] == "REVIEW_REQUIRED"]
    summary = {"ruleset": VERSION, "generatedAt": now.isoformat(), "snapshotTime": proof["time"],
               "rows": len(rows), "safeCategoryNominations": len(safe), "reviewRequired": len(review),
               "categoryEvidenceKnownIncludingHeld": sum(r["targetSlug"] is not None for r in decisions),
               "byType": {kind: dict(Counter(r["status"] for r in decisions if r["catalogType"] == kind)) for kind in TYPES},
               "safeTargets": dict(Counter(r["targetSlug"] for r in safe)),
               "exclusiveReviewQueues": dict(Counter(r["primaryQueue"] for r in review)),
               "safeEvidenceOrigins": dict(Counter(r["origin"] for r in safe)),
               "reviewReasonsOccurrencesNotDistinctProducts": dict(Counter(x for r in review for x in r["reasons"])),
               "applyAllowed": False, "databaseWrites": 0, "allNutritionVerified": False,
               "releaseRequirements": "Fresh exact before-images, source identity/R3/R4/R5 gates, explicit admin category approval, backup and rehearsed rollback. Internal manifests are not external nutrition verification."}
    output.mkdir(parents=True, exist_ok=True)
    manifest = {"summary": summary, "inputHashes": {key: sha(path) for key, path in paths.items()},
                "liveTaxonomySha256": fingerprint(list(categories.values())), "rows": decisions}
    (output / "category-dry-run.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    fields = ("id", "name", "catalogType", "market", "sourceKey", "targetSlug", "status", "primaryQueue", "origin", "reasons", "warnings", "beforeSha256", "sourceIdentitySha256", "applyAllowed")
    for name, selected in (("all-696.csv", decisions), ("safe-category-nominations.csv", safe), ("review-required.csv", review)):
        with (output / name).open("w", encoding="utf-8-sig", newline="") as stream:
            writer = csv.DictWriter(stream, fieldnames=fields)
            writer.writeheader()
            for row in selected:
                values = {key: " | ".join(row[key]) if isinstance(row[key], list) else row[key] for key in fields}
                writer.writerow({key: "'" + val if isinstance(val, str) and val.lstrip().startswith(("=", "+", "-", "@")) else val for key, val in values.items()})
    (output / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=ROOT / "outputs/r6-everyday-categories-20261012/review")
    args = parser.parse_args()
    build(args.output, datetime.now(timezone.utc))

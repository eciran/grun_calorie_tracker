"""Full nutrient/current-option gate. Outputs review only; never connects to DB.

Only source-backed, fresh decisions are release candidates. Unknown fields are
retained, not zero-filled. This manifest is not permission to run live SQL.
"""
import argparse
import csv
import importlib.util
import json
import math
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

spec = importlib.util.spec_from_file_location("source_options", Path(__file__).with_name("prepare-r5-source-options.py"))
source = importlib.util.module_from_spec(spec)
spec.loader.exec_module(source)
FIELDS = {"calories": "energy-kcal", "protein": "proteins", "fat": "fat", "carbs": "carbohydrates",
          "fiber": "fiber", "sugar": "sugars", "sodium": "sodium", "potassium": "potassium",
          "cholesterol": "cholesterol", "calcium": "calcium", "iron": "iron", "magnesium": "magnesium", "zinc": "zinc",
          "vitamin_a": "vitamin-a", "vitamin_c": "vitamin-c", "vitamin_d": "vitamin-d", "vitamin_e": "vitamin-e",
          "vitamin_b12": "vitamin-b12", "saturated_fat": "saturated-fat", "trans_fat": "trans-fat", "sugar_alcohol": "sugar-alcohol"}
USDA = {"protein": (1003, "g"), "fat": (1004, "g"), "carbs": (1005, "g"), "fiber": (1079, "g"),
        "sugar": (2000, "g"), "sodium": (1093, "mg"), "potassium": (1092, "mg"), "cholesterol": (1253, "mg"),
        "calcium": (1087, "mg"), "iron": (1089, "mg"), "magnesium": (1090, "mg"), "zinc": (1095, "mg"),
        "vitamin_a": (1104, "iu"), "vitamin_c": (1162, "mg"), "vitamin_d": (1114, "ug"),
        "vitamin_e": (1109, "mg"), "vitamin_b12": (1178, "ug"), "saturated_fat": (1258, "g"), "trans_fat": (1257, "g")}
READY = {"SOURCE_BASIS_CORRECTION_REQUIRES_FULL_BEFORE_IMAGE", "SOURCE_PORTION_READY_FOR_REVIEW", "SOURCE_PORTIONS_READY_FOR_REVIEW"}
RULESET = 'R5_FULL_NUTRITION_AND_OPTIONS_GATE_V3_TYPED_SOURCE_REVIEW'
USDA_VARIANTS = {
    'vitamin_a': ((1104, 'iu', 'VITAMIN_A_IU'), (1106, 'ug', 'VITAMIN_A_RAE')),
    'vitamin_d': ((1110, 'iu', 'VITAMIN_D_D2_D3_IU'), (1114, 'ug', 'VITAMIN_D_D2_D3_UG')),
    'sugar': ((2000, 'g', 'TOTAL_SUGARS_2000'), (1063, 'g', 'TOTAL_SUGARS_1063')),
}


def eligible_selection(decisions):
    return [d for d in decisions if d["decision"] in READY and not d.get("evidenceOrigin") == "CACHED_OFF_DUMP"]


def all_off_fields(product):
    view, error = source.off_v35_view(product)
    if error:
        raise ValueError(error)
    values = {}
    if view:
        profile = product["nutrition"]["input_sets"][view["inputSetIndex"]]
        reference = source.measure(profile.get("per_quantity"), profile.get("per_unit"))
        amount = reference[1] if profile["per"] == "serving" else 100
        for field, name in FIELDS.items():
            nutrient = profile.get("nutrients", {}).get(name, {})
            value, unit = source.r5.number(nutrient.get("value")), nutrient.get("unit")
            scale = 1 if unit == ("kcal" if field == "calories" else "g") else {"mg": .001, "ug": .000001, "µg": .000001, "μg": .000001, "mcg": .000001}.get(unit) if field != "calories" else None
            if value is None or scale is None or nutrient.get("modifier") in {"<", "<=", ">", ">="}:
                values[field] = None
            else:
                values[field] = value * scale * 100 / amount
        values.update(view["core"])
    else:
        n = product.get("nutriments", {})
        for field, name in FIELDS.items():
            candidates = [source.r5.number(n[k + "_100g"]) for k in {name, name.replace("-", "_")} if k + "_100g" in n]
            valid = [v for v in candidates if v is not None]
            if len(set(valid)) > 1:
                raise ValueError("Legacy nutrient aliases conflict")
            values[field] = None if str(n.get(name + "_unit", "")).lower() in {"iu", "%", "% dv"} else valid[0] if valid else None
        values.update(source.off_core(product))
    return values


def all_usda_fields(food):
    values = {field: None for field in FIELDS}
    for field, (nid, unit) in USDA.items():
        candidates = [n for n in food.get("foodNutrients", []) if n.get("nutrient", {}).get("id") == nid]
        if len(candidates) == 1 and str(candidates[0]["nutrient"].get("unitName", "")).lower().replace("µ", "u").replace("μ", "u") == unit:
            values[field] = source.r5.number(candidates[0].get("amount"))
    values.update(source.usda_core(food))
    return values


def typed_usda_variants(food, body):
    """Expose every typed alternative; a matching scalar never selects its unit/form."""
    result = {}
    for field, definitions in USDA_VARIANTS.items():
        current = source.r5.number(food.get(field))
        candidates, conflict = [], False
        for nid, expected_unit, semantic in definitions:
            records = [n for n in body.get('foodNutrients', []) if n.get('nutrient', {}).get('id') == nid]
            if len(records) > 1: conflict = True
            for record in records:
                unit = str(record['nutrient'].get('unitName', '')).lower().replace('\u00b5', 'u').replace('\u03bc', 'u')
                amount = source.r5.number(record.get('amount'))
                valid = unit == expected_unit and amount is not None
                conflict = conflict or not valid
                candidates.append({'nutrientId': nid, 'semantic': semantic,
                    'sourceName': record['nutrient'].get('name'), 'unit': unit, 'amount': amount,
                    'valid': valid, 'sameNumberWithinRounding': valid and current is not None and abs(current - amount) <= .051})
        result[field] = {'current': current, 'candidates': candidates, 'sourceConflict': conflict,
            'matchingSourceIds': [c['nutrientId'] for c in candidates if c['sameNumberWithinRounding']],
            'legacyUnitAttested': False, 'sourceDefinitionAttestedForCurrent': False,
            'conversionAllowed': False, 'numericWriteAllowed': False, 'selectedCandidate': None}
    return result


def comparison(food, values, provider):
    result = {}
    for field in FIELDS:
        if field not in food:
            result[field] = {"state": "UNMEASURED_CURRENT_FIELD", "current": None, "source": values[field]}
            continue
        actual, expected = source.r5.number(food[field]), values[field]
        tolerance = .51 if field == "calories" else .051 if field in {"protein", "fat", "carbs", "fiber", "sugar", "saturated_fat", "trans_fat", "sugar_alcohol"} or provider == "USDA_FOODDATA" else .00051
        state = ("INVALID_CURRENT" if food[field] is not None and actual is None else "UNKNOWN_CURRENT_RETAINED" if actual is None
                 else "SOURCE_VALUE_REQUIRED" if expected is None else "MATCH" if abs(actual - expected) <= tolerance else "DIFFERS")
        result[field] = {"state": state, "current": actual, "source": expected, "tolerance": tolerance,
                         "unitPolicy": "OFF_NORMALIZED_GRAMS_KCAL" if provider == "OPEN_FOOD_FACTS" else "EXACT_FDC_REPORTED_UNIT_NOT_CANONICAL_MICRO_CONVERSION"}
    return result


def additive_usda_holds(decision, current, body, options):
    food, holds = current["food"], []
    normalize = source.r5.closure.review.match_name
    if normalize(food.get("name") or "") != normalize(body.get("description") or ""):
        holds.append("CURRENT_NAME_SOURCE_IDENTITY_REVIEW")
    states = source.r5.closure.source_states(body.get("description", ""))
    state = food.get("preparation_state")
    if states and state not in states and not (state == "COOKED" and "cooked" in body["description"].lower()):
        holds.append("SOURCE_PREPARATION_CONFLICT")
    if food.get("barcode") or food.get("normalized_barcode") or body.get("gtinUpc"):
        holds.append("USDA_GENERIC_GTIN_REVIEW")
    if (body.get("dataType") not in {"Foundation", "SR Legacy"} or food.get("catalog_type") != "GENERIC_INGREDIENT"
            or food.get("nutrition_reference_unit") != "PER_100G"):
        holds.append("ADDITIVE_SOURCE_TYPE_OR_BASIS_REVIEW")
    # Do not make gram-based beverage cups look like a completed ml conversion.
    category = body.get("foodCategory", {}).get("description", "")
    liquid = category == "Beverages" or category == "Fruits and Fruit Juices" and "juice" in body.get("description", "").lower()
    try:
        rebuilt_options, _ = source.usda_portion_options(body)
        rebuilt_fingerprints = {source.r5.fingerprint(o) for o in rebuilt_options}
    except (ValueError, TypeError):
        rebuilt_options = []
        rebuilt_fingerprints = set()
        holds.append("EXACT_SOURCE_PORTION_REBUILD_REQUIRED")
    if liquid and not any(o.get('mlVolume') is not None for o in rebuilt_options):
        holds.append('LIQUID_ML_CONTRACT_REVIEW')
    for option in options:
        if option.get("mlVolume") is not None and not liquid:
            holds.append("PORTION_COUNT_OR_CROSS_DIMENSION_MOBILE_REVIEW")
        # Regenerate from the complete source, including the unique whole-piece parent.
        # Source counts/fractions are allowed only when every field and label matches.
        try:
            supported = source.r5.fingerprint(option) in rebuilt_fingerprints
        except (ValueError, TypeError):
            supported = False
        if not supported:
            holds.append("EXACT_SOURCE_PORTION_REBUILD_REQUIRED")
        for old in current["options"]:
            if (old.get("label") == option["labels"]["EN"] and
                    (old.get("gram_weight"), old.get("ml_volume")) != (option.get("gramWeight"), option.get("mlVolume"))):
                holds.append("EXISTING_OPTION_LABEL_WEIGHT_CONFLICT")
    return sorted(set(holds))


def review(decision, current, body, checked_at, now, approve_additive_usda=False):
    food = current["food"]
    reasons = []
    result = {"id": decision["id"], "sourceKey": decision["sourceKey"], "before": current,
              "beforeFingerprint": source.r5.fingerprint(current), "sourceFingerprint": source.r5.fingerprint(body),
              "nutritionWriteAllowed": False, "liveApplyApproved": False, "options": [], "changes": {}, "holds": reasons}
    if food.get("source_key") != decision["sourceKey"] or food.get("is_custom") is True or food.get("publication_status") != "PUBLISHED":
        reasons.append("CURRENT_IDENTITY_OR_PUBLICATION_CHANGED")
    if source.r5.fingerprint(body) != decision["sourceFingerprint"]:
        reasons.append("SOURCE_VERSION_CHANGED_REBUILD_REQUIRED")
    try:
        age = (now - datetime.fromisoformat(checked_at.replace("Z", "+00:00"))).total_seconds()
        if not 0 <= age <= 86400:
            reasons.append("SOURCE_FRESHNESS_RECHECK_REQUIRED")
    except (TypeError, ValueError, AttributeError):
        reasons.append("SOURCE_FRESHNESS_UNPROVEN")
    if food.get("reviewed_by") or food.get("quality_validated_by") or food.get("verification_status") == "VERIFIED":
        reasons.append("EXISTING_ADMIN_APPROVAL_REVIEW_REQUIRED")
    if food.get("nutrition_basis") not in {None, "SOURCE_REPORTED"}:
        reasons.append("CALCULATED_OR_ESTIMATED_PROFILE_PROTECTED")
    provider = food.get("data_source")
    if provider == "OPEN_FOOD_FACTS":
        code = food.get("normalized_barcode") or food.get("barcode")
        if not source.r5.valid_gtin(code) or str(body.get("code")) != code:
            reasons.append("EXACT_GTIN_CONFLICT")
        fresh = source.off_decision({**food, "id": str(food["id"])}, {"product": body, "barcode": code, "status": "FETCHED", "checkedAt": checked_at if isinstance(checked_at, str) else ""}, now)
        if fresh["decision"] not in READY:
            reasons.append("CURRENT_SOURCE_BASIS_FORM_OR_CORE_REVIEW")
        values = all_off_fields(body)
        target = fresh.get("sourceReferenceUnit")
        if target and target != food.get("nutrition_reference_unit"):
            if target != "PER_100ML" or food.get("nutrition_reference_unit") != "PER_100G":
                reasons.append("UNSUPPORTED_REFERENCE_CHANGE")
            if any(current.get("references", {}).values()):
                reasons.append("ACTIVE_RECIPE_TEMPLATE_OR_PLAN_DEPENDENCY")
            if current["options"]:
                reasons.append("EXISTING_OPTIONS_REQUIRE_REPLACEMENT_REVIEW")
            result["changes"] = {"nutrition_reference_unit": target, "serving_size_grams": None, "serving_unit": "MILLILITER"}
        elif target == "PER_100G" and set(body.get("categories_tags", [])) & source.r5.LIQUID:
            reasons.append("LIQUID_LABEL_OR_MASS_VOLUME_SOURCE_REVIEW")
    elif provider == "USDA_FOODDATA":
        if source.r5.closure.review.fdc_id(food.get("source_key")) != str(body.get("fdcId")):
            reasons.append("EXACT_FDC_CONFLICT")
        if food.get("catalog_type") != "GENERIC_INGREDIENT" or any("R3/R4" in gate for gate in decision.get("requiredGates", [])):
            reasons.append("R3_R4_CLASSIFICATION_OR_PREPARATION_GATE")
        if food.get("nutrition_reference_unit") != "PER_100G":
            reasons.append("USDA_REFERENCE_REVIEW")
        values = all_usda_fields(body)
    else:
        raise ValueError("Unsupported provider")
    result["nutrients"] = comparison(food, values, provider)
    if provider == 'USDA_FOODDATA':
        result['nutrientVariants'] = typed_usda_variants(food, body)
        for field, variants in result['nutrientVariants'].items():
            check = result['nutrients'][field]
            new_state = ('SOURCE_VARIANT_CONFLICT' if variants['sourceConflict'] else
                         'SOURCE_DEFINITION_REVIEW_REQUIRED' if field == 'sugar' else 'UNIT_OR_FORM_ATTESTATION_REQUIRED')
            if variants['sourceConflict'] or (variants['candidates'] and check['state'] in {'DIFFERS', 'SOURCE_VALUE_REQUIRED'}):
                check['legacyComparisonState'] = check['state']
                check['state'] = new_state
                check['typedSourceReviewOnly'] = True
    if (values["sugar"] is not None and values["carbs"] is not None and values["sugar"] > values["carbs"] + .11
            or values["saturated_fat"] is not None and values["fat"] is not None and values["saturated_fat"] > values["fat"] + .11):
        reasons.append("SOURCE_NUTRIENT_RELATIONSHIP_CONFLICT")
    if any(v["state"] not in {"MATCH", "UNKNOWN_CURRENT_RETAINED"} for v in result["nutrients"].values()):
        reasons.append("FULL_NUTRITION_COMPARISON_REQUIRED")
    if any(food.get(field) is None for field in ("calories", "protein", "fat", "carbs")):
        reasons.append("CORE_NUTRITION_MISSING")
    existing = {(o["unit_type"], o["quantity"], o.get("gram_weight"), o.get("ml_volume")) for o in current["options"]}
    for option in decision["options"]:
        signature = (option["unitType"], option["quantity"], option.get("gramWeight"), option.get("mlVolume"))
        if signature not in existing:
            if any(len(label) > 120 or not label.strip() for label in option["labels"].values()):
                reasons.append("OPTION_LABEL_REVIEW")
            result["options"].append(option)
        elif approve_additive_usda and provider == "USDA_FOODDATA":
            equivalents = [o for o in current["options"] if
                          (o["unit_type"], o["quantity"], o.get("gram_weight"), o.get("ml_volume")) == signature]
            if not any(o.get("quality_status") == "VERIFIED" and o.get("user_visible") is True for o in equivalents):
                reasons.append("EXISTING_EQUIVALENT_OPTION_REVIEW_REQUIRED")
    if len(current["options"]) + len(result["options"]) > 20:
        reasons.append("OPTION_LIMIT_REVIEW")
    if approve_additive_usda and provider == "USDA_FOODDATA":
        reasons.extend(additive_usda_holds(decision, current, body, result["options"]))
        if not result["changes"] and set(reasons) <= {"EXISTING_ADMIN_APPROVAL_REVIEW_REQUIRED"}:
            reasons.clear()
            result["approvalPolicy"] = "RETAIN_EXISTING_APPROVAL_APPEND_ONLY"
    result["status"] = "HOLD" if reasons else "READY_FOR_BACKUP_REHEARSAL" if result["changes"] or result["options"] else "NO_CHANGE"
    return result


def run(args):
    decisions = [json.loads(line) for line in args.decisions.read_text(encoding="utf-8").splitlines()]
    args.output.mkdir(parents=True, exist_ok=True)
    if args.select:
        chosen = eligible_selection(decisions)
        path = args.output / "selected-decisions.jsonl"
        path.write_text("".join(json.dumps(d, ensure_ascii=False) + "\n" for d in chosen), encoding="utf-8")
        print(json.dumps({"selected": len(chosen), "liveWrites": 0, "selectionSha256": source.r5.closure.sha(path)}))
        return
    proof = json.loads(args.proof.read_text(encoding="utf-8"))
    if (proof.get("scope") != "r5-release" or proof.get("transactionReadOnly") is not True or proof.get("exitCode") != 0
            or proof.get("sha256") != source.r5.closure.sha(args.current) or proof.get("decisionsSha256") != source.r5.closure.sha(args.decisions)):
        raise ValueError("Current full snapshot proof mismatch")
    with args.current.open(encoding="utf-8-sig", newline="") as stream:
        rows = [{k: json.loads(v) if k != "id" else v for k, v in r.items()} for r in csv.DictReader(stream)]
    current = {str(r["id"]): r for r in rows}
    if len(current) != len(rows) or proof["rows"] != len(rows) or set(current) != {d["id"] for d in decisions}:
        raise ValueError("Exact current membership required")
    usda = json.loads(args.usda_evidence.read_text(encoding="utf-8"))
    foods = {str(f["fdcId"]): f for f in usda["foods"]}
    now, reviews = datetime.now(timezone.utc), []
    for d in decisions:
        if d["sourceKey"].startswith("USDA_FOODDATA:"):
            body = foods[source.r5.closure.review.fdc_id(d["sourceKey"])]
            checked = usda["fetchedAt"]
        else:
            record = json.loads(Path(d["evidenceFile"]).read_text(encoding="utf-8-sig"))
            if source.r5.closure.sha(Path(d["evidenceFile"])) != d["evidenceSha256"]:
                raise ValueError("Source receipt modified")
            body, checked = record["product"], record.get("checkedAt")
        reviews.append(review(d, current[d["id"]], body, checked, now, args.approve_additive_usda))
    path = args.output / "release-review.json"
    path.write_text(json.dumps({"ruleset": RULESET, "rows": reviews}, ensure_ascii=False, allow_nan=False) + "\n", encoding="utf-8")
    summary = {"rows": len(reviews), "states": dict(Counter(r["status"] for r in reviews)),
               "holds": dict(Counter(h for r in reviews for h in r["holds"])), "liveWrites": 0,
               "currentSnapshotTime": proof["time"], "reviewSha256": source.r5.closure.sha(path),
               "nutrientFieldsPerRow": len(FIELDS), "producerSha256": source.r5.closure.sha(Path(__file__))}
    (args.output / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--decisions", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--select", action="store_true")
    parser.add_argument("--approve-additive-usda", action="store_true", help="Approve exact source portions only; retain all current admin facts/defaults")
    for name in ("current", "proof", "usda-evidence"):
        parser.add_argument("--" + name, type=Path)
    run(parser.parse_args())

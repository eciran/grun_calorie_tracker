"""R5 source decisions, not an import or DB application command.

Use complete source bodies, never a join of partial replies by barcode. OFF
normalized nutrients use grams/kcal irrespective of contributor input units.
Mass-volume pairs require an explicit source ml measure; cups are mass-only.
"""
import argparse
import csv
import importlib.util
import json
import math
import re
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

spec = importlib.util.spec_from_file_location("r5", Path(__file__).with_name("build-r5-serving-review.py"))
r5 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(r5)
spec = importlib.util.spec_from_file_location("r5_qualified_measure_contract", Path(__file__).with_name("r5-qualified-source-portions.py"))
measure_contract = importlib.util.module_from_spec(spec)
spec.loader.exec_module(measure_contract)
OFF_SCHEMA = "https://openfoodfacts.github.io/documentation/docs/Product-Opener/schemas/schemas/product_nutrition/"
OFF_V35_SCHEMA = "https://openfoodfacts.github.io/documentation/docs/Product-Opener/schemas/schemas/product_nutrition_v3.5/"
MORE_CONFLICTS = {"en:dehydrated-beverages", "en:dried-products-to-be-rehydrated", "en:beverage-preparations",
                  "en:tea-bags", "en:coffee-beans", "en:ground-coffees", "en:cheeses", "en:yogurts",
                  "en:cream-desserts", "en:curds", "en:dairy-desserts"}
UNITS = {"ml": ("ml", 1), "cl": ("ml", 10), "l": ("ml", 1000),
         "g": ("g", 1), "kg": ("g", 1000)}


def measure(value, unit):
    normalized = UNITS.get(str(unit or "").strip().lower())
    amount = r5.number(value)
    if normalized is None or amount is None or amount <= 0:
        return None
    amount *= normalized[1]
    return (normalized[0], amount) if math.isfinite(amount) and amount > 0 else None


def text_measure(text):
    # Accept one explicit mass/volume expression, not multi-packs or counts.
    text = str(text or "").strip()
    if re.search(r"\d\s*[x×]\s*\d", text, re.I):
        return None
    found = re.findall(r"(?<![\w.])([0-9]+(?:[.,][0-9]+)?)\s*(ml|cl|kg|g|l)\b", text, re.I)
    return measure(found[0][0].replace(",", "."), found[0][1]) if len(found) == 1 else None


def serving_measure(product):
    structured = measure(product.get("serving_quantity"), product.get("serving_quantity_unit"))
    textual = text_measure(product.get("serving_size"))
    if structured and textual and (structured[0] != textual[0] or abs(structured[1] - textual[1]) > 0.001):
        return None, "SERVING_MEASURES_CONFLICT"
    return structured or textual, None


def off_core(product):
    n = product.get("nutriments", {})
    result = {field: r5.number(n.get(source + "_100g")) for field, source in
              (("calories", "energy-kcal"), ("protein", "proteins"), ("fat", "fat"), ("carbs", "carbohydrates"))}
    if result["calories"] is None:
        kj = r5.number(n.get("energy-kj_100g", n.get("energy_100g")))
        result["calories"] = kj / 4.184 if kj is not None else None
    return result


def off_v35_view(product):
    """Cross-check one actual label/manufacturer input set, never mix profiles.

    Aggregated values alone may include estimates or calculated calories. Keep
    the original body immutable and require actual source values for all cores.
    """
    nutrition = product.get("nutrition")
    if not isinstance(nutrition, dict) or not nutrition.get("input_sets"):
        return None, None
    aggregate = nutrition.get("aggregated_set") or {}
    if aggregate.get("preparation") != "as_sold" or aggregate.get("per") not in {"100g", "100ml"}:
        return None, "V35_AS_SOLD_NORMALIZED_PROFILE_REQUIRED"
    nutrients, sets = aggregate.get("nutrients") or {}, nutrition["input_sets"]
    names = {"calories": "energy-kcal", "protein": "proteins", "fat": "fat", "carbs": "carbohydrates"}
    energy_name = "energy-kcal" if r5.number(nutrients.get("energy-kcal", {}).get("value")) is not None else "energy-kj"
    names["calories"] = energy_name
    indexes = {nutrients.get(name, {}).get("source_index") for name in names.values()}
    if len(indexes) != 1:
        return None, "V35_MIXED_OR_MISSING_SOURCE_PROFILE"
    index = next(iter(indexes))
    if not isinstance(index, int) or isinstance(index, bool) or not 0 <= index < len(sets):
        return None, "V35_SOURCE_INDEX_REQUIRED"
    source = sets[index]
    if source.get("source") not in {"packaging", "manufacturer"} or source.get("preparation") != "as_sold":
        return None, "V35_ACTUAL_AS_SOLD_LABEL_REQUIRED"
    per = source.get("per")
    if per in {"100g", "100ml"}:
        basis_measure = ("g" if per == "100g" else "ml", 100)
        supplied = measure(source.get("per_quantity"), source.get("per_unit"))
        if (source.get("per_quantity") is not None or source.get("per_unit") is not None) and supplied != basis_measure:
            return None, "V35_REFERENCE_QUANTITY_CONFLICT"
    elif per == "serving":
        basis_measure = measure(source.get("per_quantity"), source.get("per_unit"))
        if not basis_measure:
            return None, "V35_EXPLICIT_SERVING_UNIT_REQUIRED"
    else:
        return None, "V35_SOURCE_REFERENCE_REQUIRED"
    if aggregate["per"] != ("100ml" if basis_measure[0] == "ml" else "100g"):
        return None, "V35_SOURCE_AGGREGATE_BASIS_CONFLICT"
    core = {}
    for field, name in names.items():
        normalized, original = nutrients.get(name, {}), source.get("nutrients", {}).get(name, {})
        expected_unit = "kcal" if name == "energy-kcal" else "kJ" if name == "energy-kj" else "g"
        if (normalized.get("source") != source["source"] or normalized.get("source_per") != per
                or normalized.get("unit") != expected_unit or normalized.get("modifier") in {"<", "<=", ">", ">="}
                or original.get("modifier") in {"<", "<=", ">", ">="}):
            return None, "V35_SOURCE_UNIT_OR_MODIFIER_REVIEW"
        actual, derived = r5.number(original.get("value")), r5.number(normalized.get("value"))
        if actual is None or derived is None:
            # value_computed is intentionally NOT a replacement for source data.
            return None, "V35_EXPLICIT_CORE_REQUIRED"
        unit = str(original.get("unit", ""))
        scale = 1 if unit == expected_unit else {"mg": .001, "kg": 1000}.get(unit) if expected_unit == "g" else None
        if scale is None:
            return None, "V35_SOURCE_UNIT_OR_MODIFIER_REVIEW"
        expected = actual * scale * 100 / basis_measure[1]
        if not math.isfinite(expected) or abs(expected - derived) > (0.51 if field == "calories" else .051):
            return None, "V35_INPUT_AGGREGATE_VALUE_CONFLICT"
        core[field] = derived / 4.184 if name == "energy-kj" else derived
    if product.get("nutriments"):
        legacy = off_core(product)
        if any(legacy[field] is not None and abs(legacy[field] - value) > (0.51 if field == "calories" else .051)
               for field, value in core.items()):
            return None, "V35_LEGACY_VALUE_CONFLICT"
    return {"core": core, "basis": aggregate["per"], "inputSetIndex": index,
            "inputSetFingerprint": r5.fingerprint(source), "serving": basis_measure if per == "serving" else None}, None


def core_matches(row, core):
    return all(value is not None and r5.number(row.get(field)) is not None
               and abs(r5.number(row[field]) - value) <= (0.51 if field == "calories" else 0.051)
               for field, value in core.items())


def core_differences(row, core):
    return {field: {"current": r5.number(row.get(field)), "source": value}
            for field, value in core.items() if value is None or r5.number(row.get(field)) is None
            or abs(r5.number(row[field]) - value) > (0.51 if field == "calories" else 0.051)}


def usda_core(food):
    result = r5.closure.source_core(food)
    if result["calories"] is None:
        # Documented energy entries, not macro-derived calories or nearest-value selection.
        for nutrient_id in (2048, 2047):
            matches = [n.get("amount") for n in food.get("foodNutrients", []) if n.get("nutrient", {}).get("id") == nutrient_id
                       and n["nutrient"].get("unitName", "").lower() == "kcal"]
            if len(matches) == 1 and r5.number(matches[0]) is not None:
                result["calories"] = r5.number(matches[0])
                break
    return result


def off_decision(row, evidence, as_of):
    product = evidence.get("product") or {}
    code = row.get("normalized_barcode") or row.get("barcode")
    result = {"id": row["id"], "sourceKey": row["source_key"], "beforeFingerprint": r5.fingerprint(row),
              "currentName": row.get("display_name") or row.get("name"), "currentReferenceUnit": row.get("nutrition_reference_unit"),
              "currentServingBeforeImageMeasured": "options" in row and "option_localizations" in row,
              "sourceFingerprint": r5.fingerprint(product), "sourceCheckedAt": evidence.get("checkedAt"),
              "sourceLastModified": product.get("last_modified_t"), "sourceSchema": OFF_SCHEMA,
              "applyAllowed": False, "nutritionWriteAllowed": False, "options": []}
    def hold(reason):
        return {**result, "decision": reason}
    if row["data_source"] != "OPEN_FOOD_FACTS" or not r5.valid_gtin(code) or str(product.get("code")) != code or str(evidence.get("barcode")) != code:
        return hold("SOURCE_IDENTITY_REQUIRED")
    bulk = evidence.get("status") == "BULK_EXTRACTED" and evidence.get("verifiedBulkFile") is True
    result["sourceFreshnessApproved"] = False
    result["evidenceOrigin"] = "CACHED_OFF_DUMP" if bulk else "EXACT_SOURCE_REPLY"
    if evidence.get("status") != "FETCHED" and not bulk:
        return hold("SOURCE_FETCH_REQUIRED")
    if not bulk:
        try:
            age = (as_of - datetime.fromisoformat(evidence["checkedAt"].replace("Z", "+00:00"))).total_seconds()
        except (ValueError, KeyError, TypeError):
            return hold("SOURCE_TIME_REQUIRED")
        if age < 0 or age > 30 * 86400:
            return hold("SOURCE_TIME_OUTSIDE_REVIEW_WINDOW")
        result["sourceFreshnessApproved"] = True
    if "response" in evidence and (evidence["response"].get("product") != product
            or evidence.get("responseFingerprint") != r5.fingerprint(evidence["response"])):
        return hold("SOURCE_RESPONSE_INTEGRITY_REQUIRED")
    if product.get("no_nutrition_data") == "on" or product.get("nutrition_data") in {"off", False}:
        return hold("RAW_NUTRITION_REQUIRED")
    tags = {str(t).strip().lower() for t in product.get("categories_tags", [])}
    if tags & (r5.CONFLICT | MORE_CONFLICTS):
        return hold("SOURCE_FORM_CONFLICT")
    serving, conflict = serving_measure(product)
    if conflict:
        return hold(conflict)
    view, v35_error = off_v35_view(product)
    if v35_error:
        return hold(v35_error)
    basis = str(product.get("nutrition_data_per", "")).lower().strip()
    if view:
        if view["serving"] and serving and view["serving"] != serving:
            return hold("V35_PRODUCT_SERVING_CONFLICT")
        serving = serving or view["serving"]
        source_basis = "PER_100ML" if view["basis"] == "100ml" else "PER_100G"
        if source_basis == "PER_100ML" and not tags & r5.LIQUID:
            return hold("SOURCE_LIQUID_FORM_REQUIRED")
        basis_proof = "V35_EXACT_ACTUAL_INPUT_SET_AND_AGGREGATE"
        result.update(sourceSchema=OFF_V35_SCHEMA, sourceInputSetIndex=view["inputSetIndex"], sourceInputSetFingerprint=view["inputSetFingerprint"])
    elif basis == "100ml":
        source_basis = "PER_100ML"
        if not tags & r5.LIQUID:
            return hold("SOURCE_LIQUID_FORM_REQUIRED")
        basis_proof = "EXPLICIT_100ML"
    elif basis == "serving" and serving:
        source_basis = "PER_100ML" if serving[0] == "ml" else "PER_100G"
        if source_basis == "PER_100ML" and not tags & r5.LIQUID:
            return hold("SOURCE_LIQUID_FORM_REQUIRED")
        basis_proof = "EXPLICIT_SERVING_UNIT_AND_NORMALIZED_VALUES"
        names = {"calories": "energy-kcal", "protein": "proteins", "fat": "fat", "carbs": "carbohydrates"}
        core = off_core(product)
        for field, name in names.items():
            normalized, per_serving = core[field], r5.number(product.get("nutriments", {}).get(name + "_serving"))
            if normalized is None or per_serving is None:
                return hold("PER_SERVING_NORMALIZATION_PROOF_REQUIRED")
            expected = normalized * serving[1] / 100
            # Printed serving values are commonly rounded to whole kcal/0.1 g.
            if abs(expected - per_serving) > (0.51 if field == "calories" else 0.051):
                return hold("PER_SERVING_NORMALIZATION_CONFLICT")
    elif basis == "100g" and serving and serving[0] == "g":
        source_basis, basis_proof = "PER_100G", "EXPLICIT_MASS_SERVING"
    elif basis == "100g" and tags & r5.LIQUID:
        # The generic selector and a volume pack are not a literal label proof.
        return hold("LIQUID_GENERIC_100G_LABEL_REVIEW_REQUIRED")
    else:
        return hold("SOURCE_REFERENCE_BASIS_REQUIRED")
    core = view["core"] if view else off_core(product)
    result["sourceCore"] = core
    result["coreDifferences"] = core_differences(row, core)
    result.update(sourceReferenceUnit=source_basis, basisProof=basis_proof)
    if any(v is None for v in core.values()):
        return hold("SOURCE_CORE_INCOMPLETE")
    if not core_matches(row, core):
        return hold("CURRENT_CORE_DIFFERS_FROM_SOURCE")
    result.update(sourceReferenceUnit=source_basis, basisProof=basis_proof, sourceCore=core,
                  normalizedUnitPolicy="OFF_V35_ACTUAL_INPUT_VALIDATED_NORMALIZED_AGGREGATE" if view else "OFF_NORMALIZED_100G_FIELDS_G_OR_KCAL_NOT_RAW_INPUT_UNIT")
    if serving and (serving[0] == "ml") == (source_basis == "PER_100ML"):
        amount = serving[1]
        result["options"] = [{"unitType": "SERVING", "quantity": 1,
            "gramWeight": amount if serving[0] == "g" else None, "mlVolume": amount if serving[0] == "ml" else None,
            "labels": {"EN": f"Serving ({measure_contract.number_label(amount)} {serving[0]})", "TR": f"Porsiyon ({measure_contract.number_label(amount)} {serving[0]})"},
            "defaultOption": False, "qualityStatus": "NEEDS_REVIEW", "userVisible": False}]
        if not measure_contract.valid_labels(result['options'][0]['labels']):
            result['options'] = []
            return hold('COMPLETE_BOUNDED_EN_TR_LABELS_REQUIRED_NO_TRUNCATION')
    result["decision"] = "SOURCE_PORTION_READY_FOR_REVIEW" if row["nutrition_reference_unit"] == source_basis else "SOURCE_BASIS_CORRECTION_REQUIRES_FULL_BEFORE_IMAGE"
    result["requiredGates"] = ["Fresh full nutrition before-image and comparison; not just four macros",
        "R8 source/version/identity/market and idempotent application/rollback approval",
        "Apply any reference correction before making a native volume option visible"]
    if bulk:
        result["decision"] = "BULK_" + result["decision"]
        result["requiredGates"].append("Cached OFF dump remote origin/time is unverified; exact current provider/label recheck required")
    return result


def source_portion(p):
    if not isinstance(p, dict):
        return None, "SOURCE_PORTION_INVALID"
    quantity, grams = p.get("amount"), p.get("gramWeight")
    if type(p.get("id")) is not int or p['id'] <= 0 or not r5.positive(quantity) or not r5.positive(grams):
        return None, "SOURCE_PORTION_INVALID"
    if p.get('footnote') not in (None, ''):
        return None, 'SOURCE_FOOTNOTE_REQUIRES_REVIEW'
    measure = p.get('measureUnit', {})
    modifier, description = p.get('modifier'), p.get('portionDescription')
    if (not isinstance(measure, dict) or any(v is not None and not isinstance(v, str)
            for v in (measure.get('name'), modifier, description))):
        return None, 'INVALID_SOURCE_DESCRIPTOR_OR_UNIT'
    if modifier and description and modifier.strip() != description.strip():
        return None, 'CONFLICTING_SOURCE_DESCRIPTORS_REQUIRE_REVIEW'
    raw = (modifier or description or '').strip()
    unit = (measure.get('name') or '').strip().lower()
    descriptor = raw if unit in {"", "undetermined"} else unit + (" " + raw if raw else "")
    if not measure_contract.valid_labels({'EN': descriptor, 'TR': descriptor}):
        return None, 'SOURCE_DESCRIPTOR_TEXT_REQUIRES_REVIEW'
    lower = descriptor.lower()
    ml = quantity if lower in {"ml", "milliliter", "milliliters"} else None
    shape = "SERVING"
    suffix_en, suffix_tr = "Source portion", "Kaynak porsiyonu"
    if lower == "cup":
        shape, suffix_en, suffix_tr = "CUP", "US measuring cup", "ABD ölçü kabı"
    elif lower in {"tbsp", "tablespoon"}:
        shape, suffix_en, suffix_tr = "TABLESPOON", "tablespoon", "yemek kaşığı"
    elif lower in {"tsp", "teaspoon"}:
        shape, suffix_en, suffix_tr = "TEASPOON", "teaspoon", "çay kaşığı"
    elif lower == "oz":
        suffix_en, suffix_tr = "oz portion", "ons porsiyon"
    elif lower in {"ml", "milliliter", "milliliters"}:
        suffix_en, suffix_tr = "ml portion", "ml porsiyon"
    elif re.fullmatch(r"(?:extra small|extra large|small|medium|large)(?: \([^\r\n]*\))?", lower):
        size = lower.split(" (")[0]
        tr = {"extra small": "çok küçük", "small": "küçük", "medium": "orta", "large": "büyük", "extra large": "çok büyük"}[size]
        shape, suffix_en, suffix_tr = "PIECE", size + " piece", tr + " adet"
    elif lower in {"slice", "piece", "fruit", "egg", "item", "whole"}:
        shape = "SLICE" if lower == "slice" else "PIECE"
        suffix_en, suffix_tr = ("slice", "dilim") if shape == "SLICE" else ("piece", "adet")
    else:
        return None, "SOURCE_SHAPE_OR_QUALIFIER_LABEL_REVIEW"
    labels = {"EN": f"{measure_contract.number_label(quantity)} {suffix_en} ({measure_contract.number_label(grams)} g)",
              "TR": f"{measure_contract.number_label(quantity)} {suffix_tr} ({measure_contract.number_label(grams)} g)"}
    if not measure_contract.valid_labels(labels):
        return None, 'COMPLETE_BOUNDED_EN_TR_LABELS_REQUIRED_NO_TRUNCATION'
    return {"sourcePortionId": p["id"], "sourcePortionFingerprint": r5.fingerprint(p), "sourceDescriptor": descriptor,
            "unitType": shape, "quantity": quantity, "gramWeight": grams, "mlVolume": ml,
            "labels": labels,
            "defaultOption": False, "qualityStatus": "NEEDS_REVIEW", "userVisible": False}, None


def usda_portion_options(food):
    options, holds = [], []
    seen = set()
    for p in food.get("foodPortions", []):
        if p.get("id") in seen:
            raise ValueError("Duplicate USDA source portion ID")
        seen.add(p.get("id"))
        option, reason = source_portion(p)
        if option:
            options.append(option)
        else:
            holds.append({"sourcePortion": p, "reason": reason})
    # Fractions of a source-defined medium/unqualified whole piece, not guessed sizes.
    pieces = [o for o in options if o["unitType"] == "PIECE" and o["quantity"] == 1
              and (o["sourceDescriptor"].lower().startswith("medium") or o["sourceDescriptor"].lower() in {"piece", "fruit", "egg", "item", "whole"})]
    if len(pieces) == 1:
        parent = pieces[0]
        for fraction, en, tr in ((0.5, "Half", "Yarım"), (0.25, "Quarter", "Çeyrek")):
            options.append({**parent, "quantity": fraction, "gramWeight": parent["gramWeight"] * fraction,
                "mlVolume": parent["mlVolume"] * fraction if parent["mlVolume"] is not None else None,
                "derivation": {"method": "EXACT_SOURCE_PIECE_FRACTION", "fraction": fraction, "parentPortionId": parent["sourcePortionId"]},
                "labels": {"EN": f"{en} piece ({measure_contract.number_label(parent['gramWeight'] * fraction)} g)",
                           "TR": f"{tr} adet ({measure_contract.number_label(parent['gramWeight'] * fraction)} g)"}})
    return options, holds


def usda_decision(row, food, r4_keys):
    result = {"id": row["id"], "sourceKey": row["source_key"], "beforeFingerprint": r5.fingerprint(row),
              "currentName": row.get("display_name") or row.get("name"), "currentReferenceUnit": row.get("nutrition_reference_unit"),
              "currentServingBeforeImageMeasured": "options" in row and "option_localizations" in row,
              "sourceFingerprint": r5.fingerprint(food), "applyAllowed": False, "nutritionWriteAllowed": False,
              "sourceDescription": food.get("description"), "options": [], "sourcePortionHolds": []}
    if row.get("data_source") != "USDA_FOODDATA" or r5.closure.review.fdc_id(row.get("source_key")) != str(food.get("fdcId")):
        return {**result, "decision": "SOURCE_IDENTITY_REQUIRED"}
    result["sourceCore"] = usda_core(food)
    result["coreDifferences"] = core_differences(row, result["sourceCore"])
    result["sourceEnergyEntries"] = [{"nutrientId": n["nutrient"]["id"], "amount": n.get("amount"), "unit": n["nutrient"].get("unitName")}
        for n in food.get("foodNutrients", []) if n.get("nutrient", {}).get("id") in {1008, 2047, 2048}]
    if not core_matches(row, result["sourceCore"]):
        return {**result, "decision": "CURRENT_CORE_DIFFERS_FROM_SOURCE"}
    if row["nutrition_reference_unit"] != "PER_100G" or food.get("dataType") not in {"Foundation", "SR Legacy"}:
        return {**result, "decision": "SOURCE_TYPE_OR_REFERENCE_REVIEW"}
    result["options"], result["sourcePortionHolds"] = usda_portion_options(food)
    keys = [(o["unitType"], o["quantity"], o["gramWeight"], o["mlVolume"]) for o in result["options"]]
    if len(keys) != len(set(keys)):
        return {**result, "decision": "EQUIVALENT_SOURCE_OPTIONS_REQUIRE_DEDUP_REVIEW"}
    result["decision"] = "SOURCE_PORTIONS_READY_FOR_REVIEW" if result["options"] else "SOURCE_PORTION_LABEL_OR_WEIGHT_REQUIRED"
    if len(result["options"]) > 20:
        result["decision"] = "OPTION_LIMIT_REVIEW_NO_SILENT_TRUNCATION"
    result["requiredGates"] = ["R8 fresh options/localizations/defaults/identity before-image and approval",
                              "Edible part and raw/cooked source profile must remain unchanged"]
    if row["source_key"] in r4_keys:
        result["requiredGates"].append("Combine exact R3/R4 preparation and naming correction")
    if row["catalog_type"] == "BRANDED_PRODUCT":
        result["requiredGates"].append("Combine R3 generic classification correction; do not apply as barcode product")
    return result


def load_bulk(folder):
    summary_path, product_path = folder / "summary.json", folder / "products.jsonl"
    summary = json.loads(summary_path.read_text(encoding="utf-8"))
    if (summary.get("ruleset") != "R5_OFF_FULL_BODY_BULK_V1" or summary.get("fullScanComplete") is not True
            or summary.get("origin") != "LOCAL_CACHED_OFF_DUMP" or summary.get("sourceFreshnessApproved") is not False
            or summary.get("productsSha256") != r5.closure.sha(product_path)):
        raise ValueError("Complete pinned bulk extraction proof required")
    rows, seen = [], set()
    with product_path.open(encoding="utf-8") as stream:
        for line in stream:
            body = json.loads(line)
            code = str(body["barcode"])
            fingerprint = r5.fingerprint(body["product"])
            if (code in seen or fingerprint != summary["matchedFingerprints"].get(code)
                    or fingerprint != body.get("sourceProductFingerprint") or body.get("status") != "BULK_EXTRACTED"):
                raise ValueError("Bulk body membership/hash mismatch")
            seen.add(code)
            rows.append(body)
    if len(seen) != summary["matchedProducts"] or set(summary["matchedFingerprints"]) != seen:
        raise ValueError("Bulk extraction coverage mismatch")
    return rows, {"summarySha256": r5.closure.sha(summary_path), "productsSha256": r5.closure.sha(product_path)}


def run(args):
    source_rows, _, foods = r5.closure.verify_inputs(args.source_catalog, args.source_proof, args.usda_evidence)
    proof = json.loads(args.proof.read_text(encoding="utf-8"))
    if (proof.get("scope") != "r5-servings" or proof.get("origin") != "AWS_READ_ONLY_SNAPSHOT"
            or proof.get("exitCode") != 0 or proof.get("transactionReadOnly") is not True
            or proof.get("sha256") != r5.closure.sha(args.catalog)):
        raise ValueError("R5 snapshot proof mismatch")
    r4 = json.loads(args.r4_manifest.read_text(encoding="utf-8"))
    expected_hashes = {"catalog": r5.closure.sha(args.source_catalog), "proof": r5.closure.sha(args.source_proof),
                       "evidence": r5.closure.sha(args.usda_evidence)}
    if any(r4.get("inputHashes", {}).get(key) != value for key, value in expected_hashes.items()):
        raise ValueError("R4 naming manifest ancestry mismatch")
    if any(d.get("sourceFingerprint") != r5.fingerprint(foods.get(r5.closure.review.fdc_id(d["sourceKey"])))
           for d in r4["decisions"]):
        raise ValueError("R4 source fingerprint mismatch")
    r4_keys = {d["sourceKey"] for d in r4["decisions"]}
    as_of = datetime.now(timezone.utc)
    source_by_code = defaultdict(list)
    bulk_hashes = {}
    for folder in args.off_bulk:
        rows, hashes = load_bulk(folder)
        for body in rows:
            source_by_code[str(body["barcode"])].append((folder / "products.jsonl", {**body, "verifiedBulkFile": True}))
        bulk_hashes[str(folder)] = hashes
    for folder in args.off_evidence:
        for path in sorted(folder.glob("*.json")):
            body = json.loads(path.read_text(encoding="utf-8-sig"))
            if body.get("product") and body.get("barcode"):
                source_by_code[str(body["barcode"])].append((path, body))
    args.output.mkdir(parents=True, exist_ok=True)
    off_counts, usda_counts, rows_seen, ids = Counter(), Counter(), 0, set()
    totals, origin_counts, queue_coverage = Counter(), Counter(), defaultdict(Counter)
    with (args.output / "source-decisions.jsonl.partial").open("w", encoding="utf-8") as out, args.catalog.open(encoding="utf-8-sig", newline="") as stream:
        for raw in csv.DictReader(stream):
            row = r5.decode_row(raw)
            if row["id"] in ids:
                raise ValueError("Duplicate snapshot ID")
            ids.add(row["id"])
            rows_seen += 1
            source_id = r5.closure.review.fdc_id(row["source_key"])
            decision = None
            if row["data_source"] == "USDA_FOODDATA" and source_id in foods:
                decision = usda_decision(row, foods[source_id], r4_keys)
                usda_counts[decision["decision"]] += 1
                totals["usdaOptionsPrepared"] += len(decision["options"])
                totals["usdaUnsupportedSourceMeasures"] += len(decision["sourcePortionHolds"])
            elif row["data_source"] == "OPEN_FOOD_FACTS":
                queue = r5.triage(row)["queue"]
                queue_coverage[queue]["rows"] += 1
                code = row.get("normalized_barcode") or row.get("barcode")
                evidence = source_by_code.get(code, [])
                if not evidence:
                    queue_coverage[queue]["noSourceBody"] += 1
                    continue
                # Do not combine metadata/values from distinct source bodies.
                path, body = max(evidence, key=lambda x: (x[1].get("status") == "FETCHED", x[1].get("checkedAt", "")))
                decision = off_decision(row, body, as_of)
                decision["evidenceFile"] = str(path)
                decision["evidenceSha256"] = bulk_hashes[str(path.parent)]["productsSha256"] if body.get("verifiedBulkFile") else r5.closure.sha(path)
                off_counts[decision["decision"]] += 1
                origin_counts[decision["evidenceOrigin"]] += 1
                queue_coverage[queue]["sourceDecisions"] += 1
                totals["offOptionsPrepared"] += len(decision["options"])
                totals["offBulkOptionsPrepared" if body.get("verifiedBulkFile") else "offReplyOptionsPrepared"] += len(decision["options"])
            if decision:
                out.write(json.dumps(decision, ensure_ascii=False, allow_nan=False) + "\n")
        # Healthy/previously served products excluded from the selected R5
        # queue still deserve source proposals. Their option before-image is
        # explicitly unmeasured; never pretend the older R3 row contains it.
        for row in source_rows:
            source_id = r5.closure.review.fdc_id(row.get("source_key"))
            if str(row["id"]) in ids or row.get("data_source") != "USDA_FOODDATA" or source_id not in foods:
                continue
            decision = usda_decision(row, foods[source_id], r4_keys)
            decision["beforeSnapshot"] = "R3_SOURCE_CATALOG_NOT_R5_OPTION_SNAPSHOT"
            decision["currentServingBeforeImageMeasured"] = False
            decision.setdefault("requiredGates", []).append("Fresh full options/localizations/basis before-image required; R3 omitted these fields")
            usda_counts[decision["decision"]] += 1
            totals["usdaProductsOutsideSelectedQueue"] += 1
            totals["usdaOptionsPrepared"] += len(decision["options"])
            totals["usdaUnsupportedSourceMeasures"] += len(decision["sourcePortionHolds"])
            out.write(json.dumps(decision, ensure_ascii=False, allow_nan=False) + "\n")
    if rows_seen != proof["rows"]:
        raise ValueError("Snapshot coverage mismatch")
    partial = args.output / "source-decisions.jsonl.partial"
    final = args.output / "source-decisions.jsonl"
    partial.replace(final)
    table_partial = args.output / "review-actions.csv.partial"
    fields = ["id", "currentName", "sourceKey", "decision", "currentReferenceUnit", "sourceReferenceUnit",
              "evidenceOrigin", "sourceFreshnessApproved", "currentServingBeforeImageMeasured", "optionCount",
              "beforeFingerprint", "sourceFingerprint", "applyAllowed"]
    with table_partial.open("w", encoding="utf-8-sig", newline="") as table, final.open(encoding="utf-8") as stream:
        writer = csv.DictWriter(table, fieldnames=fields)
        writer.writeheader()
        for line in stream:
            record = json.loads(line)
            writer.writerow({**{name: record.get(name, "") for name in fields}, "optionCount": len(record["options"])})
    table_final = table_partial.replace(args.output / "review-actions.csv")
    summary = {"ruleset": "R5_SOURCE_OPTIONS_V2", "snapshotTime": proof["time"], "analyzedAt": as_of.isoformat(),
        "snapshotRowsChecked": rows_seen, "offDecisions": dict(off_counts), "usdaDecisions": dict(usda_counts), **totals,
        "sourceCatalogRowsOutsideR5Snapshot": sum(str(r["id"]) not in ids for r in source_rows),
        "databaseWrites": 0, "applyAllowed": False,
        "offEvidenceOrigins": dict(origin_counts), "offQueueCoverage": {key: dict(value) for key, value in queue_coverage.items()},
        "bulkHashes": bulk_hashes,
        "inputHashes": {name: r5.closure.sha(getattr(args, name)) for name in ("catalog", "proof", "source_catalog", "source_proof", "usda_evidence", "r4_manifest")},
        "producerSha256": r5.closure.sha(Path(__file__)), "decisionsSha256": r5.closure.sha(final), "reviewTableSha256": r5.closure.sha(table_final)}
    (args.output / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    for name in ("catalog", "proof", "source-catalog", "source-proof", "usda-evidence", "r4-manifest", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--off-evidence", action="append", type=Path, default=[])
    parser.add_argument("--off-bulk", action="append", type=Path, default=[])
    run(parser.parse_args())

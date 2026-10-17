"""Exact five-record, source-backed metadata dry run. No DB writer or live SQL."""
import argparse
import copy
import csv
import hashlib
import importlib.util
import json
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCOPE = {"181013": "171466", "181138": "170380", "181140": "169969", "181155": "168510"}
OFF_ID, OFF_CODE = "49340", "8696368000237"


def module(name, file):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(file))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


gate = module("r7_nutrient_gate", "close-r5-release-review.py")
typed = module("r7_typed_source", "review-r5-common-readiness.py")
fetch = module("r7_off_fetch", "fetch-r5-off-evidence.py")
names = module("r7_name_helpers", "prepare-r6-held-names.py")


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def fingerprint(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def fresh(stamp, now):
    value = datetime.fromisoformat(stamp.replace("Z", "+00:00"))
    if value.tzinfo is None or not 0 <= (now - value).total_seconds() <= 86400:
        raise ValueError("Fresh timezone-aware source/snapshot receipt required")


def registry():
    entries = []
    for file in ("source-name-decisions-r4-v1.json", "source-name-decisions-r7-v1.json"):
        entries.extend(json.loads((ROOT / "src/main/resources/catalog" / file).read_text(encoding="utf-8"))["entries"])
    return {r["sourceKey"]: r for r in entries}


def unbalanced(value):
    depth = 0
    for character in value:
        if character == "(": depth += 1
        elif character == ")":
            depth -= 1
            if depth < 0: return True
    return depth != 0


def review_record(row, body, decisions):
    identifier, food = row["id"], row["food"]
    if (identifier not in {*SCOPE, OFF_ID} or str(food["id"]) != identifier
            or food.get("is_custom") or food.get("publication_status") != "PUBLISHED"):
        raise ValueError("Exact published non-custom cohort required")
    child = row["name_review"]
    for key in ("aliases", "localizations", "options", "option_localizations"):
        if key not in child or len({r["id"] for r in child[key]}) != len(child[key]):
            raise ValueError("Full unambiguous child before-images required")
    if any(field not in food for field in gate.FIELDS):
        raise ValueError("All 21 current numeric fields must be captured")
    if "assignments" not in row:
        raise ValueError("Full category assignment before-image required")
    changes, holds = {}, []
    if identifier in SCOPE:
        key = "USDA_FOODDATA:fdc:" + SCOPE[identifier]
        decision = decisions[key]
        if (food.get("source_key") != key or food.get("data_source") != "USDA_FOODDATA"
                or str(body.get("fdcId")) != SCOPE[identifier] or body.get("dataType") != "SR Legacy"
                or body.get("description") != decision["sourceDescription"]
                or any(food.get(k) for k in ("barcode", "normalized_barcode", "brand", "brand_id", "canonical_brand_id"))
                or body.get("gtinUpc") or body.get("brandOwner")
                or food.get("source_data_type") not in (None, "SR Legacy")
                or food.get("catalog_type") not in ("BRANDED_PRODUCT", "GENERIC_INGREDIENT")
                or food.get("preparation_state") != decision["targetState"]
                or food.get("nutrition_reference_unit") != "PER_100G"):
            raise ValueError("USDA subtype/description/GTIN/brand/preparation identity conflict")
        if any(food.get(k) is not None for k in ("food_concept_key", "composition_variant_key", "canonical_food_key")):
            raise ValueError("Previously assigned identity requires a separate rebase")
        en, tr = decision["displayNameEn"], decision["displayNameTr"]
        changes = {"catalog_type": "GENERIC_INGREDIENT", "source_data_type": "SR Legacy",
                   "name": en, "display_name": en, "short_display_name": en}
        comparison = gate.comparison(food, gate.all_usda_fields(body), "USDA_FOODDATA")
        comparison["vitamin_a"] = typed.vitamin_a(food, body)
        concept = "chicken_light_meat" if identifier == "181013" else "broccoli"
        expected_category = {"id": 5, "description": "Poultry Products"} if identifier == "181013" else {
            "id": 11, "description": "Vegetables and Vegetable Products"}
        if any(body.get("foodCategory", {}).get(k) != v for k, v in expected_category.items()):
            raise ValueError("USDA source category changed")
        category = {"targetSlug": "poultry" if identifier == "181013" else "vegetables",
                    "sourceCategory": expected_category, "applyAllowed": False}
        identity = {"food_concept_key": concept, "composition_variant_key": "fdc_" + SCOPE[identifier],
                    "canonical_food_key": "C2:GENERIC_INGREDIENT:" + food["preparation_state"] + ":" + concept + ":fdc_" + SCOPE[identifier]}
        holds.append("CANONICAL_IDENTITY_NAMESPACE_AND_FK_REBASE_REQUIRED")
    else:
        if (food.get("source_key") != "barcode:" + OFF_CODE or food.get("barcode") != OFF_CODE
                or food.get("normalized_barcode") != OFF_CODE or food.get("catalog_type") != "BRANDED_PRODUCT"
                or food.get("brand") != "Activia" or food.get("market_region") != "TR"
                or body.get("code") != OFF_CODE or body.get("brands") != "Activia"
                or body.get("product_name_en") != "Probiotic Shot, Banana and Strawberry"
                or body.get("product_name_tr") != "Activiashot Shot Çilek & Muz Probiyotik İçecek 80 ml"
                or body.get("nutrition_data_per") != "100g" or food.get("nutrition_reference_unit") != "PER_100G"):
            raise ValueError("Exact OFF GTIN/brand/localized source identity required")
        en, tr = "Activia Probiotic Shot (Banana and Strawberry)", "Activia Shot (Çilek ve Muz)"
        comparison = gate.comparison(food, gate.all_off_fields(body), "OPEN_FOOD_FACTS")
        identity = None
        if "en:beverages" not in body.get("categories_tags", []):
            raise ValueError("Reviewed OFF broad beverage category missing")
        category = {"targetSlug": "beverages", "sourceTags": body["categories_tags"],
                    "reason": "Broad source-backed beverage root, not an inferred dairy/yogurt subtype", "applyAllowed": False}
        holds.append("R5_VOLUME_PACK_MASS_BASIS_NO_INVENTED_ML")
    if not row["assignments"]:
        holds.append("ACTIVE_TAXONOMY_PRIMARY_CATEGORY_REBASE_REQUIRED")
    else:
        category["preserveExistingAssignments"] = True
    for field, value in comparison.items():
        if value["state"] in {"DIFFERS", "INVALID_CURRENT", "UNMEASURED_CURRENT_FIELD", "SOURCE_VALUE_REQUIRED"}:
            holds.append("R5_NUMERIC_REVIEW_" + field.upper())
        if field == "vitamin_a" and value.get("legacyUnitAttested") is False:
            holds.append("R5_VITAMIN_A_LEGACY_UNIT_NOT_ATTESTED")
    actions = []
    for language, label in (("EN", en), ("TR", tr)):
        active = [l for l in child["localizations"] if l["active"] and l["language"] == language]
        if len(active) > 1 or not active and any(l["language"] == language for l in child["localizations"]):
            raise ValueError("Ambiguous/inactive localization needs explicit admin review")
        if active:
            delta = {k: label for k in ("display_name", "short_display_name") if active[0][k] != label}
            if delta: actions.append({"action": "UPDATE_EXACT_ACTIVE_LOCALIZATION", "before": active[0], "changes": delta})
        else:
            actions.append({"action": "ADD_MISSING_LOCALIZATION", "language": language,
                            "display_name": label, "short_display_name": label})
    aliases = []
    for alias in child["aliases"]:
        if alias["food_item_id"] != int(identifier): raise ValueError("Alias owner drift")
        if alias["active"] and (identifier == "181013" and names.unsafe_alias("REMOVE_BREAST", alias["alias"])
                                or identifier in {"181138", "181140"} and unbalanced(alias["alias"])):
            aliases.append({"action": "DEACTIVATE_EXACT_MISLEADING_ALIAS", "before": alias,
                            "reason": "Source does not prove breast-only identity" if identifier == "181013"
                            else "Unbalanced fragment of a source-qualified display name"})
    return {"id": identifier, "sourceKey": food["source_key"], "sourceDescription": body.get("description") or body.get("product_name_en"),
            "sourceBodySha256": fingerprint(body), "expectedBefore": copy.deepcopy(row), "beforeSha256": fingerprint(row),
            "foodChanges": {k: v for k, v in changes.items() if food.get(k) != v}, "localizationActions": actions,
            "aliasActions": aliases, "identityProposal": identity, "categoryProposal": category, "numericComparison": comparison,
            "reviewHolds": sorted(set(holds)), "metadataReviewComplete": True, "liveApplyAllowed": False,
            "nutritionWriteAllowed": False, "mlConversionAllowed": False}


def run(args):
    now = datetime.now(timezone.utc)
    with args.catalog.open(encoding="utf-8-sig", newline="") as handle:
        rows = [{"id": r["id"], **{k: json.loads(v) for k, v in r.items() if k != "id"}} for r in csv.DictReader(handle)]
    proof = json.loads(args.proof.read_text(encoding="utf-8"))
    if (proof.get("origin") != "AWS_READ_ONLY_SNAPSHOT" or proof.get("transactionReadOnly") is not True
            or proof.get("exitCode") != 0 or proof.get("rows") != 5 or proof.get("schemaVersion") != "307"
            or proof.get("sha256") != sha(args.catalog) or len(rows) != 5 or {r["id"] for r in rows} != {*SCOPE, OFF_ID}):
        raise ValueError("Intact exact-five current schema307 read-only snapshot required")
    fresh(proof["time"], now)
    evidence = json.loads(args.usda.read_text(encoding="utf-8"))
    if (evidence.get("version") != "R3_EXACT_FDC_EVIDENCE_V1"
            or evidence.get("source") != "https://api.nal.usda.gov/fdc/v1/foods" or evidence.get("matchedCount") != 4
            or len(evidence.get("requestedIds", [])) != 4 or set(evidence["requestedIds"]) != set(SCOPE.values())):
        raise ValueError("Official four-profile full USDA receipt required")
    fresh(evidence["fetchedAt"], now)
    bodies = []
    for entry in evidence["batchFiles"]:
        if Path(entry["file"]).name != entry["file"]: raise ValueError("Invalid source filename")
        path = args.usda.parent / entry["file"]
        if sha(path) != entry["sha256"]: raise ValueError("USDA batch changed")
        fresh(entry["fetchedAt"], now)
        bodies.extend(json.loads(path.read_text(encoding="utf-8")))
    if fingerprint(bodies) != fingerprint(evidence["foods"]) or len(bodies) != 4 or {str(f["fdcId"]) for f in bodies} != set(SCOPE.values()):
        raise ValueError("Exact USDA coverage/body hash conflict")
    receipt = json.loads(args.off.read_text(encoding="utf-8"))
    if not fetch.cached_valid(receipt, OFF_CODE, now): raise ValueError("Exact official OFF receipt required")
    fresh(receipt["checkedAt"], now)
    source = {str(f["fdcId"]): f for f in bodies}
    decisions = registry()
    result = [review_record(row, source[SCOPE[row["id"]]] if row["id"] in SCOPE else receipt["product"], decisions) for row in rows]
    counts = Counter(v["state"] for r in result for v in r["numericComparison"].values())
    plan = {"version": "R7_SOURCE_PRESENTATION_REVIEW_V1", "status": "SOURCE_METADATA_REVIEW_COMPLETE_NOT_LIVE_APPROVED",
            "createdAt": now.isoformat(), "snapshotTime": proof["time"], "rows": result,
            "inputHashes": {str(p): sha(p) for p in (args.catalog, args.proof, args.usda, args.off, Path(__file__),
                ROOT / "src/main/resources/catalog/source-name-decisions-r4-v1.json",
                ROOT / "src/main/resources/catalog/source-name-decisions-r7-v1.json")},
            "liveApplyAllowed": False, "databaseWrites": 0, "nutritionWrites": 0, "mlConversions": 0,
            "numericFieldsCompared": 105, "numericDispositions": dict(counts), "physicalMerges": 0,
            "deploymentGate": "Product-only deploy, fresh encrypted backup, full FK/key namespace rebase, guarded PG apply/undo rehearsal, explicit preservation of unresolved R5 values, then EN/TR API acceptance",
            "summary": {"products": 5, "genericReclassifications": 4,
                        "parentFieldProposals": sum(len(r["foodChanges"]) for r in result),
                        "localizedNameActions": sum(len(r["localizationActions"]) for r in result),
                        "aliasesToRetire": sum(len(r["aliasActions"]) for r in result),
                        "categoryReviewProposals": len(result),
                        "servingOptionsRetained": sum(len(r["expectedBefore"]["name_review"]["options"]) for r in result)}}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    if args.output.exists(): raise ValueError("Use a new plan path; preserve prior evidence")
    args.output.write_text(json.dumps(plan, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": plan["status"], **plan["summary"], "numericDispositions": dict(counts), "databaseWrites": 0}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    for name in ("catalog", "proof", "usda", "off", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    run(parser.parse_args())

"""Fresh exact-source review of the held R6 cohort; never emits apply SQL."""
import argparse
import csv
import importlib.util
import json
import re
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path


def load(name, file):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(file))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


everyday = load("held_everyday", "review-r6-everyday-categories.py")
resolution = load("held_resolution", "resolve-r6-everyday-review.py")
gate = load("held_gate", "close-r5-release-review.py")
fdc = load("held_fdc", "fetch-r3-usda-evidence.py")
csv.field_size_limit(16 * 1024 * 1024)


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def held_rows(path):
    body = read_json(path)
    rows = body["rows"]
    if (body.get("status") != "HISTORICAL_REVIEW_PREPARATION_ONLY" or len(rows) != 118
            or len({str(r["id"]) for r in rows}) != 118 or any(r.get("applyAllowed") is not False for r in rows)):
        raise ValueError("Exact 118 review-only held identities required")
    for row in rows:
        if not re.fullmatch(r"USDA_FOODDATA:fdc:[1-9][0-9]{0,17}", row["sourceKey"]):
            raise ValueError("Unsupported held source identity")
    return rows


def prepare(args):
    rows = held_rows(args.held)
    args.output.mkdir(parents=True, exist_ok=False)
    (args.output / "selected-ids.jsonl").write_text("".join(json.dumps({"id": r["id"]}) + "\n" for r in rows), encoding="utf-8")
    with (args.output / "source-queue.csv").open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=["id", "source_key", "data_source"])
        writer.writeheader()
        writer.writerows({"id": r["id"], "source_key": r["sourceKey"], "data_source": "USDA_FOODDATA"} for r in rows)
    (args.output / "selection-proof.json").write_text(json.dumps({"heldSha256": everyday.sha(args.held),
        "queueSha256": everyday.sha(args.output / "source-queue.csv"), "rows": 118,
        "liveApplyAllowed": False, "origin": "HISTORICAL_IDENTITIES_ONLY_NOT_CURRENT_DB"}, indent=2), encoding="utf-8")
    print("Prepared exact 118-product read/source selection; no approval or writes")


def snapshot(folder, scope, now, decisions=None):
    path, proof_path = folder / "catalog.csv", folder / "snapshot-proof.json"
    proof = read_json(proof_path)
    with path.open(encoding="utf-8-sig", newline="") as stream:
        rows = list(csv.DictReader(stream))
    if (proof.get("origin") != "AWS_READ_ONLY_SNAPSHOT" or proof.get("scope") != scope
            or proof.get("transactionReadOnly") is not True or proof.get("exitCode") != 0
            or proof.get("sha256") != everyday.sha(path) or proof.get("rows") != len(rows)
            or not everyday.fresh(proof["time"], now) or len({r["id"] for r in rows}) != len(rows)):
        raise ValueError("Fresh intact read-only scope-bound snapshot required")
    if scope == "r5-release":
        collector = load("held_collector", "collect-r3-source-snapshot.py")
        expected_sql = collector.release_sql(decisions)
        if proof.get("decisionsSha256") != everyday.sha(decisions):
            raise ValueError("Selected IDs changed")
    else:
        expected_sql = (Path(__file__).with_name("sql") / "export-r6-category-release-review.sql").read_text(encoding="utf-8")
    import hashlib
    if proof.get("sqlSha256") != hashlib.sha256(expected_sql.encode()).hexdigest():
        raise ValueError("Snapshot query changed")
    parsed = {r["id"]: {k: (None if k == "name_review" and v == "" else json.loads(v)) if k != "id" else v
                        for k, v in r.items()} for r in rows}
    return parsed, proof


def source_evidence(folder, queue, now):
    evidence = read_json(folder / "evidence.json")
    with queue.open(encoding="utf-8-sig", newline="") as stream:
        ids = sorted({r["source_key"].split(":")[-1] for r in csv.DictReader(stream)}, key=int)
    if (evidence.get("version") != "R3_EXACT_FDC_EVIDENCE_V1"
            or evidence.get("source") != "https://api.nal.usda.gov/fdc/v1/foods"
            or evidence.get("catalogSha256") != everyday.sha(queue)
            or evidence.get("requestedIds") != ids or evidence.get("matchedCount") != len(ids)
            or not everyday.fresh(evidence["fetchedAt"], now)):
        raise ValueError("Fresh exact FDC source membership/checksum required")
    # New fetch directories only: resuming older batches is not a freshness proof.
    bodies, files = [], set()
    for batch in evidence["batchFiles"]:
        file = batch["file"]
        if not re.fullmatch(r"batch-[0-9]{3}\.json", file) or file in files:
            raise ValueError("Invalid/duplicate source batch")
        files.add(file)
        path = folder / file
        if everyday.sha(path) != batch["sha256"]:
            raise ValueError("Source batch changed")
        if "fetchedAt" in batch:
            receipt = fdc.cached_batch_receipt(path, batch["ids"], now)
            if receipt["fetchedAt"] != batch["fetchedAt"]:
                raise ValueError("Source batch fetch-time receipt changed")
        bodies.extend(fdc.validate_batch(batch["ids"], read_json(path)))
    fdc.validate_batch(ids, bodies)
    if everyday.fingerprint(sorted(bodies, key=lambda f: int(f["fdcId"]))) != everyday.fingerprint(sorted(evidence["foods"], key=lambda f: int(f["fdcId"]))):
        raise ValueError("Source body/index changed")
    return {str(f["fdcId"]): f for f in bodies}, evidence


def reviewed_name_identity(food, body, registry):
    if (registry.get("version") != "R6_HELD_NAME_DECISIONS_V1" or registry.get("liveApplyAllowed") is not False
            or len(registry["entries"]) != 35 or len({r["sourceKey"] for r in registry["entries"]}) != 35
            or len({r["id"] for r in registry["entries"]}) != 35):
        raise ValueError("Exact review-only naming registry required")
    entry = next((r for r in registry["entries"] if r["sourceKey"] == food.get("source_key")), None)
    if not entry:
        return None
    if (entry["id"] != str(food["id"]) or entry["sourceKey"] != "USDA_FOODDATA:fdc:" + str(body["fdcId"])
            or entry["sourceDescription"] != body["description"] or body.get("dataType") not in {"SR Legacy", "Foundation"}):
        return False
    if entry["decision"] == "SOURCE_BRAND_SCOPE_HOLD":
        return False
    if entry["decision"] not in {"EQUIVALENT_SHORT_NAME", "IDENTITY_CORRECTION", "QUALIFIER_RESTORATION"}:
        raise ValueError("Unknown reviewed name disposition")
    if entry["decision"] == "IDENTITY_CORRECTION" and food.get("canonical_food_key") not in entry.get("acceptedCanonicalFoodKeys", []):
        return False
    expected = entry["currentName"] if entry["decision"] == "EQUIVALENT_SHORT_NAME" else entry["en"]
    return food.get("name") == expected


def assess(held, current, category, body, registry, categories, additional=None, reviewed_names=None):
    food, reasons = current["food"], []
    if (str(food["id"]) != held["id"] or food.get("source_key") != held["sourceKey"]
            or food.get("data_source") != "USDA_FOODDATA" or food.get("is_custom")
            or food.get("publication_status") != "PUBLISHED" or food.get("catalog_type") != "GENERIC_INGREDIENT"):
        raise ValueError("Current held identity/publication/classification drift")
    if category["food"]["source_key"] != held["sourceKey"]:
        raise ValueError("Category snapshot identity differs")
    volatile = {"usage_count", "search_selection_count", "last_searched_at", "last_accessed_at"}
    if {k: v for k, v in food.items() if k not in volatile} != {k: v for k, v in category["food"].items() if k not in volatile}:
        reasons.append("CROSS_SNAPSHOT_CURRENT_PROFILE_CHANGED")
    if category["assignments"]:
        reasons.append("EXISTING_CATEGORY_ASSIGNMENT_REVIEW")
    if "USDA_FOODDATA:fdc:" + str(body["fdcId"]) != held["sourceKey"]:
        raise ValueError("Source identity differs")
    target, category_proof = everyday.usda_target(body), "OFFICIAL_FDC_FOOD_CATEGORY"
    nominated_target = held["targetSlug"]
    if additional:
        if (additional.get("applyAllowed") is not False
                or additional.get("version") != "R6_HELD_EXACT_CATEGORY_NOMINATIONS_V1"):
            raise ValueError("Review-only category nominations required")
        proposals = additional["entries"]
        if len({p["sourceKey"] for p in proposals}) != len(proposals):
            raise ValueError("Duplicate category nomination")
        proposal = next((p for p in proposals if p["sourceKey"] == held["sourceKey"]), None)
        if proposal:
            if (proposal["id"] == held["id"] and proposal["description"] == body["description"]
                    and proposal["foodCategoryCode"] == body.get("foodCategory", {}).get("code")):
                target = nominated_target = proposal["target"]
                category_proof = "NEW_EXACT_SOURCE_CATEGORY_NOMINATION_NOT_APPROVED"
            else:
                reasons.append("NEW_CATEGORY_NOMINATION_SOURCE_CHANGED")
    exact_rule = resolution.rules_index(registry).get(held["sourceKey"])
    if exact_rule:
        expected = {"USDA_FOODDATA:fdc:172233": ("0200", "Dill weed, fresh"),
                    "USDA_FOODDATA:fdc:169640": ("1900", "Honey")}.get(held["sourceKey"])
        if expected and (body.get("foodCategory", {}).get("code"), body["description"]) == expected:
            target, category_proof = exact_rule["target"], "EXACT_REVIEWED_CATEGORY_RULE_WITH_CURRENT_FDC"
        else:
            reasons.append("EXACT_REVIEWED_CATEGORY_SOURCE_CHANGED")
            target = None
    name_rule = next((d for d in registry["usdaNames"] if d["sourceKey"] == held["sourceKey"]), None)
    normalized = everyday.normalized
    identity = normalized(food["name"]) == normalized(body["description"])
    if name_rule and body["description"] == name_rule["sourceDescription"]:
        expected_name = name_rule["currentName"]
        if name_rule["decision"] == "NAME_CORRECTION_REQUIRED":
            expected_name = name_rule["displayNameEn"]
        identity = normalized(food["name"]) == normalized(expected_name)
        if body.get("dataType") == "Survey (FNDDS)":
            attrs = {a.get("name"): str(a.get("value")) for a in body.get("foodAttributes", [])}
            if attrs.get("WWEIA Category number") == "2502" and attrs.get("WWEIA Category description") == "Eggs and omelets":
                target, category_proof = "eggs", "EXACT_FNDDS_WWEIA_ATTRIBUTES_2502"
    if reviewed_names:
        reviewed_identity = reviewed_name_identity(food, body, reviewed_names)
        if reviewed_identity is not None:
            identity = reviewed_identity
    if not identity:
        reasons.append("CURRENT_NAME_SOURCE_IDENTITY_REVIEW")
    if target != nominated_target or not resolution.target_active(target, categories):
        reasons.append("SOURCE_TARGET_CATEGORY_REVIEW")
    states = gate.source.r5.closure.source_states(body["description"])
    state = food.get("preparation_state")
    coarse_cooked = state == "COOKED" and ("cooked" in body["description"].lower()
        or (name_rule is not None and name_rule["id"] == "181466" and identity and target == "eggs"))
    if states and state not in states and not coarse_cooked:
        reasons.append("SOURCE_PREPARATION_REVIEW")
    if food.get("nutrition_reference_unit") != "PER_100G":
        reasons.append("USDA_NATIVE_REFERENCE_REVIEW")
    nutrients = gate.comparison(food, gate.all_usda_fields(body), "USDA_FOODDATA")
    if any(v["state"] not in {"MATCH", "UNKNOWN_CURRENT_RETAINED"} for v in nutrients.values()):
        reasons.append("FULL_NUTRITION_COMPARISON_REQUIRED")
    if food.get("reviewed_by") or food.get("quality_validated_by") or food.get("verification_status") == "VERIFIED":
        reasons.append("EXISTING_ADMIN_APPROVAL_PROTECTED")
    option_checks = []
    for option in current["options"]:
        matches = []
        for portion in body.get("foodPortions", []):
            rebuilt, error = gate.source.source_portion(portion)
            if not error and (option.get("unit_type"), option.get("quantity"), option.get("gram_weight"), option.get("ml_volume")) == (rebuilt["unitType"], rebuilt["quantity"], rebuilt.get("gramWeight"), rebuilt.get("mlVolume")):
                matches.append(portion["id"])
        option_checks.append({"id": option["id"], "sourcePortionIds": matches,
                              "status": "EXACT_MEASURE_SOURCE_MATCH_NOT_APPROVAL" if matches else "CURRENT_OPTION_SOURCE_OR_ADMIN_PROOF_REQUIRED"})
    # R5 application gates cannot be cleared by a category-only comparison.
    portion_gates = [r for r in held["historicalReasons"] if r.startswith("R5_") and any(k in r for k in ("PORTION", "OPTION", "LIQUID"))]
    reasons.extend(portion_gates)
    primary_queue = ("NAME_SOURCE_IDENTITY" if "CURRENT_NAME_SOURCE_IDENTITY_REVIEW" in reasons
        else "REPORTED_NUTRIENT_UNIT_OR_VALUE" if "FULL_NUTRITION_COMPARISON_REQUIRED" in reasons
        else "PORTION_OR_LIQUID" if any("PORTION" in r or "OPTION" in r or "LIQUID" in r for r in reasons)
        else "ADMIN_OR_CATEGORY_GATE" if reasons else "EXPLICIT_CATEGORY_APPROVAL")
    return {"id": held["id"], "sourceKey": held["sourceKey"], "currentName": food["name"],
            "sourceDescription": body["description"], "targetSlug": nominated_target,
            "previousTargetSlug": held["targetSlug"], "categoryProof": category_proof,
            "categoryEvidenceCompatible": identity and target is not None and target == nominated_target and resolution.target_active(target, categories),
            "preparationComparison": "SOURCE_SPECIFIC_COMPATIBLE_WITH_COARSE_COOKED" if coarse_cooked and state not in states else "SOURCE_STATE_REVIEW" if "SOURCE_PREPARATION_REVIEW" in reasons else "COMPATIBLE_OR_SOURCE_UNSPECIFIED",
            "sourceBodySha256": everyday.fingerprint(body), "currentBeforeSha256": everyday.fingerprint(current),
            "categoryBeforeSha256": everyday.fingerprint(category), "nutrients": nutrients, "portionChecks": option_checks,
            "historicalReasons": held["historicalReasons"], "holds": sorted(set(reasons)),
            "primaryQueue": primary_queue,
            "applyAllowed": False, "nutritionWriteAllowed": False, "portionWriteAllowed": False,
            "status": "REVIEW_REQUIRED" if reasons else "CATEGORY_NOMINATION_AWAITING_EXPLICIT_APPROVAL"}


def run(args):
    now = datetime.now(timezone.utc)
    rows = held_rows(args.held)
    prepared = read_json(args.selection / "selection-proof.json")
    if prepared["heldSha256"] != everyday.sha(args.held) or prepared["queueSha256"] != everyday.sha(args.selection / "source-queue.csv"):
        raise ValueError("Prepared selection changed")
    current, current_proof = snapshot(args.current, "r5-release", now, args.selection / "selected-ids.jsonl")
    category, category_proof = snapshot(args.categories, "r6-release", now)
    if set(current) != {r["id"] for r in rows} or not set(current) <= set(category):
        raise ValueError("Exact held membership required")
    categories = {c["slug"]: c for c in category_proof["metrics"][0]["categories"]}
    foods, evidence = source_evidence(args.sources, args.selection / "source-queue.csv", now)
    registry = read_json(Path(__file__).resolve().parents[1] / "data/catalog/everyday-category-review-r6-v1.json")
    additional_path = Path(__file__).resolve().parents[1] / "data/catalog/r6-held-category-evidence-v1.json"
    additional = read_json(additional_path)
    name_registry_path = Path(__file__).resolve().parents[1] / "data/catalog/r6-held-name-decisions-v1.json"
    name_registry = read_json(name_registry_path)
    results = [assess(r, current[r["id"]], category[r["id"]], foods[r["sourceKey"].split(":")[-1]], registry, categories, additional, name_registry) for r in rows]
    args.output.mkdir(parents=True, exist_ok=False)
    path = args.output / "held-review.json"
    path.write_text(json.dumps({"rows": results, "applyAllowed": False}, ensure_ascii=False, allow_nan=False) + "\n", encoding="utf-8")
    summary = {"products": len(results), "categoryEvidenceCompatible": sum(r["categoryEvidenceCompatible"] for r in results),
               "states": dict(Counter(r["status"] for r in results)), "overlappingHolds": dict(Counter(h for r in results for h in r["holds"])),
               "exclusivePrimaryQueues": dict(Counter(r["primaryQueue"] for r in results)),
               "nutrientComparisonStates": dict(Counter(n["state"] for r in results for n in r["nutrients"].values())),
               "portionComparisonStates": dict(Counter(p["status"] for r in results for p in r["portionChecks"])),
               "preparationComparisonStates": dict(Counter(r["preparationComparison"] for r in results)),
               "knownNutrientComparisonDifferencesByField": dict(Counter(k for r in results for k, v in r["nutrients"].items() if v["state"] == "DIFFERS")),
               "newCategoryNominations": sum(r["targetSlug"] != r["previousTargetSlug"] for r in results),
               "awsMeasuredAt": current_proof["time"], "categoriesMeasuredAt": category_proof["time"], "sourceFetchedAt": evidence["fetchedAt"],
               "reviewSha256": everyday.sha(path), "additionalNominationsSha256": everyday.sha(additional_path),
               "reviewedNamesRegistrySha256": everyday.sha(name_registry_path),
               "currentSnapshotSha256": current_proof["sha256"], "categorySnapshotSha256": category_proof["sha256"],
               "sourceEvidenceSha256": everyday.sha(args.sources / "evidence.json"),
               "comparisonDifferencesAreNotProvenDataErrors": True, "sourceUnitNormalizationApprovalRequired": True,
               "databaseWrites": 0, "approvedProducts": 0}
    (args.output / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--prepare", action="store_true")
    parser.add_argument("--held", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    for name in ("selection", "current", "categories", "sources"):
        parser.add_argument("--" + name, type=Path)
    args = parser.parse_args()
    prepare(args) if args.prepare else run(args)

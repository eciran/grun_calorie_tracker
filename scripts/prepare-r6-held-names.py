"""Exact 35-product name/alias preparation. No DB writer or live apply command."""
import argparse
import csv
import hashlib
import json
import re
import unicodedata
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

from importlib.util import module_from_spec, spec_from_file_location


def module(name, file):
    spec = spec_from_file_location(name, Path(__file__).with_name(file))
    result = module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


held = module("name_held", "review-r6-held-categories.py")
aliases = module("name_alias", "prepare-r6-category-rehearsal.py")
collector = module("name_collector", "collect-r3-source-snapshot.py")
ROOT = Path(__file__).resolve().parents[1]
REGISTRY = ROOT / "data/catalog/r6-held-name-decisions-v1.json"
DECISIONS = {"EQUIVALENT_SHORT_NAME", "QUALIFIER_RESTORATION", "IDENTITY_CORRECTION", "SOURCE_BRAND_SCOPE_HOLD"}
POLICIES = {None, "REMOVE_OIL_ONLY", "REMOVE_BREAST", "REMOVE_AYRAN", "REMOVE_PLAIN_RICE", "REMOVE_WHITE_TUNA"}
csv.field_size_limit(16 * 1024 * 1024)


def decisions(review_path, registry_path):
    summary = held.read_json(review_path.parent / "summary.json")
    if summary["reviewSha256"] != held.everyday.sha(review_path):
        raise ValueError("Held source review changed")
    selected = {r["id"]: r for r in held.read_json(review_path)["rows"] if r["primaryQueue"] == "NAME_SOURCE_IDENTITY"}
    registry = held.read_json(registry_path)
    entries = registry["entries"]
    if (registry.get("version") != "R6_HELD_NAME_DECISIONS_V1" or registry.get("liveApplyAllowed") is not False
            or len(entries) != 35 or len(selected) != 35 or len({r["sourceKey"] for r in entries}) != 35
            or len({r["id"] for r in entries}) != 35 or {r["id"] for r in entries} != set(selected)):
        raise ValueError("Exact 35 review-only source decisions required")
    for row in entries:
        previous = selected[row["id"]]
        if (row["decision"] not in DECISIONS or row.get("aliasPolicy") not in POLICIES or not row.get("reason")
                or any(row[k] != previous[k] for k in ("sourceKey", "currentName", "sourceDescription"))):
            raise ValueError("Source decision identity/reason changed")
        if row["decision"] == "EQUIVALENT_SHORT_NAME" and row["en"] != row["currentName"]:
            raise ValueError("Equivalence cannot silently rename the English identity")
        for lang in ("en", "tr"):
            label = row[lang]
            if not isinstance(label, str) or not label.strip() or len(label) > 255 or aliases.r6.everyday.normalized(label) == "":
                raise ValueError("Missing bounded localized name")
            if label != label.strip() or label != unicodedata.normalize("NFC", label) or re.search(r"\s{2,}", label):
                raise ValueError("Whitespace name drift")
            if any(unicodedata.category(c) in {"Cc", "Cf"} for c in label) or "<" in label or ">" in label:
                raise ValueError("Unsafe/control name content")
    return entries, selected


def prepare(args):
    entries, _ = decisions(args.review, args.registry)
    args.output.mkdir(parents=True, exist_ok=False)
    path = args.output / "selected-ids.jsonl"
    path.write_text("".join(json.dumps({"id": int(r["id"]), "requestedAliases": [
        {"language": language, "normalized_alias": aliases.normalize_alias(r[key])} for language, key in (("EN", "en"), ("TR", "tr"))]
        }, ensure_ascii=False) + "\n" for r in entries), encoding="utf-8")
    (args.output / "selection-proof.json").write_text(json.dumps({"registrySha256": held.everyday.sha(args.registry),
        "reviewSha256": held.everyday.sha(args.review), "decisionsSha256": held.everyday.sha(path),
        "liveApplyAllowed": False, "products": 35}, indent=2), encoding="utf-8")
    print("Prepared 35 exact naming identities and bounded EN/TR alias collision read")


def unsafe_alias(policy, value):
    words = set(re.findall(r"[a-z0-9]+", aliases.normalize_alias(value)))
    if policy == "REMOVE_OIL_ONLY":
        return bool(words & {"olive", "zeytinyagi", "zeytinyag", "zeytinyagli"}) and not words & {"mayonnaise", "mayo", "mayonez", "mayonezi"}
    if policy == "REMOVE_BREAST":
        return bool(words & {"breast", "breasts", "gogus", "gogsu"})
    if policy == "REMOVE_AYRAN":
        return "ayran" in words
    if policy == "REMOVE_PLAIN_RICE":
        return bool(words & {"rice", "pirinc"}) and not words & {"cream", "krema", "kremasi", "cereal", "cereals"}
    if policy == "REMOVE_WHITE_TUNA":
        return bool(words & {"white", "albacore", "beyaz"})
    return False


def already_searchable_exact_label(food, captured, language, label):
    """Only literal case-insensitive matches: accent/punctuation folding needs aliases."""
    expected = label.lower()
    direct = [food.get(k) for k in ('name', 'display_name', 'short_display_name')] if language == 'EN' else []
    direct.extend(value.get(k) for value in captured['localizations']
                  if value['active'] and value['language'] == language
                  for k in ('display_name', 'short_display_name'))
    return any(isinstance(value, str) and value.lower() == expected for value in direct)


def audit(row, decision, body, previous, conflicts):
    food, captured = row["food"], row["name_review"]
    if (str(food["id"]) != decision["id"] or row["id"] != decision["id"]
            or food.get("source_key") != decision["sourceKey"] or food.get("name") != decision["currentName"]
            or food.get("catalog_type") != "GENERIC_INGREDIENT" or food.get("data_source") != "USDA_FOODDATA"
            or food.get("is_custom") or food.get("publication_status") != "PUBLISHED"
            or decision["sourceKey"] != "USDA_FOODDATA:fdc:" + str(body["fdcId"])
            or body["description"] != decision["sourceDescription"] or body.get("dataType") not in {"SR Legacy", "Foundation"}
            or held.everyday.fingerprint(body) != previous["sourceBodySha256"]):
        raise ValueError("Current/source naming identity or version drift")
    if not isinstance(captured, dict) or set(captured) != {"aliases", "localizations", "options", "option_localizations"}:
        raise ValueError("Full alias/localization/portion state required")
    for key, values in captured.items():
        if len({v["id"] for v in values}) != len(values):
            raise ValueError("Ambiguous captured child rows: " + key)
    is_held = decision["decision"] == "SOURCE_BRAND_SCOPE_HOLD"
    holds = ["SOURCE_BRAND_AND_CLASSIFICATION_REVIEW_REQUIRED"] if is_held else []
    if decision["decision"] == "IDENTITY_CORRECTION":
        holds.append("CANONICAL_IDENTITY_MAP_AND_DEPENDENCY_REVIEW_REQUIRED")
    source_states = held.gate.source.r5.closure.source_states(body["description"])
    current_state = food.get("preparation_state")
    if current_state not in {None, "UNSPECIFIED", "PREPARED"} and not source_states:
        holds.append("SOURCE_PREPARATION_METHOD_REVIEW_REQUIRED")
    food_changes, locale_actions, alias_actions, preserved = {}, [], [], []
    if not is_held:
        food_changes = {k: decision["en"] for k in ("name", "display_name", "short_display_name") if food.get(k) != decision["en"]}
        for lang, key in (("EN", "en"), ("TR", "tr")):
            active = [r for r in captured["localizations"] if r["language"] == lang and r["active"]]
            if len(active) > 1:
                holds.append("DUPLICATE_ACTIVE_LOCALIZATION_" + lang)
            elif active:
                before = active[0]
                changes = {k: decision[key] for k in ("display_name", "short_display_name") if before.get(k) != decision[key]}
                if changes:
                    locale_actions.append({"action": "UPDATE_EXACT_ACTIVE_LOCALIZATION", "before": before, "changes": changes})
            elif any(r["language"] == lang for r in captured["localizations"]):
                holds.append("INACTIVE_LOCALIZATION_REACTIVATION_REQUIRES_APPROVAL_" + lang)
            else:
                locale_actions.append({"action": "ADD_MISSING_LOCALIZATION", "language": lang, "display_name": decision[key], "short_display_name": decision[key]})
    for alias in captured["aliases"]:
        if alias["food_item_id"] != int(decision["id"]):
            raise ValueError("Alias ownership drift")
        if alias["active"] and not is_held and unsafe_alias(decision.get("aliasPolicy"), alias["alias"]):
            if alias["language"] not in {"EN", "TR"}:
                holds.append("UNSUPPORTED_LANGUAGE_ALIAS_REVIEW")
                preserved.append(alias["id"])
            else:
                alias_actions.append({"action": "DEACTIVATE_EXACT_MISLEADING_ALIAS", "before": alias, "reason": decision["reason"]})
        else:
            preserved.append(alias["id"])
    conflict_rows, new_conflicts, omitted = [], [], []
    if not is_held:
        for lang, key in (("EN", "en"), ("TR", "tr")):
            normalized = aliases.normalize_alias(decision[key])
            matching = [a for a in captured["aliases"] if a["language"] == lang and a["normalized_alias"] == normalized]
            external = [a for a in conflicts if a["language"] == lang and a["normalized_alias"] == normalized and a["food_item_id"] != int(decision["id"])]
            if len(matching) > 1:
                holds.append("DUPLICATE_EXISTING_ALIAS_" + lang)
            elif matching and not matching[0]["active"]:
                holds.append("INACTIVE_ALIAS_REACTIVATION_REQUIRES_APPROVAL_" + lang)
            elif not matching:
                if external and already_searchable_exact_label(food, captured, lang, decision[key]):
                    omitted.append({"language": lang, "alias": decision[key], "normalized_alias": normalized,
                                    "reason": "EXACT_CURRENT_NAME_ALREADY_SEARCHABLE_NO_NEW_ALIAS",
                                    "externalAliasIdsPreserved": [a['id'] for a in external]})
                else:
                    alias_actions.append({"action": "ADD_SOURCE_QUALIFIED_ALIAS", "language": lang, "alias": decision[key], "normalized_alias": normalized})
            conflict_rows.extend(external)
            if not matching and not any(a['language'] == lang for a in omitted):
                new_conflicts.extend(external)
    if new_conflicts:
        holds.append("CROSS_PRODUCT_ALIAS_COLLISION_REVIEW")
    return {"id": decision["id"], "sourceKey": decision["sourceKey"], "decision": decision["decision"], "reason": decision["reason"],
        "currentName": food["name"], "sourceDescription": body["description"], "sourceUrl": "https://fdc.nal.usda.gov/food-details/" + str(body["fdcId"]) + "/nutrients",
        "proposedNames": {"EN": decision["en"], "TR": decision["tr"]}, "sourceBodySha256": held.everyday.fingerprint(body),
        "expectedBefore": row, "beforeSha256": held.everyday.fingerprint(row), "foodNameChanges": food_changes,
        "localizationActions": locale_actions, "aliasActions": alias_actions, "preserveAliasIds": preserved,
        "crossProductAliasConflicts": conflict_rows, "newAliasCollisionIds": [a["id"] for a in new_conflicts],
        "redundantAliasesOmitted": omitted,
        "identityCorrectionRequiresDependencyReview": decision["decision"] == "IDENTITY_CORRECTION", "nameApplicationHolds": sorted(set(holds)),
        "canonicalIdentityReview": {"currentCanonicalFoodKey": food.get("canonical_food_key"),
            "sourceProfileKey": decision["sourceKey"], "numericProductIdPreserved": True,
            "canonicalKeyWriteAllowed": False, "reviewRequired": decision["decision"] == "IDENTITY_CORRECTION"},
        "preparationReview": {"currentState": current_state, "explicitSourceStates": source_states,
            "writeAllowed": False, "methodUnproven": "SOURCE_PREPARATION_METHOD_REVIEW_REQUIRED" in holds},
        "independentDataPrerequisites": [h for h in previous["holds"] if h != "CURRENT_NAME_SOURCE_IDENTITY_REVIEW"],
        "semanticNameReviewResolved": not is_held, "liveApplyAllowed": False,
        "nutritionWriteAllowed": False, "portionWriteAllowed": False, "classificationWriteAllowed": False,
        "status": "SOURCE_BRAND_SCOPE_HOLD" if is_held else "NAME_ALIAS_APPLICATION_REVIEW_REQUIRED" if holds else "NAME_ALIAS_PREPARATION_COMPLETE_NOT_APPROVED"}


def build(args):
    now = datetime.now(timezone.utc)
    entries, selected = decisions(args.review, args.registry)
    selection = held.read_json(args.selection / "selection-proof.json")
    path = args.selection / "selected-ids.jsonl"
    if (selection["registrySha256"] != held.everyday.sha(args.registry) or selection["reviewSha256"] != held.everyday.sha(args.review)
            or selection["decisionsSha256"] != held.everyday.sha(path)):
        raise ValueError("Naming selection changed")
    proof = held.read_json(args.current / "snapshot-proof.json")
    catalog = args.current / "catalog.csv"
    if (proof.get("origin") != "AWS_READ_ONLY_SNAPSHOT" or proof.get("scope") != "r6-names"
            or proof.get("transactionReadOnly") is not True or proof.get("exitCode") != 0 or proof.get("rows") != 35
            or proof.get("schemaVersion") != "305" or proof.get("sha256") != held.everyday.sha(catalog)
            or proof.get("decisionsSha256") != held.everyday.sha(path)
            or proof.get("sqlSha256") != hashlib.sha256(collector.name_review_sql(path).encode()).hexdigest()
            or not held.everyday.fresh(proof["time"], now)):
        raise ValueError("Fresh exact naming snapshot required")
    with catalog.open(encoding="utf-8-sig", newline="") as stream:
        raw = list(csv.DictReader(stream))
    if len(raw) != 35 or len({r["id"] for r in raw}) != 35 or {r["id"] for r in raw} != set(selected):
        raise ValueError("Naming cohort drift")
    current = {r["id"]: {k: json.loads(v) if k != "id" else v for k, v in r.items()} for r in raw}
    if len(proof["metrics"]) != 1:
        raise ValueError("Alias collision measurement missing")
    conflicts = proof["metrics"][0]["requestedAliasConflicts"]
    foods, evidence = held.source_evidence(args.sources, args.source_queue, now)
    rows = [audit(current[r["id"]], r, foods[r["sourceKey"].split(":")[-1]], selected[r["id"]], conflicts) for r in entries]
    args.output.mkdir(parents=True, exist_ok=False)
    plan = {"version": "R6_HELD_NAME_APPLICATION_PREPARATION_V1", "liveApplyAllowed": False, "approval": None, "backup": None,
        "schemaVersion": proof["schemaVersion"], "snapshotTime": proof["time"], "sourceFetchedAt": evidence["fetchedAt"],
        "requiredReleaseGates": ["EXACT_OWNER_APPROVAL", "FRESH_ENCRYPTED_BACKUP", "FRESH_FULL_NAME_ALIAS_PORTION_BEFORE_IMAGES",
            "REVIEWED_SOURCE_PROFILE_CANONICAL_IDENTITY_MAP",
            "IDENTITY_CORRECTION_HISTORY_AND_RECIPE_DEPENDENCY_REVIEW",
            "CURRENT_EXACT_FDC_SOURCE_AND_RULE_HASHES", "TRANSACTION_LOCK_AND_EXACT_COUNT", "APPLY_AND_OWNED_ROW_UNDO_REHEARSAL",
            "SEARCH_CACHE_REVISION_AFTER_COMMIT", "EN_TR_SEARCH_AND_NO_MISLEADING_ALIAS_ACCEPTANCE"],
        "forbiddenWrites": ["nutrition", "reference_units", "servings", "preparation_state", "categories", "source_evidence", "barcodes", "brand_links", "canonical_food_key", "food_concept_key", "merges", "deletes"],
        "inputHashes": {"registry": held.everyday.sha(args.registry), "review": held.everyday.sha(args.review),
            "snapshot": held.everyday.sha(catalog), "sourceEvidence": held.everyday.sha(args.sources / "evidence.json"), "producer": held.everyday.sha(Path(__file__))}, "rows": rows}
    output = args.output / "name-alias-plan.json"
    output.write_text(json.dumps(plan, ensure_ascii=False, allow_nan=False) + "\n", encoding="utf-8")
    summary = {"products": 35, "decisions": dict(Counter(r["decision"] for r in rows)),
        "states": dict(Counter(r["status"] for r in rows)), "semanticNameReviewsResolved": sum(r["semanticNameReviewResolved"] for r in rows),
        "proposedFoodNameChanges": sum(bool(r["foodNameChanges"]) for r in rows), "localizationActions": sum(len(r["localizationActions"]) for r in rows),
        "aliasActions": dict(Counter(a["action"] for r in rows for a in r["aliasActions"])),
        "preservedAliases": sum(len(r["preserveAliasIds"]) for r in rows), "overlappingApplicationHolds": dict(Counter(h for r in rows for h in r["nameApplicationHolds"])),
        "snapshotTime": proof["time"], "sourceFetchedAt": evidence["fetchedAt"], "planSha256": held.everyday.sha(output),
        "databaseWrites": 0, "approvedProducts": 0, "liveApplyAllowed": False}
    (args.output / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--prepare", action="store_true")
    parser.add_argument("--registry", type=Path, default=REGISTRY)
    for name in ("review", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    for name in ("selection", "current", "sources", "source-queue"):
        parser.add_argument("--" + name, type=Path)
    args = parser.parse_args()
    prepare(args) if args.prepare else build(args)

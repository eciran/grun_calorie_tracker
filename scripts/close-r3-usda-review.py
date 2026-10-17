"""Freeze source-proven classifications and conservative, non-destructive merge decisions.

This is NOT an import file or application command. Different FDC profiles remain
separate even when labels, macros or concepts coincide. R8 owns any live writes.
"""
import argparse
import csv
import hashlib
import importlib.util
import json
import math
import re
from collections import Counter
from pathlib import Path

spec = importlib.util.spec_from_file_location("identity_review", Path(__file__).with_name("build-product-source-identity-review.py"))
review = importlib.util.module_from_spec(spec)
spec.loader.exec_module(review)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_csv(path):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def verify_inputs(catalog_path, proof_path, evidence_path):
    catalog = read_csv(catalog_path)
    proof = json.loads(proof_path.read_text(encoding="utf-8-sig"))
    evidence = json.loads(evidence_path.read_text(encoding="utf-8-sig"))
    if evidence.get("version") != "R3_EXACT_FDC_EVIDENCE_V1" or evidence.get("source") != "https://api.nal.usda.gov/fdc/v1/foods":
        raise ValueError("Official full-detail USDA evidence required")
    digest = sha(catalog_path)
    if proof.get("origin") != "AWS_READ_ONLY_SNAPSHOT" or proof.get("transactionReadOnly") is not True or proof.get("exitCode") != 0:
        raise ValueError("Read-only snapshot proof required")
    if proof.get("sha256") != digest or evidence.get("catalogSha256") != digest or proof.get("rows") != len(catalog):
        raise ValueError("Snapshot/evidence hash or row count mismatch")
    if len({r["id"] for r in catalog}) != len(catalog):
        raise ValueError("Duplicate DB id in snapshot")
    requested = {review.fdc_id(r.get("source_key")) for r in catalog if r.get("data_source") == "USDA_FOODDATA"}
    if None in requested or not requested:
        raise ValueError("Invalid source key")
    foods = evidence.get("foods", [])
    ids = [str(f.get("fdcId")) for f in foods]
    if len(ids) != len(set(ids)) or set(ids) != requested or set(evidence.get("requestedIds", [])) != requested or evidence.get("matchedCount") != len(requested):
        raise ValueError("Exact FDC evidence coverage is incomplete")
    batch_ids = []
    batch_foods = []
    for batch in evidence.get("batchFiles", []):
        name = batch["file"]
        if Path(name).name != name:
            raise ValueError("Invalid batch filename")
        path = evidence_path.parent / name
        if sha(path) != batch["sha256"]:
            raise ValueError("Source batch hash mismatch")
        content = json.loads(path.read_text(encoding="utf-8"))
        current = [str(f.get("fdcId")) for f in content]
        if len(current) != len(set(current)) or set(current) != set(batch["ids"]):
            raise ValueError("Source batch membership mismatch")
        batch_ids.extend(current)
        batch_foods.extend(content)
    if Counter(batch_ids) != Counter(ids) or review.fingerprint(sorted(batch_foods, key=lambda f: f["fdcId"])) != review.fingerprint(sorted(foods, key=lambda f: f["fdcId"])):
        raise ValueError("Frozen source bodies do not match evidence manifest")
    return catalog, proof, {str(f["fdcId"]): f for f in foods}


def source_states(description):
    words = set(re.findall(r"[a-z]+", description.lower()))
    specific = {word.upper() for word in ("raw", "boiled", "roasted", "grilled", "fried", "baked", "steamed") if word in words}
    return sorted(specific or ({"COOKED"} if "cooked" in words else set()))


def source_core(food):
    wanted = {"calories": (1008, "kcal"), "protein": (1003, "g"), "fat": (1004, "g"), "carbs": (1005, "g")}
    result = {}
    for field, (nutrient_id, unit) in wanted.items():
        matches = [n.get("amount") for n in food.get("foodNutrients", []) if n.get("nutrient", {}).get("id") == nutrient_id
                   and n["nutrient"].get("unitName", "").lower() == unit]
        result[field] = matches[0] if len(matches) == 1 and isinstance(matches[0], (int, float)) and not isinstance(matches[0], bool) and math.isfinite(matches[0]) and matches[0] >= 0 else None
    return result


def source_concept(description):
    """Reviewed source-description families, never translated display-name slugs.

    These keys group a food concept only. Each distinct FDC composition profile
    stays separate; parts, preparation and all source qualifiers remain evidence.
    """
    parts = [review.match_name(p) for p in description.split(",")]
    base = parts[0]
    simple = {"raspberries": "raspberry", "strawberries": "strawberry", "pears": "pear", "kiwifruit": "kiwi",
              "pineapple": "pineapple", "asparagus": "asparagus", "mushrooms": "mushroom", "beets": "beetroot",
              "broccoli": "broccoli", "cauliflower": "cauliflower", "millet": "millet", "melons": "melon",
              "oranges": "orange", "tangerines": "mandarin", "mangos": "mango", "peaches": "peach", "kale": "kale",
              "spinach": "spinach", "buckwheat groats": "buckwheat", "apples": "apple", "avocados": "avocado",
              "blueberries": "blueberry", "grapefruit": "grapefruit", "bananas": "banana", "cherries": "cherry",
              "soybeans": "soybean", "lentils": "lentil", "rice noodles": "rice_noodle", "sweet potato leaves": "sweet_potato_leaf",
              "brussels sprouts": "brussels_sprouts"}
    if base in simple:
        return simple[base]
    words = set(review.match_name(description).split())
    if base == "rice":
        return "brown_rice" if "brown" in words else "white_rice" if "white" in words else None
    if base in {"pasta", "macaroni", "spaghetti"}:
        return "pasta"
    if base == "noodles":
        return "soba_noodle" if "soba" in words else "somen_noodle" if "somen" in words else "egg_noodle" if "egg" in words else None
    if base in {"fish", "crustaceans", "mollusks"}:
        species = {"haddock", "cod", "tilapia", "swordfish", "shrimp", "salmon", "mackerel", "pollock", "mussel", "crab", "halibut", "oyster", "trout"}
        matches = species & words
        return next(iter(matches)) if len(matches) == 1 else None
    if base in {"chicken", "turkey"}:
        for cut in ("drumstick", "thigh", "breast", "leg"):
            if cut in words:
                return base + "_" + cut
        return base + ("_dark_meat" if "dark" in words else "_light_meat" if "light" in words else "")
    if base == "pork":
        return "pork_tenderloin" if "tenderloin" in words else "pork_loin" if "loin" in words else None
    if base == "beef":
        for phrase, key in [("eye of round", "beef_eye_of_round"), ("top round", "beef_top_round"),
                            ("tenderloin", "beef_tenderloin"), ("sirloin", "beef_sirloin")]:
            if phrase in review.match_name(description):
                return key
    if base == "beans":
        for word in ("adzuki", "black", "cranberry", "french", "kidney", "navy", "yellow", "pink", "pinto", "white"):
            if word in words:
                return word + "_bean"
        if {"great", "northern"} <= words:
            return "great_northern_bean"
    if base == "peas" and "split" in words:
        return "split_pea"
    if base == "nuts":
        for terms, key in [({"almonds"}, "almond"), ({"cashew"}, "cashew"), ({"pistachio"}, "pistachio")]:
            if terms <= words:
                return key + ("_butter" if "butter" in words else "")
    if base == "seeds":
        if "tahini" in words:
            return "tahini"
        for term, key in [("sunflower", "sunflower_seed"), ("sesame", "sesame_seed"), ("pumpkin", "pumpkin_seed")]:
            if term in words:
                return key + ("_butter" if "butter" in words else "")
    if base == "peppers":
        return "red_pepper" if "red" in words else "green_pepper" if "green" in words else None
    if base == "carrots":
        return "carrot"
    if base == "potatoes":
        return "potato_skin" if "skin" in parts and "flesh" not in parts else "potato"
    if base == "squash":
        if "zucchini" in words:
            return "courgette"
        for variety in ("butternut", "spaghetti", "acorn", "hubbard", "scallop"):
            if variety in words:
                return variety + "_squash"
        return "summer_squash" if "summer" in words else "winter_squash" if "winter" in words else None
    return None


def decide(row, food, catalog):
    record_id = review.fdc_id(row.get("source_key"))
    if str(food.get("fdcId")) != record_id or food.get("dataType") not in {"Foundation", "SR Legacy"}:
        raise ValueError("Candidate lacks an exact generic USDA source")
    if row.get("barcode") or row.get("normalized_barcode") or food.get("gtinUpc"):
        raise ValueError("Non-branded classification conflicts with GTIN")
    if row.get("source_data_type") and row["source_data_type"] != food["dataType"]:
        raise ValueError("Stored source subtype conflict")
    same_source = [r for r in catalog if r["id"] != row["id"] and review.fdc_id(r.get("source_key")) == record_id]
    before = review.fingerprint(row)
    states = source_states(food["description"])
    findings = []
    if not states:
        findings.append("PREPARATION_NOT_EXPLICIT_IN_SOURCE")
    elif row.get("preparation_state") not in states and not (row.get("preparation_state") == "COOKED" and "cooked" in food["description"].lower()):
        findings.append("PREPARATION_SOURCE_REVIEW")
    if len(states) > 1:
        findings.append("MULTIPLE_SOURCE_PREPARATION_ALTERNATIVES")
    if "marinated" in row.get("name", "").lower() and "with added solution" in food["description"].lower():
        findings.append("ADDED_SOLUTION_IS_NOT_MARINADE")
    source_words = set(review.match_name(food["description"]).split())
    if "dry" in source_words and "cooked" not in source_words and row.get("preparation_state") in {"BOILED", "COOKED", "PREPARED"}:
        findings.append("DRY_SOURCE_PRESENTED_AS_READY_COOKED")
    exceptions = {"171466": "LIGHT_MEAT_IS_NOT_EXPLICIT_BREAST", "168237": "PORK_LOIN_BLADE_IS_NOT_SHOULDER",
                  "169195": "PORK_LOIN_BLADE_IS_NOT_SHOULDER", "169294": "ACORN_SQUASH_IS_NOT_CORN",
                  "169299": "SOURCE_DOES_NOT_STATE_ROASTED"}
    if record_id in exceptions:
        findings.append(exceptions[record_id])
    if row.get("nutrition_reference_unit") != "PER_100G":
        findings.append("SOURCE_MASS_BASIS_REQUIRES_BRIDGE")
    core = source_core(food)
    if any(v is None for v in core.values()):
        findings.append("SOURCE_CORE_NOT_FULLY_REPORTED")
    differences = {}
    if row.get("nutrition_reference_unit") == "PER_100G":
        for field, value in core.items():
            try:
                old = float(row.get(field, ""))
            except (ValueError, TypeError):
                differences[field] = "MISSING_OR_INVALID_LOCAL_VALUE"
                continue
            if not math.isfinite(old) or old < 0:
                differences[field] = "MISSING_OR_INVALID_LOCAL_VALUE"
            elif value is not None and abs(old - value) > (0.51 if field == "calories" else 0.051):
                differences[field] = {"before": old, "source": value}
    if differences:
        findings.append("SOURCE_CORE_DIFFERENCE_REVIEW")
    return {"id": row["id"], "sourceKey": row["source_key"], "before": row, "beforeFingerprint": before,
            "sourceFdcId": record_id, "sourceDescription": food["description"], "sourceDataType": food["dataType"],
            "sourceFingerprint": review.fingerprint(food), "sourceUrl": "https://fdc.nal.usda.gov/food-details/" + record_id + "/nutrients",
            "sourceCategory": food.get("foodCategory", {}).get("description"), "sourcePreparationAlternatives": states,
            "classificationDecision": "RECLASSIFY_TO_GENERIC", "proposedType": "GENERIC_INGREDIENT",
            "mergeDecision": "SAME_SOURCE_REQUIRES_REFERENCE_REVIEW" if same_source else "KEEP_SEPARATE_SOURCE_PROFILE",
            "sameSourceTargets": [{"id": r["id"], "beforeFingerprint": review.fingerprint(r)} for r in same_source],
            "mergeReason": "Exact FDC duplicate requires history/reference review" if same_source else "Distinct FDC profile; no proven interchangeable composition. Name/macro similarity is not merge evidence.",
            "proposedConceptKey": source_concept(food["description"]),
            "proposedCompositionVariantKey": "fdc_" + record_id, "sourceCore": core,
            "findings": findings, "coreDifferences": differences,
            "allowedFutureFields": ["catalog_type", "source_data_type"], "applyAllowed": False,
            "nutritionWriteAllowed": False, "portionWriteAllowed": False, "deleteAllowed": False}


def build(args):
    catalog, proof, foods = verify_inputs(args.catalog, args.proof, args.evidence)
    candidates = [r for r in catalog if r.get("data_source") == "USDA_FOODDATA" and r.get("catalog_type") == "BRANDED_PRODUCT"
                  and not r.get("barcode") and not r.get("normalized_barcode")]
    if len(candidates) != args.expected_candidates:
        raise ValueError("Exact-count candidate gate failed; no approved decisions generated")
    decisions = [decide(r, foods[review.fdc_id(r["source_key"])], catalog) for r in candidates]
    if any(not d["proposedConceptKey"] for d in decisions):
        raise ValueError("Concept coverage incomplete; do not close R3")
    candidate_ids = {c["id"] for c in candidates}
    existing = [r for r in catalog if r["id"] not in candidate_ids]
    index = review.index_catalog(existing, [])
    candidate_index = review.index_catalog(candidates, [])
    for decision in decisions:
        row = decision["before"]
        matches = review.overlaps(row, index)["candidates"]
        peers = [c for c in review.overlaps(row, candidate_index)["candidates"] if c.get("targetId") != row["id"]]
        decision["existingNameOverlapCandidates"] = matches
        decision["peerNameOverlapCandidates"] = peers
        for match in matches + peers:
            source_id = review.fdc_id(match.get("targetSourceKey"))
            if source_id and source_id in foods:
                source = foods[source_id]
                match["sourceDescription"] = source["description"]
                match["sourceDataType"] = source["dataType"]
                match["sourceFingerprint"] = review.fingerprint(source)
            match["mergeDecision"] = "KEEP_SEPARATE_NO_PROVEN_PROFILE_EQUIVALENCE"
        decision["identityAssignmentDecision"] = "DEFER_WITH_FINDINGS_TO_R4_R5" if decision["findings"] else "SOURCE_PROFILE_IDENTITY_PROPOSAL_ONLY"
    cofid = read_csv(args.cofid)
    if len(cofid) != len({r["source_key"] for r in cofid}):
        raise ValueError("CoFID source-key collision")
    cofid_index = review.index_catalog(catalog, [])
    cofid_decisions = [review.overlaps(row, cofid_index) for row in cofid]
    for row in cofid_decisions:
        row["mergeDecision"] = "KEEP_SEPARATE_UNPROVEN_CROSS_SOURCE_EQUIVALENCE"
        row["importApproval"] = "NOT_GRANTED"
    summary = {"status": "SOURCE_AND_MERGE_DECISIONS_CLOSED_NOT_APPLIED" if not any(d["sameSourceTargets"] for d in decisions) else "REFERENCE_REVIEW_REQUIRED",
               "snapshotTime": proof["time"], "catalogRows": len(catalog), "catalogTypes": dict(Counter(r["catalog_type"] for r in catalog)),
               "sourceRecordsMatched": len(foods), "candidates": len(decisions),
               "candidateSourceTypes": dict(Counter(d["sourceDataType"] for d in decisions)),
               "conceptProposals": sum(bool(d["proposedConceptKey"]) for d in decisions),
               "rowsWithFindings": sum(bool(d["findings"]) for d in decisions),
               "coreCompleteAtSource": sum(all(v is not None for v in d["sourceCore"].values()) for d in decisions),
               "coreDifferenceRows": sum(bool(d["coreDifferences"]) for d in decisions),
               "mergeDecisions": dict(Counter(d["mergeDecision"] for d in decisions)),
               "exactSameSourcePairs": sum(len(d["sameSourceTargets"]) for d in decisions),
               "withExistingNameOverlap": sum(bool(d["existingNameOverlapCandidates"]) for d in decisions),
               "withPeerNameOverlap": sum(bool(d["peerNameOverlapCandidates"]) for d in decisions),
               "findingCounts": dict(Counter(f for d in decisions for f in d["findings"])),
               "cofidCompared": len(cofid_decisions), "cofidOverlapRows": sum(bool(d["candidates"]) for d in cofid_decisions),
               "automaticMerges": 0, "databaseWrites": 0, "applyAllowed": False}
    report = {"ruleset": "R3_EXACT_SOURCE_COMPATIBILITY_V1", "summary": summary,
              "inputHashes": {"catalog": sha(args.catalog), "proof": sha(args.proof), "evidence": sha(args.evidence), "cofid": sha(args.cofid)},
              "scope": "173 USDA classification and no-merge decisions; CoFID comparison is not import/nutrition/portion approval. Identity concept and findings go to R4/R5; R8 owns narrow guarded live application.",
              "decisions": decisions, "cofid": cofid_decisions}
    args.output.mkdir(parents=True, exist_ok=True)
    report_path = args.output / "compatibility-manifest.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    columns = ["id", "sourceKey", "beforeName", "sourceDescription", "sourceDataType", "classificationDecision", "mergeDecision", "findings", "sourceUrl", "beforeFingerprint"]
    with (args.output / "173-decisions.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=columns)
        writer.writeheader()
        for d in decisions:
            writer.writerow({**{k: d[k] for k in columns if k not in {"beforeName", "findings"}},
                             "beforeName": d["before"]["name"], "findings": "|".join(d["findings"])})
    with (args.output / "r4-followup-queue.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=["id", "sourceKey", "beforeName", "sourceDescription", "beforeState", "sourcePreparationAlternatives", "findings", "priority", "beforeFingerprint"])
        writer.writeheader()
        for d in decisions:
            if d["findings"]:
                writer.writerow({"id": d["id"], "sourceKey": d["sourceKey"], "beforeName": d["before"]["name"],
                    "sourceDescription": d["sourceDescription"], "beforeState": d["before"]["preparation_state"],
                    "sourcePreparationAlternatives": "|".join(d["sourcePreparationAlternatives"]), "findings": "|".join(d["findings"]),
                    "priority": "P1" if "DRY_SOURCE_PRESENTED_AS_READY_COOKED" in d["findings"] else "P2", "beforeFingerprint": d["beforeFingerprint"]})
    args.output.joinpath("summary.json").write_text(json.dumps({**summary, "manifestSha256": sha(report_path)}, indent=2), encoding="utf-8")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    for name in ("catalog", "proof", "evidence", "cofid", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--expected-candidates", type=int, default=173)
    build(parser.parse_args())

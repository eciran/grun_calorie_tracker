"""R3 offline, review-only source/type and CoFID overlap reconciliation.

Reads a product-only CSV snapshot; never connects to a database or provider.
Names find candidates, never establish interchangeable composition or portions.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import unicodedata
from collections import Counter, defaultdict
from difflib import SequenceMatcher
from pathlib import Path


DATA_TYPES = {"Foundation", "SR Legacy", "Branded", "Survey (FNDDS)", "Experimental"}


def load_csv(path):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def fingerprint(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def fdc_id(source_key):
    match = re.fullmatch(r"USDA(?:_FOODDATA)?:fdc:([1-9][0-9]{0,17})", source_key or "")
    return match.group(1) if match else None


def match_name(value):
    value = unicodedata.normalize("NFKD", (value or "").replace("ı", "i"))
    value = "".join(char for char in value if not unicodedata.combining(char)).lower()
    return " ".join(re.findall(r"[a-z0-9]+", value))


def classify(row, source_entries):
    record_id = fdc_id(row.get("source_key"))
    result = {"id": row.get("id"), "sourceKey": row.get("source_key"), "beforeFingerprint": fingerprint(row),
              "beforeType": row.get("catalog_type"), "applyAllowed": False}
    matches = source_entries.get(record_id, [])
    if not record_id or len(matches) != 1:
        return {**result, "decision": "SOURCE_REQUIRED", "reason": "MISSING_OR_AMBIGUOUS_EXACT_FDC_SOURCE"}
    source = matches[0]
    data_type = source.get("selectedDataType")
    if data_type not in DATA_TYPES or (row.get("source_data_type") and row["source_data_type"] != data_type):
        return {**result, "decision": "SOURCE_REQUIRED", "reason": "UNKNOWN_OR_CONFLICTING_SUBTYPE"}
    barcode = row.get("normalized_barcode") or row.get("barcode")
    if data_type in {"Survey (FNDDS)", "Experimental"}:
        return {**result, "decision": "ADMIN_REVIEW", "reason": "EXPLICIT_CLASSIFICATION_REQUIRED", "sourceDataType": data_type}
    if data_type != "Branded" and barcode:
        return {**result, "decision": "ADMIN_REVIEW", "reason": "NON_BRANDED_SOURCE_WITH_GTIN"}
    target_type = "BRANDED_PRODUCT" if data_type == "Branded" else "GENERIC_INGREDIENT"
    proposed_concept = source.get("identity") if target_type == "GENERIC_INGREDIENT" else None
    # Conservative source-specific variant proposal. No cross-source formulation merge.
    return {**result, "decision": "TYPE_AND_IDENTITY_REVIEW", "targetType": target_type, "sourceDataType": data_type,
            "sourceEvidence": source, "proposedConceptKey": proposed_concept,
            "proposedCompositionVariantKey": "fdc_" + record_id if proposed_concept else None,
            "reason": "EXACT_SOURCE_ID_NOT_MERGE_APPROVAL"}


def index_catalog(catalog, prepared):
    index = defaultdict(list)
    for row in catalog:
        target = {"targetSourceKey": row.get("source_key"), "targetId": row.get("id"),
                  "targetType": row.get("catalog_type"), "state": row.get("preparation_state"),
                  "basis": row.get("nutrition_reference_unit"), "beforeFingerprint": fingerprint(row)}
        for field in ("name", "display_name", "display_name_en", "display_name_tr", "short_display_name"):
            key = match_name(row.get(field))
            if key and target not in index[key]:
                index[key].append(target)
    for row in prepared:
        for field in ("display_name_en", "display_name_tr"):
            key = match_name(row.get(field))
            target = {"targetItemKey": row["item_key"], "targetType": "STANDARD_PREPARED_ITEM", "targetEvidence": "SCOPE_MANIFEST_NOT_LIVE_ROW"}
            if key and target not in index[key]:
                index[key].append(target)
    return index


def overlaps(source, catalog_index):
    candidates = {}
    for field in ("name", "display_name", "display_name_en"):
        query = match_name(source.get(field))
        if not query:
            continue
        for key in catalog_index:
            exact = query == key
            if not exact and not set(query.split()) & set(key.split()):
                continue
            score = 1.0 if exact else SequenceMatcher(None, query, key).ratio()
            if score < 0.85:
                continue
            for target in catalog_index[key]:
                identity = target.get("targetSourceKey") or target.get("targetItemKey") or "DB:" + str(target.get("targetId"))
                candidate = {**target, "score": round(score, 4), "matchEvidence": "NAME_ONLY",
                             "stateParity": "UNKNOWN" if not target.get("state") else "SAME" if source.get("preparation_state") == target["state"] else "DIFFERENT",
                             "basisParity": "UNKNOWN" if not target.get("basis") else "SAME" if source.get("nutrition_reference_unit") == target["basis"] else "DIFFERENT"}
                if identity not in candidates or candidate["score"] > candidates[identity]["score"]:
                    candidates[identity] = candidate
    ordered = sorted(candidates.values(), key=lambda row: (-row["score"], row.get("targetSourceKey") or row.get("targetItemKey") or "DB:" + str(row.get("targetId"))))
    reasons = set(filter(None, source.get("review_reasons", "").split("|")))
    source_required = source.get("preparation_state") in {None, "", "UNSPECIFIED"} or "INCOMPLETE_MACROS" in reasons
    return {"sourceKey": source["source_key"], "sourceFingerprint": fingerprint(source), "applyAllowed": False,
            "decision": "OVERLAP_REVIEW" if ordered else "SOURCE_REQUIRED" if source_required else "NO_KNOWN_MATCH_REVIEW",
            "sourceState": source.get("preparation_state"), "sourceBasis": source.get("nutrition_reference_unit"),
            "candidateCount": len(ordered), "candidates": ordered, "sourceRequired": source_required}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--catalog", required=True, type=Path)
    parser.add_argument("--catalog-origin", choices=["LOCAL_FIXTURE", "AWS_READ_ONLY_SNAPSHOT"], default="LOCAL_FIXTURE")
    parser.add_argument("--usda-selection", required=True, type=Path)
    parser.add_argument("--cofid", required=True, type=Path)
    parser.add_argument("--prepared-manifest", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    catalog = load_csv(args.catalog)
    sources = defaultdict(list)
    for entry in json.loads(args.usda_selection.read_text(encoding="utf-8-sig"))["entries"]:
        if fdc_id(entry.get("sourceKey")) != str(entry.get("fdcId")):
            raise ValueError("USDA selection source id/key mismatch")
        sources[str(entry["fdcId"])].append(entry)
    classification = [classify(row, sources) for row in catalog if row.get("data_source") in {"USDA", "USDA_FOODDATA"} or fdc_id(row.get("source_key"))]
    prepared = load_csv(args.prepared_manifest)
    index = index_catalog(catalog, prepared)
    cofid = load_csv(args.cofid)
    if len(cofid) != len({row["source_key"] for row in cofid}):
        raise ValueError("CoFID source-key collision: cannot reconcile ambiguous provider identities")
    decisions = [overlaps(row, index) for row in cofid]
    report = {"ruleset": "R3_SOURCE_IDENTITY_REVIEW_V1", "catalogOrigin": args.catalog_origin, "applyAllowed": False,
              "inputSha256": {key: hashlib.sha256(getattr(args, key).read_bytes()).hexdigest()
                              for key in ("catalog", "usda_selection", "cofid", "prepared_manifest")},
              "catalogRows": len(catalog), "classificationRows": len(classification),
              "classificationDecisions": dict(Counter(row["decision"] for row in classification)),
              "cofidRows": len(decisions), "cofidDecisions": dict(Counter(row["decision"] for row in decisions)),
              "cofidSourceRequired": sum(row["sourceRequired"] for row in decisions),
              "automaticMergeRows": 0, "classification": classification, "cofid": decisions,
              "releasePolicy": "No apply. Frozen-source proposals require live id/fingerprint, subtype, state, composition, basis and overlap approval under R8. Local fixtures are not AWS coverage."}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key not in {"classification", "cofid"}}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

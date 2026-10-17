"""Offline R4 decisions and streaming catalog-format dry-run. Never applies data."""
import argparse
import csv
import importlib.util
import json
import re
import unicodedata
from collections import Counter
from pathlib import Path


def load_module(filename, name):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


closure = load_module("close-r3-usda-review.py", "closure")
review = closure.review


def clean(value):
    return re.sub(r"\s+([,.;:!?])", r"\1", re.sub(r"\s+", " ", unicodedata.normalize("NFC", value or "")).strip())


def build_decisions(catalog, foods, r3, registry):
    if registry.get("version") != "catalog-naming-r4-v1":
        raise ValueError("Unexpected naming ruleset")
    expected = {d["sourceKey"]: d for d in r3["decisions"] if d["findings"]}
    entries = registry.get("entries", [])
    keys = [e["sourceKey"] for e in entries]
    if len(keys) != 16 or len(set(keys)) != 16 or set(keys) != set(expected):
        raise ValueError("Exact 16-row R3 naming queue coverage required")
    by_source = {}
    for row in catalog:
        if row["source_key"] in by_source:
            raise ValueError("Ambiguous source identity in snapshot")
        by_source[row["source_key"]] = row
    decisions = []
    allowed_states = {"RAW", "COOKED", "BOILED", "GRILLED", "FRIED", "BAKED", "ROASTED", "STEAMED", "PREPARED", "UNSPECIFIED"}
    for entry in entries:
        row = by_source[entry["sourceKey"]]
        old = expected[entry["sourceKey"]]
        food = foods[review.fdc_id(row["source_key"])]
        if old["beforeFingerprint"] != review.fingerprint(row) or old["sourceFingerprint"] != review.fingerprint(food):
            raise ValueError("R3 before/source fingerprint drift")
        if food["dataType"] != "SR Legacy" or entry["sourceDescription"] != food["description"]:
            raise ValueError("Exact official source description/subtype mismatch")
        if entry["targetState"] not in allowed_states or not entry.get("stateRationale") or not isinstance(entry.get("servingReviewRequired"), bool):
            raise ValueError("Unreviewed preparation decision")
        for field in ("displayNameEn", "displayNameTr"):
            name = entry[field]
            if not name or len(name) > 255 or clean(name) != name or "<" in name or any(unicodedata.category(c) == "Cf" for c in name):
                raise ValueError("Invalid reviewed display name")
        if review.fdc_id(entry["sourceKey"]) == "168876" and ("Dry" not in entry["displayNameEn"] or "Kuru" not in entry["displayNameTr"] or entry["targetState"] != "UNSPECIFIED"):
            raise ValueError("Dry rice must not claim a ready-cooked or raw state")
        changes = {"name": entry["sourceDescription"], "display_name": entry["displayNameEn"],
                   "short_display_name": entry["displayNameEn"], "preparation_state": entry["targetState"]}
        decisions.append({"id": row["id"], "sourceKey": row["source_key"], "beforeFingerprint": old["beforeFingerprint"],
            "sourceFingerprint": old["sourceFingerprint"], "before": row, "changes": changes,
            "localizations": {"EN": {"displayName": entry["displayNameEn"], "shortDisplayName": entry["displayNameEn"]},
                              "TR": {"displayName": entry["displayNameTr"], "shortDisplayName": entry["displayNameTr"]}},
            "decision": "SOURCE_NAMING_RESOLVED", "stateRationale": entry["stateRationale"],
            "identityAssignment": "DO_NOT_INFER_HEAT_STATE_OR_REKEY", "servingReviewRequired": entry["servingReviewRequired"],
            "aliasPolicy": "Do not retain misleading old cut/preparation names as active aliases. Export and review exact alias rows under R8 before changing any.",
            "freshBeforeImagesRequired": ["localizations", "search_aliases", "servings", "canonical_references"],
            "applyAllowed": False, "nutritionWriteAllowed": False, "barcodeWriteAllowed": False, "mergeAllowed": False})
    return decisions


def format_findings(row, fields):
    findings = []
    proposals = {}
    for field in ("name", "display_name", "short_display_name", "brand", "display_name_en", "display_name_tr", "short_display_name_en", "short_display_name_tr"):
        if field not in fields or not row.get(field):
            continue
        value = row[field]
        if clean(value) != value:
            findings.append(field.upper() + "_COSMETIC_FORMAT")
            proposals[field] = clean(value)
        if re.search(r"</?[A-Za-z][^>]*>", value):
            findings.append(field.upper() + "_HTML_REVIEW")
            proposals.pop(field, None)
        if any(c == "\ufffd" or unicodedata.category(c) in {"Cc", "Cf"} for c in value):
            findings.append(field.upper() + "_CONTROL_OR_ENCODING_REVIEW")
            proposals.pop(field, None)
        if value.count("(") != value.count(")"):
            findings.append(field.upper() + "_PARENTHESES_REVIEW")
    if "display_name" in fields and not row.get("display_name"):
        findings.append("MISSING_DISPLAY_NAME")
    if row.get("catalog_type") not in {"BRANDED_PRODUCT", "USER_CUSTOM"}:
        for lang in ("en", "tr"):
            field = "display_name_" + lang
            if field in fields and not row.get(field):
                findings.append("MISSING_" + lang.upper() + "_LOCALIZATION")
        tr = row.get("display_name_tr", "")
        if re.search(r"\b(cig|gogus|gogsu|sut|pirinc|pismis|haslanmis|firinlanmis|yagsiz|cikolata|cilek)\b", tr, re.I):
            findings.append("TR_DIACRITICS_REVIEW")
        if re.search(r"\b(raw|cooked|boiled|grilled|roasted|steamed|skinless|boneless|unsalted|unsweetened)\b", tr, re.I):
            findings.append("ENGLISH_DESCRIPTOR_IN_TR_NAME")
    if row.get("canonical_brand_name") and row.get("brand") and row["canonical_brand_name"] != row["brand"]:
        findings.append("CANONICAL_BRAND_DIFFERENCE_REVIEW")
    return findings, proposals


def stream_dry_run(catalog_path, output):
    counts = Counter()
    ids = set()
    rows = 0
    with catalog_path.open(encoding="utf-8-sig", newline="") as source, output.open("w", encoding="utf-8-sig", newline="") as target:
        reader = csv.DictReader(source)
        fields = set(reader.fieldnames or [])
        if not {"id", "name", "display_name"} <= fields:
            raise ValueError("Product-only snapshot with id/name/display_name required")
        writer = csv.DictWriter(target, fieldnames=["id", "source_key", "before_fingerprint", "findings", "cosmetic_proposals", "apply_allowed"])
        writer.writeheader()
        for row in reader:
            if not row["id"] or row["id"] in ids:
                raise ValueError("Duplicate/missing row id; partial dry-run is not a completion report")
            ids.add(row["id"])
            findings, proposals = format_findings(row, fields)
            counts.update(findings)
            writer.writerow({"id": row["id"], "source_key": row.get("source_key"), "before_fingerprint": review.fingerprint(row),
                             "findings": "|".join(findings), "cosmetic_proposals": json.dumps(proposals, ensure_ascii=False), "apply_allowed": False})
            rows += 1
    return {"rows": rows, "findingCounts": dict(counts), "brandMeasured": "brand" in fields,
            "localizationsMeasured": {lang: "display_name_" + lang.lower() in fields for lang in ("EN", "TR")},
            "scope": "Streaming Unicode/space/markup format dry-run only. Full language/approved-brand checks run in catalog-audit-r4-v1. Missing snapshot fields are unmeasured, not missing facts."}


def main():
    parser = argparse.ArgumentParser()
    for name in ("catalog", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    for name in ("proof", "evidence", "r3-manifest"):
        parser.add_argument("--" + name, type=Path)
    parser.add_argument("--format-only", action="store_true", help="Stream any product-only naming snapshot without source/apply approval")
    parser.add_argument("--registry", type=Path, default=Path("src/main/resources/catalog/source-name-decisions-r4-v1.json"))
    args = parser.parse_args()
    if args.format_only:
        args.output.mkdir(parents=True, exist_ok=True)
        partial = args.output / "catalog-format-dry-run.csv.partial"
        dry = stream_dry_run(args.catalog, partial)
        partial.replace(args.output / "catalog-format-dry-run.csv")
        summary = {"status": "FORMAT_DRY_RUN_ONLY_NOT_APPROVED", "inputSha256": closure.sha(args.catalog),
                   "catalogOrigin": "PRODUCT_FILE_NOT_DATABASE_VERIFICATION", "applyAllowed": False, **dry}
        (args.output / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
        print(json.dumps(summary, indent=2))
        return
    if any(getattr(args, name) is None for name in ("proof", "evidence", "r3_manifest")):
        parser.error("Source decisions require --proof, --evidence and --r3-manifest; --format-only grants no approval")
    catalog, proof, foods = closure.verify_inputs(args.catalog, args.proof, args.evidence)
    r3 = json.loads(args.r3_manifest.read_text(encoding="utf-8"))
    if r3["inputHashes"]["catalog"] != closure.sha(args.catalog) or r3["inputHashes"]["evidence"] != closure.sha(args.evidence):
        raise ValueError("R3 input hashes differ; no naming decisions produced")
    registry = json.loads(args.registry.read_text(encoding="utf-8"))
    decisions = build_decisions(catalog, foods, r3, registry)
    args.output.mkdir(parents=True, exist_ok=True)
    partial = args.output / "catalog-format-dry-run.csv.partial"
    dry = stream_dry_run(args.catalog, partial)
    partial.replace(args.output / "catalog-format-dry-run.csv")
    summary = {"status": "R4_NAMING_DECISIONS_AND_RULES_READY_NOT_APPLIED", "snapshotTime": proof["time"],
               "resolvedNamingRows": len(decisions), "localizedNamePlans": len(decisions) * 2,
               "preparationChanges": sum(d["before"]["preparation_state"] != d["changes"]["preparation_state"] for d in decisions),
               "sourceHeatStateUnspecified": sum(d["changes"]["preparation_state"] == "UNSPECIFIED" for d in decisions),
               "servingReviewRows": sum(d["servingReviewRequired"] for d in decisions), "catalogFormatDryRun": dry,
               "databaseWrites": 0, "applyAllowed": False}
    report = {"ruleset": registry["version"], "summary": summary,
              "inputHashes": {name: closure.sha(getattr(args, name)) for name in ("catalog", "proof", "evidence", "r3_manifest", "registry")},
              "releaseRequirements": "R8 snapshot/rehearsal/approval; combine the exact R3 classification map, obtain localization/alias/serving before-images, verify identity compatibility and clear serving review. No bulk nutrition import or silent rekey.",
              "decisions": decisions}
    report_path = args.output / "naming-manifest.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    with (args.output / "16-name-decisions.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=["id", "source_key", "old_name", "new_en", "new_tr", "old_state", "new_state", "state_rationale", "serving_review", "apply_allowed"])
        writer.writeheader()
        for d in decisions:
            writer.writerow({"id": d["id"], "source_key": d["sourceKey"], "old_name": d["before"]["name"],
                "new_en": d["localizations"]["EN"]["displayName"], "new_tr": d["localizations"]["TR"]["displayName"],
                "old_state": d["before"]["preparation_state"], "new_state": d["changes"]["preparation_state"],
                "state_rationale": d["stateRationale"], "serving_review": d["servingReviewRequired"], "apply_allowed": False})
    (args.output / "summary.json").write_text(json.dumps({**summary, "manifestSha256": closure.sha(report_path)}, indent=2), encoding="utf-8")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()

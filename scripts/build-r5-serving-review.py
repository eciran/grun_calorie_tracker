"""Source-pinned portion decisions and exhaustive snapshot triage, never live SQL.

Native volume, mass/volume bridges, and label-basis corrections are different
operations. Core matches nominate a review; they do not authorize changing the
basis of every nutrient. Unknown portions/density remain unknown.
"""
import argparse
import csv
import hashlib
import importlib.util
import json
import math
import re
from collections import Counter
from contextlib import ExitStack
from datetime import datetime
from pathlib import Path

spec = importlib.util.spec_from_file_location("closure", Path(__file__).with_name("close-r3-usda-review.py"))
closure = importlib.util.module_from_spec(spec)
spec.loader.exec_module(closure)
LIQUID = {"en:beverages", "en:non-alcoholic-beverages", "en:alcoholic-beverages", "en:milks",
          "en:plant-based-milks", "en:waters", "en:juices-and-nectars", "en:fruit-juices",
          "en:soft-drinks", "en:carbonated-drinks", "en:energy-drinks", "en:sports-drinks",
          "en:milk-drinks", "en:dairy-drinks", "en:fermented-drinks", "en:fermented-milk-drinks",
          "en:plant-based-beverages", "en:coffee-drinks", "en:tea-based-beverages", "en:iced-teas",
          "en:syrups", "en:simple-syrups", "tr:içecek", "tr:gazlı-içecek"}
CONFLICT = {"en:powders", "en:drink-powders", "en:milk-powders", "en:plant-based-drink-powders",
            "en:concentrates", "en:ice-creams", "en:frozen-desserts", "en:pickles"}


def fingerprint(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()).hexdigest()


def positive(value):
    return isinstance(value, (float, int)) and not isinstance(value, bool) and math.isfinite(value) and value > 0


def number(value):
    if isinstance(value, bool):
        return None
    try:
        result = float(value)
        return result if math.isfinite(result) and result >= 0 else None
    except (ValueError, TypeError):
        return None


def decode_row(row):
    result = dict(row)
    for field in ("categories", "options", "option_localizations", "evidence"):
        result[field] = json.loads(row[field])
        if not isinstance(result[field], list):
            raise ValueError("Missing structured snapshot field " + field)
    return result


def option_usable(row, option):
    native = "ml_volume" if row["nutrition_reference_unit"] == "PER_100ML" else "gram_weight"
    return (option.get("quality_status") == "VERIFIED" and option.get("user_visible") is True
            and positive(option.get("quantity")) and positive(option.get(native))
            and all(option.get(k) is None or positive(option[k]) for k in ("gram_weight", "ml_volume")))


def valid_gtin(code):
    if not re.fullmatch(r"(?:[0-9]{8}|[0-9]{12}|[0-9]{13}|[0-9]{14})", code or ""):
        return False
    return (10 - sum(int(n) * (3 if i % 2 == 0 else 1) for i, n in enumerate(reversed(code[:-1]))) % 10) % 10 == int(code[-1])


def off_candidate(row, path, snapshot_time):
    """Cached provider response is a review nomination, not all-nutrient approval."""
    code = row.get("normalized_barcode") or row.get("barcode")
    if row.get("data_source") != "OPEN_FOOD_FACTS" or not valid_gtin(code):
        return "NO_EXACT_OFF_IDENTITY", None
    if path is None or not path.is_file():
        return "OFF_SOURCE_REQUIRED", None
    evidence = json.loads(path.read_text(encoding="utf-8-sig"))
    product = evidence.get("product", {})
    if evidence.get("status") != "FETCHED" or str(evidence.get("barcode")) != code or str(product.get("code")) != code:
        return "OFF_IDENTITY_CONFLICT", None
    try:
        age = (datetime.fromisoformat(snapshot_time.replace("Z", "+00:00"))
               - datetime.fromisoformat(evidence["checkedAt"].replace("Z", "+00:00"))).total_seconds()
    except (KeyError, ValueError, TypeError):
        return "OFF_TIME_UNPROVEN", None
    if age < 0 or age > 30 * 86400:
        return "OFF_SOURCE_STALE", None
    tags = set(product.get("categories_tags") or [])
    if tags & CONFLICT or not tags & LIQUID:
        return "OFF_FORM_UNPROVEN", None
    if str(product.get("nutrition_data_per", "")).lower().strip() != "100ml":
        return "OFF_VOLUME_BASIS_REQUIRED", None
    nutrients = product.get("nutriments", {})
    for field, source, unit, tolerance in (("calories", "energy-kcal", "kcal", 0.51),
            ("protein", "proteins", "g", 0.051), ("fat", "fat", "g", 0.051), ("carbs", "carbohydrates", "g", 0.051)):
        actual, expected = number(row.get(field)), number(nutrients.get(source + "_100g"))
        if actual is None or expected is None or str(nutrients.get(source + "_unit", "")).lower() != unit:
            return "OFF_CORE_OR_UNITS_INCOMPLETE", None
        if abs(actual - expected) > tolerance:
            return "OFF_CORE_MISMATCH", None
    return "EXTERNAL_NATIVE_VOLUME_CANDIDATE", {"file": str(path), "sha256": closure.sha(path),
        "checkedAt": evidence["checkedAt"], "code": code, "sourceBasis": "100ml",
        "allNutrientUnitCorrectionApproved": False, "applyAllowed": False}


def triage(row, evidence_dir=None, snapshot_time=None):
    tags = {str(t).strip().lower() for t in row["categories"]}
    usable = [o for o in row["options"] if option_usable(row, o)]
    findings = []
    for option in row["options"]:
        if not positive(option.get("quantity")):
            findings.append("INVALID_SERVING_QUANTITY")
        if any(option.get(k) is not None and not positive(option[k]) for k in ("gram_weight", "ml_volume")):
            findings.append("INVALID_SERVING_DIMENSION")
        if option.get("user_visible") is True and option.get("quality_status") == "VERIFIED" and not option_usable(row, option):
            findings.append("VISIBLE_VERIFIED_OPTION_NOT_CALCULABLE")
    if sum(o.get("is_default") is True for o in row["options"]) > 1:
        findings.append("MULTIPLE_DEFAULTS")
    if not usable:
        findings.append("NO_CALCULABLE_NAMED_OPTION")
    volume_gap = row["nutrition_reference_unit"] == "PER_100G" and any(
        o.get("quality_status") == "VERIFIED" and o.get("user_visible") is True
        and positive(o.get("ml_volume")) and not positive(o.get("gram_weight")) for o in row["options"])
    liquid = bool(tags & LIQUID) or row["nutrition_reference_unit"] == "PER_100ML" or volume_gap
    external = None
    if row["nutrition_reference_unit"] not in {"PER_100G", "PER_100ML"}:
        queue = "FORM_OR_IDENTITY_REVIEW"
        reason = "REFERENCE_BASIS_REQUIRED"
    elif tags & CONFLICT:
        queue = "FORM_OR_IDENTITY_REVIEW"
        reason = "CATEGORY_FORM_CONFLICT_NO_UNIT_INFERENCE"
    elif liquid:
        if row["nutrition_reference_unit"] == "PER_100ML":
            queue = "NATIVE_VOLUME_REVIEW"
            reason = "ML_ALREADY_NATIVE_SOURCE_CERTIFICATION_SEPARATE"
        else:
            code = row.get("normalized_barcode") or row.get("barcode") or ""
            path = evidence_dir / ("barcode-" + code + ".json") if evidence_dir and valid_gtin(code) else None
            reason, external = off_candidate(row, path, snapshot_time)
            if volume_gap and not external:
                reason = "EXISTING_VOLUME_OPTION_REQUIRES_PROVIDER_MASS_BRIDGE"
            queue = "NATIVE_VOLUME_REVIEW" if external else "MASS_VOLUME_SOURCE_REQUIRED"
        findings.append("LIQUID_CATEGORY_IS_NOT_DENSITY_PROOF")
    else:
        queue = "NAMED_SERVING_SOURCE_REQUIRED" if not usable else "EXISTING_MASS_SERVING_REVIEW"
        reason = "NO_PIECE_WEIGHT_OR_LIQUID_ASSUMPTION"
    return {"id": row["id"], "source_key": row["source_key"], "name": row["name"], "queue": queue,
            "reason": reason, "native_unit": row["nutrition_reference_unit"], "usable_options": len(usable),
            "findings": "|".join(sorted(set(findings))), "beforeFingerprint": fingerprint(row),
            "externalProof": external, "applyAllowed": False, "approval_status": "REVIEW_ONLY"}


def six_decisions(rows, foods, r4):
    expected = [d for d in r4["decisions"] if d["servingReviewRequired"]]
    if len(expected) != 6 or len({d["sourceKey"] for d in expected}) != 6:
        raise ValueError("Exactly six unique R4 serving gates are required")
    by_id = {r["id"]: r for r in rows}
    result = []
    for decision in expected:
        row = by_id.get(decision["id"])
        if row is None or row["source_key"] != decision["sourceKey"] or row["data_source"] != "USDA_FOODDATA":
            raise ValueError("Missing or changed priority product identity")
        food = foods[closure.review.fdc_id(row["source_key"])]
        if closure.review.fingerprint(food) != decision["sourceFingerprint"]:
            raise ValueError("R4 source body drift")
        if row["nutrition_reference_unit"] != "PER_100G":
            raise ValueError("USDA source portion requires its original mass basis")
        for field, value in closure.source_core(food).items():
            current = number(row.get(field))
            if current is None or value is None or abs(current - value) > (0.51 if field == "calories" else 0.051):
                raise ValueError("Current source composition drift; portion approval held")
        portions, seen = [], set()
        for p in food.get("foodPortions", []):
            if not positive(p.get("amount")) or not positive(p.get("gramWeight")) or p.get("id") in seen:
                raise ValueError("Invalid or duplicate authoritative source portion")
            seen.add(p.get("id"))
            modifier = p.get("modifier", "")
            if modifier not in {"cup", "oz"} or p.get("id") is None:
                raise ValueError("Source measure needs an explicit reviewed label mapping")
            unit = "CUP" if modifier == "cup" else "SERVING"
            en = f"{p['amount']:g} US measuring cup ({p['gramWeight']:g} g)" if modifier == "cup" else f"{p['amount']:g} oz ({p['gramWeight']:g} g)"
            tr = f"{p['amount']:g} ABD ölçü kabı ({p['gramWeight']:g} g)" if modifier == "cup" else en
            portions.append({"sourcePortionId": p["id"], "sourcePortionFingerprint": fingerprint(p), "unitType": unit,
                "quantity": p["amount"], "gramWeight": p["gramWeight"], "mlVolume": None,
                "labels": {"EN": en, "TR": tr}, "sourceMeasure": modifier,
                "defaultOption": False, "userVisible": False, "qualityStatus": "NEEDS_REVIEW"})
        if not portions:
            raise ValueError("No authoritative portions for priority product")
        checks = []
        for option in row["options"]:
            matches = [p for p in portions if positive(option.get("gram_weight"))
                       and abs(option["gram_weight"] - p["gramWeight"]) < 0.001
                       and positive(option.get("quantity")) and abs(option["quantity"] - p["quantity"]) < 0.001
                       and option.get("ml_volume") is None and option.get("unit_type") == p["unitType"]]
            # Numeric equivalence alone is not a semantic/source certification.
            checks.append({"optionId": option["id"], "decision": "NUMERIC_MATCH_LABEL_SOURCE_REVIEW" if matches else "REQUIRES_SOURCE_OR_DISABLE_AT_R8",
                           "sourceMatches": [p["sourcePortionId"] for p in matches], "applyAllowed": False})
        result.append({"id": row["id"], "sourceKey": row["source_key"], "before": row,
            "beforeFingerprint": fingerprint(row), "sourceFingerprint": decision["sourceFingerprint"],
            "targetPreparation": decision["changes"]["preparation_state"], "names": decision["localizations"],
            "sourcePortions": portions, "existingOptionChecks": checks,
            "legacyServingGrams": row["serving_size_grams"],
            "legacyDecision": "LEGACY_MASS_IS_NOT_PROOF_OF_A_NAMED_PORTION",
            "publicationGate": "REVIEW_EXISTING_LABELS_AND_STALE_OPTIONS_COMBINED_WITH_R3_R4_AT_R8",
            "nutritionWriteAllowed": False, "basisChangeAllowed": False, "applyAllowed": False})
    return result


def direct_source_pair_plans(row, food):
    if (not food or row["data_source"] != "USDA_FOODDATA" or row["nutrition_reference_unit"] != "PER_100G"
            or closure.review.fdc_id(row["source_key"]) != str(food.get("fdcId"))):
        return []
    measures = [p for p in food.get("foodPortions", []) if p.get("id") is not None
        and (p.get("modifier", "").strip().lower() == "ml" or p.get("measureUnit", {}).get("name", "").lower() in {"ml", "milliliter"})
        and positive(p.get("amount")) and positive(p.get("gramWeight"))]
    if len(measures) != 1:
        return []
    core = closure.source_core(food)
    if not all(number(row.get(k)) is not None and v is not None and
               abs(number(row[k]) - v) <= (0.51 if k == "calories" else 0.051) for k, v in core.items()):
        return []
    p, plans = measures[0], []
    for option in row["options"]:
        if (option.get("gram_weight") is None and positive(option.get("ml_volume")) and positive(option.get("quantity"))
                and option.get("quality_status") == "VERIFIED" and option.get("user_visible") is True):
            grams = option["ml_volume"] * p["gramWeight"] / p["amount"]
            if positive(grams):
                plans.append({"optionId": option["id"], "gramWeight": grams, "mlVolume": option["ml_volume"],
                    "sourcePortionId": p["id"], "sourcePortionFingerprint": fingerprint(p),
                    "method": "EXACT_PRODUCT_SOURCE_ML_MASS_PAIR_SCALED", "applyAllowed": False})
    return plans


def build(args):
    _, _, foods = closure.verify_inputs(args.source_catalog, args.source_proof, args.evidence)
    r4 = json.loads(args.r4_manifest.read_text(encoding="utf-8"))
    if r4["inputHashes"]["evidence"] != closure.sha(args.evidence) or r4["inputHashes"]["catalog"] != closure.sha(args.source_catalog):
        raise ValueError("R4 evidence ancestry mismatch")
    proof = json.loads(args.proof.read_text(encoding="utf-8"))
    if (proof.get("scope") != "r5-servings" or proof.get("origin") != "AWS_READ_ONLY_SNAPSHOT"
            or proof.get("transactionReadOnly") is not True or proof.get("exitCode") != 0
            or proof.get("sha256") != closure.sha(args.catalog)):
        raise ValueError("Complete read-only R5 proof required")
    args.output.mkdir(parents=True, exist_ok=True)
    columns = ["id", "source_key", "name", "queue", "reason", "native_unit", "usable_options", "findings", "beforeFingerprint", "applyAllowed", "approval_status"]
    counts, findings, seen, priority, external, structural = Counter(), Counter(), set(), [], [], []
    priority_ids = {d["id"] for d in r4["decisions"] if d["servingReviewRequired"]}
    partial = args.output / "all-queues.csv.partial"
    queue_paths = {}
    with args.catalog.open(encoding="utf-8-sig", newline="") as stream, partial.open("w", encoding="utf-8-sig", newline="") as out, ExitStack() as stack:
        writer = csv.DictWriter(out, fieldnames=columns)
        writer.writeheader()
        for raw in csv.DictReader(stream):
            row = decode_row(raw)
            if row["id"] in seen:
                raise ValueError("Duplicate snapshot id")
            seen.add(row["id"])
            item = triage(row, args.off_evidence, proof["time"])
            export = {k: item[k] for k in columns}
            # Human review CSVs are not import files or spreadsheet formulas.
            if export["name"].lstrip().startswith(("=", "+", "-", "@")):
                export["name"] = "'" + export["name"]
            writer.writerow(export)
            queue = item["queue"]
            if queue not in queue_paths:
                path = args.output / (queue.lower() + ".csv.partial")
                handle = stack.enter_context(path.open("w", encoding="utf-8-sig", newline=""))
                queue_writer = csv.DictWriter(handle, fieldnames=columns)
                queue_writer.writeheader()
                queue_paths[queue] = (path, queue_writer)
            queue_paths[queue][1].writerow(export)
            counts[item["queue"]] += 1
            findings.update(filter(None, item["findings"].split("|")))
            if item["externalProof"]:
                external.append({**item, "before": row})
            if row["id"] in priority_ids:
                priority.append(row)
            if "VISIBLE_VERIFIED_OPTION_NOT_CALCULABLE" in item["findings"]:
                source_id = closure.review.fdc_id(row["source_key"])
                food = foods.get(source_id) if row["data_source"] == "USDA_FOODDATA" else None
                plans = direct_source_pair_plans(row, food)
                structural.append({"before": row, "beforeFingerprint": fingerprint(row),
                    "sourceFingerprint": closure.review.fingerprint(food) if food else None,
                    "sourcePairPlans": plans, "decision": "SOURCE_PAIR_READY_FOR_R8" if plans else "NO_DENSITY_INFERENCE_SOURCE_REQUIRED",
                    "nutritionWriteAllowed": False, "basisChangeAllowed": False, "applyAllowed": False})
    if len(seen) != proof.get("rows"):
        raise ValueError("Snapshot count mismatch; report not completed")
    decisions = six_decisions(priority, foods, r4)
    partial.replace(args.output / "all-queues.csv")
    for path, _ in queue_paths.values():
        path.replace(path.with_suffix(""))
    summary = {"version": "R5_SERVING_REVIEW_V1", "snapshotTime": proof["time"], "catalogMetrics": proof.get("metrics"),
               "triagedRows": len(seen), "queues": dict(counts), "findings": dict(findings),
               "priorityProductsSourceVerified": len(decisions), "authoritativePortions": sum(len(d["sourcePortions"]) for d in decisions),
               "externalNativeVolumeCandidates": len(external), "databaseWrites": 0, "applyAllowed": False,
               "uncalculableVisibleVerifiedRows": len(structural), "directSourceBridgeRows": sum(bool(s["sourcePairPlans"]) for s in structural),
               "status": "TRIAGE_COMPLETE_SOURCE_PORTIONS_VERIFIED_REVIEW_AND_R8_GATES_OPEN"}
    manifest = {"summary": summary, "inputHashes": {name: closure.sha(getattr(args, name)) for name in
               ("catalog", "proof", "source_catalog", "source_proof", "evidence", "r4_manifest")},
                "producerHashes": {"review": closure.sha(Path(__file__)),
                    "snapshotCollector": closure.sha(Path(__file__).with_name("collect-r3-source-snapshot.py")),
                    "snapshotSql": closure.sha(Path(__file__).with_name("sql") / "export-r5-serving-review.sql")},
                "queueHashes": {queue: closure.sha(path.with_suffix("")) for queue, (path, _) in queue_paths.items()},
                "priorityDecisions": decisions, "externalVolumeCandidates": external, "structuralServingDecisions": structural,
                "releaseGates": ["Full nutrient/basis and exact provider identity evidence for any basis correction",
                    "Product-specific mass/volume pair evidence for real conversion; never names or generic density",
                    "Every option/localization/default/legacy before-image and current hash, operation-owned inserted IDs",
                    "Fresh locked preflight, approved exact count, idempotent apply and conflict-aware rollback rehearsal",
                    "R3/R4 identity and preparation compatibility; old diary snapshots never rewritten",
                    "Source-required queue is not approval or a promise of complete named serving coverage"]}
    (args.output / "serving-manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    (args.output / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    for name in ("catalog", "proof", "source-catalog", "source-proof", "evidence", "r4-manifest", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--off-evidence", type=Path, help="Frozen exact-barcode OFF responses; never a source of density guesses")
    build(parser.parse_args())

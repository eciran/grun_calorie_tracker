"""Source/package review of complete R6 groups. Never selects a survivor or writes DB."""
import argparse
import csv
import importlib.util
import json
import re
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

spec = importlib.util.spec_from_file_location("review", Path(__file__).with_name("review-r6-catalog-audit.py"))
review = importlib.util.module_from_spec(spec)
spec.loader.exec_module(review)
spec = importlib.util.spec_from_file_location("fetch", Path(__file__).with_name("fetch-r5-off-evidence.py"))
fetch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fetch)
gate, source = review.r5, review.r5.source


def package_measure(body):
    structured = source.measure(body.get("product_quantity"), body.get("product_quantity_unit"))
    text = body.get("quantity") or ""
    if re.search(r"[1-9][0-9]*[.,][0-9]{3}\s*(?:g|kg|ml|cl|l)\b", text, re.I):
        return None, "PACKAGE_DECIMAL_OR_THOUSANDS_AMBIGUOUS"
    if re.search(r"\d\s*[x×]\s*\d", text, re.I):
        return None, "PACKAGE_MULTIPACK_DETAIL_REVIEW"
    textual = source.text_measure(text)
    if structured and textual and structured != textual:
        return None, "SOURCE_PACKAGE_MEASURES_CONFLICT"
    return structured or textual, None


def member_review(member, receipt, now):
    food, issues = member["food"], []
    code = food.get("normalized_barcode") or food.get("barcode")
    result = {"id": member["id"], "barcode": code, "issues": issues, "applyAllowed": False}
    if (food.get("source_key") != "barcode:" + (code or "") or not receipt or
            not fetch.cached_valid(receipt, code, now)):
        return {**result, "issues": ["COMPLETE_EXACT_SOURCE_RECEIPT_REQUIRED"]}
    try:
        age = (now - datetime.fromisoformat(receipt["checkedAt"].replace("Z", "+00:00"))).total_seconds()
        if not 0 <= age <= 86400:
            return {**result, "issues": ["SOURCE_FRESHNESS_RECHECK_REQUIRED"]}
        body = receipt["product"]
        view, error = source.off_v35_view(body)
        if error:
            return {**result, "issues": [error]}
        basis = view["basis"] if view else body.get("nutrition_data_per")
        unit = {"100g": "PER_100G", "100ml": "PER_100ML"}.get(basis)
        if unit is None:
            issues.append("SOURCE_REFERENCE_LABEL_REQUIRED")
        elif unit != food.get("nutrition_reference_unit"):
            issues.append("SOURCE_CURRENT_REFERENCE_UNIT_DIFFERS_R5")
        if not view and basis == "100g" and set(body.get("categories_tags", [])) & source.r5.LIQUID:
            issues.append("LIQUID_GENERIC_100G_LABEL_REVIEW_R5")
        if body.get("no_nutrition_data") == "on" or body.get("nutrition_data") in {"off", False}:
            issues.append("SOURCE_RAW_NUTRITION_UNPROVEN")
        values = gate.all_off_fields(body)
        comparison = gate.comparison(food, values, "OPEN_FOOD_FACTS")
        if any(v["state"] not in {"MATCH", "UNKNOWN_CURRENT_RETAINED"} for v in comparison.values()):
            issues.append("CURRENT_SOURCE_NUTRIENT_COMPARISON_REQUIRED")
        if any(values[k] is None for k in ("calories", "protein", "fat", "carbs")):
            issues.append("SOURCE_CORE_INCOMPLETE")
        if (values["sugar"] is not None and values["carbs"] is not None and values["sugar"] > values["carbs"] + .11
                or values["saturated_fat"] is not None and values["fat"] is not None and values["saturated_fat"] > values["fat"] + .11):
            issues.append("SOURCE_NUTRIENT_RELATIONSHIP_CONFLICT")
        package, package_error = package_measure(body)
        if package_error:
            issues.append(package_error)
        if package and package[0] == "ml" and unit == "PER_100G":
            issues.append("VOLUME_PACK_MASS_LABEL_OR_BRIDGE_REVIEW_R5")
        result.update(sourceName=body.get("product_name"), sourceBrands=body.get("brands"), sourceCountries=body.get("countries_tags"),
                      sourcePackage=package, sourceQuantityText=body.get("quantity"), sourceReference=unit,
                      ingredientsText=body.get("ingredients_text"), sourceValues=values, comparison=comparison,
                      checkedAt=receipt["checkedAt"], sourceUrl=receipt["sourceUrl"], sourceFingerprint=review.fingerprint(body))
    except (TypeError, ValueError, KeyError) as exc:
        issues.append("SOURCE_FORMAT_REVIEW:" + type(exc).__name__)
    return result


def group_state(members):
    if any(m["issues"] for m in members):
        return "SOURCE_PROFILE_OR_UNIT_REVIEW_KEEP_SEPARATE"
    packages = [m.get("sourcePackage") for m in members]
    if all(packages) and len({review.fingerprint(p) for p in packages}) > 1:
        return "SOURCE_REPORTED_PACKAGE_VARIANTS_KEEP_SEPARATE"
    if not all(packages) or not all(m.get("ingredientsText") for m in members):
        return "PACKAGE_OR_COMPOSITION_PROOF_REQUIRED"
    signatures = {(m.get("sourceName"), m.get("ingredientsText"), review.fingerprint(m.get("sourceValues")),
                   m.get("sourceQuantityText")) for m in members}
    return "MATCHING_LABEL_PROFILE_ADMIN_REVIEW_REQUIRED" if len(signatures) == 1 else "LABEL_OR_COMPOSITION_VARIANT_REVIEW_KEEP_SEPARATE"


def variant_findings(members):
    """Keep variant evidence visible even when nutrient/basis gates block review."""
    findings = []
    for field, reason in (("sourcePackage", "SOURCE_PACKAGE_SIZE_OR_DIMENSION_DIFFERS"),
                          ("sourceName", "SOURCE_NAME_DECLARATION_DIFFERS"),
                          ("ingredientsText", "SOURCE_INGREDIENT_DECLARATION_DIFFERS"),
                          ("sourceBrands", "SOURCE_BRAND_DECLARATION_DIFFERS")):
        values = [m.get(field) for m in members]
        if all(values) and len({review.fingerprint(v) for v in values}) > 1:
            findings.append(reason)
    return findings


def verified_reviews(path, now):
    summary = json.loads(path.parent.joinpath("summary.json").read_text())
    manifest = json.loads(path.read_text(encoding="utf-8"))
    if summary["reviewSha256"] != source.r5.closure.sha(path):
        raise ValueError("Reviewed current snapshot manifest changed")
    try:
        measured = datetime.fromisoformat(manifest["measuredAt"].replace("Z", "+00:00"))
        age = (now - measured).total_seconds()
        valid = 0 <= age <= 86400 and manifest["measuredAt"] == summary["measuredAt"]
    except (ValueError, TypeError, KeyError):
        valid = False
    if not valid:
        raise ValueError("Fresh matching AWS review timestamp required")
    return manifest["rows"], summary


def run(args):
    now = datetime.now(timezone.utc)
    candidates, summary = verified_reviews(args.review, now)
    chosen, _ = review.select_sources(candidates, args.source_budget)
    results = []
    for candidate in chosen:
        members = []
        for member in candidate["members"]:
            code = member["food"].get("normalized_barcode") or member["food"].get("barcode")
            path = args.receipts / ("barcode-" + code + ".json")
            receipt = json.loads(path.read_text(encoding="utf-8")) if path.exists() else None
            entry = member_review(member, receipt, now)
            entry["receiptSha256"] = source.r5.closure.sha(path) if path.exists() else None
            members.append(entry)
        results.append({"brand": candidate["brand"], "name": candidate["name"], "ids": candidate["sampledIds"],
                        "state": group_state(members), "members": members, "applyAllowed": False,
                        "variantFindings": variant_findings(members),
                        "snapshotGroupFingerprint": candidate["currentGroupFingerprint"]})
    args.output.mkdir(parents=True, exist_ok=True)
    path = args.output / "source-review.json"
    path.write_text(json.dumps({"rows": results, "sourceReceiptIsNotManufacturerCertification": True}, ensure_ascii=False) + "\n", encoding="utf-8")
    with (args.output / "source-review-groups.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=["brand", "name", "ids", "state", "sourceNames", "sourcePackages", "issues", "applyAllowed"])
        writer.writeheader()
        for r in results:
            writer.writerow({"brand": review.csv_value(r["brand"]), "name": review.csv_value(r["name"]), "ids": json.dumps(r["ids"]), "state": r["state"],
                "sourceNames": json.dumps([m.get("sourceName") for m in r["members"]], ensure_ascii=False),
                "sourcePackages": json.dumps([m.get("sourcePackage") for m in r["members"]]),
                "issues": json.dumps([m["issues"] for m in r["members"]]), "applyAllowed": False})
    proof = {"groups": len(results), "products": sum(len(r["members"]) for r in results),
             "states": dict(Counter(r["state"] for r in results)),
             "overlappingMemberIssues": dict(Counter(h for r in results for m in r["members"] for h in m["issues"])),
             "comparedNutrientFieldsPerProduct": len(gate.FIELDS), "approvedGroups": 0, "databaseWrites": 0,
             "checkedAt": now.isoformat(), "awsMeasuredAt": json.loads(args.review.read_text(encoding="utf-8"))["measuredAt"],
             "currentReviewSha256": summary["reviewSha256"], "sourceReviewSha256": source.r5.closure.sha(path)}
    proof["sourceBudget"] = args.source_budget
    proof["overlappingVariantFindings"] = dict(Counter(f for r in results for f in r["variantFindings"]))
    (args.output / "summary.json").write_text(json.dumps(proof, indent=2) + "\n")
    print(json.dumps(proof, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    for name in ("review", "receipts", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--source-budget", type=int, default=20)
    run(parser.parse_args())

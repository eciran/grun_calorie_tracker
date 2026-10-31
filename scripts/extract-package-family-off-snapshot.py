"""Extract exact-GTIN OFF evidence for explicit package-family candidates; no DB writes."""
import csv
import gzip
import json
import re
import sys
import unicodedata
from collections import Counter
from pathlib import Path

ROOT = Path("outputs/package-family-audit-20261029")
CORE = {
    "calories": "energy-kcal_100g",
    "protein": "proteins_100g",
    "carbs": "carbohydrates_100g",
    "fat": "fat_100g",
}


def fold(value):
    text = unicodedata.normalize("NFKD", str(value or "").casefold().replace("ı", "i"))
    return re.sub(r"[^a-z0-9]+", " ", "".join(c for c in text if not unicodedata.combining(c))).strip()


def tokens(value):
    ignored = {
        "g", "gr", "gm", "gram", "grams", "kg", "kilogram", "kilograms",
        "ml", "milliliter", "milliliters", "millilitre", "millilitres",
        "l", "lt", "liter", "liters", "litre", "litres",
        "oz", "ounce", "ounces", "lb", "pound", "pounds", "x", "adet", "the", "with",
    }
    return {part for part in fold(value).split() if len(part) > 1 and part not in ignored and not part.isdigit()}


def package(value):
    matches = list(re.finditer(
        r"(?<!\w)(?:(\d+)\s*[xX]\s*)?(\d+(?:[.,]\d+)?)\s*"
        r"(kilograms?|kg|grams?|gm|gr|g|millilit(?:er|re)s?|ml|cl|lit(?:er|re)s?|lt|l|ounces?|oz|pounds?|lb)(?!\w)",
        str(value or ""), re.I,
    ))
    if not matches:
        return None
    match = matches[-1]
    count = float(match.group(1) or 1)
    amount = round(float(match.group(2).replace(",", ".")) * count, 6)
    unit = match.group(3).lower()
    if unit in {"kg", "kilogram", "kilograms"}:
        return amount * 1000, "g"
    if unit in {"l", "lt", "liter", "liters", "litre", "litres"}:
        return amount * 1000, "ml"
    if unit == "cl":
        return amount * 10, "ml"
    if unit in {"ml", "milliliter", "milliliters", "millilitre", "millilitres"}:
        return amount, "ml"
    if unit in {"oz", "ounce", "ounces"}:
        return round(amount * 28.349523125, 6), "g"
    if unit in {"lb", "pound", "pounds"}:
        return round(amount * 453.59237, 6), "g"
    return amount, "g"


def number(value):
    try:
        return float(value) if str(value or "").strip() else None
    except ValueError:
        return None


def close(left, right, tolerance=0.10):
    if left is None or right is None:
        return False
    return abs(left - right) / max(abs(left), abs(right), 1.0) <= tolerance


def family_nutrition_compatible(rows):
    if not rows:
        return False
    reference = rows[0]
    tolerances = {
        "calories": (5.0, 0.05),
        "protein": (1.0, 0.10),
        "carbs": (1.0, 0.10),
        "fat": (1.0, 0.10),
    }
    for row in rows[1:]:
        for field, (absolute, relative) in tolerances.items():
            left = number(reference.get(field))
            right = number(row.get(field))
            if left is None or right is None:
                return False
            if abs(left - right) > max(absolute, max(abs(left), abs(right), 1.0) * relative):
                return False
    return True


def extract(snapshot, root=ROOT):
    coverage = json.loads((root / "package-family-coverage.json").read_text(encoding="utf-8"))
    candidates = {g["group_id"]: g for g in json.loads((root / "candidates.json").read_text(encoding="utf-8"))}
    groups = [
        g for g in coverage["groups"]
        if g["tier"].startswith("EXPLICIT_PACKAGE") or g["tier"] in {
            "PACKAGE_METADATA_REQUIRED", "PACKAGE_AND_NUTRITION_EVIDENCE_REQUIRED"
        }
    ]
    targets = {barcode for group in groups for barcode in group["barcodes"] if barcode}
    evidence = {}
    with gzip.open(snapshot, "rt", encoding="utf-8", errors="replace", newline="") as stream:
        header = stream.readline().rstrip("\r\n").split("\t")
        wanted = [
            "code", "product_name", "brands", "quantity", "product_quantity",
            "last_modified_t", "countries_tags", *CORE.values(), "fiber_100g", "sugars_100g",
        ]
        indexes = {name: header.index(name) for name in wanted}
        max_index = max(indexes.values())
        for line in stream:
            code = line.split("\t", 1)[0]
            if code not in targets:
                continue
            values = line.rstrip("\r\n").split("\t")
            if len(values) <= max_index:
                continue
            evidence[code] = {name: values[index] for name, index in indexes.items()}
            if len(evidence) == len(targets):
                break

    decisions = []
    for summary_group in groups:
        source_group = candidates[summary_group["groupId"]]
        rows = []
        for row in source_group["products"]:
            source = evidence.get(row["barcode"])
            expected_package = package(row.get("display_name") or row.get("name"))
            if not source:
                rows.append({"id": int(row["id"]), "barcode": row["barcode"], "status": "OFF_NOT_FOUND"})
                continue
            observed_package = package(source.get("quantity"))
            if observed_package is None and number(source.get("product_quantity")) is not None and expected_package:
                observed_package = number(source["product_quantity"]), expected_package[1]
            name_overlap = len(tokens(row.get("display_name") or row.get("name")) & tokens(source["product_name"])) / max(
                1, min(len(tokens(row.get("display_name") or row.get("name"))), len(tokens(source["product_name"])))
            )
            brand_match = fold(row.get("brand")) in fold(source.get("brands")) or fold(source.get("brands")) in fold(row.get("brand"))
            package_match = bool(expected_package and observed_package and expected_package[1] == observed_package[1]
                                 and abs(expected_package[0] - observed_package[0]) <= max(1, expected_package[0] * .01))
            nutrition = {field: number(source[column]) for field, column in CORE.items()}
            nutrition_match = all(close(number(row[field]), nutrition[field]) for field in CORE)
            if not brand_match or name_overlap < .5:
                status = "OFF_IDENTITY_MISMATCH"
            elif expected_package is None and observed_package is None:
                status = "OFF_PACKAGE_METADATA_MISSING"
            elif expected_package is None and not all(value is not None for value in nutrition.values()):
                status = "OFF_SOURCE_PACKAGE_CONFIRMED_NUTRITION_INCOMPLETE"
            elif expected_package is None and not nutrition_match:
                status = "OFF_SOURCE_PACKAGE_CONFIRMED_NUTRITION_DIFF"
            elif expected_package is None:
                status = "OFF_SOURCE_PACKAGE_AND_NUTRITION_CONFIRMED"
            elif not package_match:
                status = "OFF_PACKAGE_MISMATCH"
            elif not all(value is not None for value in nutrition.values()):
                status = "OFF_NUTRITION_INCOMPLETE"
            elif not nutrition_match:
                status = "OFF_PACKAGE_CONFIRMED_NUTRITION_DIFF"
            else:
                status = "OFF_PACKAGE_AND_NUTRITION_CONFIRMED"
            rows.append({
                "id": int(row["id"]), "barcode": row["barcode"], "status": status,
                "catalogName": row.get("display_name") or row.get("name"), "sourceName": source["product_name"],
                "catalogPackage": expected_package, "sourcePackage": observed_package,
                "catalogNutrition": {field: number(row[field]) for field in CORE}, "sourceNutrition": nutrition,
                "sourceBrand": source["brands"], "sourceQuantity": source["quantity"],
                "sourceLastModified": source["last_modified_t"], "sourceCountries": source["countries_tags"],
            })
        statuses = {row["status"] for row in rows}
        normalized_packages = {
            tuple(row.get("catalogPackage") or row.get("sourcePackage"))
            for row in rows
            if row.get("catalogPackage") is not None or row.get("sourcePackage") is not None
        }
        package_units = {signature[1] for signature in normalized_packages}
        parsed_package_count = sum(
            row.get("catalogPackage") is not None or row.get("sourcePackage") is not None
            for row in rows
        )
        cross_member_nutrition_compatible = family_nutrition_compatible(source_group["products"])
        if parsed_package_count < len(rows):
            decision = "OFF_MEMBER_PACKAGE_METADATA_REQUIRED"
        elif len(package_units) != 1:
            decision = "OFF_PACKAGE_UNIT_REVIEW"
        elif len(normalized_packages) != len(rows):
            decision = "OFF_SAME_NORMALIZED_PACKAGE_REVIEW"
        elif not cross_member_nutrition_compatible:
            decision = "OFF_CROSS_MEMBER_NUTRITION_REVIEW"
        elif statuses.issubset({
            "OFF_PACKAGE_AND_NUTRITION_CONFIRMED",
            "OFF_SOURCE_PACKAGE_AND_NUTRITION_CONFIRMED",
        }):
            decision = "OFF_SNAPSHOT_FAMILY_REVIEW_READY"
        elif statuses & {"OFF_PACKAGE_CONFIRMED_NUTRITION_DIFF", "OFF_SOURCE_PACKAGE_CONFIRMED_NUTRITION_DIFF"}:
            decision = "OFF_PACKAGE_CONFIRMED_NUTRITION_REVIEW"
        elif "OFF_IDENTITY_MISMATCH" in statuses:
            decision = "OFF_IDENTITY_REVIEW"
        elif "OFF_PACKAGE_MISMATCH" in statuses:
            decision = "OFF_PACKAGE_REVIEW"
        elif "OFF_NUTRITION_INCOMPLETE" in statuses:
            decision = "OFF_NUTRITION_EVIDENCE_REQUIRED"
        else:
            decision = "OFF_COVERAGE_INCOMPLETE"
        decisions.append({
            "groupId": summary_group["groupId"],
            "decision": decision,
            "crossMemberNutritionCompatible": cross_member_nutrition_compatible,
            "products": rows,
            "catalogWrites": 0,
        })

    summary = {
        "snapshot": str(snapshot), "targetGroups": len(groups), "targetGtins": len(targets),
        "matchedGtins": len(evidence), "missingGtins": len(targets - set(evidence)),
        "groupDecisionCounts": dict(Counter(group["decision"] for group in decisions)),
        "productStatusCounts": dict(Counter(row["status"] for group in decisions for row in group["products"])),
        "automaticApplicationApproved": 0, "catalogWrites": 0,
    }
    (root / "explicit-package-off-evidence.json").write_text(
        json.dumps({"summary": summary, "groups": decisions}, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (root / "explicit-package-off-evidence-summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(json.dumps(summary, ensure_ascii=False), flush=True)
    return summary


if __name__ == "__main__":
    extract(Path(sys.argv[1] if len(sys.argv) > 1 else "outputs/openfoodfacts-products.csv.gz"))

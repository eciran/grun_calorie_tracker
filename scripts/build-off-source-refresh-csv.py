"""Build a fail-closed SOURCE_REFRESH CSV from complete OFF evidence receipts.

The output intentionally contains no display, localization, alias, brand,
category or canonical identity columns. It is accepted only by the backend's
SOURCE_REFRESH mode and cannot create products.
"""
import argparse
import csv
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path

NUTRIENTS = {
    "energy_kcal_100g": "energy-kcal_100g",
    "proteins_100g": "proteins_100g",
    "fat_100g": "fat_100g",
    "carbohydrates_100g": "carbohydrates_100g",
    "fiber_100g": "fiber_100g",
    "sugars_100g": "sugars_100g",
    "sodium_100g": "sodium_100g",
    "potassium_100g": "potassium_100g",
    "cholesterol_100g": "cholesterol_100g",
    "calcium_100g": "calcium_100g",
    "iron_100g": "iron_100g",
    "magnesium_100g": "magnesium_100g",
    "zinc_100g": "zinc_100g",
    "vitamin_a_100g": "vitamin-a_100g",
    "vitamin_c_100g": "vitamin-c_100g",
    "vitamin_d_100g": "vitamin-d_100g",
    "vitamin_e_100g": "vitamin-e_100g",
    "vitamin_b12_100g": "vitamin-b12_100g",
    "saturated_fat_100g": "saturated-fat_100g",
    "trans_fat_100g": "trans-fat_100g",
}


def digest(value):
    encoded = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def load_queue(path):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        return {row["source_key"]: row["id"] for row in csv.DictReader(stream)}


def build(queue_path, evidence_dir, output_csv, report_path, now):
    queue = load_queue(queue_path)
    approved, held = [], []
    for path in sorted((evidence_dir / "items").glob("*.json")):
        receipt = json.loads(path.read_text(encoding="utf-8"))
        product = receipt.get("product") or {}
        barcode = str(receipt.get("barcode") or "")
        source_key = "barcode:" + barcode
        reasons = []
        if source_key not in queue:
            reasons.append("NOT_IN_PINNED_QUEUE")
        if receipt.get("status") != "FETCHED" or receipt.get("response", {}).get("product") != product:
            reasons.append("INCOMPLETE_SOURCE_RECEIPT")
        if receipt.get("responseFingerprint") != digest(receipt.get("response")):
            reasons.append("SOURCE_FINGERPRINT_MISMATCH")
        if str(product.get("code") or "") != barcode:
            reasons.append("SOURCE_IDENTITY_MISMATCH")
        try:
            checked = datetime.fromisoformat(receipt["checkedAt"].replace("Z", "+00:00"))
            if not 0 <= (now - checked).total_seconds() <= 30 * 86400:
                reasons.append("SOURCE_RECEIPT_EXPIRED")
        except (KeyError, TypeError, ValueError):
            reasons.append("INVALID_SOURCE_TIME")

        basis = product.get("nutrition_data_per")
        unit = str(product.get("serving_quantity_unit") or "").lower()
        quantity = product.get("serving_quantity")
        if basis not in ("100g", "100ml"):
            reasons.append("UNSUPPORTED_OR_MISSING_SOURCE_BASIS")
        if basis == "100ml" and unit != "ml":
            reasons.append("ML_BASIS_WITHOUT_ML_SOURCE_SERVING")
        if basis == "100g" and unit == "ml":
            reasons.append("MASS_BASIS_WITH_ML_SOURCE_SERVING")
        if basis == "100g" and str(product.get("product_quantity_unit") or "").lower() == "ml":
            reasons.append("MASS_BASIS_WITH_ML_PACKAGE_REQUIRES_REVIEW")
        if not isinstance(product.get("nutriments"), dict) or product["nutriments"].get("energy-kcal_100g") is None:
            reasons.append("MISSING_SOURCE_CALORIES")
        if reasons:
            held.append({"id": queue.get(source_key), "sourceKey": source_key, "reasons": sorted(set(reasons))})
            continue

        row = {
            "source_key": source_key,
            "code": barcode,
            "product_name": product.get("product_name") or source_key,
            "nutrition_data_per": basis,
            "serving_quantity": quantity if quantity is not None else "",
            "serving_quantity_unit": unit,
            "market_region": "TR",
        }
        nutrients = product["nutriments"]
        row.update({column: nutrients.get(source_field, "") for column, source_field in NUTRIENTS.items()})
        approved.append(row)

    fields = ["source_key", "code", "product_name", *NUTRIENTS.keys(), "nutrition_data_per",
              "serving_quantity", "serving_quantity_unit", "market_region"]
    output_csv.parent.mkdir(parents=True, exist_ok=True)
    with output_csv.open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        writer.writerows(approved)
    report = {
        "contract": "OFF_SOURCE_REFRESH_V1",
        "inputCount": len(queue),
        "approvedCount": len(approved),
        "heldCount": len(held),
        "protectedFields": ["name", "display_name", "short_display_name", "brand", "canonical_brand",
                            "categories", "aliases", "localizations", "canonical_food_key"],
        "approvedSourceKeys": [row["source_key"] for row in approved],
        "held": held,
        "databaseWrites": 0,
    }
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--queue", type=Path, required=True)
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(build(args.queue, args.evidence, args.output, args.report, datetime.now(timezone.utc)),
                     ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

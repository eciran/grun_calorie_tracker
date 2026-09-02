#!/usr/bin/env python3
"""Build a dry-run-only nutrition correction package from reviewed OFF evidence."""

import argparse
import csv
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path


DECISION = "PROMOTABLE_AFTER_LICENSE_REVIEW"


def clean_number(value):
    if value is None:
        return ""
    return format(float(value), ".6g")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output-dir", required=True)
    args = parser.parse_args()

    source = Path(args.input)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    document = json.loads(source.read_text(encoding="utf-8"))
    products = [row for row in document["products"] if row.get("decision") == DECISION]

    seen = set()
    corrections = []
    evidence = []
    for row in products:
        barcode = str(row["barcode"])
        nutrition = row["nutrition"]
        if barcode in seen:
            raise ValueError(f"Duplicate barcode: {barcode}")
        seen.add(barcode)
        if not all(nutrition.get(field) is not None for field in ("calories", "protein", "fat", "carbs")):
            raise ValueError(f"Incomplete core nutrition: {barcode}")
        if not all(row.get(check) is True for check in ("exactBarcode", "completeCoreNutrition", "plausibleNutrition", "energyConsistent", "identityMatch", "packageMatch", "per100Basis")):
            raise ValueError(f"Failed evidence gate: {barcode}")

        # Optional zero values are omitted because an absent label value is often
        # represented as zero in community data. Blank CSV cells never overwrite.
        sodium = row.get("sodiumMg")
        correction = {
            "barcode": barcode,
            "calories": clean_number(nutrition["calories"]),
            "protein": clean_number(nutrition["protein"]),
            "fat": clean_number(nutrition["fat"]),
            "carbs": clean_number(nutrition["carbs"]),
            "fiber": clean_number(row.get("fiber")) if (row.get("fiber") or 0) > 0 else "",
            "sugar": clean_number(row.get("sugar")) if (row.get("sugar") or 0) > 0 else "",
            "sodium": clean_number(sodium) if (sodium or 0) > 0 else "",
            "review_note": "OFF exact GTIN evidence 2026-08-29; ODbL review required",
        }
        corrections.append(correction)
        evidence.append({
            "barcode": barcode,
            "catalogId": row["catalogId"],
            "catalogName": row["catalogName"],
            "sourceUrl": row["sourceUrl"],
            "sourceLicense": "ODbL-1.0",
            "checks": {key: row[key] for key in ("exactBarcode", "completeCoreNutrition", "plausibleNutrition", "energyConsistent", "identityMatch", "packageMatch", "per100Basis")},
            "correction": correction,
        })

    csv_path = output_dir / "nutrition-corrections-dry-run.csv"
    fields = ["barcode", "calories", "protein", "fat", "carbs", "fiber", "sugar", "sodium", "review_note"]
    with csv_path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        writer.writerows(corrections)

    evidence_path = output_dir / "evidence.json"
    evidence_path.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    digest = hashlib.sha256(csv_path.read_bytes()).hexdigest()
    manifest = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "classification": "DRY_RUN_ONLY_LICENSE_REVIEW_REQUIRED",
        "databaseApplied": False,
        "markVerified": False,
        "source": "Open Food Facts exact GTIN API",
        "sourceLicense": "ODbL-1.0",
        "rows": len(corrections),
        "uniqueBarcodes": len(seen),
        "csvSha256": digest,
        "safety": [
            "Product names are not included and cannot be changed by this package.",
            "Only exact-GTIN rows passing all identity and nutrition gates are included.",
            "Blank optional nutrient cells do not overwrite existing values.",
            "Run admin import with dryRun=true and markVerified=false first.",
        ],
    }
    (output_dir / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(manifest, ensure_ascii=False))


if __name__ == "__main__":
    main()

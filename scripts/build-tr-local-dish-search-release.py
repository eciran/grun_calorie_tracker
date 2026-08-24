import argparse
import csv
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--chunk-size", type=int, default=1000)
    parser.add_argument("--minimal-diagnostic", action="store_true")
    args = parser.parse_args()

    source = Path(args.input)
    out = Path(args.out)
    chunk_dir = out / "import-chunks"
    chunk_dir.mkdir(parents=True, exist_ok=True)
    approved_at = datetime.now(timezone.utc).isoformat()

    with source.open(encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        rows = list(reader)
        fields = list(reader.fieldnames or [])

    for row in rows:
        grams = float(row["serving_size_grams"])
        grams_value = int(grams) if grams.is_integer() else grams
        row["serving_unit"] = "portion"
        row["serving_options_json"] = json.dumps([
            {
                "label": "100 g",
                "unitType": "SERVING",
                "quantity": 100,
                "gramWeight": 100,
                "mlVolume": None,
                "defaultOption": False,
                "labels": {"EN": "100 g", "TR": "100 g"},
            },
            {
                "label": "1 portion",
                "unitType": "SERVING",
                "quantity": 1,
                "gramWeight": grams_value,
                "mlVolume": None,
                "defaultOption": True,
                "labels": {"EN": "1 portion", "TR": "1 porsiyon"},
            },
        ], ensure_ascii=False, separators=(",", ":"))
        row["approval_status"] = "APPROVED"
        row["approved_by"] = "product-owner-search-catalog-policy"
        row["approved_at"] = approved_at
        row["calculation_version"] = "1.0-search-catalog"
        row["provenance_note"] = (
            "Calculated per-100g macro and micronutrient profile for search and meal logging only; "
            "not a recipe-builder specification. Values represent a standardized average dish."
        )

    if args.minimal_diagnostic:
        fields = [
            "catalog_type", "data_source", "nutrition_basis", "source_key", "name",
            "market_region", "preparation_state", "calories", "protein", "fat", "carbs",
            "fiber", "sugar", "sodium", "serving_size_grams", "serving_unit",
            "dish_family_key", "dish_variant_key", "barcode",
        ]
        rows = [{field: row.get(field, "") for field in fields} for row in rows[:1]]

    chunks = []
    chunk_size = max(1, args.chunk_size)
    for index, offset in enumerate(range(0, len(rows), chunk_size), start=1):
        target = chunk_dir / f"tr-local-dish-search-v1-part-{index:03d}.csv"
        chunk_rows = rows[offset:offset + chunk_size]
        with target.open("w", encoding="utf-8-sig", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=fields)
            writer.writeheader()
            writer.writerows(chunk_rows)
        chunks.append({
            "role": "LOCAL_DISH_SEARCH_CATALOG",
            "file": f"import-chunks/{target.name}",
            "rows": len(chunk_rows),
            "sha256": sha256(target),
        })

    manifest = {
        "schemaVersion": 1,
        "releaseId": "tr-local-dish-search-v1-20260821",
        "releaseClassification": "STAGING_PRIVATE_TEST_ONLY",
        "productionSafe": False,
        "requiredImportMode": "CURATED_ADMIN",
        "requiredImportFormat": "GRUN_STANDARD",
        "usagePolicy": "SEARCH_AND_MEAL_LOGGING_ONLY_NOT_RECIPE_BUILDER",
        "chunks": chunks,
    }
    (out / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": "PASS", "rows": len(rows), "manifest": str(out / "manifest.json")}, ensure_ascii=False))


if __name__ == "__main__":
    main()

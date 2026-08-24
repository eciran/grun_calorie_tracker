#!/usr/bin/env python3
"""Build the uncapped strict UK/IE Open Food Facts import candidate set.

The script is deliberately file-only. It never calls the application API or
mutates a database. The output is deterministic for a given source snapshot.
"""

from __future__ import annotations

import argparse
import csv
import gzip
import hashlib
import json
import re
import sys
import time
import unicodedata
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path


MARKET_TERMS = {
    "united kingdom",
    "en:united-kingdom",
    "ireland",
    "en:ireland",
    "gb",
    "uk",
}

IMPORT_COLUMNS = (
    "catalog_type",
    "data_source",
    "nutrition_basis",
    "barcode",
    "source_key",
    "name",
    "brand",
    "calories",
    "protein",
    "fat",
    "carbs",
    "fiber",
    "sugar",
    "sodium",
    "serving_size_grams",
    "serving_unit",
    "market_region",
    "image_url",
    "external_image_url",
    "display_image_url",
    "allergens",
    "nutri_score",
)


def normalize_text(value: str | None) -> str:
    if not value:
        return ""
    value = value.lower().replace("\u0131", "i")
    return "".join(
        char
        for char in unicodedata.normalize("NFD", value)
        if unicodedata.category(char) != "Mn"
    )


def market_match(value: str) -> bool:
    normalized = normalize_text(value)
    if any(term in normalized for term in MARKET_TERMS if len(term) > 3):
        return True
    tokens = {
        token.strip()
        for token in re.split(r"[,;|]", normalized)
        if token.strip()
    }
    if tokens & MARKET_TERMS:
        return True
    words = set(re.findall(r"[a-z0-9:-]+", normalized))
    if words & {"gb", "uk"}:
        return True
    return False


def decimal(value: str | None) -> float | None:
    if not value:
        return None
    try:
        return float(value.replace(",", "."))
    except ValueError:
        return None


def valid_gtin(code: str) -> bool:
    if not re.fullmatch(r"(?:\d{8}|\d{12}|\d{13}|\d{14})", code or ""):
        return False
    total = 0
    weight = 3
    for digit in reversed(code[:-1]):
        total += int(digit) * weight
        weight = 1 if weight == 3 else 3
    return (10 - total % 10) % 10 == int(code[-1])


def plausible_nutrition(values: tuple[float | None, ...]) -> bool:
    calories, protein, fat, carbs = values
    if any(value is None for value in values):
        return False
    assert calories is not None and protein is not None and fat is not None and carbs is not None
    return (
        0 <= calories <= 1000
        and all(0 <= value <= 100 for value in (protein, fat, carbs))
        and protein + fat + carbs <= 110
    )


def first(row: dict[str, str], *names: str) -> str:
    for name in names:
        value = (row.get(name) or "").strip()
        if value:
            return value
    return ""


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest().upper()


def existing_barcodes(path: Path | None) -> set[str]:
    if path is None or not path.exists():
        return set()
    with path.open("r", encoding="utf-8-sig", newline="") as stream:
        return {
            (row.get("barcode") or "").strip()
            for row in csv.DictReader(stream)
            if (row.get("barcode") or "").strip()
        }


def serving_grams(row: dict[str, str]) -> str:
    quantity = first(row, "serving_quantity")
    unit = normalize_text(first(row, "serving_quantity_unit"))
    if decimal(quantity) is None:
        return ""
    if unit and unit not in {"g", "gram", "grams"}:
        return ""
    return quantity


def richness(item: dict[str, str]) -> tuple[int, ...]:
    return (
        int(bool(item["image_url"])),
        int(bool(item["serving_size_grams"])),
        int(bool(item["fiber"])),
        int(bool(item["sugar"])),
        int(bool(item["sodium"])),
        int(bool(item["allergens"])),
        len(item["name"]),
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", default="outputs/openfoodfacts-products.csv.gz")
    parser.add_argument(
        "--output-dir",
        default="outputs/product-catalog-aug08/uk-ie-off-latest",
    )
    parser.add_argument(
        "--existing-import",
        default="outputs/product-data-readiness/open-food-facts-uk-ie-branded-v1-gate-import.csv",
    )
    parser.add_argument("--max-rows", type=int, default=10_000_000)
    parser.add_argument("--progress-every", type=int, default=250_000)
    args = parser.parse_args()

    input_path = Path(args.input).resolve()
    output_dir = Path(args.output_dir).resolve()
    existing_path = Path(args.existing_import).resolve() if args.existing_import else None
    output_dir.mkdir(parents=True, exist_ok=True)
    import_path = output_dir / "uk-ie-off-strict-import.csv"
    report_path = output_dir / "uk-ie-off-capacity-report.json"

    if not input_path.is_file():
        raise FileNotFoundError(input_path)

    source_hash = sha256(input_path)
    previous = existing_barcodes(existing_path)
    started_at = datetime.now(timezone.utc)
    started = time.perf_counter()
    accepted: dict[str, dict[str, str]] = {}
    reject_counts: Counter[str] = Counter()
    rows_read = relevant_rows = duplicate_rows = malformed_rows = 0

    csv.field_size_limit(2_147_483_647)
    opener = gzip.open if input_path.suffix.lower() == ".gz" else open
    with opener(input_path, "rt", encoding="utf-8", errors="replace", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t", quotechar='"')
        for row in reader:
            if rows_read >= args.max_rows:
                break
            rows_read += 1
            if args.progress_every and rows_read % args.progress_every == 0:
                elapsed = max(time.perf_counter() - started, 0.001)
                print(
                    f"rows={rows_read} relevant={relevant_rows} strict={len(accepted)} "
                    f"rate={rows_read / elapsed:.0f}/s",
                    file=sys.stderr,
                    flush=True,
                )
            if None in row:
                malformed_rows += 1

            countries = " ".join(
                filter(
                    None,
                    (
                        first(row, "countries_tags"),
                        first(row, "countries_tags_en"),
                        first(row, "countries"),
                    ),
                )
            )
            if not market_match(countries):
                continue
            relevant_rows += 1

            barcode = first(row, "code", "barcode")
            name = first(row, "product_name_en", "product_name", "generic_name")
            brand = first(row, "brands", "brand")
            calories_text = first(row, "energy-kcal_100g", "energy_kcal_100g")
            protein_text = first(row, "proteins_100g", "protein_100g")
            fat_text = first(row, "fat_100g")
            carbs_text = first(row, "carbohydrates_100g", "carbs_100g")
            nutrition = (
                decimal(calories_text),
                decimal(protein_text),
                decimal(fat_text),
                decimal(carbs_text),
            )

            rejected = False
            for condition, code in (
                (not valid_gtin(barcode), "INVALID_GTIN"),
                (not name, "MISSING_NAME"),
                (not brand, "MISSING_BRAND"),
                (len(name) > 255, "NAME_TOO_LONG"),
                (len(brand) > 255, "BRAND_TOO_LONG"),
                (not plausible_nutrition(nutrition), "INCOMPLETE_OR_IMPLAUSIBLE_CORE_NUTRITION"),
            ):
                if condition:
                    reject_counts[code] += 1
                    rejected = True
            if rejected:
                continue

            image = first(row, "image_front_url", "image_url")
            serving = serving_grams(row)
            item = {
                "catalog_type": "BRANDED_PRODUCT",
                "data_source": "OPEN_FOOD_FACTS",
                "nutrition_basis": "SOURCE_REPORTED",
                "barcode": barcode,
                "source_key": f"barcode:{barcode}",
                "name": name,
                "brand": brand,
                "calories": calories_text,
                "protein": protein_text,
                "fat": fat_text,
                "carbs": carbs_text,
                "fiber": first(row, "fiber_100g"),
                "sugar": first(row, "sugars_100g", "sugar_100g"),
                "sodium": first(row, "sodium_100g"),
                "serving_size_grams": serving,
                "serving_unit": "GRAM" if serving else "",
                "market_region": "UK_IE",
                "image_url": image,
                "external_image_url": image,
                "display_image_url": "",
                "allergens": first(row, "allergens_tags", "allergens"),
                "nutri_score": first(row, "nutriscore_grade", "nutrition_grade_fr"),
            }
            current = accepted.get(barcode)
            if current is None:
                accepted[barcode] = item
            else:
                duplicate_rows += 1
                if richness(item) > richness(current):
                    accepted[barcode] = item

    ordered = [accepted[key] for key in sorted(accepted)]
    with import_path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=IMPORT_COLUMNS, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(ordered)

    barcodes = set(accepted)
    report = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "startedAt": started_at.isoformat(),
        "marketRegion": "UK_IE",
        "releaseClassification": "LICENSED_SOURCE_CANDIDATE_REQUIRES_RELEASE_REHEARSAL",
        "source": {
            "path": str(input_path),
            "sha256": source_hash,
            "bytes": input_path.stat().st_size,
            "license": "Open Database License (ODbL); content/image terms must also be preserved",
        },
        "analyzer": {"runtime": "python", "version": 1},
        "durationMs": int((time.perf_counter() - started) * 1000),
        "rowsRead": rows_read,
        "malformedRows": malformed_rows,
        "marketRelevantRows": relevant_rows,
        "strictUniqueRows": len(ordered),
        "duplicateAcceptedRows": duplicate_rows,
        "existingGateRows": len(previous),
        "existingGateOverlapRows": len(barcodes & previous),
        "netNewVsExistingGateRows": len(barcodes - previous),
        "rejectCounts": dict(reject_counts),
        "completeness": {
            "imageRows": sum(bool(item["image_url"]) for item in ordered),
            "servingRows": sum(bool(item["serving_size_grams"]) for item in ordered),
            "fiberRows": sum(bool(item["fiber"]) for item in ordered),
            "sugarRows": sum(bool(item["sugar"]) for item in ordered),
            "sodiumRows": sum(bool(item["sodium"]) for item in ordered),
        },
        "gates": {
            "pilot5000": len(ordered) >= 5000,
            "gate25000": len(ordered) >= 25000,
            "uncappedCapacityMeasured": rows_read < args.max_rows,
        },
        "artifacts": {
            "strictImport": str(import_path),
            "report": str(report_path),
        },
    }
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

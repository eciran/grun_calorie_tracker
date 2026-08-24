#!/usr/bin/env python3
"""Match the TR retailer nutrition queue to the frozen OFF bulk dump by exact GTIN.

The script is file-only and produces evidence candidates. It never updates the
retailer catalog or a database. Exact identity, source provenance and the OFF
license remain explicit so promotion can be reviewed separately.
"""

from __future__ import annotations

import argparse
from collections import Counter
import csv
import gzip
import hashlib
import json
import sys
import time
from datetime import datetime, timezone
from pathlib import Path


def decimal(value: str | None) -> float | None:
    if not value:
        return None
    try:
        return float(value.replace(",", "."))
    except ValueError:
        return None


def plausible(values: tuple[float | None, ...]) -> bool:
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


def richness(candidate: dict[str, object]) -> tuple[int, ...]:
    return (
        int(bool(candidate["lastModified"])),
        int(bool(candidate["offName"])),
        int(bool(candidate["offBrand"])),
        int(bool(candidate["imageUrl"])),
        int(bool(candidate["nutrition"]["fiber"])),
        int(bool(candidate["nutrition"]["sugar"])),
        int(bool(candidate["nutrition"]["sodium"])),
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--input",
        default="outputs/product-catalog-aug08/sources/openfoodfacts-20260802.csv.gz",
    )
    parser.add_argument(
        "--queue",
        default="outputs/TR_Products/nutrition-enrichment/queue.json",
    )
    parser.add_argument(
        "--current-rich-import",
        default="outputs/product-catalog-aug08/tr-retailer-test-v1/tr-retailer-test-rich-import.csv",
    )
    parser.add_argument(
        "--off-candidates",
        default="outputs/product-data-readiness/s9-tr-internet/tr-off-candidates.tsv",
    )
    parser.add_argument(
        "--output-dir",
        default="outputs/product-catalog-aug08/tr-off-nutrition-match-20260802",
    )
    parser.add_argument("--progress-every", type=int, default=500_000)
    args = parser.parse_args()

    input_path = Path(args.input).resolve()
    queue_path = Path(args.queue).resolve()
    current_rich_path = Path(args.current_rich_import).resolve()
    off_candidates_path = Path(args.off_candidates).resolve()
    output_dir = Path(args.output_dir).resolve()
    if not input_path.is_file():
        raise FileNotFoundError(input_path)
    if not queue_path.is_file():
        raise FileNotFoundError(queue_path)

    queue = json.loads(queue_path.read_text(encoding="utf-8"))
    products = queue.get("products", [])
    target_to_catalogs: dict[str, set[str]] = {}
    product_by_id: dict[str, dict[str, object]] = {}
    for product in products:
        catalog_id = str(product.get("catalogId") or "").strip()
        if not catalog_id:
            continue
        product_by_id[catalog_id] = product
        for barcode in product.get("barcodes") or []:
            barcode = str(barcode).strip()
            if barcode:
                target_to_catalogs.setdefault(barcode, set()).add(catalog_id)

    started = time.perf_counter()
    source_hash = sha256(input_path)
    rows_read = matched_source_rows = plausible_source_rows = 0
    best_by_barcode: dict[str, dict[str, object]] = {}
    csv.field_size_limit(2_147_483_647)
    opener = gzip.open if input_path.suffix.lower() == ".gz" else open
    with opener(input_path, "rt", encoding="utf-8", errors="replace", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t", quotechar='"')
        for row in reader:
            rows_read += 1
            if args.progress_every and rows_read % args.progress_every == 0:
                elapsed = max(time.perf_counter() - started, 0.001)
                print(
                    f"rows={rows_read} matched={matched_source_rows} plausible={plausible_source_rows} "
                    f"rate={rows_read / elapsed:.0f}/s",
                    file=sys.stderr,
                    flush=True,
                )
            barcode = first(row, "code", "barcode")
            if barcode not in target_to_catalogs:
                continue
            matched_source_rows += 1
            calories_text = first(row, "energy-kcal_100g", "energy_kcal_100g")
            protein_text = first(row, "proteins_100g", "protein_100g")
            fat_text = first(row, "fat_100g")
            carbs_text = first(row, "carbohydrates_100g", "carbs_100g")
            core = (
                decimal(calories_text),
                decimal(protein_text),
                decimal(fat_text),
                decimal(carbs_text),
            )
            if not plausible(core):
                continue
            plausible_source_rows += 1
            candidate: dict[str, object] = {
                "barcode": barcode,
                "offName": first(row, "product_name_tr", "product_name", "product_name_en"),
                "offBrand": first(row, "brands", "brand"),
                "nutrition": {
                    "calories": core[0],
                    "protein": core[1],
                    "fat": core[2],
                    "carbs": core[3],
                    "fiber": decimal(first(row, "fiber_100g")),
                    "sugar": decimal(first(row, "sugars_100g", "sugar_100g")),
                    "sodium": decimal(first(row, "sodium_100g")),
                    "salt": decimal(first(row, "salt_100g")),
                },
                "imageUrl": first(row, "image_front_url", "image_url"),
                "lastModified": first(row, "last_modified_datetime", "last_modified_t"),
                "sourceUrl": f"https://world.openfoodfacts.org/product/{barcode}",
                "sourceRow": rows_read,
            }
            previous = best_by_barcode.get(barcode)
            if previous is None or richness(candidate) > richness(previous):
                best_by_barcode[barcode] = candidate

    candidates: list[dict[str, object]] = []
    for catalog_id, product in product_by_id.items():
        matches = [best_by_barcode[barcode] for barcode in product.get("barcodes") or [] if barcode in best_by_barcode]
        if not matches:
            continue
        match = max(matches, key=richness)
        candidates.append(
            {
                "catalogId": catalog_id,
                "retailerName": product.get("name"),
                "retailerBrand": product.get("brand"),
                "packageAmount": product.get("packageAmount"),
                "packageUnit": product.get("packageUnit"),
                "matchedBarcode": match["barcode"],
                "identityMatch": "EXACT_GTIN",
                "promotionStatus": "OFF_ODBL_NUTRITION_CANDIDATE_REVIEW_REQUIRED",
                "off": match,
            }
        )
    candidates.sort(key=lambda item: (str(item["matchedBarcode"]), str(item["catalogId"])))
    barcode_counts = Counter(str(item["matchedBarcode"]) for item in candidates)
    for item in candidates:
        if barcode_counts[str(item["matchedBarcode"])] > 1:
            item["promotionStatus"] = "DUPLICATE_GTIN_REVIEW_REQUIRED"

    with current_rich_path.open("r", encoding="utf-8-sig", newline="") as stream:
        current_rich_barcodes = {

            str(row.get("barcode") or "").strip()
            for row in csv.DictReader(stream)
            if str(row.get("barcode") or "").strip()
        }
    with off_candidates_path.open("r", encoding="utf-8-sig", newline="") as stream:
        off_strict_barcodes = {
            str(row.get("barcode") or "").strip()
            for row in csv.DictReader(stream, delimiter="\t")
            if row.get("tier") == "STRICT"
        }
    candidate_barcodes = set(barcode_counts)
    current_private_union = current_rich_barcodes | off_strict_barcodes
    projected_private_union = current_private_union | candidate_barcodes

    output_dir.mkdir(parents=True, exist_ok=True)
    candidate_path = output_dir / "tr-retailer-off-nutrition-candidates.json"
    report_path = output_dir / "report.json"
    candidate_document = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "classification": "EVIDENCE_CANDIDATES_NOT_APPLIED",
        "source": {
            "id": "OPEN_FOOD_FACTS",
            "path": str(input_path),
            "sha256": source_hash,
            "license": "ODbL-1.0",
            "attributionRequired": True,
            "shareAlikeReviewRequired": True,
        },
        "matchingPolicy": "Exact GTIN only; complete plausible per-100 core nutrition required.",
        "products": candidates,
    }
    candidate_path.write_text(json.dumps(candidate_document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    report = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "status": "PASS",
        "queueRows": len(products),
        "queueUniqueBarcodes": len(target_to_catalogs),
        "sourceRowsRead": rows_read,
        "matchedSourceRows": matched_source_rows,
        "plausibleSourceRows": plausible_source_rows,
        "matchedQueueProducts": len(candidates),
        "uniqueMatchedBarcodes": len(candidate_barcodes),
        "duplicateGtinGroups": sum(1 for count in barcode_counts.values() if count > 1),
        "netNewVersusCurrentRetailerRich": len(candidate_barcodes - current_rich_barcodes),
        "projectedRetailerRichAfterReview": len(current_rich_barcodes | candidate_barcodes),
        "currentOffStrictPlusRetailerRichUnion": len(current_private_union),
        "netNewVersusOffStrictPlusRetailerRich": len(candidate_barcodes - current_private_union),
        "projectedPrivateUnionAfterReview": len(projected_private_union),
        "unmatchedQueueProducts": len(products) - len(candidates),
        "coveragePercent": round(100 * len(candidates) / max(len(products), 1), 2),
        "durationMs": int((time.perf_counter() - started) * 1000),
        "artifacts": {
            "candidates": str(candidate_path),
            "candidateSha256": sha256(candidate_path),
        },
    }
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

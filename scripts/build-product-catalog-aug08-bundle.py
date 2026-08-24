#!/usr/bin/env python3
"""Build the rights-cleared, maximum-capacity August 8 import bundle."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest().upper()


def valid_gtin(code: str) -> bool:
    if not re.fullmatch(r"(?:\d{8}|\d{12}|\d{13}|\d{14})", code or ""):
        return False
    total = 0
    weight = 3
    for digit in reversed(code[:-1]):
        total += int(digit) * weight
        weight = 1 if weight == 3 else 3
    return (10 - total % 10) % 10 == int(code[-1])


def number(value: str | None) -> float | None:
    if value is None or not value.strip():
        return None
    try:
        return float(value.replace(",", "."))
    except ValueError:
        return None


def plausible_core(row: dict[str, str]) -> bool:
    values = [number(row.get(key)) for key in ("calories", "protein", "fat", "carbs")]
    if any(value is None for value in values):
        return False
    calories, protein, fat, carbs = values
    assert calories is not None and protein is not None and fat is not None and carbs is not None
    return (
        0 <= calories <= 1000
        and all(0 <= value <= 100 for value in (protein, fat, carbs))
        and protein + fat + carbs <= 110
    )


def read_csv(path: Path) -> tuple[list[str], list[dict[str, str]]]:
    with path.open("r", encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        if not reader.fieldnames:
            raise ValueError(f"Missing CSV header: {path}")
        rows = list(reader)
        return list(reader.fieldnames), rows


def richness(row: dict[str, str]) -> tuple[int, ...]:
    return (
        int(bool((row.get("display_image_url") or row.get("external_image_url") or row.get("image_url") or "").strip())),
        int(bool((row.get("serving_size_grams") or "").strip())),
        int(bool((row.get("fiber") or "").strip())),
        int(bool((row.get("sugar") or "").strip())),
        int(bool((row.get("sodium") or "").strip())),
        len((row.get("name") or "").strip()),
    )


def strict_branded_rows(path: Path, market: str) -> tuple[list[str], list[dict[str, str]], dict[str, int]]:
    fields, source_rows = read_csv(path)
    selected: dict[str, dict[str, str]] = {}
    rejected: Counter[str] = Counter()
    duplicates = 0
    for row in source_rows:
        barcode = (row.get("barcode") or "").strip()
        reasons: list[str] = []
        if not valid_gtin(barcode):
            reasons.append("INVALID_GTIN")
        if not (row.get("name") or "").strip():
            reasons.append("MISSING_NAME")
        if not (row.get("brand") or "").strip():
            reasons.append("MISSING_BRAND")
        if len((row.get("name") or "").strip()) > 255:
            reasons.append("NAME_TOO_LONG")
        if len((row.get("brand") or "").strip()) > 255:
            reasons.append("BRAND_TOO_LONG")
        if not plausible_core(row):
            reasons.append("INCOMPLETE_OR_IMPLAUSIBLE_CORE_NUTRITION")
        if (row.get("market_region") or "").strip() != market:
            reasons.append("MARKET_MISMATCH")
        if reasons:
            rejected.update(reasons)
            continue
        current = selected.get(barcode)
        if current is None:
            selected[barcode] = row
        else:
            duplicates += 1
            if richness(row) > richness(current):
                selected[barcode] = row
    metrics = {
        "sourceRows": len(source_rows),
        "retainedRows": len(selected),
        "duplicateSourceRows": duplicates,
        **{f"rejected{key.title().replace('_', '')}Rows": value for key, value in sorted(rejected.items())},
    }
    return fields, [selected[key] for key in sorted(selected)], metrics


def generic_rows(path: Path) -> tuple[list[str], list[dict[str, str]], dict[str, int]]:
    fields, rows = read_csv(path)
    seen: set[str] = set()
    retained: list[dict[str, str]] = []
    rejected = 0
    for row in rows:
        key = (row.get("source_key") or "").strip()
        if not key or key in seen or not (row.get("name") or "").strip() or not plausible_core(row):
            rejected += 1
            continue
        seen.add(key)
        retained.append(row)
    retained.sort(key=lambda row: row.get("source_key") or "")
    return fields, retained, {"sourceRows": len(rows), "retainedRows": len(retained), "rejectedRows": rejected}


def write_chunks(
    output_dir: Path,
    prefix: str,
    fields: list[str],
    rows: list[dict[str, str]],
    chunk_size: int,
    market: str,
    role: str,
) -> list[dict[str, object]]:
    chunks: list[dict[str, object]] = []
    for offset in range(0, len(rows), chunk_size):
        sequence = offset // chunk_size + 1
        filename = f"{prefix}-part-{sequence:03d}.csv"
        path = output_dir / "import-chunks" / filename
        with path.open("w", encoding="utf-8", newline="") as stream:
            writer = csv.DictWriter(stream, fieldnames=fields, extrasaction="ignore")
            writer.writeheader()
            writer.writerows(rows[offset:offset + chunk_size])
        chunks.append({
            "market": market,
            "role": role,
            "sequence": sequence,
            "file": f"import-chunks/{filename}",
            "rows": min(chunk_size, len(rows) - offset),
            "bytes": path.stat().st_size,
            "sha256": sha256(path),
        })
    return chunks


def completeness(rows: list[dict[str, str]]) -> dict[str, int]:
    return {
        "rows": len(rows),
        "imageRows": sum(bool((row.get("display_image_url") or row.get("external_image_url") or row.get("image_url") or "").strip()) for row in rows),
        "servingRows": sum(bool((row.get("serving_size_grams") or "").strip()) for row in rows),
        "fiberRows": sum(bool((row.get("fiber") or "").strip()) for row in rows),
        "sugarRows": sum(bool((row.get("sugar") or "").strip()) for row in rows),
        "sodiumRows": sum(bool((row.get("sodium") or "").strip()) for row in rows),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--uk", default="outputs/product-catalog-aug08/uk-ie-off-20260619-capacity/uk-ie-off-strict-import.csv")
    parser.add_argument("--eu", default="outputs/product-data-readiness/open-food-facts-eu-branded-v1-gate-import.csv")
    parser.add_argument("--tr", default="outputs/product-data-readiness/open-food-facts-tr-branded-v1-gate-import.csv")
    parser.add_argument("--global-generic", default="outputs/product-data-readiness/production-release-v1/import-chunks/generic-food-approved-seed-v1-part-001.csv")
    parser.add_argument("--snapshot", default="outputs/product-catalog-aug08/sources/openfoodfacts-20260802.csv.gz")
    parser.add_argument("--retailer-overlay-manifest", default="outputs/product-catalog-aug08/tr-retailer-test-v1/manifest.json")
    parser.add_argument("--output-dir", default="outputs/product-catalog-aug08/licensed-default-20260802")
    parser.add_argument("--chunk-size", type=int, default=10_000)
    args = parser.parse_args()

    if args.chunk_size < 1:
        raise ValueError("chunk-size must be positive")
    output_dir = Path(args.output_dir).resolve()
    (output_dir / "import-chunks").mkdir(parents=True, exist_ok=True)

    sources: dict[str, Path] = {
        "UK_IE": Path(args.uk).resolve(),
        "EU": Path(args.eu).resolve(),
        "TR": Path(args.tr).resolve(),
        "GLOBAL": Path(args.global_generic).resolve(),
    }
    source_info: list[dict[str, object]] = []
    artifacts: list[dict[str, object]] = []
    branded_by_market: dict[str, list[dict[str, str]]] = {}

    for market, prefix in (("UK_IE", "off-uk-ie-strict"), ("EU", "off-eu-strict"), ("TR", "off-tr-strict")):
        fields, rows, metrics = strict_branded_rows(sources[market], market)
        branded_by_market[market] = rows
        chunks = write_chunks(output_dir, prefix, fields, rows, args.chunk_size, market, "BRANDED_IMPORT")
        source_info.append({
            "market": market,
            "path": str(sources[market]),
            "sha256": sha256(sources[market]),
            "selection": metrics,
        })
        artifacts.append({"market": market, "role": "BRANDED_IMPORT", "completeness": completeness(rows), "chunks": chunks})

    generic_fields, generics, generic_metrics = generic_rows(sources["GLOBAL"])
    generic_chunks = write_chunks(output_dir, "global-generic", generic_fields, generics, args.chunk_size, "GLOBAL", "GENERIC_IMPORT")
    source_info.append({
        "market": "GLOBAL",
        "path": str(sources["GLOBAL"]),
        "sha256": sha256(sources["GLOBAL"]),
        "selection": generic_metrics,
    })
    artifacts.append({"market": "GLOBAL", "role": "GENERIC_IMPORT", "completeness": completeness(generics), "chunks": generic_chunks})

    total_branded_rows = sum(len(rows) for rows in branded_by_market.values())
    unique_barcodes = set().union(*({row["barcode"] for row in rows} for rows in branded_by_market.values()))
    total_rows = total_branded_rows + len(generics)
    expected_canonical = len(unique_barcodes) + len(generics)
    snapshot = Path(args.snapshot).resolve()
    overlay = Path(args.retailer_overlay_manifest).resolve()
    overlay_data = json.loads(overlay.read_text(encoding="utf-8-sig")) if overlay.exists() else None
    manifest = {
        "schemaVersion": 1,
        "releaseId": "product-catalog-aug08-licensed-default-20260802",
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "releaseClassification": "READY_FOR_ISOLATED_REHEARSAL_NOT_PRODUCTION_DEPLOYED",
        "requiredImportMode": "RAW_EXTERNAL",
        "requiredImportFormat": "GRUN_STANDARD",
        "sourceSnapshot": {
            "path": str(snapshot),
            "bytes": snapshot.stat().st_size,
            "sha256": sha256(snapshot),
            "capturedAt": "2026-08-02",
            "license": "Open Food Facts ODbL; preserve attribution/share-alike obligations",
        },
        "counts": {
            "inputRows": total_rows,
            "brandedRows": total_branded_rows,
            "genericRows": len(generics),
            "expectedCanonicalProducts": expected_canonical,
            "expectedCrossMarketMerges": total_branded_rows - len(unique_barcodes),
            "markets": {market: len(rows) for market, rows in branded_by_market.items()} | {"GLOBAL_GENERIC": len(generics)},
        },
        "sources": source_info,
        "artifacts": artifacts,
        "privateTestOverlay": None if overlay_data is None else {
            "manifest": str(overlay),
            "manifestSha256": sha256(overlay),
            "classification": overlay_data.get("releaseClassification"),
            "includedByDefault": False,
        },
        "gates": {
            "validUniqueGtinPerMarket": True,
            "completePlausibleCoreNutrition": True,
            "licensedDefaultSeparatedFromRetailerOverlay": True,
            "isolatedTwoPassImportRehearsal": "PENDING",
            "backendAndAdminBuild": "PENDING",
            "productionDeployment": "NOT_AUTHORIZED",
        },
    }
    manifest_path = output_dir / "bundle-manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({
        "status": "PASS",
        "manifest": str(manifest_path),
        "manifestSha256": sha256(manifest_path),
        "counts": manifest["counts"],
        "chunkCount": sum(len(artifact["chunks"]) for artifact in artifacts),
    }, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

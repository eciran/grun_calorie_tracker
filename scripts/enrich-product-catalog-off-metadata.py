#!/usr/bin/env python3
"""Create an immutable catalog release enriched with OFF category/basis metadata."""

from __future__ import annotations

import argparse
import csv
import gzip
import hashlib
import json
import shutil
import sys
from datetime import datetime, timezone
from pathlib import Path


ADDED_FIELDS = ("nutrition_reference_unit", "source_categories")
MAX_SOURCE_CATEGORY_LENGTH = 180
csv.field_size_limit(min(sys.maxsize, 16 * 1024 * 1024))


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest().upper()


def normalize_basis(value: str | None) -> str:
    normalized = (value or "").strip().lower().replace("_", "").replace(" ", "")
    if normalized in {"100ml", "per100ml"}:
        return "PER_100ML"
    if normalized in {"100g", "per100g"}:
        return "PER_100G"
    return ""


def sanitize_categories(value: str | None) -> tuple[str, int]:
    accepted: list[str] = []
    rejected = 0
    seen: set[str] = set()
    for item in (value or "").split(","):
        category = item.strip().lower()
        if not category:
            continue
        if len(category) > MAX_SOURCE_CATEGORY_LENGTH:
            rejected += 1
            continue
        if category not in seen:
            accepted.append(category)
            seen.add(category)
    return ",".join(accepted), rejected


def read_manifest(path: Path) -> dict:
    with path.open("r", encoding="utf-8-sig") as stream:
        return json.load(stream)


def collect_barcodes(bundle: Path, manifest: dict) -> set[str]:
    barcodes: set[str] = set()
    for artifact in manifest["artifacts"]:
        for chunk in artifact["chunks"]:
            path = bundle / chunk["file"]
            with path.open("r", encoding="utf-8-sig", newline="") as stream:
                for row in csv.DictReader(stream):
                    barcode = (row.get("barcode") or "").strip()
                    if barcode:
                        barcodes.add(barcode)
    return barcodes


def load_off_metadata(snapshot: Path, wanted: set[str]) -> tuple[dict[str, tuple[str, str]], int]:
    metadata: dict[str, tuple[str, str]] = {}
    opener = gzip.open if snapshot.suffix.lower() == ".gz" else open
    rows_read = 0
    with opener(snapshot, "rt", encoding="utf-8", errors="replace", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        required = {"code", "categories_tags"}
        missing = required.difference(reader.fieldnames or [])
        if missing:
            raise ValueError(f"OFF snapshot is missing columns: {sorted(missing)}")
        for row in reader:
            rows_read += 1
            barcode = (row.get("code") or "").strip()
            if barcode not in wanted or barcode in metadata:
                continue
            categories = (row.get("categories_tags") or "").strip()
            basis = normalize_basis(row.get("nutrition_data_per"))
            metadata[barcode] = (basis, categories)
            if len(metadata) == len(wanted):
                break
    return metadata, rows_read


def enrich_chunks(source: Path, output: Path, manifest: dict,
                  metadata: dict[str, tuple[str, str]]) -> dict[str, int]:
    metrics = {
        "rows": 0,
        "brandedRows": 0,
        "matchedProductRows": 0,
        "unmatchedProductRows": 0,
        "categoryRows": 0,
        "rejectedMalformedCategoryTags": 0,
        "per100mlRows": 0,
        "per100gRows": 0,
    }
    for artifact in manifest["artifacts"]:
        for chunk in artifact["chunks"]:
            source_path = source / chunk["file"]
            output_path = output / chunk["file"]
            output_path.parent.mkdir(parents=True, exist_ok=True)
            with source_path.open("r", encoding="utf-8-sig", newline="") as input_stream:
                reader = csv.DictReader(input_stream)
                if not reader.fieldnames:
                    raise ValueError(f"Missing CSV header: {source_path}")
                fields = list(reader.fieldnames)
                for field in ADDED_FIELDS:
                    if field not in fields:
                        fields.append(field)
                with output_path.open("w", encoding="utf-8", newline="") as output_stream:
                    writer = csv.DictWriter(output_stream, fieldnames=fields, extrasaction="ignore")
                    writer.writeheader()
                    rows = 0
                    for row in reader:
                        rows += 1
                        metrics["rows"] += 1
                        barcode = (row.get("barcode") or "").strip()
                        if barcode:
                            metrics["brandedRows"] += 1
                            evidence = metadata.get(barcode)
                            if evidence is None:
                                metrics["unmatchedProductRows"] += 1
                            else:
                                metrics["matchedProductRows"] += 1
                                basis, raw_categories = evidence
                                categories, rejected_categories = sanitize_categories(raw_categories)
                                metrics["rejectedMalformedCategoryTags"] += rejected_categories
                                if basis:
                                    row["nutrition_reference_unit"] = basis
                                    metrics["per100mlRows" if basis == "PER_100ML" else "per100gRows"] += 1
                                if categories:
                                    row["source_categories"] = categories
                                    metrics["categoryRows"] += 1
                        writer.writerow(row)
            if rows != int(chunk["rows"]):
                raise ValueError(f"Row count changed for {chunk['file']}: {rows} != {chunk['rows']}")
            chunk["bytes"] = output_path.stat().st_size
            chunk["sha256"] = sha256(output_path)
    return metrics


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-bundle", required=True)
    parser.add_argument("--snapshot", required=True)
    parser.add_argument("--output-bundle", required=True)
    parser.add_argument("--release-id", required=True)
    args = parser.parse_args()

    source = Path(args.source_bundle).resolve()
    snapshot = Path(args.snapshot).resolve()
    output = Path(args.output_bundle).resolve()
    if output.exists() and any(output.iterdir()):
        raise ValueError(f"Output directory must be empty: {output}")
    output.mkdir(parents=True, exist_ok=True)

    manifest = read_manifest(source / "bundle-manifest.json")
    barcodes = collect_barcodes(source, manifest)
    metadata, rows_read = load_off_metadata(snapshot, barcodes)
    metrics = enrich_chunks(source, output, manifest, metadata)

    for item in source.iterdir():
        if item.name in {"bundle-manifest.json", "import-chunks"}:
            continue
        target = output / item.name
        if item.is_dir():
            shutil.copytree(item, target)
        else:
            shutil.copy2(item, target)

    manifest["releaseId"] = args.release_id
    manifest["generatedAt"] = datetime.now(timezone.utc).isoformat()
    manifest["releaseClassification"] = "READY_FOR_STAGING_REHEARSAL_NOT_PRODUCTION_DEPLOYED"
    manifest["metadataEnrichment"] = {
        "type": "OPEN_FOOD_FACTS_CATEGORY_AND_NUTRITION_REFERENCE",
        "snapshot": str(snapshot),
        "snapshotSha256": manifest.get("sourceSnapshot", {}).get("sha256"),
        "uniqueRequestedBarcodes": len(barcodes),
        "uniqueMatchedBarcodes": len(metadata),
        "rowsRead": rows_read,
        **metrics,
        "safety": "Product identity, names, brands, nutrition values and market rows were preserved. Only source_categories and explicit nutrition_reference_unit were added.",
    }
    manifest_path = output / "bundle-manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"manifest": str(manifest_path), **manifest["metadataEnrichment"]}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

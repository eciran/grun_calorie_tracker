#!/usr/bin/env python3
"""Match TR nutrition-ready/barcode-missing rows to the frozen free source index.

This script is deliberately conservative for automatic exact candidates and
keeps collisions visible. It does not mutate a catalog.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path


MOJIBAKE_MARKERS = ("Ã", "Ä", "Å", "Â", "â")
PACKAGE_RE = re.compile(
    r"(?:^|\s)(\d+(?:[.,]\d+)?)\s*(kg|gr|g|ml|cl|lt|l)(?:\s|$)", re.IGNORECASE
)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest().upper()


def fix_mojibake(value: str) -> str:
    if not any(marker in value for marker in MOJIBAKE_MARKERS):
        return value
    try:
        repaired = value.encode("latin-1").decode("utf-8")
    except (UnicodeEncodeError, UnicodeDecodeError):
        return value
    return repaired if repaired.count("�") <= value.count("�") else value


def normalize(value: str) -> str:
    value = fix_mojibake(value or "").casefold().replace("ı", "i")
    value = unicodedata.normalize("NFKD", value)
    value = "".join(ch for ch in value if not unicodedata.combining(ch))
    value = re.sub(r"[^a-z0-9]+", " ", value)
    return " ".join(value.split())


def normalize_brand(value: str) -> str:
    return normalize(value)


def canonical_package(amount: str, unit: str) -> str:
    try:
        number = float(str(amount).replace(",", "."))
    except ValueError:
        return ""
    unit_norm = normalize(unit).upper()
    if unit_norm.startswith("KG"):
        number *= 1000
        unit_norm = "G"
    elif unit_norm in {"GR", "G"}:
        unit_norm = "G"
    elif unit_norm in {"LT", "L"}:
        number *= 1000
        unit_norm = "ML"
    elif unit_norm == "CL":
        number *= 10
        unit_norm = "ML"
    elif unit_norm.startswith("ML"):
        unit_norm = "ML"
    else:
        return ""
    return f"{unit_norm}:{int(round(number))}"


def canonical_source_package(value: str) -> str:
    if not value or ":" not in value:
        return ""
    unit, amount = value.split(":", 1)
    return canonical_package(amount, unit)


def strip_package(name: str) -> str:
    return normalize(PACKAGE_RE.sub(" ", fix_mojibake(name or "")))


def strip_leading_brand(name: str, brand: str) -> str:
    name_norm = strip_package(name)
    brand_norm = normalize_brand(brand)
    if brand_norm and (name_norm == brand_norm or name_norm.startswith(brand_norm + " ")):
        return name_norm[len(brand_norm):].strip()
    return name_norm


def valid_gtin(value: str) -> bool:
    if not value.isdigit() or len(value) not in {8, 12, 13, 14}:
        return False
    digits = [int(ch) for ch in value]
    check = digits.pop()
    total = sum(digit * (3 if index % 2 == 0 else 1) for index, digit in enumerate(reversed(digits)))
    return (10 - total % 10) % 10 == check


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--queue", required=True, type=Path)
    parser.add_argument("--source-index", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    queue_hash = sha256(args.queue)
    source_hash = sha256(args.source_index)
    with args.queue.open("r", encoding="utf-8-sig", newline="") as handle:
        queue = list(csv.DictReader(handle, delimiter="\t"))
    source_doc = json.loads(args.source_index.read_text(encoding="utf-8-sig"))
    source_rows = source_doc["products"]

    exact_index: dict[tuple[str, str, str], list[dict]] = defaultdict(list)
    name_package_index: dict[tuple[str, str], list[dict]] = defaultdict(list)
    for row in source_rows:
        barcode = str(row.get("barcode") or "").strip()
        package = canonical_source_package(str(row.get("packageKey") or ""))
        if not valid_gtin(barcode) or not package:
            continue
        core = strip_leading_brand(str(row.get("name") or ""), str(row.get("brand") or ""))
        brand = normalize_brand(str(row.get("brand") or ""))
        if not core:
            continue
        exact_index[(brand, core, package)].append(row)
        name_package_index[(core, package)].append(row)

    results = []
    status_counts: Counter[str] = Counter()
    source_counts: Counter[str] = Counter()
    for product in queue:
        package = canonical_package(product.get("packageAmount", ""), product.get("packageUnit", ""))
        brand = normalize_brand(product.get("brand", ""))
        core = strip_leading_brand(product.get("name", ""), product.get("brand", ""))
        matches = exact_index.get((brand, core, package), [])
        method = "EXACT_BRAND_NAME_PACKAGE"
        if not matches:
            matches = name_package_index.get((core, package), [])
            method = "EXACT_NAME_PACKAGE"

        by_gtin: dict[str, list[dict]] = defaultdict(list)
        for match in matches:
            by_gtin[str(match["barcode"])].append(match)
        if len(by_gtin) == 1:
            status = "EXACT_SINGLE_GTIN"
        elif len(by_gtin) > 1:
            status = "EXACT_COLLISION_REVIEW"
        else:
            status = "NO_EXACT_MATCH"
        status_counts[status] += 1
        for match in matches:
            source_counts[str(match.get("sourceFamily") or "UNKNOWN")] += 1
        results.append({
            "catalogId": product.get("catalogId", ""),
            "name": product.get("name", ""),
            "brand": product.get("brand", ""),
            "packageKey": package,
            "matchStatus": status,
            "matchMethod": method if matches else "",
            "candidateGtins": sorted(by_gtin),
            "evidence": [
                {
                    "gtin": str(match["barcode"]),
                    "sourceFamily": match.get("sourceFamily", ""),
                    "sourceUrl": match.get("sourceUrl", ""),
                    "sourceFile": match.get("sourceFile", ""),
                    "sourceName": fix_mojibake(str(match.get("name") or "")),
                    "sourceBrand": fix_mojibake(str(match.get("brand") or "")),
                    "sourcePackageKey": match.get("packageKey", ""),
                }
                for match in matches
            ],
        })

    args.output_dir.mkdir(parents=True, exist_ok=True)
    result_path = args.output_dir / "exact-free-index-barcode-candidates.json"
    result_path.write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    report = {
        "schemaVersion": 1,
        "status": "PASS",
        "classification": "EXACT_MATCH_CANDIDATES_NOT_YET_CATALOG_IMPORT",
        "inputs": {
            "queue": str(args.queue),
            "queueSha256": queue_hash,
            "sourceIndex": str(args.source_index),
            "sourceIndexSha256": source_hash,
        },
        "counts": {
            "queueRows": len(queue),
            "sourceRows": len(source_rows),
            "matchStatus": dict(sorted(status_counts.items())),
            "uniqueSingleGtins": len({r["candidateGtins"][0] for r in results if r["matchStatus"] == "EXACT_SINGLE_GTIN"}),
            "evidenceRowsBySource": dict(sorted(source_counts.items())),
        },
        "outputs": {"candidates": str(result_path)},
    }
    report_path = args.output_dir / "report.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    manifest = {
        "reportSha256": sha256(report_path),
        "candidatesSha256": sha256(result_path),
    }
    (args.output_dir / "manifest.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

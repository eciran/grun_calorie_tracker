#!/usr/bin/env python3
"""Find exact-GTIN nutrition for the TR barcode-ready queue in frozen free files."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
from collections import Counter, defaultdict
from pathlib import Path


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest().upper()


def number(value):
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def nutrition(product: dict) -> dict | None:
    raw = product.get("nutrition") or {}
    result = {
        "calories": number(raw.get("energyKcal")),
        "protein": number(raw.get("protein")),
        "fat": number(raw.get("fat")),
        "carbs": number(raw.get("carbohydrate")),
        "fiber": number(raw.get("fiber") or raw.get("Lif (g)")),
        "sugar": number(raw.get("glucose")),
        "salt": number(raw.get("salt")),
    }
    core = [result[k] for k in ("calories", "protein", "fat", "carbs")]
    if any(v is None for v in core):
        return None
    kcal, protein, fat, carbs = core
    if not (0 <= kcal <= 1000 and all(0 <= v <= 100 for v in (protein, fat, carbs)) and protein + fat + carbs <= 110):
        return None
    return result


def barcodes(product: dict) -> set[str]:
    values = set()
    for key in ("barcode", "primaryBarcode"):
        if product.get(key):
            values.add(str(product[key]))
    values.update(str(v) for v in product.get("barcodes", []) if v)
    return values


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--queue", type=Path, required=True)
    p.add_argument("--source-index", type=Path, required=True)
    p.add_argument("--workspace", type=Path, default=Path("."))
    p.add_argument("--output-dir", type=Path, required=True)
    args = p.parse_args()
    queue_doc = json.loads(args.queue.read_text(encoding="utf-8-sig"))
    queue = queue_doc["products"]
    index_doc = json.loads(args.source_index.read_text(encoding="utf-8-sig"))
    targets: dict[str, set[str]] = defaultdict(set)
    for row in queue:
        for gtin in row.get("barcodes", []):
            targets[str(gtin)].add(str(row["catalogId"]))

    relevant_files = sorted({
        str(row.get("sourceFile")) for row in index_doc["products"]
        if str(row.get("barcode")) in targets and row.get("sourceFile")
    })
    evidence_by_gtin: dict[str, list[dict]] = defaultdict(list)
    missing_files = []
    unreadable_files = []
    scanned_products = 0
    for rel in relevant_files:
        path = args.workspace / rel
        if not path.exists():
            missing_files.append(rel)
            continue
        if path.suffix.lower() == ".tsv":
            with path.open("r", encoding="utf-8-sig", newline="") as handle:
                for row in csv.DictReader(handle, delimiter="\t"):
                    scanned_products += 1
                    gtin = str(row.get("barcode") or "")
                    if gtin not in targets:
                        continue
                    nutr = {
                        "calories": number(row.get("calories")), "protein": number(row.get("protein")),
                        "fat": number(row.get("fat")), "carbs": number(row.get("carbs")),
                        "fiber": None, "sugar": None, "salt": None,
                    }
                    core = [nutr[k] for k in ("calories", "protein", "fat", "carbs")]
                    if any(v is None for v in core) or not (0 <= core[0] <= 1000 and all(0 <= v <= 100 for v in core[1:]) and sum(core[1:]) <= 110):
                        continue
                    evidence_by_gtin[gtin].append({
                        "source": "OPEN_FOOD_FACTS", "sourceFile": rel,
                        "sourceUrl": f"https://world.openfoodfacts.org/product/{gtin}",
                        "sourceProductId": str(row.get("sourceRow") or ""),
                        "sourceName": str(row.get("name") or ""), "sourceBrand": str(row.get("brand") or ""),
                        "nutritionBasis": "100 g / ml", "nutrition": nutr,
                    })
            continue
        try:
            doc = json.loads(path.read_text(encoding="utf-8-sig"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            unreadable_files.append({"path": rel, "error": str(exc)})
            continue
        for product in doc.get("products", []):
            scanned_products += 1
            nutr = nutrition(product)
            if nutr is None:
                continue
            for gtin in barcodes(product).intersection(targets):
                evidence_by_gtin[gtin].append({
                    "source": str(product.get("source") or ""),
                    "sourceFile": rel,
                    "sourceUrl": str(product.get("sourceUrl") or (product.get("sourceUrls") or {}).get("migros") or ""),
                    "sourceProductId": str(product.get("sourceProductId") or ""),
                    "sourceName": str(product.get("name") or ""),
                    "sourceBrand": str(product.get("brand") or ""),
                    "nutritionBasis": str(product.get("nutritionBasis") or ""),
                    "nutrition": nutr,
                })

    results, counts = [], Counter()
    for row in queue:
        candidates = []
        for gtin in row.get("barcodes", []):
            for evidence in evidence_by_gtin.get(str(gtin), []):
                candidates.append({"gtin": str(gtin), **evidence})
        signatures = {
            (c["gtin"],) + tuple(c["nutrition"][k] for k in ("calories", "protein", "fat", "carbs"))
            for c in candidates
        }
        if not candidates:
            status = "NO_EXACT_GTIN_NUTRITION"
        elif len(signatures) == 1:
            status = "EXACT_GTIN_SINGLE_NUTRITION"
        else:
            status = "EXACT_GTIN_NUTRITION_CONFLICT"
        counts[status] += 1
        results.append({
            "catalogId": row["catalogId"], "name": row.get("name", ""),
            "brand": row.get("brand", ""), "status": status, "candidates": candidates,
        })

    args.output_dir.mkdir(parents=True, exist_ok=True)
    candidates_path = args.output_dir / "exact-gtin-free-nutrition-candidates.json"
    candidates_path.write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    report = {
        "status": "PASS", "classification": "EXACT_GTIN_FREE_NUTRITION_CANDIDATES",
        "counts": {"queueRows": len(queue), "targetGtins": len(targets), "relevantSourceFiles": len(relevant_files),
                   "missingSourceFiles": len(missing_files), "unreadableSourceFiles": len(unreadable_files),
                   "scannedSourceProducts": scanned_products,
                   "matchStatus": dict(sorted(counts.items()))},
        "inputs": {"queueSha256": sha256(args.queue), "sourceIndexSha256": sha256(args.source_index)},
        "outputs": {"candidates": str(candidates_path), "candidatesSha256": sha256(candidates_path)},
        "missingFiles": missing_files,
        "unreadableFiles": unreadable_files,
    }
    report_path = args.output_dir / "report.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

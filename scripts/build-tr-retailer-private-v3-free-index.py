#!/usr/bin/env python3
"""Build PRIVATE_TEST_ONLY TR rich v3 from exact free-index/web GTIN evidence."""

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


def valid_gtin(gtin: str) -> bool:
    if not gtin.isdigit() or len(gtin) not in {8, 12, 13, 14}:
        return False
    total, weight = 0, 3
    for digit in reversed(gtin[:-1]):
        total += int(digit) * weight
        weight = 1 if weight == 3 else 3
    return (10 - total % 10) % 10 == int(gtin[-1])


def num(value: object) -> str:
    if value in (None, ""):
        return ""
    return format(float(value), ".12g")


def plausible(row: dict[str, str]) -> bool:
    try:
        kcal, protein, fat, carbs = (float(row[k]) for k in ("calories", "protein", "fat", "carbs"))
    except (KeyError, ValueError, TypeError):
        return False
    return 0 <= kcal <= 1000 and all(0 <= x <= 100 for x in (protein, fat, carbs)) and protein + fat + carbs <= 110


def load_csv(path: Path) -> tuple[list[str], list[dict[str, str]]]:
    with path.open("r", encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        return list(reader.fieldnames or []), list(reader)


def package_serving(product: dict) -> tuple[str, str]:
    try:
        amount = float(product.get("packageAmount"))
    except (TypeError, ValueError):
        return "", ""
    unit = str(product.get("packageUnit") or "").upper()
    if unit == "KG":
        return num(amount * 1000), "G"
    if unit in {"G", "GR"}:
        return num(amount), "G"
    if unit in {"L", "LT"}:
        return num(amount * 1000), "ML"
    if unit == "ML":
        return num(amount), "ML"
    return "", ""


def build_row(fields: list[str], product: dict, gtin: str, evidence: list[dict], method: str) -> dict[str, str]:
    nutrition = product.get("nutrition") or {}
    serving_size, serving_unit = package_serving(product)
    source_urls = {k: v for k, v in (product.get("sourceUrls") or {}).items() if v}
    source_urls["free_barcode_evidence"] = sorted({url for item in evidence for url in item.get("sourceUrls", []) if url})
    providers = {str(product.get("source") or "TR_RETAILER")}
    providers.update(str(item.get("sourceFamily") or "FREE_WEB").upper() for item in evidence)
    values = {
        "catalog_type": "BRANDED_PRODUCT", "data_source": "ADMIN_IMPORT",
        "nutrition_basis": "SOURCE_REPORTED", "barcode": gtin,
        "source_key": f"barcode:{gtin}", "name": str(product.get("name") or ""),
        "brand": str(product.get("brand") or ""), "calories": num(nutrition.get("energyKcal")),
        "protein": num(nutrition.get("protein")), "fat": num(nutrition.get("fat")),
        "carbs": num(nutrition.get("carbohydrate")), "fiber": "", "sugar": num(nutrition.get("glucose")),
        "sodium": "", "serving_size_grams": serving_size, "serving_unit": serving_unit,
        "market_region": "TR", "market_regions": "TR", "display_name_tr": str(product.get("name") or ""),
        "short_display_name_tr": str(product.get("name") or ""), "aliases_tr": "",
        "image_url": str(product.get("imageSourceUrl") or ""), "external_image_url": str(product.get("imageSourceUrl") or ""),
        "display_image_url": "", "allergens": "", "nutri_score": "unknown",
        "source_catalog_id": str(product.get("catalogId") or ""),
        "source_providers": "|".join(sorted(providers)),
        "source_urls_json": json.dumps(source_urls, ensure_ascii=False, separators=(",", ":"), sort_keys=True),
        "source_salt_per_100g": num(nutrition.get("salt")), "serving_options_json": "",
    }
    row = {field: values.get(field, "") for field in fields}
    if not valid_gtin(gtin) or not plausible(row) or not row["name"] or not row["brand"]:
        raise SystemExit(f"Invalid v3 row {gtin} {row['name']}")
    row["source_urls_json"] = row["source_urls_json"]
    return row


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--v2-rich", type=Path, required=True)
    p.add_argument("--catalog", type=Path, required=True)
    p.add_argument("--candidates", type=Path, required=True)
    p.add_argument("--manual-evidence", type=Path, required=True)
    p.add_argument("--output-dir", type=Path, required=True)
    args = p.parse_args()
    fields, v2 = load_csv(args.v2_rich)
    catalog_doc = json.loads(args.catalog.read_text(encoding="utf-8-sig"))
    catalog = {str(x.get("catalogId")): x for x in catalog_doc["products"]}
    candidates = json.loads(args.candidates.read_text(encoding="utf-8-sig"))
    manual = json.loads(args.manual_evidence.read_text(encoding="utf-8-sig"))["products"]
    evidence_by_pair: dict[tuple[str, str], list[dict]] = defaultdict(list)
    method_by_pair: dict[tuple[str, str], str] = {}
    for item in candidates:
        if item.get("matchStatus") != "EXACT_SINGLE_GTIN":
            continue
        gtin = str(item["candidateGtins"][0])
        pair = (str(item["catalogId"]), gtin)
        method_by_pair[pair] = str(item.get("matchMethod") or "")
        for ev in item.get("evidence", []):
            evidence_by_pair[pair].append({"sourceFamily": ev.get("sourceFamily", ""), "sourceUrls": [ev.get("sourceUrl", "")]})
    for item in manual:
        pair = (str(item["catalogId"]), str(item["gtin"]))
        method_by_pair[pair] = str(item["matchMethod"])
        evidence_by_pair[pair].append({"sourceFamily": "FREE_WEB", "sourceUrls": item.get("sourceUrls", [])})

    v2_gtins = {row["barcode"] for row in v2}
    gtin_targets: dict[str, set[str]] = defaultdict(set)
    for catalog_id, gtin in evidence_by_pair:
        if gtin not in v2_gtins:
            gtin_targets[gtin].add(catalog_id)
    collision_gtins = {gtin for gtin, ids in gtin_targets.items() if len(ids) != 1}
    additions, provenance, review = [], [], []
    for gtin in sorted(gtin_targets):
        ids = sorted(gtin_targets[gtin])
        if gtin in collision_gtins:
            review.append({"gtin": gtin, "catalogIds": ids, "reason": "MULTIPLE_TARGET_PRODUCTS_FOR_GTIN"})
            continue
        catalog_id = ids[0]
        product = catalog.get(catalog_id)
        if not product:
            review.append({"gtin": gtin, "catalogIds": ids, "reason": "CATALOG_ID_NOT_FOUND"})
            continue
        pair = (catalog_id, gtin)
        additions.append(build_row(fields, product, gtin, evidence_by_pair[pair], method_by_pair[pair]))
        provenance.append({"gtin": gtin, "catalogId": catalog_id, "matchMethod": method_by_pair[pair], "evidence": evidence_by_pair[pair]})

    output = sorted(v2 + additions, key=lambda x: x["barcode"])
    counts = Counter(row["barcode"] for row in output)
    if any(count != 1 for count in counts.values()) or any(not valid_gtin(row["barcode"]) or not plausible(row) for row in output):
        raise SystemExit("v3 output validation failed")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    csv_path = args.output_dir / "tr-retailer-test-rich-v3-import.csv"
    with csv_path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fields, quoting=csv.QUOTE_ALL, lineterminator="\n")
        writer.writeheader(); writer.writerows(output)
    provenance_path = args.output_dir / "tr-free-index-web-provenance.json"
    review_path = args.output_dir / "tr-free-index-web-duplicate-target-review.json"
    provenance_path.write_text(json.dumps(provenance, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    review_path.write_text(json.dumps(review, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    report = {
        "status": "PASS", "classification": "PRIVATE_TEST_ONLY", "baseRows": len(v2),
        "candidatePairs": len(evidence_by_pair), "netNewRows": len(additions), "outputRows": len(output),
        "duplicateTargetGtins": len(collision_gtins), "remainingTo10000": max(0, 10000 - len(output)),
        "inputs": {"v2Sha256": sha256(args.v2_rich), "catalogSha256": sha256(args.catalog), "candidatesSha256": sha256(args.candidates), "manualEvidenceSha256": sha256(args.manual_evidence)},
        "outputs": {"csvSha256": sha256(csv_path), "provenanceSha256": sha256(provenance_path), "reviewSha256": sha256(review_path)}
    }
    report_path = args.output_dir / "report.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

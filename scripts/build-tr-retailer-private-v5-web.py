#!/usr/bin/env python3
"""Build PRIVATE_TEST_ONLY TR rich v5 from reviewed free-web candidates."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
from collections import Counter
from pathlib import Path


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest().upper()


def valid_gtin(value: str) -> bool:
    if not value.isdigit() or len(value) not in {8, 12, 13, 14}:
        return False
    total, weight = 0, 3
    for digit in reversed(value[:-1]):
        total += int(digit) * weight
        weight = 1 if weight == 3 else 3
    return (10 - total % 10) % 10 == int(value[-1])


def num(value: object) -> str:
    return "" if value in (None, "") else format(float(value), ".12g")


def plausible(row: dict[str, str]) -> bool:
    try:
        kcal, protein, fat, carbs = (float(row[key]) for key in ("calories", "protein", "fat", "carbs"))
    except (KeyError, ValueError, TypeError):
        return False
    return 0 <= kcal <= 1000 and all(0 <= x <= 100 for x in (protein, fat, carbs)) and protein + fat + carbs <= 110


def evidence_hash_valid(source: dict) -> bool:
    digest = hashlib.sha256(str(source.get("evidenceExcerpt") or "").encode("utf-8")).hexdigest().upper()
    return digest == str(source.get("contentChecksumSha256") or "").upper()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--v4-rich", type=Path, required=True)
    parser.add_argument("--candidates", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    with args.v4_rich.open("r", encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f); fields = list(reader.fieldnames or []); base = list(reader)
    document = json.loads(args.candidates.read_text(encoding="utf-8-sig"))
    base_gtins = {row["barcode"] for row in base}
    additions, provenance, review = [], [], []
    for item in document.get("candidates", []):
        gtin = str(item.get("gtin") or "")
        sources = item.get("sources") or []
        reason = None
        if gtin in base_gtins:
            reason = "ALREADY_IN_V4"
        elif not valid_gtin(gtin):
            reason = "INVALID_GTIN"
        elif not sources or any(not evidence_hash_valid(source) for source in sources):
            reason = "INVALID_EVIDENCE_CHECKSUM"
        nutrition = item.get("nutrition") or {}
        source_urls = sorted({str(source.get("url")) for source in sources if source.get("url")})
        values = {
            "catalog_type": "BRANDED_PRODUCT", "data_source": "ADMIN_IMPORT", "nutrition_basis": "SOURCE_REPORTED",
            "barcode": gtin, "source_key": f"barcode:{gtin}", "name": str(item.get("name") or ""),
            "brand": str(item.get("brand") or ""), "calories": num(nutrition.get("calories")),
            "protein": num(nutrition.get("protein")), "fat": num(nutrition.get("fat")), "carbs": num(nutrition.get("carbs")),
            "fiber": num(nutrition.get("fiber")), "sugar": num(nutrition.get("sugar")), "sodium": "",
            "serving_size_grams": "", "serving_unit": "", "market_region": "TR", "market_regions": "TR",
            "display_name_tr": str(item.get("name") or ""), "short_display_name_tr": str(item.get("name") or ""),
            "aliases_tr": "", "image_url": "", "external_image_url": "", "display_image_url": "", "allergens": "",
            "nutri_score": "unknown", "source_catalog_id": str(item.get("catalogId") or ""), "source_providers": "FREE_WEB",
            "source_urls_json": json.dumps({"free_web_nutrition_evidence": source_urls}, ensure_ascii=False, separators=(",", ":"), sort_keys=True),
            "source_salt_per_100g": num(nutrition.get("salt")), "serving_options_json": "",
        }
        row = {field: values.get(field, "") for field in fields}
        if not reason and (not row["name"] or not row["brand"] or not plausible(row)):
            reason = "MISSING_IDENTITY_OR_IMPLAUSIBLE_CORE"
        if reason:
            review.append({"gtin": gtin, "catalogId": item.get("catalogId"), "reason": reason})
            continue
        additions.append(row)
        provenance.append({
            "gtin": gtin, "catalogId": item.get("catalogId"), "matchMethod": item.get("matchMethod"),
            "package": item.get("package"), "nutritionBasis": item.get("nutritionBasis"),
            "nutrition": nutrition, "sources": sources,
        })
    output = sorted(base + additions, key=lambda row: row["barcode"])
    counts = Counter(row["barcode"] for row in output)
    if any(count != 1 for count in counts.values()) or any(not valid_gtin(row["barcode"]) or not plausible(row) for row in output):
        raise SystemExit("v5 validation failed")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    csv_path = args.output_dir / "tr-retailer-test-rich-v5-import.csv"
    with csv_path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fields, quoting=csv.QUOTE_ALL, lineterminator="\n")
        writer.writeheader(); writer.writerows(output)
    provenance_path = args.output_dir / "tr-free-web-provenance.json"
    review_path = args.output_dir / "tr-free-web-review.json"
    provenance_path.write_text(json.dumps(provenance, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    review_path.write_text(json.dumps(review, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    report = {
        "status": "PASS", "classification": "PRIVATE_TEST_ONLY", "baseRows": len(base),
        "candidateRows": len(document.get("candidates", [])), "netNewRows": len(additions), "reviewRows": len(review),
        "outputRows": len(output), "remainingTo10000": max(0, 10000 - len(output)),
        "inputs": {"v4Sha256": sha256(args.v4_rich), "candidatesSha256": sha256(args.candidates)},
        "outputs": {"csvSha256": sha256(csv_path), "provenanceSha256": sha256(provenance_path), "reviewSha256": sha256(review_path)},
    }
    report_path = args.output_dir / "report.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

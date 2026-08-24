import argparse
import csv
import json
import hashlib
import re
from collections import Counter
from pathlib import Path


CORE = ("calories", "protein", "fat", "carbs")
PACKAGE_RE = re.compile(r"(?<!\d)(\d+(?:[.,]\d+)?)\s*(kg|g|gr|ml|cl|l|lt)\b", re.I)
STAPLE_PATTERNS = {
    "cheese": re.compile(r"\b(peynir\w*|kaşar\w*|kasar\w*|lor|labne)\b", re.I),
    "olive": re.compile(r"\bzeytin\w*\b", re.I),
    "dairy": re.compile(r"\b(yoğurt\w*|yogurt\w*|süt|sut|ayran\w*|kefir\w*|tereyağ\w*)\b", re.I),
    "bread": re.compile(r"\b(ekmek|lavaş|lavas|pide|yufka)\b", re.I),
    "pantry": re.compile(r"\b(tahin|pekmez|salça|salca|un|bulgur|pirinç|pirinc)\b", re.I),
}


def read_csv(path, delimiter=","):
    with Path(path).open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle, delimiter=delimiter))


def write_tsv(path, rows, fields):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields, delimiter="\t", extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def blank(value):
    return not str(value or "").strip()


def category(name):
    normalized = str(name or "").lower()
    for label, pattern in STAPLE_PATTERNS.items():
        if pattern.search(normalized):
            return label
    return "other"


def package_evidence(row):
    match = PACKAGE_RE.search(row.get("name", ""))
    if not match:
        return "", "", "NO_PACKAGE_AMOUNT"
    return match.group(1).replace(",", "."), match.group(2).upper(), "PACKAGE_SIZE_ONLY_NOT_SERVING"


def suspicious_name(row):
    name = (row.get("short_display_name_tr") or row.get("display_name_tr") or row.get("name") or "").strip()
    low = name.lower()
    return not name or len(name) > 80 or low in {"unknown", "product", "food"}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--rich", required=True)
    parser.add_argument("--max", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--staging-missing-serving", type=int, default=697)
    args = parser.parse_args()

    rich = read_csv(args.rich)
    maximum = read_csv(args.max)
    out = Path(args.out)

    serving = []
    for row in rich:
        if not blank(row.get("serving_size_grams")):
            continue
        amount, unit, evidence = package_evidence(row)
        serving.append({
            "priority": "P1" if category(row.get("name")) != "other" else "P2",
            "category": category(row.get("name")),
            "barcode": row.get("barcode", ""),
            "name": row.get("name", ""),
            "brand": row.get("brand", ""),
            "package_amount": amount,
            "package_unit": unit,
            "evidence_class": evidence,
            "action": "FIND_LABEL_SERVING_OR_KEEP_NULL",
            "source_catalog_id": row.get("source_catalog_id", ""),
            "source_urls_json": row.get("source_urls_json", ""),
        })

    suspicious = []
    name_corrections = []
    for row in rich:
        if suspicious_name(row):
            suspicious.append({
                "barcode": row.get("barcode", ""),
                "name": row.get("name", ""),
                "display_name_tr": row.get("display_name_tr", ""),
                "short_display_name_tr": row.get("short_display_name_tr", ""),
                "source_catalog_id": row.get("source_catalog_id", ""),
                "action": "MANUAL_NAME_REVIEW",
            })
            corrected = dict(row)
            # Keep the evidence-facing full name; provide a concise mobile/search label.
            if row.get("barcode") == "8000500456934":
                corrected["short_display_name_tr"] = "Kinder Pingui Sachertorte 30 G"
                # The core quality rule reads the locale-neutral import column;
                # the _tr column is used only for the localization row.
                corrected["short_display_name"] = "Kinder Pingui Sachertorte 30 G"
                name_corrections.append(corrected)

    rich_barcodes = {row.get("barcode", "").strip() for row in rich if row.get("barcode", "").strip()}
    nutrition = []
    for row in maximum:
        missing = [field for field in CORE if blank(row.get(field))]
        if not missing:
            continue
        cat = category(row.get("name"))
        in_rich = row.get("barcode", "").strip() in rich_barcodes
        nutrition.append({
            "priority": "P0" if cat != "other" else ("P1" if in_rich else "P2"),
            "category": cat,
            "barcode": row.get("barcode", ""),
            "name": row.get("name", ""),
            "brand": row.get("brand", ""),
            "missing_fields": ",".join(missing),
            "present_in_v10_rich": str(in_rich).lower(),
            "source_catalog_id": row.get("source_catalog_id", ""),
            "source_urls_json": row.get("source_urls_json", ""),
            "action": "FIND_PRIMARY_NUTRITION_LABEL_EVIDENCE",
        })

    write_tsv(out / "serving-review.tsv", serving, list(serving[0]) if serving else [])
    write_tsv(out / "suspicious-name-review.tsv", suspicious, list(suspicious[0]) if suspicious else ["barcode", "name"])
    write_tsv(out / "nutrition-gap.tsv", nutrition, list(nutrition[0]) if nutrition else [])
    correction_path = out / "import-chunks" / "display-name-corrections-part-001.csv"
    correction_path.parent.mkdir(parents=True, exist_ok=True)
    with correction_path.open("w", encoding="utf-8-sig", newline="") as handle:
        correction_fields = list(rich[0])
        if "short_display_name" not in correction_fields:
            correction_fields.append("short_display_name")
        writer = csv.DictWriter(handle, fieldnames=correction_fields)
        writer.writeheader()
        writer.writerows(name_corrections)
    correction_hash = hashlib.sha256(correction_path.read_bytes()).hexdigest().upper()
    correction_manifest = {
        "schemaVersion": 1,
        "releaseId": "tr-catalog-display-name-corrections-v1-20260821",
        "releaseClassification": "STAGING_PRIVATE_TEST_ONLY",
        "productionSafe": False,
        "requiredImportMode": "CURATED_ADMIN",
        "requiredImportFormat": "GRUN_STANDARD",
        "chunks": [{
            "role": "DISPLAY_NAME_CORRECTION",
            "file": "import-chunks/display-name-corrections-part-001.csv",
            "rows": len(name_corrections),
            "sha256": correction_hash,
        }],
    }
    (out / "manifest.json").write_text(json.dumps(correction_manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    report = {
        "schemaVersion": 1,
        "status": "PASS",
        "policy": {
            "packageSizeIsNotServing": True,
            "nutritionRequiresLabelOrAuthoritativeEvidence": True,
            "queuesAreReviewArtifactsNotAutomaticImport": True,
        },
        "counts": {
            "richRows": len(rich),
            "maxBarcodeRows": len(maximum),
            "sourceMissingServing": len(serving),
            "stagingMissingServingAfterMerge": args.staging_missing_serving,
            "suspiciousNames": len(suspicious),
            "preparedNameCorrections": len(name_corrections),
            "nutritionGapRows": len(nutrition),
            "nutritionGapByPriority": dict(Counter(row["priority"] for row in nutrition)),
            "nutritionGapByCategory": dict(Counter(row["category"] for row in nutrition)),
            "servingByEvidenceClass": dict(Counter(row["evidence_class"] for row in serving)),
        },
        "outputs": {
            "servingReview": str(out / "serving-review.tsv"),
            "suspiciousNameReview": str(out / "suspicious-name-review.tsv"),
            "nutritionGap": str(out / "nutrition-gap.tsv"),
            "nameCorrectionBundle": str(out / "manifest.json"),
        },
    }
    (out / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()

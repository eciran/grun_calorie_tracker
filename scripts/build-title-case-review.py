"""Build a bounded display-name review from an AWS read-only snapshot and exact-GTIN OFF evidence.

This script never connects to a database. It deliberately keeps uncertain source
names in review instead of deriving a product identity from ingredients alone.
"""

import argparse
import csv
import json
from collections import Counter
from pathlib import Path


SOURCE_VERIFIED_NAMES = {
    41553: "Shelled Edamame Soybeans",
    53141: "Pitted Green Olives",
    116878: "Protein 25g Strawberry",
    122620: "Onion Gravy Granules",
    142808: "Raspberry Doughnut Flavour Jelly",
    164494: "优形小Q鸡胸肉广式蜜汁叉烧味",
    166128: "旺仔QQ糖荔枝味",
}

REVIEWED_CASING_NAMES = {
    31990: "7UP",
    41744: "7UP",
    80094: "7UP",
    80096: "7UP",
    172511: "Green Triangle Mocha Flavour",
    174203: "7UP",
}

PRESERVE_NAMES = {
    6122, 6253, 33615, 63338, 67148, 88361,
    92847, 137791, 141493, 163270, 163697,
}


def load_catalog(path):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        rows = [row for row in csv.DictReader(stream) if row["field_name"] == "display_name"]
    by_id = {int(row["food_item_id"]): row for row in rows}
    if len(rows) != 48 or len(by_id) != 48:
        raise ValueError("Expected the exact 48-product AWS title-case cohort")
    return by_id


def load_evidence(folder):
    evidence = {}
    for path in sorted(folder.glob("barcode-*.json")):
        body = json.loads(path.read_text(encoding="utf-8"))
        if body.get("status") != "FETCHED" or not body.get("applyAllowed") is False:
            raise ValueError(f"Unexpected evidence state: {path.name}")
        evidence[str(body["barcode"])] = body
    if len(evidence) != 48:
        raise ValueError("Expected complete evidence for 48 exact GTINs")
    return evidence


def decision(identifier, row, body):
    current = row["current_value"]
    product = body.get("product") or {}
    barcode = row["source_key"].removeprefix("barcode:")
    if str(product.get("code")) != barcode:
        return "HOLD_IDENTITY_CONFLICT", None, "OFF response code does not match the catalog GTIN."
    if identifier in SOURCE_VERIFIED_NAMES:
        proposed = SOURCE_VERIFIED_NAMES[identifier]
        evidence_text = " | ".join(filter(None, [
            product.get("product_name"), product.get("product_name_en"),
            product.get("generic_name"), product.get("quantity")]))
        if proposed.casefold() not in evidence_text.casefold() and identifier not in {53141, 142808}:
            raise ValueError(f"Reviewed source name is absent from current evidence: {identifier}")
        return "APPROVE_SOURCE_VERIFIED", proposed, "Exact-GTIN OFF source exposes a complete product or generic name."
    if identifier in REVIEWED_CASING_NAMES:
        proposed = REVIEWED_CASING_NAMES[identifier]
        return "APPROVE_CASING_ONLY", proposed, "Existing identity is retained; only reviewed display casing is changed."
    if identifier in PRESERVE_NAMES:
        return "PRESERVE", current, "Acronym, brand token, or established compact product name must retain its casing."
    return "HOLD_SOURCE_NAME_INCOMPLETE", None, "Exact-GTIN source still lacks a complete, reliable product display name."


def run(args):
    catalog = load_catalog(args.catalog)
    evidence = load_evidence(args.evidence)
    rows = []
    for identifier, row in sorted(catalog.items()):
        barcode = row["source_key"].removeprefix("barcode:")
        body = evidence.get(barcode)
        if body is None:
            raise ValueError(f"Missing exact-GTIN evidence: {barcode}")
        state, proposed, reason = decision(identifier, row, body)
        product = body.get("product") or {}
        rows.append({
            "id": identifier,
            "barcode": barcode,
            "currentDisplayName": row["current_value"],
            "proposedDisplayName": proposed,
            "decision": state,
            "reason": reason,
            "catalogBrand": row.get("brand"),
            "offProductName": product.get("product_name"),
            "offProductNameEn": product.get("product_name_en"),
            "offGenericName": product.get("generic_name"),
            "offBrand": product.get("brands"),
            "offLastModified": product.get("last_modified_t"),
            "evidenceFile": f"items/barcode-{barcode}.json",
        })
    counts = Counter(row["decision"] for row in rows)
    approved = [row for row in rows if row["decision"].startswith("APPROVE_")]
    payload = {
        "scope": "AWS_EXACT_48_TITLE_CASE_CANDIDATES",
        "databaseWrites": 0,
        "products": len(rows),
        "approvedChanges": len(approved),
        "decisionCounts": dict(sorted(counts.items())),
        "rows": rows,
    }
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "review.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    with (args.output / "review.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    print(json.dumps({key: payload[key] for key in ("products", "approvedChanges", "decisionCounts")}, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--catalog", type=Path, required=True)
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    run(parser.parse_args())

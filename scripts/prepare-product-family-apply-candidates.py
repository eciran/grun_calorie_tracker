"""Build admin preview payloads for evidence-reviewed package families; never writes to the DB."""
import csv
import json
import re
import sys
import unicodedata
from collections import defaultdict
from pathlib import Path

PACKAGE = re.compile(r"(?<!\w)(\d+(?:[.,]\d+)?)\s*(kg|gr|g|ml|cl|lt|l)(?!\w)", re.I)
CORE = ("calories", "protein", "carbs", "fat")


def fold(value):
    text = unicodedata.normalize("NFKD", (value or "").casefold().replace("ı", "i"))
    return "".join(char for char in text if not unicodedata.combining(char))


def slug(value):
    return re.sub(r"[^a-z0-9]+", "-", fold(value)).strip("-")


def package_from_name(value):
    matches = list(PACKAGE.finditer(value or ""))
    if len(matches) != 1:
        return None
    amount = float(matches[0].group(1).replace(",", "."))
    unit = matches[0].group(2).lower()
    if unit == "kg":
        amount, unit = amount * 1000, "GRAM"
    elif unit in ("g", "gr"):
        unit = "GRAM"
    elif unit in ("l", "lt"):
        amount, unit = amount * 1000, "MILLILITER"
    elif unit == "cl":
        amount, unit = amount * 10, "MILLILITER"
    else:
        unit = "MILLILITER"
    return amount, unit, matches[0].span()


def family_display_name(value):
    parsed = package_from_name(value)
    if not parsed:
        return None
    start, end = parsed[2]
    return re.sub(r"\s+", " ", (value[:start] + value[end:]).strip(" ,-"))


def nutrition_matches(rows):
    first = rows[0]
    for row in rows[1:]:
        for field in CORE:
            left, right = float(first[field]), float(row[field])
            scale = max(abs(left), abs(right), 1.0)
            if abs(left - right) / scale > 0.10:
                return False
    return True


def prepare(root):
    rows = {row["id"]: row for row in csv.DictReader((root / "catalog.csv").open(encoding="utf-8-sig"))}
    reviews = json.loads((root / "general-evidence-review.json").read_text(encoding="utf-8"))
    ready, held = [], []
    for review in reviews:
        if not review.get("presentation_review_ready"):
            continue
        products = [rows[item] for item in review["product_ids"].split(";") if item in rows]
        reasons = []
        if len(products) < 2:
            reasons.append("MISSING_SNAPSHOT_PRODUCTS")
        if not review.get("all_gtins_valid"):
            reasons.append("VALID_GTINS_REQUIRED")
        if review.get("all_zero_core_profile"):
            reasons.append("ZERO_CORE_NUTRITION_NOT_AUTO_APPLICABLE")
        if any(row["catalog_type"] != "BRANDED_PRODUCT" for row in products):
            reasons.append("ONLY_BRANDED_PRODUCTS_SUPPORTED")
        if any(row["publication_status"] != "PUBLISHED" for row in products):
            reasons.append("ALL_PRODUCTS_MUST_BE_PUBLISHED")
        if len({fold(row["brand"]) for row in products}) != 1:
            reasons.append("BRAND_MISMATCH")
        if len({(row["market_region"], row["preparation_state"], row["nutrition_reference_unit"]) for row in products}) != 1:
            reasons.append("MARKET_PREPARATION_OR_REFERENCE_MISMATCH")
        if len({row["barcode"] for row in products if row["barcode"]}) != len(products):
            reasons.append("DISTINCT_BARCODES_REQUIRED")
        parsed = [package_from_name(row["display_name"] or row["name"]) for row in products]
        if any(value is None for value in parsed):
            reasons.append("EXACTLY_ONE_PACKAGE_AMOUNT_REQUIRED")
        elif len({value[1] for value in parsed}) != 1 or len({(value[0], value[1]) for value in parsed}) != len(products):
            reasons.append("DISTINCT_COMPATIBLE_PACKAGE_AMOUNTS_REQUIRED")
        if products and not nutrition_matches(products):
            reasons.append("CORE_NUTRITION_VARIANCE_EXCEEDS_10_PERCENT")
        display_names = {family_display_name(row["display_name"] or row["name"]) for row in products}
        if None in display_names or len(display_names) != 1:
            reasons.append("FAMILY_DISPLAY_NAME_MISMATCH")
        if reasons:
            held.append({"groupId": review["group_id"], "productIds": [int(row["id"]) for row in products], "reasons": reasons})
            continue
        members = sorted(zip(products, parsed), key=lambda item: item[1][0])
        representative = int(members[0][0]["id"])
        display_name = display_names.pop()
        brand_key = slug(products[0]["brand"])
        request = {
            "familyKey": f"{brand_key}:{slug(display_name.removeprefix(products[0]['brand']).strip())}",
            "displayName": display_name,
            "representativeProductId": representative,
            "evidenceReason": f"{review['reason']} Sources: {review['sources']}",
            "members": [
                {
                    "productId": int(row["id"]),
                    "packageQuantity": amount,
                    "packageUnit": unit,
                    "packageLabel": f"{amount:g} {'g' if unit == 'GRAM' else 'ml'}",
                    "defaultVariant": int(row["id"]) == representative,
                }
                for row, (amount, unit, _) in members
            ],
        }
        ready.append({"groupId": review["group_id"], "previewPath": "/api/v1/admin/product-families/preview", "request": request})
    output = {"readyCount": len(ready), "heldCount": len(held), "ready": ready, "held": held, "catalogWrites": 0}
    (root / "family-admin-preview-payloads.json").write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"readyCount": len(ready), "heldCount": len(held), "catalogWrites": 0}))
    return output


if __name__ == "__main__":
    prepare(Path(sys.argv[1] if len(sys.argv) > 1 else "outputs/package-family-audit-20261029"))

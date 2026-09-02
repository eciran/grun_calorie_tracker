#!/usr/bin/env python3
"""Live, bounded OFF verification for exact-GTIN nutrition review rows."""

import argparse
import csv
import json
import re
import time
import unicodedata
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlencode
from urllib.error import HTTPError
from urllib.request import Request, urlopen


FIELDS = "code,product_name,product_name_tr,brands,quantity,product_quantity,product_quantity_unit,nutrition_data_per,nutriments,last_modified_t,countries_tags"


def norm(value):
    value = unicodedata.normalize("NFKD", str(value or "")).encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]+", " ", value).strip()


def tokens(value):
    ignored = {"g", "gr", "kg", "ml", "l", "lt", "adet", "li", "the", "with"}
    return {part for part in norm(value).split() if len(part) > 1 and part not in ignored and not part.isdigit()}


def package_from_name(value):
    matches = list(re.finditer(r"(\d+(?:[.,]\d+)?)\s*(kg|ml|lt|l|gr|g)\b", str(value or ""), re.I))
    if not matches:
        return None
    match = matches[-1]
    amount = float(match.group(1).replace(",", "."))
    unit = match.group(2).lower()
    if unit == "kg":
        return amount * 1000, "g"
    if unit in {"lt", "l"}:
        return amount * 1000, "ml"
    return amount, "ml" if unit == "ml" else "g"


def number(mapping, *keys):
    for key in keys:
        value = mapping.get(key)
        try:
            if value is not None and str(value).strip() != "":
                return float(value)
        except (TypeError, ValueError):
            pass
    return None


def fetch(barcode, timeout, retries):
    url = f"https://world.openfoodfacts.org/api/v2/product/{barcode}.json?{urlencode({'fields': FIELDS})}"
    request = Request(url, headers={"User-Agent": "GRUNCatalogResearch/1.0 (controlled exact-GTIN validation)"})
    for attempt in range(retries + 1):
        try:
            with urlopen(request, timeout=timeout) as response:
                raw = response.read()
            return url, json.loads(raw)
        except HTTPError as exc:
            if exc.code == 404:
                return url, {"code": barcode, "status": 0, "status_verbose": "product not found"}
            if exc.code != 429 or attempt >= retries:
                raise
            retry_after = exc.headers.get("Retry-After")
            wait = float(retry_after) if retry_after and retry_after.isdigit() else min(5 * (attempt + 1), 15)
            time.sleep(wait)


def classify(row, document):
    product = document.get("product") or {}
    nutrients = product.get("nutriments") or {}
    core = {
        "calories": number(nutrients, "energy-kcal_100g"),
        "protein": number(nutrients, "proteins_100g"),
        "fat": number(nutrients, "fat_100g"),
        "carbs": number(nutrients, "carbohydrates_100g"),
    }
    exact_code = str(document.get("code") or product.get("code") or "") == row["barcode"]
    complete = all(value is not None for value in core.values())
    plausible = complete and 0 <= core["calories"] <= 1000 and all(0 <= core[k] <= 100 for k in ("protein", "fat", "carbs")) and sum(core[k] for k in ("protein", "fat", "carbs")) <= 110
    calculated = 4 * (core["protein"] or 0) + 9 * (core["fat"] or 0) + 4 * (core["carbs"] or 0)
    energy_ok = complete and abs(core["calories"] - calculated) <= max(50, core["calories"] * .25)
    off_name = product.get("product_name_tr") or product.get("product_name") or ""
    name_tokens, off_tokens = tokens(row["name"]), tokens(off_name)
    name_overlap = len(name_tokens & off_tokens) / max(1, min(len(name_tokens), len(off_tokens)))
    brand_ok = not norm(row.get("brand")) or norm(row["brand"]) in norm(product.get("brands")) or norm(product.get("brands")) in norm(row["brand"])
    identity_ok = brand_ok and name_overlap >= .5
    local_package = package_from_name(row["name"])
    off_amount = number(product, "product_quantity")
    off_unit = str(product.get("product_quantity_unit") or "").lower()
    package_known = local_package is not None and off_amount is not None and off_unit in {"g", "ml"}
    package_ok = package_known and local_package[1] == off_unit and abs(local_package[0] - off_amount) <= max(1, local_package[0] * .01)
    per_100 = str(product.get("nutrition_data_per") or "100g").lower() in {"100g", "100ml"}
    if document.get("status") != 1:
        decision = "NOT_FOUND"
    elif exact_code and plausible and energy_ok and identity_ok and package_ok and per_100:
        decision = "PROMOTABLE_AFTER_LICENSE_REVIEW"
    elif exact_code and plausible:
        decision = "MANUAL_IDENTITY_REVIEW"
    else:
        decision = "REJECT_LIVE_EVIDENCE"
    return {
        "decision": decision,
        "exactBarcode": exact_code,
        "completeCoreNutrition": complete,
        "plausibleNutrition": plausible,
        "energyConsistent": energy_ok,
        "identityMatch": identity_ok,
        "brandMatch": brand_ok,
        "nameTokenOverlap": round(name_overlap, 3),
        "packageKnown": package_known,
        "packageMatch": package_ok,
        "per100Basis": per_100,
        "offName": off_name,
        "offBrand": product.get("brands"),
        "offQuantity": product.get("quantity"),
        "nutrition": core,
        "fiber": number(nutrients, "fiber_100g"),
        "sugar": number(nutrients, "sugars_100g"),
        # OFF sodium_100g is expressed in grams; GRUN stores sodium in mg.
        "sodiumMg": None if number(nutrients, "sodium_100g") is None else round(number(nutrients, "sodium_100g") * 1000, 3),
        "lastModified": product.get("last_modified_t"),
        "countriesTags": product.get("countries_tags") or [],
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--priority", default="P0")
    parser.add_argument("--limit", type=int, default=10)
    parser.add_argument("--skip", type=int, default=0)
    parser.add_argument("--timeout", type=int, default=20)
    parser.add_argument("--delay-ms", type=int, default=250)
    parser.add_argument("--retries", type=int, default=2)
    parser.add_argument("--previous")
    args = parser.parse_args()
    with Path(args.input).open(encoding="utf-8-sig", newline="") as handle:
        rows = [row for row in csv.DictReader(handle, delimiter="\t") if row["decision"] == "REVIEW_EXACT_GTIN_NUTRITION" and row["priority"] == args.priority]
    rows = rows[args.skip:args.skip + args.limit]
    previous_results = []
    if args.previous:
        previous_results = json.loads(Path(args.previous).read_text(encoding="utf-8"))["products"]
        retry_barcodes = {row["barcode"] for row in previous_results if row["decision"] == "FETCH_ERROR" and "429" in str(row.get("error"))}
        rows = [row for row in rows if row["barcode"] in retry_barcodes]
    results = []
    for row in rows:
        try:
            url, document = fetch(row["barcode"], args.timeout, args.retries)
            result = {"catalogId": row["source_catalog_id"], "barcode": row["barcode"], "catalogName": row["name"], "catalogBrand": row["brand"], "sourceUrl": url, **classify(row, document), "error": None}
        except Exception as exc:
            result = {"catalogId": row["source_catalog_id"], "barcode": row["barcode"], "catalogName": row["name"], "catalogBrand": row["brand"], "decision": "FETCH_ERROR", "error": f"{type(exc).__name__}: {exc}"}
        results.append(result)
        time.sleep(max(0, args.delay_ms) / 1000)
    if previous_results:
        refreshed = {row["barcode"]: row for row in results}
        results = [refreshed.get(row["barcode"], row) for row in previous_results]
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    document = {"schemaVersion": 1, "generatedAt": datetime.now(timezone.utc).isoformat(), "classification": "LIVE_EVIDENCE_REVIEW_NOT_APPLIED", "sourceLicense": "ODbL-1.0", "priority": args.priority, "skip": args.skip, "limit": args.limit, "counts": {decision: sum(row["decision"] == decision for row in results) for decision in sorted({row["decision"] for row in results})}, "products": results}
    output.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"rows": len(results), "counts": document["counts"], "output": str(output)}, ensure_ascii=False))


if __name__ == "__main__":
    main()

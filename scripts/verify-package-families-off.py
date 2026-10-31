"""Read-only Open Food Facts evidence scan for package-family candidates."""
from __future__ import annotations

import csv
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import time
import urllib.error
import urllib.request

ROOT = Path("outputs/package-family-audit-20261029")
CACHE = ROOT / "off-source-evidence"
FIELDS = (
    "code,product_name,product_name_en,product_name_tr,brands,quantity,"
    "nutrition_data_per,nutriments,countries_tags,last_modified_t"
)
CORE = {
    "calories": "energy-kcal_100g",
    "protein": "proteins_100g",
    "carbs": "carbohydrates_100g",
    "fat": "fat_100g",
    "fiber": "fiber_100g",
    "sugar": "sugars_100g",
}
PACK_RE = re.compile(
    r"(?<!\w)(?:(\d+)\s*[xX]\s*)?(\d+(?:[.,]\d+)?)\s*(kg|mg|gr|g|ml|cl|lt|l)(?!\w)",
    re.IGNORECASE,
)


def normalized_package(value: str | None) -> list[tuple[int, str, str]]:
    result = []
    for count, amount, unit in PACK_RE.findall(value or ""):
        number = float(amount.replace(",", "."))
        unit = unit.lower()
        if unit == "kg":
            number, unit = number * 1000, "g"
        elif unit == "mg":
            number, unit = number / 1000, "g"
        elif unit in ("l", "lt"):
            number, unit = number * 1000, "ml"
        elif unit == "cl":
            number, unit = number * 10, "ml"
        elif unit == "gr":
            unit = "g"
        result.append((int(count or 1), f"{number:g}", unit))
    return sorted(result)


def number(value):
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def nutrition_comparison(product: dict, off: dict) -> dict:
    nutriments = off.get("nutriments") or {}
    comparisons = []
    for local_key, off_key in CORE.items():
        local = number(product.get(local_key))
        remote = number(nutriments.get(off_key))
        if local is None or remote is None:
            state = "MISSING"
        else:
            tolerance = max(0.2, abs(remote) * 0.03)
            state = "MATCH" if abs(local - remote) <= tolerance else "DIFFERENT"
        comparisons.append(
            {"field": local_key, "catalog": local, "off": remote, "state": state}
        )
    comparable = [row for row in comparisons if row["state"] != "MISSING"]
    return {
        "fields": comparisons,
        "comparableCount": len(comparable),
        "matchCount": sum(row["state"] == "MATCH" for row in comparable),
        "differentCount": sum(row["state"] == "DIFFERENT" for row in comparable),
    }


def fetch(barcode: str, refresh: bool = False) -> dict:
    CACHE.mkdir(exist_ok=True)
    target = CACHE / f"{barcode}.json"
    if target.exists() and not refresh:
        return json.loads(target.read_text(encoding="utf-8"))
    url = f"https://world.openfoodfacts.org/api/v2/product/{barcode}.json?fields={FIELDS}"
    result = {
        "barcode": barcode,
        "url": url,
        "retrievedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
    }
    try:
        request = urllib.request.Request(
            url,
            headers={
                "User-Agent": "GRunCatalogEvidence/0.1 (catalog audit; contact via project owner)",
                "Accept": "application/json",
            },
        )
        with urllib.request.urlopen(request, timeout=20) as response:
            body = response.read(1024 * 1024 + 1)
            if len(body) > 1024 * 1024:
                raise ValueError("OFF response exceeds 1 MiB")
        payload = json.loads(body)
        result.update(
            status="FOUND" if payload.get("status") == 1 else "NOT_FOUND",
            responseSha256=hashlib.sha256(body).hexdigest(),
            product=payload.get("product") or {},
        )
    except (OSError, ValueError, urllib.error.URLError) as error:
        result.update(status="UNAVAILABLE", error=str(error))
    target.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    time.sleep(0.25)
    return result


def product_decision(product: dict, evidence: dict) -> dict:
    remote = evidence.get("product") or {}
    local_package = normalized_package(product.get("name"))
    remote_package = normalized_package(remote.get("quantity"))
    if not remote_package:
        remote_package = normalized_package(remote.get("product_name"))
    package_state = (
        "MATCH"
        if local_package and local_package == remote_package
        else "MISSING"
        if not remote_package
        else "DIFFERENT"
    )
    nutrition = nutrition_comparison(product, remote)
    identity_ready = evidence.get("status") == "FOUND" and package_state == "MATCH"
    nutrition_ready = (
        nutrition["comparableCount"] >= 4 and nutrition["differentCount"] == 0
    )
    return {
        "id": product["id"],
        "name": product["name"],
        "barcode": product.get("barcode"),
        "status": evidence.get("status"),
        "offName": remote.get("product_name"),
        "offBrands": remote.get("brands"),
        "offQuantity": remote.get("quantity"),
        "offLastModified": remote.get("last_modified_t"),
        "packageState": package_state,
        "identityEvidenceReady": identity_ready,
        "nutritionEvidenceReady": nutrition_ready,
        "nutrition": nutrition,
    }


def main() -> None:
    groups = [
        group
        for group in json.loads((ROOT / "candidates.json").read_text(encoding="utf-8"))
        if group["decision"] == "PACKAGE_FAMILY_CANDIDATE"
    ]
    products = [product for group in groups for product in group["products"]]
    barcodes = sorted(
        {
            str(product.get("barcode") or "")
            for product in products
            if str(product.get("barcode") or "").isdigit()
        }
    )
    evidence = {}
    for index, barcode in enumerate(barcodes, 1):
        evidence[barcode] = fetch(barcode)
        if index % 25 == 0:
            print(f"OFF evidence {index}/{len(barcodes)}", flush=True)

    reviewed = []
    for group in groups:
        rows = []
        for product in group["products"]:
            barcode = str(product.get("barcode") or "")
            rows.append(
                product_decision(
                    product,
                    evidence.get(barcode, {"status": "NO_VALID_GTIN", "product": {}}),
                )
            )
        reviewed.append(
            {
                "group_id": group["group_id"],
                "products": rows,
                "allIdentityEvidenceReady": all(row["identityEvidenceReady"] for row in rows),
                "allNutritionEvidenceReady": all(row["nutritionEvidenceReady"] for row in rows),
                "safeToApply": False,
            }
        )

    summary = {
        "groups": len(reviewed),
        "products": len(products),
        "validGtinsQueried": len(barcodes),
        "foundProducts": sum(value.get("status") == "FOUND" for value in evidence.values()),
        "groupsWithCompleteOffIdentityEvidence": sum(
            group["allIdentityEvidenceReady"] for group in reviewed
        ),
        "groupsWithCompleteOffNutritionEvidence": sum(
            group["allNutritionEvidenceReady"] for group in reviewed
        ),
        "catalogWrites": 0,
        "limitations": [
            "OFF is community-maintained evidence, not automatic approval.",
            "Package and nutrition matches do not prove formula or market identity.",
            "Missing and zero values are kept distinct.",
        ],
    }
    (ROOT / "off-review.json").write_text(
        json.dumps(reviewed, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (ROOT / "off-review-summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    with (ROOT / "off-review.csv").open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "group_id", "id", "name", "barcode", "status", "off_name",
                "off_brands", "off_quantity", "package_state", "comparable_nutrients",
                "matching_nutrients", "different_nutrients", "safe_to_apply",
            ],
        )
        writer.writeheader()
        for group in reviewed:
            for row in group["products"]:
                writer.writerow(
                    {
                        "group_id": group["group_id"], "id": row["id"],
                        "name": row["name"], "barcode": row["barcode"],
                        "status": row["status"], "off_name": row["offName"],
                        "off_brands": row["offBrands"], "off_quantity": row["offQuantity"],
                        "package_state": row["packageState"],
                        "comparable_nutrients": row["nutrition"]["comparableCount"],
                        "matching_nutrients": row["nutrition"]["matchCount"],
                        "different_nutrients": row["nutrition"]["differentCount"],
                        "safe_to_apply": False,
                    }
                )
    print(json.dumps(summary, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()

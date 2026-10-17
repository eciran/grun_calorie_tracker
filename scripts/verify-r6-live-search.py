"""Authenticated GET-only acceptance; credentials remain in process memory."""
import argparse
import hashlib
import json
import os
import time
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlencode, urlparse
from urllib.request import Request, HTTPRedirectHandler, build_opener

ORIGIN = "https://api-staging.gruncalorietracker.com"
NUTRIENTS = {key: key for key in ("calories", "protein", "carbs", "fat", "fiber", "sugar", "sodium", "cholesterol",
    "potassium", "calcium", "iron", "magnesium", "zinc")}
NUTRIENTS.update(vitaminA="vitamin_a", vitaminC="vitamin_c", vitaminD="vitamin_d", vitaminE="vitamin_e",
                 vitaminB12="vitamin_b12", saturatedFat="saturated_fat", transFat="trans_fat", sugarAlcohol="sugar_alcohol")


class SameOriginRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, url):
        if urlparse(url).netloc != urlparse(ORIGIN).netloc or urlparse(url).scheme != "https":
            raise ValueError("Credential-bearing cross-origin redirect refused")
        return super().redirect_request(request, fp, code, message, headers, url)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def expected_ids(rows, food_by_id, taxonomy, category, catalog_type):
    # Current API contract includes the selected category and direct children.
    allowed = {category} | {c["id"] for c in taxonomy.values() if c["parent_id"] == category}
    return {r["food_item_id"] for r in rows if r["category_id"] in allowed
            and food_by_id[r["food_item_id"]]["catalog_type"] == catalog_type}


def run(args):
    token = os.environ.get("GRUN_CATALOG_ACCEPTANCE_TOKEN")
    if not token:
        raise ValueError("Application token must be supplied in process environment")
    package = json.loads((args.package / "manifest.json").read_text(encoding="utf-8"))
    names = json.loads((args.package / "applied-names/receipt.json").read_text(encoding="utf-8"))["rows"]
    categories = json.loads((args.category_receipt / "receipt.json").read_text(encoding="utf-8"))["rows"]
    for folder in (args.package / "applied-names", args.category_receipt):
        summary = json.loads((folder / "summary.json").read_text())
        if summary["status"] != "APPLIED_RECEIPT_COMPLETE" or summary["receiptSha256"] != sha(folder / "receipt.json"):
            raise ValueError("Intact committed receipt required")
    opener = build_opener(SameOriginRedirect())
    calls = 0
    checks, errors, searches, filters = [], [], [], []

    def get(path, **parameters):
        nonlocal calls
        if calls >= 220:
            raise ValueError("Bounded acceptance request budget exhausted")
        calls += 1
        request = Request(ORIGIN + path + ("?" + urlencode(parameters) if parameters else ""),
                          headers={"Authorization": "Bearer " + token, "Accept": "application/json"})
        started = time.monotonic()
        try:
            with opener.open(request, timeout=25) as response:
                value = json.load(response)
        except HTTPError as error:
            raise ValueError("API HTTP " + str(error.code) + " at " + path) from None
        elapsed = round((time.monotonic() - started) * 1000)
        time.sleep(.2)
        return value, elapsed

    def check(condition, label):
        (checks if condition else errors).append(label)

    try:
        tree, _ = get("/api/v1/products/categories/tree")
        api_categories = {}

        def visit(nodes):
            for category in nodes:
                api_categories[category["id"]] = category
                visit(category.get("children") or [])

        visit(tree)
        taxonomy = {c["id"]: c for c in package["categories"].values()}
        check(set(api_categories) == {c["id"] for c in taxonomy.values() if c["active"]}, "ACTIVE_CATEGORY_TREE_IDS")
        check(all(api_categories.get(i, {}).get("nameEn") == c["name_en"] and api_categories.get(i, {}).get("nameTr") == c["name_tr"]
                  for i, c in taxonomy.items() if c["active"]), "BILINGUAL_CATEGORY_TREE_LABELS")
        for row in names:
            for language in ("EN", "TR"):
                product, _ = get("/api/v1/products/" + row["id"], language=language)
                locale = next(l for l in row["name_review"]["localizations"] if l["language"] == language and l["active"])
                check(product.get("id") == int(row["id"]) and product.get("language") == language and all(product.get(k) == locale["display_name"]
                      for k in ("productName", "displayName", "shortDisplayName")), "NAME_" + row["id"] + "_" + language)
                check(all(product.get(k) == row["food"][v] for k, v in NUTRIENTS.items()), "NUTRIENTS_21_" + row["id"] + "_" + language)
                check(product.get("preparationState") == row["food"]["preparation_state"] and product.get("nutritionReferenceUnit") == row["food"]["nutrition_reference_unit"],
                      "PREPARATION_BASIS_" + row["id"] + "_" + language)
                check({o["id"] for o in product.get("servingOptions") or []} == {o["id"] for o in row["name_review"]["options"] if o["user_visible"] and o["quality_status"] == "VERIFIED"},
                      "PORTION_IDS_" + row["id"] + "_" + language)
        for language, region, queries in (("EN", "UK_IE", ["banana", "broccoli", "milk", "chicken breast", "rice"]),
                                         ("TR", "TR", ["muz", "brokoli", "süt", "tavuk göğsü", "pirinç"])):
            for query in queries:
                page, elapsed = get("/api/v1/products/search", q=query, language=language, region=region, page=0, size=37)
                content = page.get("content") or []
                check(bool(content) and page.get("totalElements", 0) > 0, "SEARCH_RESULTS_" + language + "_" + query)
                check(len({p["id"] for p in content}) == len(content), "SEARCH_NO_REPEATED_IDS_" + language + "_" + query)
                searches.append({"q": query, "language": language, "region": region, "total": page.get("totalElements"), "ms": elapsed,
                    "top5": [{k: p.get(k) for k in ("id", "productName", "canonicalName", "catalogType", "preparationState", "nutritionReferenceUnit")} for p in content[:5]]})
        food_by_id = {int(r["id"]): r["food"] for r in package["currentRows"]}
        pairs = {(r["category_id"], food_by_id[r["food_item_id"]]["catalog_type"]) for r in categories}
        # Include root filters as well as direct subcategories.
        pairs |= {(taxonomy[c]["parent_id"], kind) for c, kind in pairs if taxonomy[c]["parent_id"] is not None}
        covered = set()
        for category, kind in sorted(pairs):
            expected = expected_ids(categories, food_by_id, taxonomy, category, kind)
            regions = []
            combined = set()
            for language, region in (("EN", "UK_IE"), ("TR", "TR")):
                received = set()
                for number in range(10):
                    page, _ = get("/api/v1/products/search", categoryId=category, catalogType=kind, language=language, region=region, page=number, size=100)
                    items = page.get("content") or []
                    ids = [p["id"] for p in items]
                    check(len(ids) == len(set(ids)) and not (received & set(ids)), f"FILTER_NO_DUPLICATE_{category}_{kind}_{region}_{number}")
                    check(all(p.get("catalogType") == kind for p in items), f"FILTER_TYPE_{category}_{kind}_{region}_{number}")
                    received.update(ids)
                    if page.get("last"):
                        break
                else:
                    raise ValueError("Category pagination exceeded bound")
                check(received <= expected, f"FILTER_MEMBERSHIP_{category}_{kind}_{region}")
                combined |= received
                regions.append({"region": region, "language": language, "returned": len(received)})
            check(combined == expected, f"FILTER_COMPLETE_{category}_{kind}")
            covered |= combined & expected
            filters.append({"categoryId": category, "slug": taxonomy[category]["slug"], "catalogType": kind,
                            "expected": len(expected), "found": len(combined), "regions": regions})
        check(covered == {r["food_item_id"] for r in categories} and len(covered) == 578, "ALL_578_APPROVED_PRODUCTS_FILTER_REACHABLE")
    except Exception as error:
        errors.append(str(error))
    result = {"status": "PASS" if not errors else "FAIL", "time": datetime.now(timezone.utc).isoformat(), "origin": ORIGIN,
        "requests": calls, "passedChecks": len(checks), "errors": errors, "checks": checks, "searches": searches, "categoryFilters": filters,
        "manifestSha256": sha(args.package / "manifest.json"), "producerSha256": sha(Path(__file__)), "credentialsWritten": False,
        "httpMethods": ["GET"], "ranking": "TOP5_OBSERVED_NOT_A_GOLDEN_RELEVANCE_CERTIFICATE",
        "limitations": ["GET search may record normal search telemetry; no selection, food-log or catalog mutation endpoints called.",
                         "English UK_IE and Turkish TR coverage are combined for category reachability."]}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": result["status"], "requests": calls, "passedChecks": len(checks), "errors": errors,
                      "searchCases": len(searches), "filterCases": len(filters)}, ensure_ascii=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--package", type=Path, required=True)
    parser.add_argument("--category-receipt", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    run(parser.parse_args())

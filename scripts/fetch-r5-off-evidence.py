"""Bounded, resumable exact-GTIN source collection. No DB access or writes.

One complete OFF response per file. At most 15 product GETs/minute; stop on
blocking errors rather than retrying through rate limiting or outages.
"""
import argparse
import csv
import importlib.util
import json
import time
import urllib.error
import urllib.request
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

spec = importlib.util.spec_from_file_location("source_options", Path(__file__).with_name("prepare-r5-source-options.py"))
options = importlib.util.module_from_spec(spec)
spec.loader.exec_module(options)
API = "https://world.openfoodfacts.org/api/v2/product/"
FIELDS = "code,product_name,product_name_en,product_name_tr,generic_name,brands,categories_tags,countries_tags,ingredients_text,ingredients_tags,allergens_tags,nutrition_data_per,nutrition_data_prepared_per,nutrition_data,no_nutrition_data,serving_size,serving_quantity,serving_quantity_unit,product_quantity,product_quantity_unit,quantity,nutriments,nutrition,schema_version,last_modified_t"


def candidates(queue, priority_folder):
    with queue.open(encoding="utf-8-sig", newline="") as stream:
        records = list(csv.DictReader(stream))
    codes = {}
    for row in records:
        key = row.get("source_key", "")
        code = key.removeprefix("barcode:") if key.startswith("barcode:") else ""
        if options.r5.valid_gtin(code):
            codes.setdefault(code, []).append(row["id"])
    priority = {}
    if priority_folder:
        for path in sorted(priority_folder.glob("*.json")):
            body = json.loads(path.read_text(encoding="utf-8-sig"))
            code, product = str(body.get("barcode", "")), body.get("product") or {}
            if code in codes:
                basis = product.get("nutrition_data_per")
                priority[code] = 0 if basis == "serving" else 1 if basis == "100ml" else 2
    return sorted(codes.items(), key=lambda item: (priority.get(item[0], 3), item[0]))


def cached_valid(body, code, now):
    try:
        checked = datetime.fromisoformat(body["checkedAt"].replace("Z", "+00:00"))
        age = (now - checked).total_seconds()
        product = body.get("product") or {}
        return (body.get("sourceUrl", "").startswith(API + code + ".json?")
                and body.get("barcode") == code and body.get("responseFingerprint") == options.r5.fingerprint(body.get("response"))
                and body.get("response", {}).get("product") == product and str(product.get("code")) == code
                and body.get("status") == "FETCHED" and 0 <= age <= 30 * 86400)
    except (ValueError, TypeError, KeyError):
        return False


def run(args):
    if not 4500 <= args.delay_ms <= 60000 or not 1 <= args.max_requests <= 100:
        raise ValueError("Use delay >=4500ms and a bounded batch of 1..100 product requests")
    args.output.mkdir(parents=True, exist_ok=True)
    items = args.output / "items"
    items.mkdir(exist_ok=True)
    counts, requests, last_started = Counter(), 0, None
    inputs = candidates(args.queue, args.priority_evidence)
    for code, ids in inputs:
        path = items / ("barcode-" + code + ".json")
        if path.exists():
            body = json.loads(path.read_text(encoding="utf-8"))
            if cached_valid(body, code, datetime.now(timezone.utc)):
                counts["CACHED_COMPLETE"] += 1
                continue
            # Preserve previous source versions; never silently overwrite evidence.
            counts["EXISTING_RECHECK_REQUIRED"] += 1
            continue
        if requests >= args.max_requests:
            break
        if last_started is not None:
            time.sleep(max(0, args.delay_ms / 1000 - (time.monotonic() - last_started)))
        url = API + code + ".json?fields=" + FIELDS
        request = urllib.request.Request(url, headers={"User-Agent": "GRunCatalogReview/2.0 (bounded read-only portion/source audit)", "Accept": "application/json"})
        last_started = time.monotonic()
        requests += 1
        body = {"barcode": code, "catalogIds": ids, "sourceUrl": url, "checkedAt": datetime.now(timezone.utc).isoformat(),
                "status": "FETCH_FAILED", "applyAllowed": False}
        stop = False
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                if response.geturl() != url:
                    raise ValueError("Unexpected source redirect")
                raw = response.read(4 * 1024 * 1024 + 1)
                if len(raw) > 4 * 1024 * 1024:
                    raise ValueError("Source reply exceeds evidence size bound")
                data = json.loads(raw)
            if not isinstance(data, dict):
                raise ValueError("Expected product response object")
            product = data.get("product") or {}
            status = "FETCHED" if data.get("status") == 1 and str(product.get("code")) == code else "NOT_FOUND" if data.get("status") == 0 else "SOURCE_IDENTITY_CONFLICT"
            body.update(status=status, product=product, response=data, responseFingerprint=options.r5.fingerprint(data))
        except urllib.error.HTTPError as exc:
            body.update(httpStatus=exc.code, status="NOT_FOUND" if exc.code == 404 else "FETCH_FAILED")
            stop = exc.code != 404
        except (OSError, ValueError) as exc:
            body["errorType"] = type(exc).__name__
            stop = True
        partial = path.with_suffix(".json.partial")
        partial.write_text(json.dumps(body, ensure_ascii=False, allow_nan=False) + "\n", encoding="utf-8")
        partial.replace(path)
        counts[body["status"]] += 1
        print(json.dumps({"request": requests, "barcode": code, "status": body["status"], "httpStatus": body.get("httpStatus")}), flush=True)
        if stop:
            counts["STOPPED_ON_PROVIDER_OR_TRANSPORT_ERROR"] += 1
            break
    summary = {"source": API, "queuedUniqueGtins": len(inputs), "requests": requests, "counts": dict(counts),
               "delayMs": args.delay_ms, "maxRequests": args.max_requests, "queueSha256": options.r5.closure.sha(args.queue),
               "completedAt": datetime.now(timezone.utc).isoformat(), "databaseWrites": 0,
               "fullQueueCompleted": counts["FETCHED"] + counts["NOT_FOUND"] + counts["CACHED_COMPLETE"] == len(inputs)
                    and not counts["STOPPED_ON_PROVIDER_OR_TRANSPORT_ERROR"]}
    (args.output / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--queue", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--priority-evidence", type=Path)
    parser.add_argument("--max-requests", type=int, default=20)
    parser.add_argument("--delay-ms", type=int, default=4500)
    run(parser.parse_args())

"""Preflight, apply, or rollback one warning-free product-family batch."""

import argparse
import json
import os
import urllib.error
import urllib.request
from pathlib import Path


def save(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def call(base_url, token, method, path, payload=None):
    request = urllib.request.Request(
        f"{base_url.rstrip('/')}{path}",
        data=None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Authorization": f"Bearer {token}", "Accept": "application/json",
                 "Content-Type": "application/json"},
        method=method,
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            body = response.read()
            return json.loads(body) if body else None
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {path} failed with HTTP {error.code}: {body[:500]}") from error


def load_batch(source_path, preview_path, expected_count):
    source = json.loads(source_path.read_text(encoding="utf-8-sig"))
    preview = json.loads(preview_path.read_text(encoding="utf-8-sig"))
    payloads = {row["groupId"]: row for row in source["payloads"]}
    ready = [row for row in preview["results"] if row.get("valid") and not row.get("warnings")]
    if len(ready) != expected_count:
        raise ValueError(f"Expected exactly {expected_count} warning-free families, found {len(ready)}")
    if len({row["groupId"] for row in ready}) != expected_count:
        raise ValueError("Family group ids are not unique")
    if any(row["groupId"] not in payloads for row in ready):
        raise ValueError("A previewed family payload is missing from the source batch")
    return [(row, payloads[row["groupId"]]["request"]) for row in ready]


def preflight(base_url, token, families):
    checks = []
    for index, (review, request) in enumerate(families, start=1):
        live = call(base_url, token, "POST", "/api/v1/admin/product-families/preview", request)
        errors, warnings = live.get("errors", []), live.get("warnings", [])
        checks.append({"groupId": review["groupId"], "ready": not errors and not warnings,
                       "errors": errors, "warnings": warnings})
        if index % 25 == 0:
            print(json.dumps({"preflight": index, "total": len(families)}))
    return checks


def apply(base_url, token, families, receipt_path):
    if receipt_path.exists():
        receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
        if receipt.get("version") != "PRODUCT_FAMILY_BATCH_APPLY_V1" or receipt.get("completed"):
            raise ValueError("Only an incomplete owned receipt can be resumed")
    else:
        receipt = {"version": "PRODUCT_FAMILY_BATCH_APPLY_V1", "expectedCount": len(families),
                   "applications": [], "completed": False}
    applied_ids = {row["groupId"] for row in receipt["applications"]}
    save(receipt_path, receipt)
    try:
        for index, (review, request) in enumerate(families, start=1):
            if review["groupId"] in applied_ids:
                continue
            live = call(base_url, token, "POST", "/api/v1/admin/product-families/preview", request)
            if live.get("errors") or live.get("warnings"):
                raise RuntimeError(f"Family preview changed before apply: {review['groupId']}")
            result = call(base_url, token, "POST", "/api/v1/admin/product-families", request)
            receipt["applications"].append({"groupId": review["groupId"], "familyId": result["id"],
                                             "familyKey": result["familyKey"],
                                             "memberCount": len(result.get("members", []))})
            save(receipt_path, receipt)
            if index % 25 == 0:
                print(json.dumps({"applied": index, "total": len(families)}))
    except Exception as error:
        receipt["failure"] = str(error)
        save(receipt_path, receipt)
        raise
    receipt.pop("failure", None)
    receipt["completed"] = True
    save(receipt_path, receipt)
    return receipt


def rollback(base_url, token, receipt_path):
    receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
    result = {"version": "PRODUCT_FAMILY_BATCH_ROLLBACK_V1", "families": [], "completed": False}
    for row in reversed(receipt.get("applications", [])):
        response = call(base_url, token, "DELETE", f"/api/v1/admin/product-families/{row['familyId']}",
                        {"reason": "Controlled rollback of warning-free product-family batch"})
        result["families"].append({"groupId": row["groupId"], "familyId": row["familyId"],
                                   "response": response})
    result["completed"] = True
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--preview", type=Path, required=True)
    parser.add_argument("--expected-count", type=int, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--base-url", default="https://api-staging.gruncalorietracker.com")
    parser.add_argument("--token", default=os.environ.get("GRUN_ADMIN_TOKEN"))
    parser.add_argument("--mode", choices=("preflight", "apply", "rollback"), default="preflight")
    args = parser.parse_args()
    if not args.token:
        parser.error("Provide --token or GRUN_ADMIN_TOKEN; the token is never written to output")
    families = load_batch(args.source, args.preview, args.expected_count)
    if args.mode == "rollback":
        result = rollback(args.base_url, args.token, args.output)
        save(args.output.with_suffix(".rollback.json"), result)
        print(json.dumps({"rolledBack": len(result["families"]), "completed": result["completed"]}))
        return
    checks = preflight(args.base_url, args.token, families)
    if args.mode == "preflight":
        report = {"expectedCount": args.expected_count, "ready": sum(row["ready"] for row in checks),
                  "held": sum(not row["ready"] for row in checks), "catalogWrites": 0, "checks": checks}
        save(args.output, report)
        print(json.dumps({key: report[key] for key in ("expectedCount", "ready", "held", "catalogWrites")}))
        return
    if not all(row["ready"] for row in checks):
        raise RuntimeError("Apply refused because preflight has held candidates")
    result = apply(args.base_url, args.token, families, args.output)
    print(json.dumps({"applied": len(result["applications"]), "completed": result["completed"]}))


if __name__ == "__main__":
    main()

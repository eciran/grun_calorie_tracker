"""Preflight/apply/rollback source-reviewed product families and search collapses."""

import argparse
import json
import os
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


def call(base_url, token, method, path, payload=None, query=None):
    url = f"{base_url.rstrip('/')}{path}"
    if query:
        url += "?" + urllib.parse.urlencode(query)
    request = urllib.request.Request(
        url,
        data=None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/json",
            "Content-Type": "application/json",
        },
        method=method,
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            body = response.read()
            return response.status, json.loads(body) if body else None
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        try:
            detail = json.loads(body)
        except json.JSONDecodeError:
            detail = {"message": body[:500]}
        raise RuntimeError(f"{method} {path} failed with HTTP {error.code}: {detail}") from error


def save(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def load_plan(duplicate_path, family_source_path, family_preview_path):
    duplicate = json.loads(duplicate_path.read_text(encoding="utf-8"))
    family_source = json.loads(family_source_path.read_text(encoding="utf-8"))
    family_preview = json.loads(family_preview_path.read_text(encoding="utf-8"))
    payloads = {row["groupId"]: row for row in family_source["payloads"]}

    duplicate_ready = [row for row in duplicate["groups"] if row.get("liveVerified")]
    for row in duplicate_ready:
        decision = row.get("decisionPayload") or {}
        if "survivorProductId" not in decision and "survivorFoodItemId" in decision:
            decision["survivorProductId"] = decision.pop("survivorFoodItemId")
    family_ready = [row for row in family_preview["results"] if row.get("valid") and not row.get("warnings")]
    if len(duplicate_ready) != duplicate.get("liveVerified") or len(family_ready) != 366:
        raise ValueError("Reviewed candidate counts changed")
    if any(row["groupId"] not in payloads for row in family_ready):
        raise ValueError("Family preview payload is missing")
    return duplicate_ready, [(row, payloads[row["groupId"]]) for row in family_ready]


def preflight(base_url, token, duplicate_rows, family_rows):
    duplicate_checks = []
    for index, row in enumerate(duplicate_rows, start=1):
        _, live = call(base_url, token, "GET", "/api/v1/admin/products/duplicates/branded/exact", query={
            "brandKey": row["brandKey"], "nameKey": row["nameKey"]
        })
        actual = sorted(int(candidate["productId"]) for candidate in live.get("candidates", []))
        ready = (
            actual == sorted(row["liveProductIds"])
            and live.get("candidateFingerprint") == row["candidateFingerprint"]
            and live.get("storedDecision") is None
            and live.get("searchCollapse") is None
        )
        duplicate_checks.append({"brandKey": row["brandKey"], "nameKey": row["nameKey"], "ready": ready})
        if index % 50 == 0:
            print(json.dumps({"duplicatePreflight": index, "total": len(duplicate_rows)}))

    family_checks = []
    for index, (review, source) in enumerate(family_rows, start=1):
        _, live = call(base_url, token, "POST", "/api/v1/admin/product-families/preview", source["request"])
        errors = live.get("errors", [])
        warnings = live.get("warnings", [])
        family_checks.append({"groupId": review["groupId"], "ready": not errors and not warnings,
                              "errors": errors, "warnings": warnings})
        if index % 50 == 0:
            print(json.dumps({"familyPreflight": index, "total": len(family_rows)}))
    return duplicate_checks, family_checks


def apply(base_url, token, duplicate_rows, family_rows, receipt_path):
    if receipt_path.exists():
        receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
        if receipt.get("version") != "PRODUCT_FAMILY_COLLAPSE_APPLY_V1" or receipt.get("completed"):
            raise ValueError("Only an incomplete owned apply receipt can be resumed")
        receipt.pop("failure", None)
    else:
        receipt = {
            "version": "PRODUCT_FAMILY_COLLAPSE_APPLY_V1",
            "duplicateScope": 183,
            "familyScope": 366,
            "duplicateApplications": [],
            "familyApplications": [],
            "completed": False,
        }
    applied_duplicates = {(row["brandKey"], row["nameKey"])
                          for row in receipt["duplicateApplications"]}
    applied_families = {row["groupId"] for row in receipt["familyApplications"]}
    save(receipt_path, receipt)
    try:
        for index, row in enumerate(duplicate_rows, start=1):
            if (row["brandKey"], row["nameKey"]) in applied_duplicates:
                continue
            call(base_url, token, "POST", "/api/v1/admin/products/duplicates/branded/decision",
                 row["decisionPayload"])
            try:
                _, collapsed = call(base_url, token, "POST", "/api/v1/admin/products/duplicates/branded/search-collapse",
                                    row["collapsePayload"])
            except Exception:
                call(base_url, token, "DELETE", "/api/v1/admin/products/duplicates/branded/decision", query={
                    "brandKey": row["brandKey"], "nameKey": row["nameKey"],
                    "reason": "Automatic rollback because search collapse application failed",
                })
                raise
            receipt["duplicateApplications"].append({
                "brandKey": row["brandKey"], "nameKey": row["nameKey"],
                "candidateFingerprint": row["candidateFingerprint"], "response": collapsed,
            })
            save(receipt_path, receipt)
            if index % 25 == 0:
                print(json.dumps({"duplicateApplied": index, "total": len(duplicate_rows)}))

        for index, (review, source) in enumerate(family_rows, start=1):
            if review["groupId"] in applied_families:
                continue
            _, preview = call(base_url, token, "POST", "/api/v1/admin/product-families/preview", source["request"])
            if preview.get("errors") or preview.get("warnings"):
                raise RuntimeError(f"Family preview changed before apply: {review['groupId']}")
            _, applied = call(base_url, token, "POST", "/api/v1/admin/product-families", source["request"])
            receipt["familyApplications"].append({
                "groupId": review["groupId"], "familyId": applied["id"],
                "familyKey": applied["familyKey"], "memberCount": len(applied.get("members", [])),
            })
            save(receipt_path, receipt)
            if index % 25 == 0:
                print(json.dumps({"familyApplied": index, "total": len(family_rows)}))
    except Exception as error:
        receipt["failure"] = str(error)
        save(receipt_path, receipt)
        raise
    receipt["completed"] = True
    save(receipt_path, receipt)
    return receipt


def rollback(base_url, token, receipt_path):
    receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
    result = {"families": [], "duplicates": [], "completed": False}
    for row in reversed(receipt.get("familyApplications", [])):
        _, response = call(base_url, token, "DELETE", f"/api/v1/admin/product-families/{row['familyId']}", {
            "reason": "Controlled rollback of the reviewed product-family release"
        })
        result["families"].append({"familyId": row["familyId"], "response": response})
    for row in reversed(receipt.get("duplicateApplications", [])):
        query = {"brandKey": row["brandKey"], "nameKey": row["nameKey"],
                 "reason": "Controlled rollback of the reviewed duplicate-search release"}
        _, collapse = call(base_url, token, "DELETE", "/api/v1/admin/products/duplicates/branded/search-collapse", query=query)
        call(base_url, token, "DELETE", "/api/v1/admin/products/duplicates/branded/decision", query=query)
        result["duplicates"].append({"brandKey": row["brandKey"], "nameKey": row["nameKey"],
                                     "response": collapse})
    result["completed"] = True
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--duplicates", type=Path, required=True)
    parser.add_argument("--family-source", type=Path, required=True)
    parser.add_argument("--family-preview", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--base-url", default="https://api-staging.gruncalorietracker.com")
    parser.add_argument("--token", default=os.environ.get("GRUN_ADMIN_TOKEN"))
    parser.add_argument("--mode", choices=("preflight", "apply", "rollback"), default="preflight")
    args = parser.parse_args()
    if not args.token:
        parser.error("Provide --token or GRUN_ADMIN_TOKEN; the token is never written to output")
    duplicates, families = load_plan(args.duplicates, args.family_source, args.family_preview)
    if args.mode == "preflight":
        duplicate_checks, family_checks = preflight(args.base_url, args.token, duplicates, families)
        report = {
            "duplicateReady": sum(row["ready"] for row in duplicate_checks),
            "duplicateHeld": sum(not row["ready"] for row in duplicate_checks),
            "familyReady": sum(row["ready"] for row in family_checks),
            "familyHeld": sum(not row["ready"] for row in family_checks),
            "catalogWrites": 0,
            "duplicateChecks": duplicate_checks,
            "familyChecks": family_checks,
        }
        save(args.output, report)
        print(json.dumps({key: report[key] for key in (
            "duplicateReady", "duplicateHeld", "familyReady", "familyHeld", "catalogWrites"
        )}))
    elif args.mode == "apply":
        if args.output.exists():
            previous = json.loads(args.output.read_text(encoding="utf-8"))
            applied_duplicates = {(row["brandKey"], row["nameKey"])
                                  for row in previous.get("duplicateApplications", [])}
            applied_families = {row["groupId"] for row in previous.get("familyApplications", [])}
            preflight_duplicates = [row for row in duplicates
                                    if (row["brandKey"], row["nameKey"]) not in applied_duplicates]
            preflight_families = [row for row in families if row[0]["groupId"] not in applied_families]
        else:
            preflight_duplicates, preflight_families = duplicates, families
        duplicate_checks, family_checks = preflight(
            args.base_url, args.token, preflight_duplicates, preflight_families
        )
        if not all(row["ready"] for row in duplicate_checks + family_checks):
            raise RuntimeError("Apply refused because preflight has held candidates")
        result = apply(args.base_url, args.token, duplicates, families, args.output)
        print(json.dumps({"duplicateApplied": len(result["duplicateApplications"]),
                          "familyApplied": len(result["familyApplications"]), "completed": result["completed"]}))
    else:
        result = rollback(args.base_url, args.token, args.output)
        print(json.dumps({"duplicateRolledBack": len(result["duplicates"]),
                          "familyRolledBack": len(result["families"]), "completed": result["completed"]}))


if __name__ == "__main__":
    main()

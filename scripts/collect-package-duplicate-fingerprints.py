"""Collect exact duplicate fingerprints and prepare non-applicable review payloads."""
import argparse
import json
import os
import urllib.parse
import urllib.error
import urllib.request
from pathlib import Path


def request_json(url, token):
    request = urllib.request.Request(
        url, headers={"Authorization": f"Bearer {token}", "Accept": "application/json"}
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def get_json(base_url, token, brand_key, name_key, query_text=None):
    query = urllib.parse.urlencode({"brandKey": brand_key, "nameKey": name_key})
    root = f"{base_url.rstrip('/')}/api/v1/admin/products/duplicates/branded"
    try:
        return request_json(f"{root}/exact?{query}", token)
    except urllib.error.HTTPError as error:
        if error.code != 404 or not query_text:
            raise
    fallback = urllib.parse.urlencode({"query": query_text, "page": 0, "size": 100})
    page = request_json(f"{root}?{fallback}", token)
    matches = [group for group in page.get("content", [])
               if group.get("brandKey") == brand_key and group.get("nameKey") == name_key]
    if len(matches) != 1:
        raise ValueError(f"Exact duplicate group not found safely through list fallback: {brand_key}/{name_key}")
    return matches[0]


def collect(review_path, output_path, base_url, token, catalog_path=None):
    review = json.loads(review_path.read_text(encoding="utf-8"))
    query_by_id = {}
    if catalog_path:
        import csv
        with catalog_path.open(encoding="utf-8-sig", newline="") as handle:
            query_by_id = {int(row["id"]): row.get("display_name") or row.get("name") for row in csv.DictReader(handle)}
    results = []
    for item in review["groups"]:
        query_text = query_by_id.get(item["productIds"][0])
        live = get_json(base_url, token, item["brandKey"], item["nameKey"], query_text)
        expected = sorted(item["productIds"])
        actual = sorted(int(candidate["productId"]) for candidate in live.get("candidates", []))
        reasons = []
        if actual != expected:
            reasons.append("LIVE_MEMBER_SET_CHANGED")
        if live.get("storedDecision") is not None:
            reasons.append("LIVE_DECISION_ALREADY_EXISTS")
        if live.get("searchCollapse") is not None:
            reasons.append("LIVE_COLLAPSE_ALREADY_EXISTS")
        fingerprint = live.get("candidateFingerprint")
        if not isinstance(fingerprint, str) or len(fingerprint) != 64:
            reasons.append("LIVE_FINGERPRINT_INVALID")
        ready = not reasons
        reason = (
            "Exact package identity and nutrition match reviewed; preserve all GTIN records and "
            "collapse only duplicate name-search visibility."
        )
        results.append({
            **item,
            "liveProductIds": actual,
            "candidateFingerprint": fingerprint if ready else None,
            "liveVerified": ready,
            "holdReasons": reasons,
            "decisionPayload": {
                "brandKey": item["brandKey"],
                "nameKey": item["nameKey"],
                "decision": "SURVIVOR_SELECTED",
                "survivorProductId": item["proposedSurvivorId"],
                "reason": reason,
                "candidateFingerprint": fingerprint,
            } if ready else None,
            "collapsePayload": {
                "brandKey": item["brandKey"],
                "nameKey": item["nameKey"],
                "candidateFingerprint": fingerprint,
                "reason": reason,
            } if ready else None,
            "adminApprovalRequired": True,
            "applyAllowed": False,
            "catalogWrites": 0,
        })
    output = {
        "groupsRequested": len(review["groups"]),
        "liveVerified": sum(item["liveVerified"] for item in results),
        "held": sum(not item["liveVerified"] for item in results),
        "adminApprovalRequired": len(results),
        "applyAllowed": 0,
        "catalogWrites": 0,
        "groups": results,
    }
    output_path.write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({key: output[key] for key in (
        "groupsRequested", "liveVerified", "held", "adminApprovalRequired", "catalogWrites"
    )}))
    return output


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--review", type=Path, default=Path("outputs/package-duplicate-review-20261030/collapse-review.json"))
    parser.add_argument("--output", type=Path, default=Path("outputs/package-duplicate-review-20261030/live-fingerprint-review.json"))
    parser.add_argument("--catalog", type=Path, default=Path("outputs/package-duplicate-review-20261030/catalog.csv"))
    parser.add_argument("--base-url", default="https://api.gruncalorietracker.com")
    parser.add_argument("--token", default=os.environ.get("GRUN_ADMIN_TOKEN"))
    arguments = parser.parse_args()
    if not arguments.token:
        parser.error("Provide --token or GRUN_ADMIN_TOKEN; the token is never written to output.")
    collect(arguments.review, arguments.output, arguments.base_url, arguments.token, arguments.catalog)


if __name__ == "__main__":
    main()

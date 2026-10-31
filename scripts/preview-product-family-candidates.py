"""Validate product-family candidates through the read-only admin preview endpoint."""

import argparse
import json
import os
import urllib.error
import urllib.request
from collections import Counter
from pathlib import Path


def post_preview(base_url, token, payload):
    request = urllib.request.Request(
        f"{base_url.rstrip('/')}/api/v1/admin/product-families/preview",
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/json",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        try:
            detail = json.loads(body)
        except json.JSONDecodeError:
            detail = {"message": body[:500]}
        return error.code, detail


def preview(input_path, output_path, base_url, token):
    source = json.loads(input_path.read_text(encoding="utf-8"))
    candidates = source.get("payloads", [])
    if source.get("previewCount") != len(candidates) or not candidates:
        raise ValueError("Preview count does not match the candidate payload list")

    results = []
    for index, candidate in enumerate(candidates, start=1):
        status, response = post_preview(base_url, token, candidate["request"])
        errors = response.get("errors", []) if isinstance(response, dict) else []
        warnings = response.get("warnings", []) if isinstance(response, dict) else []
        results.append({
            "groupId": candidate["groupId"],
            "reviewMode": candidate.get("reviewMode"),
            "httpStatus": status,
            "valid": status == 200 and not errors,
            "errors": errors,
            "warnings": warnings,
            "memberCount": len(candidate["request"].get("members", [])),
            "response": response,
        })
        if index % 50 == 0:
            print(json.dumps({"previewed": index, "total": len(candidates)}))

    error_codes = Counter(str(item["httpStatus"]) for item in results if item["httpStatus"] != 200)
    report = {
        "candidatesRequested": len(candidates),
        "valid": sum(item["valid"] for item in results),
        "held": sum(not item["valid"] for item in results),
        "withWarnings": sum(bool(item["warnings"]) for item in results),
        "httpErrorCounts": dict(sorted(error_codes.items())),
        "catalogWrites": 0,
        "applyCalled": False,
        "results": results,
    }
    output_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: report[key] for key in (
        "candidatesRequested", "valid", "held", "withWarnings", "catalogWrites", "applyCalled"
    )}))
    return report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--base-url", default="https://api-staging.gruncalorietracker.com")
    parser.add_argument("--token", default=os.environ.get("GRUN_ADMIN_TOKEN"))
    args = parser.parse_args()
    if not args.token:
        parser.error("Provide --token or GRUN_ADMIN_TOKEN; the token is never written to output")
    preview(args.input, args.output, args.base_url, args.token)


if __name__ == "__main__":
    main()

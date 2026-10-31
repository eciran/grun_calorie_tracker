"""Validate generated product-family preview payloads without calling the API."""
import json
import sys
from collections import Counter
from pathlib import Path


def validate(path):
    document = json.loads(path.read_text(encoding="utf-8"))
    payloads = document["payloads"]
    keys = [payload["request"]["familyKey"] for payload in payloads]
    duplicate_keys = [key for key, count in Counter(keys).items() if count > 1]
    invalid = []
    for payload in payloads:
        request = payload["request"]
        members = request["members"]
        defaults = [member for member in members if member["defaultVariant"]]
        package_signatures = [
            (member["packageQuantity"], member["packageUnit"])
            for member in members
        ]
        package_units = {member["packageUnit"] for member in members}
        if (
            len(members) < 2
            or len(defaults) != 1
            or defaults[0]["productId"] != request["representativeProductId"]
            or len(package_signatures) != len(set(package_signatures))
            or len(package_units) != 1
            or len({member["productId"] for member in members}) != len(members)
            or not request["familyKey"]
            or not request["displayName"]
        ):
            invalid.append(payload["groupId"])
    summary = {
        "payloads": len(payloads),
        "memberRows": sum(len(payload["request"]["members"]) for payload in payloads),
        "duplicateFamilyKeys": duplicate_keys,
        "invalidPayloads": invalid,
        "gramFamilies": sum(
            all(member["packageUnit"] == "GRAM" for member in payload["request"]["members"])
            for payload in payloads
        ),
        "mlFamilies": sum(
            all(member["packageUnit"] == "MILLILITER" for member in payload["request"]["members"])
            for payload in payloads
        ),
        "mixedUnitFamilies": sum(
            len({member["packageUnit"] for member in payload["request"]["members"]}) > 1
            for payload in payloads
        ),
        "valid": not duplicate_keys and not invalid,
        "catalogWrites": 0,
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    if not summary["valid"]:
        raise SystemExit(1)
    return summary


if __name__ == "__main__":
    validate(Path(sys.argv[1]))

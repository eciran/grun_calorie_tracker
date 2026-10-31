"""Split unresolved package-family evidence into explicit, non-writing review queues."""
import csv
import json
import sys
from collections import Counter
from pathlib import Path


def number(value):
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def close(left, right, absolute, relative):
    return left is not None and right is not None and abs(left - right) <= max(
        absolute, max(abs(left), abs(right), 1.0) * relative
    )


def nutrition_level(rows):
    reference = rows[0]
    strict = all(
        close(number(reference.get("calories")), number(row.get("calories")), 5.0, 0.05)
        and all(close(number(reference.get(field)), number(row.get(field)), 1.0, 0.10)
                for field in ("protein", "carbs", "fat"))
        for row in rows[1:]
    )
    if strict:
        return "COMPATIBLE"
    minor = all(
        close(number(reference.get("calories")), number(row.get("calories")), 10.0, 0.10)
        and all(close(number(reference.get(field)), number(row.get(field)), 2.0, 0.20)
                for field in ("protein", "carbs", "fat"))
        for row in rows[1:]
    )
    return "MINOR_VARIANCE" if minor else "MAJOR_CONFLICT"


def queue_decision(source_decision, level):
    if source_decision == "OFF_SAME_NORMALIZED_PACKAGE_REVIEW":
        return {
            "COMPATIBLE": "DUPLICATE_SEARCH_COLLAPSE_REVIEW",
            "MINOR_VARIANCE": "DUPLICATE_NUTRITION_REVIEW",
            "MAJOR_CONFLICT": "SEPARATE_FORMULA_REVIEW",
        }[level]
    if source_decision == "OFF_CROSS_MEMBER_NUTRITION_REVIEW":
        return "FAMILY_LABEL_VARIANCE_REVIEW" if level == "MINOR_VARIANCE" else "FAMILY_FORMULA_CONFLICT"
    return source_decision


def prepare(root):
    evidence = json.loads((root / "explicit-package-off-evidence.json").read_text(encoding="utf-8"))
    candidates = {group["group_id"]: group for group in json.loads(
        (root / "candidates.json").read_text(encoding="utf-8")
    )}
    relevant = {
        "OFF_SAME_NORMALIZED_PACKAGE_REVIEW",
        "OFF_CROSS_MEMBER_NUTRITION_REVIEW",
        "OFF_MEMBER_PACKAGE_METADATA_REQUIRED",
    }
    queues = []
    for reviewed in evidence["groups"]:
        if reviewed["decision"] not in relevant:
            continue
        candidate = candidates[reviewed["groupId"]]
        level = nutrition_level(candidate["products"])
        decision = queue_decision(reviewed["decision"], level)
        queues.append({
            "groupId": reviewed["groupId"],
            "sourceDecision": reviewed["decision"],
            "queueDecision": decision,
            "nutritionLevel": level,
            "brand": candidate["products"][0].get("brand"),
            "productIds": [int(row["id"]) for row in candidate["products"]],
            "names": [row.get("display_name") or row.get("name") for row in candidate["products"]],
            "barcodes": [row.get("barcode") for row in candidate["products"]],
            "packageEvidence": [
                row.get("catalogPackage") or row.get("sourcePackage") for row in reviewed["products"]
            ],
            "catalogWrites": 0,
        })
    summary = {
        "queueGroups": len(queues),
        "queueDecisionCounts": dict(Counter(item["queueDecision"] for item in queues)),
        "nutritionLevelCounts": dict(Counter(item["nutritionLevel"] for item in queues)),
        "automaticApplicationApproved": 0,
        "catalogWrites": 0,
    }
    (root / "package-family-review-queues.json").write_text(
        json.dumps({"summary": summary, "groups": queues}, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    with (root / "package-family-review-queues.csv").open("w", encoding="utf-8-sig", newline="") as handle:
        fields = [
            "groupId", "sourceDecision", "queueDecision", "nutritionLevel", "brand",
            "productIds", "names", "barcodes", "packageEvidence", "catalogWrites",
        ]
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        for item in queues:
            writer.writerow({**item, **{
                field: json.dumps(item[field], ensure_ascii=False)
                for field in ("productIds", "names", "barcodes", "packageEvidence")
            }})
    (root / "package-family-review-queues-summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(json.dumps(summary, ensure_ascii=False))
    return summary


if __name__ == "__main__":
    prepare(Path(sys.argv[1] if len(sys.argv) > 1 else "outputs/package-family-audit-20261029"))

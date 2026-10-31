"""Measure the full package-family opportunity without modifying catalog data."""
import csv
import json
import sys
from collections import Counter
from pathlib import Path


def has_distinct_values(rows, field):
    values = {row.get(field) for row in rows if row.get(field)}
    return len(values) == len(rows)


def valid_gtin(value):
    digits = "".join(character for character in str(value or "") if character.isdigit())
    if len(digits) not in {8, 12, 13, 14}:
        return False
    numbers = [int(character) for character in digits]
    weighted = sum(
        number * (3 if (len(numbers) - 2 - index) % 2 == 0 else 1)
        for index, number in enumerate(numbers[:-1])
    )
    return (weighted + numbers[-1]) % 10 == 0


def same_context(rows):
    return len({(
        row.get("market_region"),
        row.get("preparation_state"),
        row.get("nutrition_reference_unit"),
    ) for row in rows}) == 1


def distinct_packages(rows):
    return len({row.get("package_text") for row in rows if row.get("package_text")}) >= 2


def classify(group):
    rows = group["products"]
    if not all(row.get("catalog_type") == "BRANDED_PRODUCT" for row in rows):
        return "EXCLUDED_NON_BRANDED"
    if not all(row.get("publication_status") == "PUBLISHED" for row in rows):
        return "HELD_UNPUBLISHED"
    if not same_context(rows):
        return "HELD_CONTEXT_MISMATCH"
    if not has_distinct_values(rows, "barcode"):
        return "HELD_BARCODE_EVIDENCE"
    if not all(valid_gtin(row.get("barcode")) for row in rows):
        return "HELD_INVALID_GTIN"
    if distinct_packages(rows):
        if group["decision"] == "REVIEW_NUTRITION_DIFFERENCE":
            return "EXPLICIT_PACKAGE_NUTRITION_REVIEW"
        return "EXPLICIT_PACKAGE_REVIEW"
    if group["decision"] == "REVIEW_NUTRITION_DIFFERENCE":
        return "PACKAGE_AND_NUTRITION_EVIDENCE_REQUIRED"
    return "PACKAGE_METADATA_REQUIRED"


def analyze(root):
    groups = json.loads((root / "candidates.json").read_text(encoding="utf-8"))
    output = []
    for group in groups:
        tier = classify(group)
        output.append({
            "groupId": group["group_id"],
            "tier": tier,
            "originalDecision": group["decision"],
            "productCount": len(group["products"]),
            "productIds": [int(row["id"]) for row in group["products"]],
            "brand": group["products"][0].get("brand"),
            "names": [row.get("display_name") or row.get("name") for row in group["products"]],
            "barcodes": [row.get("barcode") for row in group["products"]],
            "validGtins": [valid_gtin(row.get("barcode")) for row in group["products"]],
            "packageTexts": [row.get("package_text") for row in group["products"]],
        })

    actionable_tiers = {
        "EXPLICIT_PACKAGE_REVIEW",
        "EXPLICIT_PACKAGE_NUTRITION_REVIEW",
        "PACKAGE_METADATA_REQUIRED",
        "PACKAGE_AND_NUTRITION_EVIDENCE_REQUIRED",
    }
    summary = {
        "catalogCandidateGroups": len(output),
        "catalogCandidateRows": sum(item["productCount"] for item in output),
        "potentialFamilyGroups": sum(item["tier"] in actionable_tiers for item in output),
        "potentialFamilyRows": sum(item["productCount"] for item in output if item["tier"] in actionable_tiers),
        "tierCounts": dict(Counter(item["tier"] for item in output)),
        "automaticApplicationApproved": 0,
        "catalogWrites": 0,
        "interpretation": [
            "Potential family means same normalized brand/name identity with distinct GTINs and compatible context.",
            "It does not prove package quantity or formula identity.",
            "Explicit-package groups need source confirmation; metadata groups need package lookup by GTIN.",
            "Nutrition-review groups must be normalized or source-verified before family application.",
        ],
    }
    (root / "package-family-coverage.json").write_text(
        json.dumps({"summary": summary, "groups": output}, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    with (root / "package-family-coverage.csv").open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=[
            "groupId", "tier", "originalDecision", "productCount", "brand",
            "productIds", "names", "barcodes", "packageTexts",
            "validGtins",
        ])
        writer.writeheader()
        for item in output:
            writer.writerow({**item, **{
                field: json.dumps(item[field], ensure_ascii=False)
                for field in ("productIds", "names", "barcodes", "packageTexts", "validGtins")
            }})
    (root / "package-family-coverage-summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(json.dumps(summary, ensure_ascii=False))
    return summary


if __name__ == "__main__":
    analyze(Path(sys.argv[1] if len(sys.argv) > 1 else "outputs/package-family-audit-20261029"))

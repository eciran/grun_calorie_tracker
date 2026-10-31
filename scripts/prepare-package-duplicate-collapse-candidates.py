"""Prepare exact backend duplicate identities for bounded read-only review; no catalog writes."""
import json
import sys
from pathlib import Path


def alnum_key(value):
    return "".join(character for character in str(value or "").lower() if character.isalnum())


def backend_identity(row):
    brand_key = f"brand:{row['brand_id']}" if row.get("brand_id") else f"legacy:{alnum_key(row.get('brand'))}"
    return brand_key, alnum_key(row.get("display_name") or row.get("name"))


def number(value):
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def close(left, right, absolute, relative):
    left, right = number(left), number(right)
    return left is not None and right is not None and abs(left - right) <= max(
        absolute, max(abs(left), abs(right), 1.0) * relative
    )


def collapse_candidate_level(rows):
    identities = {backend_identity(row) for row in rows}
    if len(identities) != 1:
        return None
    core = {
        tuple(row.get(field) for field in ("nutrition_reference_unit", "calories", "protein", "carbs", "fat"))
        for row in rows
    }
    # Search collapse preserves each barcode row and all serving metadata. Different
    # default portions therefore do not make an otherwise exact product unsafe to
    # collapse in name search.
    if len(core) == 1:
        return "EXACT_CORE"
    reference = rows[0]
    compatible = all(
        row.get("nutrition_reference_unit") == reference.get("nutrition_reference_unit")
        and close(reference.get("calories"), row.get("calories"), 5.0, 0.05)
        and all(close(reference.get(field), row.get(field), 1.0, 0.10)
                for field in ("protein", "carbs", "fat"))
        for row in rows[1:]
    )
    return "NUTRITION_TOLERANCE_REVIEW" if compatible else None


def exact_collapse_candidate(rows):
    return collapse_candidate_level(rows) is not None


def prepare(root):
    queues = json.loads((root / "package-family-review-queues.json").read_text(encoding="utf-8"))["groups"]
    candidates = {group["group_id"]: group for group in json.loads(
        (root / "candidates.json").read_text(encoding="utf-8")
    )}
    manifest = []
    held = []
    for queued in queues:
        if queued["queueDecision"] != "DUPLICATE_SEARCH_COLLAPSE_REVIEW":
            continue
        rows = candidates[queued["groupId"]]["products"]
        review_level = collapse_candidate_level(rows)
        target = manifest if review_level else held
        brand_key, name_key = backend_identity(rows[0])
        target.append({
            "groupId": queued["groupId"],
            "brandKey": brand_key,
            "nameKey": name_key,
            "productIds": sorted(int(row["id"]) for row in rows),
            "barcodes": sorted(row["barcode"] for row in rows),
            "reviewLevel": review_level,
            "reason": (
                "Exact backend identity, normalized package evidence and core nutrition match; "
                "serving metadata remains attached to each preserved barcode record; "
                "survivor and current fingerprint still require bounded AWS review."
            ) if review_level == "EXACT_CORE" else (
                "Exact backend identity and normalized package evidence match; core nutrition differs only "
                "within the bounded source-label rounding tolerance and requires explicit admin review."
            ),
            "catalogWrites": 0,
        })
    output = {
        "candidateGroups": len(manifest),
        "heldGroups": len(held),
        "reviewLevelCounts": {
            level: sum(item.get("reviewLevel") == level for item in manifest)
            for level in ("EXACT_CORE", "NUTRITION_TOLERANCE_REVIEW")
        },
        "survivorSelected": 0,
        "catalogWrites": 0,
        "groups": manifest,
        "held": held,
    }
    (root / "duplicate-search-collapse-candidates.json").write_text(
        json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    with (root / "duplicate-search-collapse-selection.jsonl").open("w", encoding="utf-8", newline="\n") as handle:
        for item in manifest:
            handle.write(json.dumps(item, ensure_ascii=False) + "\n")
    print(json.dumps({key: output[key] for key in (
        "candidateGroups", "heldGroups", "survivorSelected", "catalogWrites"
    )}))
    return output


if __name__ == "__main__":
    prepare(Path(sys.argv[1] if len(sys.argv) > 1 else "outputs/package-family-audit-20261029"))

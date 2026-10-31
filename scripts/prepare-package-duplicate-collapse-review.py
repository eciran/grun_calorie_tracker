"""Prepare bounded package duplicate collapse review; never writes catalog data."""
import csv
import hashlib
import json
import sys
from collections import defaultdict
from pathlib import Path


VERIFICATION_RANK = {
    "VERIFIED": 3,
    "NEEDS_REVIEW": 2,
    "RAW_IMPORTED": 1,
    "REJECTED": 0,
}


def file_sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def integer(value):
    return int(value or 0)


def survivor_rank(row):
    return (
        VERIFICATION_RANK.get(row.get("verification_status"), -1),
        integer(row.get("quality_score")),
        integer(row.get("usage_count")),
        integer(row.get("search_selection_count")),
    )


def prepare(manifest_path, snapshot_dir, output_path):
    proof_path = snapshot_dir / "snapshot-proof.json"
    catalog_path = snapshot_dir / "catalog.csv"
    proof = json.loads(proof_path.read_text(encoding="utf-8"))
    if proof.get("scope") != "package-duplicates" or not proof.get("transactionReadOnly"):
        raise ValueError("Snapshot proof is not a read-only package duplicate snapshot.")
    if proof.get("decisionsSha256") != file_sha256(manifest_path):
        raise ValueError("Snapshot manifest hash does not match the candidate manifest.")
    if proof.get("sha256") != file_sha256(catalog_path):
        raise ValueError("Snapshot catalog hash does not match its proof.")

    manifest = [json.loads(line) for line in manifest_path.read_text(encoding="utf-8").splitlines() if line]
    rows = list(csv.DictReader(catalog_path.open(encoding="utf-8-sig", newline="")))
    rows_by_id = {integer(row["id"]): row for row in rows}
    results = []
    counts = defaultdict(int)

    for candidate in manifest:
        expected_ids = sorted(candidate["productIds"])
        group_rows = [rows_by_id[product_id] for product_id in expected_ids if product_id in rows_by_id]
        reasons = []
        if len(group_rows) != len(expected_ids):
            reasons.append("SNAPSHOT_MEMBER_MISSING")
        if any(row.get("publication_status") != "PUBLISHED" for row in group_rows):
            reasons.append("UNPUBLISHED_MEMBER")
        if any(row.get("existing_decision_type") or row.get("existing_collapse_status") for row in group_rows):
            reasons.append("EXISTING_DUPLICATE_STATE")
        if any(
            row.get("brand_key") != candidate["brandKey"] or row.get("name_key") != candidate["nameKey"]
            for row in group_rows
        ):
            reasons.append("IDENTITY_CHANGED")

        ranked = sorted(group_rows, key=lambda row: (survivor_rank(row), -integer(row["id"])), reverse=True)
        top_rank = survivor_rank(ranked[0]) if ranked else None
        top = [row for row in ranked if survivor_rank(row) == top_rank]
        if reasons:
            status = "HELD"
            survivor_id = None
            selection_basis = None
        elif len(top) == 1:
            status = "REVIEW_READY_SIGNAL_SELECTED"
            survivor_id = integer(top[0]["id"])
            selection_basis = "VERIFICATION_QUALITY_USAGE_SEARCH"
        else:
            status = "REVIEW_READY_STABLE_OLDEST"
            survivor_id = min(integer(row["id"]) for row in top)
            selection_basis = "STABLE_OLDEST_TIE_BREAK_REQUIRES_ADMIN_APPROVAL"
        counts[status] += 1
        results.append({
            "groupId": candidate["groupId"],
            "brandKey": candidate["brandKey"],
            "nameKey": candidate["nameKey"],
            "productIds": expected_ids,
            "barcodes": candidate["barcodes"],
            "status": status,
            "proposedSurvivorId": survivor_id,
            "selectionBasis": selection_basis,
            "holdReasons": reasons,
            "candidateFingerprint": None,
            "adminApprovalRequired": True,
            "applyAllowed": False,
            "catalogWrites": 0,
        })

    output = {
        "sourceProofSha256": file_sha256(proof_path),
        "candidateGroups": len(manifest),
        "snapshotRows": len(rows),
        "statusCounts": dict(sorted(counts.items())),
        "adminApprovalRequired": len(results),
        "fingerprintsRequired": sum(item["status"].startswith("REVIEW_READY") for item in results),
        "applyAllowed": 0,
        "catalogWrites": 0,
        "groups": results,
    }
    output_path.write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({key: output[key] for key in (
        "candidateGroups", "snapshotRows", "statusCounts", "fingerprintsRequired", "catalogWrites"
    )}))
    return output


if __name__ == "__main__":
    root = Path(sys.argv[1] if len(sys.argv) > 1 else "outputs/package-family-audit-20261029")
    snapshot = Path(sys.argv[2] if len(sys.argv) > 2 else "outputs/package-duplicate-review-20261030")
    destination = Path(sys.argv[3]) if len(sys.argv) > 3 else snapshot / "collapse-review.json"
    prepare(root / "duplicate-search-collapse-selection.jsonl", snapshot, destination)

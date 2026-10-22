"""Build the reviewed AWS catalog-name remediation decision set."""
import argparse
import csv
import hashlib
import json
from pathlib import Path


MISSING_DISPLAY_IDS = {
    181510, 181514, 181516, 181521, 181526, 181528,
    181533, 181536, 181539, 181541, 181552, 181562,
}

EXPLICIT_RENAMES = {
    72: ("Sweet & Sour Tofu (Balanced Fat Loss)", "SYNTAX_ONLY_PARENTHESES"),
    30350: ("Trader Joe’s Organic Ginger Turmeric Herbal Tea", "DETERMINISTIC_ENCODING_REPAIR"),
    67809: ("Avocado Oil Crisps", "OFF_EXACT_GTIN_PRODUCT_NAME_EN"),
    88236: ("Zitronen Kuchen (Backmischung)", "SYNTAX_ONLY_PARENTHESES"),
    97470: ("Cadbury Snack Sandwich – 6 Pack", "DETERMINISTIC_ENCODING_REPAIR"),
    129297: ("Pumpkin Seeds (Light Green)", "SYNTAX_ONLY_PARENTHESES"),
    154419: ("Whet Protein (Sweet & Salty Peanut Butter)", "SYNTAX_ONLY_PARENTHESES"),
    164775: ("Blue Lab Whey Protein (White Chocolate Cookie Dough)", "SYNTAX_ONLY_PARENTHESES"),
    166595: ("Puff Paratha Pancake (Chive)", "SYNTAX_ONLY_PARENTHESES"),
    168390: ("Dairy Milk King’s Coronation", "OFF_EXACT_GTIN_PRODUCT_NAME_EN"),
    169071: ("Cadbury Bitsa Wispa Chocolate Bag – 85 g", "DETERMINISTIC_ENCODING_REPAIR"),
    169072: ("Cadbury Dairy Milk Caramel Nibbles – 85 g", "DETERMINISTIC_ENCODING_REPAIR"),
    169359: ("Cadbury’s Dairy Milk", "OFF_EXACT_GTIN_PRODUCT_NAME_EN"),
    170746: ("Giant Ravioli (Ricotta and Spinach)", "SYNTAX_ONLY_PARENTHESES"),
}


def file_sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--issues", type=Path, required=True)
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    with args.issues.open(encoding="utf-8-sig", newline="") as handle:
        source_rows = {int(row["id"]): row for row in csv.DictReader(handle)}

    approved_ids = MISSING_DISPLAY_IDS | set(EXPLICIT_RENAMES)
    if not approved_ids.issubset(source_rows):
        raise ValueError(f"Audit is missing approved IDs: {sorted(approved_ids - source_rows.keys())}")

    decisions = []
    for product_id in sorted(source_rows):
        row = source_rows[product_id]
        decision = "HOLD_MANUAL_REVIEW"
        proposed = None
        reason = "No deterministic or exact-source-safe correction."
        if product_id in MISSING_DISPLAY_IDS:
            if row["publication_status"] != "PUBLISHED" or not row["name"].strip():
                raise ValueError(f"Unsafe missing-display row: {product_id}")
            proposed = row["name"].strip()
            decision = "APPROVE_FILL_MISSING_DISPLAY"
            reason = "Published manual record has a non-empty canonical name and no display names."
        elif product_id in EXPLICIT_RENAMES:
            proposed, reason = EXPLICIT_RENAMES[product_id]
            decision = "APPROVE_EXACT_NAME_REPAIR"

        if decision.startswith("APPROVE_"):
            if row["publication_status"] != "PUBLISHED":
                raise ValueError(f"Approved record is not published: {product_id}")
            if json.loads(row["localizations"] or "[]"):
                raise ValueError(f"Approved record has localization overrides: {product_id}")
            if not proposed or proposed == row["display_name"]:
                raise ValueError(f"Approved record has no effective change: {product_id}")

        decisions.append({
            "id": product_id,
            "sourceKey": row["source_key"],
            "barcode": row["barcode"] or None,
            "dataSource": row["data_source"],
            "publicationStatus": row["publication_status"],
            "canonicalName": row["name"],
            "currentDisplayName": row["display_name"] or None,
            "currentShortDisplayName": row["short_display_name"] or None,
            "proposedDisplayName": proposed,
            "decision": decision,
            "reason": reason,
            "issueCodes": json.loads(row["issue_codes"]),
        })

    approved = [row for row in decisions if row["decision"].startswith("APPROVE_")]
    if len(approved) != 26:
        raise ValueError(f"Expected 26 approved rows, got {len(approved)}")

    evidence_hashes = {}
    for row in approved:
        if not row["barcode"]:
            continue
        evidence_file = args.evidence / f"barcode-{row['barcode']}.json"
        if evidence_file.exists():
            evidence_hashes[row["barcode"]] = file_sha(evidence_file)

    payload = {
        "version": "R7_CATALOG_NAME_REMEDIATION_REVIEW_V1",
        "databaseWrites": 0,
        "approvedCount": len(approved),
        "heldCount": len(decisions) - len(approved),
        "issuesSha256": file_sha(args.issues),
        "evidenceHashes": evidence_hashes,
        "rows": decisions,
    }
    args.output.mkdir(parents=True, exist_ok=True)
    review_path = args.output / "review.json"
    review_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    with (args.output / "approved.csv").open("w", encoding="utf-8-sig", newline="") as handle:
        fields = list(approved[0])
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        for row in approved:
            writer.writerow({**row, "issueCodes": json.dumps(row["issueCodes"], ensure_ascii=False)})
    print(json.dumps({"status": "BUILT", "approved": len(approved), "held": len(decisions) - len(approved)}))


if __name__ == "__main__":
    main()

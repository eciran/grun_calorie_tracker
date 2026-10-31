"""Create source-backed decisions for priority brands; no catalog writes."""
from __future__ import annotations

import csv
import json
from pathlib import Path

ROOT = Path("outputs/package-family-audit-20261029/priority-brands")

SOURCE_DECISIONS = {
    "BR-00041": {
        "decision": "PARTIAL_PACKAGE_IDENTITY_EVIDENCE",
        "reason": "The 4 x 45 g unit barcode and package are corroborated. The 180 g record still needs current exact-barcode evidence to establish whether it is the same retail package representation.",
        "sources": [
            "https://www.ufinternational.co.uk/products/cadbury-dairy-milk-mousse-4pk-4x45g"
        ],
    },
    "BR-00174": {
        "decision": "PARTIAL_PACKAGE_AND_NUTRITION_EVIDENCE",
        "reason": "The 74 g barcode, package and label nutrition match the catalog. The 270 g member was not jointly verified, so the family remains incomplete.",
        "sources": [
            "https://barcodepedia.com/product/7622202274831/cadbury-mini-eggs-74-g",
            "https://www.bestwaywholesale.co.uk/product/831944-1",
        ],
    },
    "BR-00187": {
        "decision": "PRESENTATION_FAMILY_REVIEW_READY_WITH_MEMBER_HOLD",
        "reason": "Cadbury lists Twirl Bites in 85 g and 100 g packs and publishes a 100 g profile matching the catalog. The member without a declared package remains outside the approved pair until its package identity is known.",
        "sources": [
            "https://www.cadbury.co.uk/terms-and-conditions/cadbury-made-to-share/appendix/index.html",
            "https://shop.cadbury.co.uk/p/cadbury-chocolate-favourites-gift-box/17965341/",
        ],
    },
}

SPELLING_ALIASES = {
    "Cadburys": "Cadbury",
    "Cadbury's": "Cadbury",
    "Cadbury’s": "Cadbury",
    "Cardbury": "Cadbury",
    "Cadburyg": "Cadbury",
    "Dorotos": "Doritos",
}


def main() -> None:
    groups = json.loads((ROOT / "groups.json").read_text(encoding="utf-8"))
    records = list(csv.DictReader((ROOT / "records.csv").open(encoding="utf-8-sig", newline="")))
    decisions = []
    for group_id, decision in SOURCE_DECISIONS.items():
        group = next(group for group in groups if group["group_id"] == group_id)
        decisions.append(
            {
                "group_id": group_id,
                **decision,
                "product_ids": [product["id"] for product in group["products"]],
                "names": [product["name"] for product in group["products"]],
                "automatic_application_approved": False,
            }
        )

    alias_rows = []
    for row in records:
        brand = row["brand"]
        if brand in SPELLING_ALIASES:
            alias_rows.append(
                {
                    "id": row["id"],
                    "name": row["name"],
                    "current_brand": brand,
                    "proposed_brand": SPELLING_ALIASES[brand],
                    "decision": "SPELLING_NORMALIZATION_CANDIDATE",
                    "automatic_application_approved": False,
                }
            )

    anomaly_groups = [
        group for group in groups if group["evidence_decision"] == "POSSIBLE_SERVING_AS_100G_ANOMALY"
    ]
    summary = {
        "sourceReviewedGroups": len(decisions),
        "presentationFamilyReviewReady": 1,
        "partialEvidenceGroups": 2,
        "spellingNormalizationCandidates": len(alias_rows),
        "possibleServingAs100gAnomalyGroups": len(anomaly_groups),
        "automaticApplicationApproved": 0,
        "catalogWrites": 0,
    }
    (ROOT / "source-decisions.json").write_text(
        json.dumps(decisions, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (ROOT / "source-decision-summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    with (ROOT / "brand-alias-candidates.csv").open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(alias_rows[0]))
        writer.writeheader()
        writer.writerows(alias_rows)
    with (ROOT / "nutrition-basis-anomalies.csv").open("w", encoding="utf-8-sig", newline="") as handle:
        fields = [
            "group_id", "target_brand", "id", "name", "barcode", "nutrition_reference_unit",
            "calories", "protein", "carbs", "fat", "source_key",
        ]
        writer = csv.DictWriter(handle, fieldnames=fields, extrasaction="ignore")
        writer.writeheader()
        for group in anomaly_groups:
            for product in group["products"]:
                writer.writerow({**product, "group_id": group["group_id"], "target_brand": group["target_brand"]})
    print(json.dumps(summary, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()

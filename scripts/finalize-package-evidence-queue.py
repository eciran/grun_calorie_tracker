"""Combine local, archived and live evidence without changing catalog records."""
from __future__ import annotations

import csv
import json
from pathlib import Path

ROOT = Path("outputs/package-family-audit-20261029")

ZERO_DECISIONS = {
    "PF-03349": {
        "decision": "WATER_FAMILY_IDENTITY_SUPPORTED_REFERENCE_AND_MINERAL_REVIEW",
        "reason": "Manufacturer lists 500 ml and 1.5 L natural spring water. Core zero macros are plausible for water, but exact GTIN and mineral declaration were not jointly verified.",
        "sources": ["https://www.sirma.com.tr/urunler/su-ailesi"],
    },
    "PF-09454": {
        "decision": "WATER_FAMILY_AND_ANALYSIS_SUPPORTED_REFERENCE_REVIEW",
        "reason": "Manufacturer publishes 0.5 L and 1.5 L analysis reports and mineral values. Product family is supported; DB reference remains PER_100G and requires a separate PER_100ML review.",
        "sources": [
            "https://www.coca-cola.com/tr/tr/brands/damla/koycegiz-kaynagimiz",
            "https://www.coca-cola.com/tr/tr/about-us/faq/damla-dogal-kaynak-suyunun-analiz-degerleri-nedir",
        ],
    },
    "PF-09455": {
        "decision": "COFFEE_IDENTITY_SUPPORTED_DRY_NUTRITION_REQUIRED",
        "reason": "Exact package identities have archived/live support, but dry soluble coffee nutrition was not established. Equal zero profiles are not nutrition evidence.",
        "sources": ["https://www.nescafe.com/tr/kahveler/gold"],
    },
    "PF-09456": {
        "decision": "COFFEE_IDENTITY_SUPPORTED_DRY_NUTRITION_REQUIRED",
        "reason": "All package GTINs are corroborated, but all catalog core values are zero. Prepared coffee and 3-in-1 nutrition cannot be substituted for dry Gold coffee.",
        "sources": ["https://www.nescafe.com/tr/kahveler/gold"],
    },
    "PF-09594": {
        "decision": "BARCODE_CONFLICT_AND_WATER_REFERENCE_REVIEW",
        "reason": "Current public catalog evidence supports the package family, but the observed 500 ml and 1.5 L GTINs differ from catalog GTINs. Do not group until identity is resolved.",
        "sources": [
            "https://abantsu.com.tr/en/analysis-reports-quality-policies",
            "https://www.foodekol.com/FOOD_EKOL_KATALOG.pdf",
        ],
    },
    "PF-09622": {
        "decision": "COFFEE_IDENTITY_SUPPORTED_DRY_NUTRITION_REQUIRED",
        "reason": "Classic 100 g and 200 g case GTIN/package identities are corroborated, but no package-specific dry nutrition declaration was verified.",
        "sources": [
            "https://www.nescafe.com/tr/classic",
            "https://emektemizlikgida.com/wp-content/uploads/2024/08/2021-MODERN-KATALOG.pdf",
        ],
    },
    "PF-09623": {
        "decision": "COFFEE_IDENTITY_SUPPORTED_DRY_NUTRITION_REQUIRED",
        "reason": "Gold economic package identity is supported locally, but dry product nutrition remains unverified and zero profiles cannot be approved.",
        "sources": ["https://www.nescafe.com/tr/kahveler/gold"],
    },
    "PF-09659": {
        "decision": "SOURCE_NAME_CONFLICT_ZERO_NUTRITION_SUPPORTED",
        "reason": "Pret currently lists Still Water 500 ml and 750 ml with zero core nutrients. Catalog name 'Sparkling Still Water' conflicts with current source identity and has no GTIN.",
        "sources": [
            "https://www.pret.co.uk/en-GB/products/UK020347/pret-still-water-500ml",
            "https://www.pret.co.uk/en-GB/products/categories/cold-drinks/water-and-soft-drinks",
        ],
    },
    "PF-09662": {
        "decision": "SOURCE_IDENTITY_AND_ZERO_NUTRITION_REVIEW_READY",
        "reason": "The exact source pages declare 500 ml and 750 ml natural mineral water with zero core nutrients. Records have no GTIN, so this is source-key presentation evidence only.",
        "sources": [
            "https://www.calorieking.com/gb/en/foods/f/calories-in-beverages-natural-mineral-water-500ml/JcFqFr_FTRu9-q1jkONBTQ",
            "https://www.calorieking.com/gb/en/foods/f/calories-in-beverages-natural-mineral-water-750ml/MgLpln-ZQ6eMi2E1OgQy2g",
        ],
    },
}


def all_zero(product: dict) -> bool:
    values = [product.get(field) for field in ("calories", "protein", "carbs", "fat")]
    return all(value not in (None, "") for value in values) and all(float(value) == 0 for value in values)


def valid_gtin(value) -> bool:
    digits = str(value or "")
    if not digits.isdigit() or len(digits) not in (8, 12, 13, 14):
        return False
    total = sum(
        int(char) * (3 if (len(digits) - index) % 2 == 0 else 1)
        for index, char in enumerate(digits[:-1])
    )
    return (10 - total % 10) % 10 == int(digits[-1])


def main() -> None:
    candidates = {
        group["group_id"]: group
        for group in json.loads((ROOT / "candidates.json").read_text(encoding="utf-8"))
        if group["decision"] == "PACKAGE_FAMILY_CANDIDATE"
    }
    source_review = {
        group["group_id"]: group
        for group in json.loads((ROOT / "source-review.json").read_text(encoding="utf-8"))
    }
    live_review = {
        group["group_id"]: group
        for group in json.loads((ROOT / "live-review.json").read_text(encoding="utf-8"))
    }
    priority = {
        row["group_id"]: row
        for row in json.loads((ROOT / "priority-identity-nutrition-decisions.json").read_text(encoding="utf-8"))
    }

    rows = []
    for group_id, group in sorted(candidates.items()):
        products = group["products"]
        source = source_review.get(group_id, {})
        live = live_review.get(group_id, {})
        live_rows = live.get("products", [])
        live_all = bool(live_rows) and all(
            row["status"] == "LIVE_GTIN_AND_PACKAGE_CORROBORATED" for row in live_rows
        )
        live_some = any(
            row["status"] == "LIVE_GTIN_AND_PACKAGE_CORROBORATED" for row in live_rows
        )
        archived_all = bool(source.get("products")) and all(
            product.get("matchingArchivedSources") for product in source["products"]
        )
        zero = any(all_zero(product) for product in products)
        gtins_valid = all(valid_gtin(product.get("barcode")) for product in products)
        extended_complete = all(
            product.get("fiber") not in (None, "") and product.get("sugar") not in (None, "")
            for product in products
        )

        if group_id in ZERO_DECISIONS:
            decision = ZERO_DECISIONS[group_id]["decision"]
            reason = ZERO_DECISIONS[group_id]["reason"]
            sources = ZERO_DECISIONS[group_id]["sources"]
        elif priority.get(group_id, {}).get("decision") == "PRESENTATION_FAMILY_SUPPORTED_REVIEW_READY":
            decision = "PRESENTATION_FAMILY_SUPPORTED_REVIEW_READY"
            reason = priority[group_id]["nutrition"]
            sources = priority[group_id]["sources"]
        elif live_all:
            decision = "LIVE_IDENTITY_PACKAGE_SUPPORTED_NUTRITION_REVIEW"
            reason = "All package identities have current structured GTIN/quantity support; formula and complete nutrition still require review."
            sources = [
                match["url"]
                for product in live_rows
                for match in product.get("matches", [])
            ]
        elif live_some:
            decision = "PARTIAL_LIVE_IDENTITY_EVIDENCE"
            reason = "Only part of the proposed family has current structured GTIN/quantity support."
            sources = [
                match["url"]
                for product in live_rows
                for match in product.get("matches", [])
            ]
        elif archived_all:
            decision = "ARCHIVED_IDENTITY_EVIDENCE_REFRESH_REQUIRED"
            reason = "Every package has archived GTIN/quantity support, but no complete current-source verification."
            sources = [
                match["url"]
                for product in source.get("products", [])
                for match in product.get("matchingArchivedSources", [])
            ]
        elif not gtins_valid:
            decision = "SOURCE_KEY_IDENTITY_EVIDENCE_REQUIRED"
            reason = "At least one record lacks a valid GTIN; source-key identity needs current evidence."
            sources = []
        else:
            decision = "CURRENT_IDENTITY_PACKAGE_EVIDENCE_REQUIRED"
            reason = "Candidate is profile-consistent but lacks complete current package identity evidence."
            sources = []

        review_ready = decision in {
            "PRESENTATION_FAMILY_SUPPORTED_REVIEW_READY",
            "SOURCE_IDENTITY_AND_ZERO_NUTRITION_REVIEW_READY",
        }
        rows.append(
            {
                "group_id": group_id,
                "decision": decision,
                "product_ids": ";".join(product["id"] for product in products),
                "names": " | ".join(product["name"] for product in products),
                "all_gtins_valid": gtins_valid,
                "all_zero_core_profile": zero,
                "extended_nutrition_complete": extended_complete,
                "live_all_packages_supported": live_all,
                "archived_all_packages_supported": archived_all,
                "presentation_review_ready": review_ready,
                "automatic_application_approved": False,
                "reason": reason,
                "sources": ";".join(dict.fromkeys(sources)),
            }
        )

    summary = {
        "groups": len(rows),
        "zeroProfileGroups": sum(row["all_zero_core_profile"] for row in rows),
        "presentationReviewReady": sum(row["presentation_review_ready"] for row in rows),
        "automaticApplicationApproved": 0,
        "liveAllPackagesSupported": sum(row["live_all_packages_supported"] for row in rows),
        "archivedAllPackagesSupported": sum(row["archived_all_packages_supported"] for row in rows),
        "allGtinsValid": sum(row["all_gtins_valid"] for row in rows),
        "catalogWrites": 0,
        "decisionCounts": {},
        "limitations": [
            "The snapshot contains core nutrients, fibre and sugar, not every micronutrient.",
            "Family readiness means presentation linkage only; barcode records must remain separate.",
            "No catalog record was changed.",
        ],
    }
    for row in rows:
        summary["decisionCounts"][row["decision"]] = summary["decisionCounts"].get(row["decision"], 0) + 1

    (ROOT / "general-evidence-review.json").write_text(
        json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (ROOT / "general-evidence-review-summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    with (ROOT / "general-evidence-review.csv").open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    print(json.dumps(summary, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()

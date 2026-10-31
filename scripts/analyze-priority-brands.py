"""Read-only Tayto, Pringles, Doritos and Cadbury catalog analysis."""
from __future__ import annotations

import csv
import json
import re
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path("outputs/package-family-audit-20261029")
OUT = ROOT / "priority-brands"
TARGETS = ("tayto", "pringles", "doritos", "cadbury")
PACK = re.compile(
    r"(?<!\w)(?:(\d+)\s*[xX]\s*)?(\d+(?:[.,]\d+)?)\s*(kg|mg|gr|g|ml|cl|lt|l)(?!\w)",
    re.IGNORECASE,
)
ALIASES = {
    "tayto": {"tayto", "tayto snacks", "the tayto craft"},
    "pringles": {"pringles", "pringels"},
    "doritos": {"doritos", "dorotos"},
    "cadbury": {"cadbury", "cadburys", "cardbury", "cadburyg"},
}


def fold(value: str | None) -> str:
    value = (value or "").casefold().replace("ı", "i").replace("’", "'")
    value = "".join(
        char for char in unicodedata.normalize("NFKD", value) if not unicodedata.combining(char)
    )
    return re.sub(r"[^a-z0-9]+", " ", value).strip()


def brand_parts(value: str | None) -> list[str]:
    return [fold(part) for part in re.split(r"[,;/|]+", value or "") if fold(part)]


def target_for(row: dict) -> tuple[str | None, str]:
    brand = fold(row.get("brand"))
    parts = brand_parts(row.get("brand"))
    name = fold(row.get("display_name") or row.get("name"))
    for target in TARGETS:
        aliases = ALIASES[target]
        exact = [part for part in parts if part in aliases]
        starts = [part for part in parts if any(part.startswith(alias + " ") for alias in aliases)]
        if exact:
            if len(parts) == 1 and parts[0] == target:
                return target, "CANONICAL_BRAND"
            if len(parts) == 1:
                return target, "ALIAS_OR_SUBBRAND_REVIEW"
            return target, "MULTI_BRAND_RELATION_REVIEW"
        if starts or any(alias in brand.split() for alias in aliases):
            return target, "ALIAS_OR_SUBBRAND_REVIEW"
        if name == target or name.startswith(target + " "):
            return target, "NAME_ONLY_BRAND_REVIEW"
    return None, "NOT_TARGET"


def identity(row: dict, target: str) -> tuple[str, list[str]]:
    name = row.get("display_name") or row.get("name") or ""
    packs = [match.group(0) for match in PACK.finditer(name)]
    normalized = fold(PACK.sub(" ", name))
    normalized = re.sub(rf"^(?:{re.escape(target)})\s+", "", normalized).strip()
    return normalized, packs


def profile(row: dict) -> tuple:
    return tuple(
        row.get(field) or ""
        for field in (
            "nutrition_reference_unit", "calories", "protein", "carbs", "fat", "fiber", "sugar"
        )
    )


def complete_core(row: dict) -> bool:
    return all(
        row.get(field) not in (None, "")
        for field in ("nutrition_reference_unit", "calories", "protein", "carbs", "fat")
    )


def numeric(row: dict, field: str):
    try:
        return float(row.get(field))
    except (TypeError, ValueError):
        return None


def maximum_relative_spread(products: list[dict]) -> float | None:
    spreads = []
    for field in ("calories", "protein", "carbs", "fat"):
        values = [numeric(row, field) for row in products]
        if any(value is None for value in values):
            return None
        high, low = max(values), min(values)
        denominator = max(abs(high), abs(low), 1.0)
        spreads.append(abs(high - low) / denominator)
    return max(spreads)


def evidence_decision(target: str, key: str, products: list[dict]) -> tuple[str, float | None]:
    references = {row.get("nutrition_reference_unit") or "" for row in products}
    package_values = {row["package_text"] for row in products if row["package_text"]}
    spread = maximum_relative_spread(products)
    calories = [numeric(row, "calories") for row in products]
    calories = [value for value in calories if value is not None]
    if key in ("", target):
        return "INSUFFICIENT_FLAVOR_OR_PRODUCT_IDENTITY", spread
    if len(references) > 1:
        return "NUTRITION_REFERENCE_BASIS_REVIEW", spread
    if calories and min(calories) < 250 < max(calories):
        return "POSSIBLE_SERVING_AS_100G_ANOMALY", spread
    if len(package_values) > 1 and spread is not None and spread <= 0.05:
        return "CLOSE_PROFILE_PACKAGE_FAMILY_CANDIDATE", spread
    if len(package_values) > 1:
        return "PACKAGE_FORMULA_DIFFERENCE_REVIEW", spread
    if spread is not None and spread <= 0.03:
        return "CLOSE_PROFILE_IDENTITY_REVIEW", spread
    return "CURRENT_IDENTITY_AND_FORMULA_EVIDENCE_REQUIRED", spread


def family_decision(target: str, key: str, products: list[dict]) -> str:
    barcodes = {row.get("barcode") for row in products if row.get("barcode")}
    packages = {row.get("package_text") for row in products if row.get("package_text")}
    contexts = {
        (row.get("market_region"), row.get("preparation_state"), row.get("catalog_type"))
        for row in products
    }
    spread = maximum_relative_spread(products)
    if len(barcodes) < 2:
        return "NOT_A_MULTI_BARCODE_FAMILY"
    if key in ("", target):
        return "FAMILY_IDENTITY_DETAIL_REQUIRED"
    if len(contexts) > 1:
        return "FAMILY_CONTEXT_SPLIT_REQUIRED"
    if len(packages) < 2:
        return "FAMILY_PACKAGE_METADATA_REQUIRED"
    if spread is None:
        return "FAMILY_NUTRITION_COMPLETENESS_REVIEW"
    if spread <= 0.05:
        return "FAMILY_CANDIDATE_READY_FOR_SOURCE_REVIEW"
    return "FAMILY_NUTRITION_OR_FORMULA_REVIEW"


def main() -> None:
    OUT.mkdir(exist_ok=True)
    with (ROOT / "catalog.csv").open(encoding="utf-8-sig", newline="") as handle:
        catalog = list(csv.DictReader(handle))

    selected = []
    for row in catalog:
        target, brand_state = target_for(row)
        if not target:
            continue
        identity_key, packs = identity(row, target)
        selected.append(
            {
                **row,
                "target_brand": target,
                "brand_state": brand_state,
                "identity_key": identity_key,
                "package_text": " | ".join(packs),
            }
        )

    groups = defaultdict(list)
    for row in selected:
        if row["identity_key"]:
            groups[(row["target_brand"], row["identity_key"])].append(row)

    decisions = []
    for (target, key), products in groups.items():
        if len(products) < 2:
            continue
        profiles = {profile(row) for row in products}
        contexts = {
            (row.get("market_region"), row.get("preparation_state"), row.get("catalog_type"))
            for row in products
        }
        package_values = {row["package_text"] for row in products if row["package_text"]}
        barcodes = [row.get("barcode") or "" for row in products]
        duplicate_barcodes = len([value for value in barcodes if value]) != len(
            set(value for value in barcodes if value)
        )
        if duplicate_barcodes:
            decision = "EXACT_GTIN_DUPLICATE_REVIEW"
        elif len(contexts) > 1:
            decision = "MARKET_PREPARATION_OR_TYPE_REVIEW"
        elif not all(complete_core(row) for row in products):
            decision = "INCOMPLETE_NUTRITION_REVIEW"
        elif len(profiles) > 1:
            decision = "NUTRITION_OR_FORMULA_DIFFERENCE_REVIEW"
        elif len(package_values) > 1:
            decision = "PACKAGE_VARIANT_CANDIDATE"
        else:
            decision = "IDENTITY_DUPLICATE_REVIEW"
        evidence_state, profile_spread = evidence_decision(target, key, products)
        family_state = family_decision(target, key, products)
        decisions.append(
            {
                "group_id": f"BR-{len(decisions) + 1:05d}",
                "target_brand": target,
                "identity_key": key,
                "decision": decision,
                "evidence_decision": evidence_state,
                "family_decision": family_state,
                "maximum_core_relative_spread": profile_spread,
                "products": products,
                "safe_to_apply": False,
            }
        )

    gtin_index = defaultdict(list)
    for row in selected:
        if row.get("barcode"):
            gtin_index[row["barcode"]].append(row)
    gtin_duplicates = {
        barcode: rows for barcode, rows in gtin_index.items() if len(rows) > 1
    }

    summary = {
        "catalogRows": len(catalog),
        "selectedRows": len(selected),
        "rowsByTarget": dict(Counter(row["target_brand"] for row in selected)),
        "brandStates": dict(Counter(row["brand_state"] for row in selected)),
        "multiRecordIdentityGroups": len(decisions),
        "groupDecisions": dict(Counter(group["decision"] for group in decisions)),
        "evidenceDecisions": dict(Counter(group["evidence_decision"] for group in decisions)),
        "familyDecisions": dict(Counter(group["family_decision"] for group in decisions)),
        "familyDecisionsByTarget": {
            target: dict(Counter(
                group["family_decision"] for group in decisions if group["target_brand"] == target
            ))
            for target in TARGETS
        },
        "evidenceDecisionsByTarget": {
            target: dict(Counter(
                group["evidence_decision"] for group in decisions if group["target_brand"] == target
            ))
            for target in TARGETS
        },
        "exactGtinDuplicateGroups": len(gtin_duplicates),
        "publicationStates": dict(Counter(row["publication_status"] for row in selected)),
        "automaticApplicationApproved": 0,
        "catalogWrites": 0,
        "limitations": [
            "Brand aliases and name-only matches are review candidates, not canonical assignments.",
            "Equal core nutrition does not prove equal formula.",
            "Package variant candidates preserve every GTIN record.",
        ],
    }
    (OUT / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (OUT / "groups.json").write_text(
        json.dumps(decisions, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    with (OUT / "records.csv").open("w", encoding="utf-8-sig", newline="") as handle:
        fields = [
            "target_brand", "brand_state", "id", "name", "display_name", "brand", "barcode",
            "source_key", "market_region", "preparation_state", "nutrition_reference_unit",
            "calories", "protein", "carbs", "fat", "fiber", "sugar", "publication_status",
            "identity_key", "package_text",
        ]
        writer = csv.DictWriter(handle, fieldnames=fields, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(selected)
    with (OUT / "groups.csv").open("w", encoding="utf-8-sig", newline="") as handle:
        fields = [
            "group_id", "target_brand", "decision", "evidence_decision", "family_decision",
            "maximum_core_relative_spread", "id", "name", "brand", "brand_state",
            "barcode", "package_text", "nutrition_reference_unit", "calories", "protein",
            "carbs", "fat", "fiber", "sugar", "safe_to_apply",
        ]
        writer = csv.DictWriter(handle, fieldnames=fields, extrasaction="ignore")
        writer.writeheader()
        for group in decisions:
            for product in group["products"]:
                writer.writerow({**product, **{key: value for key, value in group.items() if key != "products"}})
    with (OUT / "gtin-duplicates.csv").open("w", encoding="utf-8-sig", newline="") as handle:
        fields = ["barcode", "target_brand", "id", "name", "brand", "source_key", "publication_status"]
        writer = csv.DictWriter(handle, fieldnames=fields, extrasaction="ignore")
        writer.writeheader()
        for barcode, rows in gtin_duplicates.items():
            for row in rows:
                writer.writerow({**row, "barcode": barcode})
    print(json.dumps(summary, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()

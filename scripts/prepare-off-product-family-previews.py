"""Create admin preview payloads from OFF-confirmed package families; never calls the API."""
import importlib.util
import json
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path("outputs/package-family-audit-20261029")
SPEC = importlib.util.spec_from_file_location(
    "coverage", Path(__file__).with_name("analyze-package-family-coverage.py")
)
COVERAGE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(COVERAGE)
OFF_SPEC = importlib.util.spec_from_file_location(
    "off", Path(__file__).with_name("extract-package-family-off-snapshot.py")
)
OFF = importlib.util.module_from_spec(OFF_SPEC)
OFF_SPEC.loader.exec_module(OFF)


def display_name(value, brand=None):
    without_packages = OFF.package_pattern.sub(" ", value or "") if hasattr(OFF, "package_pattern") else value
    # The extractor intentionally exposes a parser, not its regex. Remove all supported package expressions here.
    without_packages = re.sub(
        r"(?<!\w)(?:\d+\s*[xX]\s*)?\d+(?:[.,]\d+)?\s*(?:kg|gr|g|ml|cl|lt|l)(?!\w)",
        " ", without_packages, flags=re.I,
    )
    without_packages = re.sub(r"\(\s*\)", " ", without_packages)
    without_packages = re.sub(r"\s*[-–]\s*[-–]\s*", " - ", without_packages)
    result = re.sub(r"\s+", " ", without_packages).strip(" ,-–")
    if brand:
        result = re.sub(rf"\s*[-–]\s*{re.escape(brand)}\s*$", "", result, flags=re.I).strip(" ,-–")
    return result


def family_key(brand, name):
    brand_key = COVERAGE.slug(brand) if hasattr(COVERAGE, "slug") else re.sub(r"[^a-z0-9]+", "-", OFF.fold(brand)).strip("-")
    normalized_name = OFF.fold(name)
    normalized_brand = OFF.fold(brand)
    if normalized_name.startswith(normalized_brand + " "):
        normalized_name = normalized_name[len(normalized_brand):].strip()
    variant_key = re.sub(r"[^a-z0-9]+", "-", normalized_name).strip("-")
    return f"{brand_key}:{variant_key}"


def split_family_key_collisions(payloads):
    key_counts = Counter(payload["request"]["familyKey"] for payload in payloads)
    collisions = [payload for payload in payloads if key_counts[payload["request"]["familyKey"]] > 1]
    ready = [payload for payload in payloads if key_counts[payload["request"]["familyKey"]] == 1]
    return ready, collisions


def compatible_profiles(profiles):
    if not profiles:
        return False
    first = profiles[0]
    for profile in profiles[1:]:
        if profile[0] != first[0]:
            return False
        for left, right in zip(first[1:], profile[1:]):
            if left in (None, "") or right in (None, ""):
                return False
            left, right = float(left), float(right)
            if abs(left - right) / max(abs(left), abs(right), 1.0) > 0.10:
                return False
    return True


def consolidate_family_key_collisions(payloads):
    grouped = {}
    for payload in payloads:
        grouped.setdefault(payload["request"]["familyKey"], []).append(payload)
    ready, held = [], []
    for key, matches in grouped.items():
        if len(matches) == 1:
            ready.append(matches[0])
            continue
        members = [member for match in matches for member in match["request"]["members"]]
        profiles = [profile for match in matches for profile in match.get("_coreProfiles", [])]
        product_ids = [member["productId"] for member in members]
        units = {member["packageUnit"] for member in members}
        package_signatures = {(member["packageQuantity"], member["packageUnit"]) for member in members}
        if (len(set(product_ids)) != len(product_ids) or len(units) != 1
                or len(package_signatures) != len(members) or not compatible_profiles(profiles)):
            held.extend(matches)
            continue
        members.sort(key=lambda member: (member["packageQuantity"], member["productId"]))
        representative = members[0]["productId"]
        for member in members:
            member["defaultVariant"] = member["productId"] == representative
        display_name_value = max(
            (match["request"]["displayName"] for match in matches),
            key=lambda value: (len(value.split()), len(value)),
        )
        ready.append({
            "groupId": "+".join(match["groupId"] for match in matches),
            "previewPath": matches[0]["previewPath"],
            "applyPath": matches[0]["applyPath"],
            "applyAllowed": False,
            "request": {
                "familyKey": key,
                "displayName": display_name_value,
                "representativeProductId": representative,
                "evidenceReason": (
                    "Compatible OFF-confirmed package groups sharing the same canonical family key were "
                    "consolidated. All GTIN records remain independent; human preview approval is required."
                ),
                "members": members,
            },
            "source": {
                "provider": "OPEN_FOOD_FACTS",
                "snapshot": matches[0]["source"]["snapshot"],
                "lastModifiedValues": [
                    value for match in matches for value in match["source"]["lastModifiedValues"]
                ],
            },
            "_coreProfiles": profiles,
        })
    return ready, held


def prepare(root=ROOT):
    evidence = json.loads((root / "explicit-package-off-evidence.json").read_text(encoding="utf-8"))
    candidates = {g["group_id"]: g for g in json.loads((root / "candidates.json").read_text(encoding="utf-8"))}
    payloads = []
    for reviewed in evidence["groups"]:
        full_family = reviewed["decision"] == "OFF_SNAPSHOT_FAMILY_REVIEW_READY"
        partial_family = (
            reviewed["decision"] == "OFF_MEMBER_PACKAGE_METADATA_REQUIRED"
            and reviewed.get("crossMemberNutritionCompatible")
        )
        if not full_family and not partial_family:
            continue
        candidate = candidates[reviewed["groupId"]]
        by_id = {int(row["id"]): row for row in candidate["products"]}
        reviewed_products = reviewed["products"]
        if partial_family:
            allowed = {
                "OFF_SOURCE_PACKAGE_AND_NUTRITION_CONFIRMED",
                "OFF_PACKAGE_AND_NUTRITION_CONFIRMED",
            }
            reviewed_products = [
                product for product in reviewed_products
                if product.get("status") in allowed
                and (product.get("catalogPackage") or product.get("sourcePackage"))
            ]
            signatures = [tuple(product.get("catalogPackage") or product.get("sourcePackage"))
                          for product in reviewed_products]
            if (len(reviewed_products) < 2 or len(set(signatures)) != len(reviewed_products)
                    or len({signature[1] for signature in signatures}) != 1):
                continue
        selected_ids = {product["id"] for product in reviewed_products}
        names = [
            display_name(row.get("display_name") or row.get("name"), row.get("brand"))
            for product_id, row in by_id.items() if product_id in selected_ids
        ]
        if len({OFF.fold(name) for name in names}) != 1:
            continue
        name = max(names, key=lambda value: (sum(char.isupper() for char in value), -len(value)))
        brand = next(iter(by_id.values()))["brand"]
        members = []
        for product in reviewed_products:
            amount, unit = product.get("catalogPackage") or product["sourcePackage"]
            members.append({
                "productId": product["id"],
                "packageQuantity": amount,
                "packageUnit": "GRAM" if unit == "g" else "MILLILITER",
                "packageLabel": f"{amount:g} {unit}",
                "sourceLastModified": product.get("sourceLastModified"),
            })
        members.sort(key=lambda item: (item["packageQuantity"], item["productId"]))
        representative = members[0]["productId"]
        request_members = [
            {key: value for key, value in member.items() if key != "sourceLastModified"}
            | {"defaultVariant": member["productId"] == representative}
            for member in members
        ]
        payloads.append({
            "groupId": reviewed["groupId"],
            "previewPath": "/api/v1/admin/product-families/preview",
            "applyPath": "/api/v1/admin/product-families",
            "applyAllowed": False,
            "request": {
                "familyKey": family_key(brand, name),
                "displayName": name,
                "representativeProductId": representative,
                "evidenceReason": (
                    "Exact GTIN, package quantity and core per-100 nutrition matched the local "
                    "Open Food Facts bulk snapshot. Human preview approval is still required."
                ) if full_family else (
                    "Only the listed members have exact OFF GTIN, package quantity and compatible core "
                    "nutrition evidence. Unverified sibling products remain outside this partial family. "
                    "Human preview approval is still required."
                ),
                "members": request_members,
            },
            "reviewMode": "FULL_FAMILY" if full_family else "VERIFIED_PARTIAL_FAMILY",
            "excludedProductIds": sorted(set(by_id) - selected_ids),
            "source": {
                "provider": "OPEN_FOOD_FACTS",
                "snapshot": evidence["summary"]["snapshot"],
                "lastModifiedValues": [member.get("sourceLastModified") for member in members],
            },
            "_coreProfiles": [
                tuple(row.get(field) for field in ("nutrition_reference_unit", "calories", "protein", "carbs", "fat"))
                for product_id, row in by_id.items() if product_id in selected_ids
            ],
        })
    payloads, collisions = consolidate_family_key_collisions(payloads)
    for payload in payloads + collisions:
        payload.pop("_coreProfiles", None)
    document = {
        "previewCount": len(payloads),
        "familyKeyCollisionCount": len(collisions),
        "applyAllowed": False,
        "catalogWrites": 0,
        "payloads": payloads,
        "familyKeyCollisionReview": collisions,
    }
    (root / "off-family-admin-preview-payloads.json").write_text(
        json.dumps(document, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(json.dumps({
        "previewCount": len(payloads),
        "familyKeyCollisionCount": len(collisions),
        "applyAllowed": False,
        "catalogWrites": 0,
    }))
    return document


if __name__ == "__main__":
    prepare(Path(sys.argv[1] if len(sys.argv) > 1 else ROOT))

"""Validate current R6 inventory and split duplicate REVIEW_ONLY candidates.

Exact profiles are not product/pack identity proof. No DB access or writes.
"""
import argparse
import csv
import hashlib
import importlib.util
import json
import math
from collections import Counter
from pathlib import Path

spec = importlib.util.spec_from_file_location("r5", Path(__file__).with_name("close-r5-release-review.py"))
r5 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(r5)


def fingerprint(value):
    return r5.source.r5.fingerprint(value)


def csv_value(value):
    if isinstance(value, (list, dict)):
        return json.dumps(value, ensure_ascii=False)
    if isinstance(value, str) and value.lstrip()[:1] in {"=", "+", "-", "@"}:
        return "'" + value
    return value


def inherited_primary_counts(categories, multiple_primary):
    by_slug = {r["slug"]: r for r in categories}
    if len(by_slug) != len(categories):
        raise ValueError("Duplicate taxonomy slug")
    totals = {slug: 0 for slug in by_slug}
    for row in categories:
        slug, seen = row["slug"], set()
        while slug:
            if slug in seen or slug not in by_slug:
                raise ValueError("Category parent cycle or missing parent")
            seen.add(slug)
            if row["active"]:
                totals[slug] += row["primary_products"]
            slug = by_slug[slug]["parent_slug"]
    return [{**row, "primary_products_including_descendants": totals[row["slug"]] if multiple_primary == 0 else None}
            for row in categories]


def validate_sections(sections):
    expected = {"inventory", "category_matrix", "category_distribution", "unassigned_source_tags",
                "duplicate_summary", "duplicate_group_index", "duplicate_samples", "integrity", "source_identity_collisions"}
    if set(sections) != expected:
        raise ValueError("Exact complete R6 section membership required")
    inventory, matrix = sections["inventory"], sections["category_matrix"]
    if sum(r["products"] for r in matrix) != inventory["published"]:
        raise ValueError("Published category matrix count mismatch")
    if len({(r["catalog_type"], r["market_region"]) for r in matrix}) != len(matrix):
        raise ValueError("Duplicate category matrix bucket")
    for row in matrix:
        if row["products"] != row["with_primary"] + row["source_tags_no_primary"] + row["no_tags_no_primary"]:
            raise ValueError("Category partitions overlap or are incomplete")
    for field, aggregate in (("with_primary", "withActivePrimary"), ("source_tags_no_primary", "withoutActivePrimaryWithTags"),
                             ("no_tags_no_primary", "withoutActivePrimaryWithoutTags")):
        if sum(r[field] for r in matrix) != inventory[aggregate]:
            raise ValueError("Category aggregate mismatch")
    groups, summary = sections["duplicate_samples"], sections["duplicate_summary"]
    if not summary["candidateOnly"] or sum(summary["decisions"].values()) != summary["groups"]:
        raise ValueError("Duplicate summary is not a complete candidate-only partition")
    index = sections["duplicate_group_index"]
    if (len(index) != summary["groups"] or len({(g["brand_key"], g["name_key"]) for g in index}) != len(index)
            or sum(g["product_count"] for g in index) != summary["candidateRows"]
            or dict(Counter(g["decision"] for g in index)) != summary["decisions"]):
        raise ValueError("Full duplicate group index is incomplete")
    if len(groups) != summary["sampledGroups"] or len(groups) > summary["sampleLimit"]:
        raise ValueError("Duplicate sample coverage mismatch")
    if len({(g["brand_key"], g["name_key"]) for g in groups}) != len(groups):
        raise ValueError("Duplicate group key")
    for group in groups:
        members = group["members"]
        if (group["applyAllowed"] is not False or not members or len(members) > summary["memberLimit"]
                or len({m["id"] for m in members}) != len(members)
                or group["sampleComplete"] != (len(members) == group["product_count"])):
            raise ValueError("Incomplete/unsafe group membership proof")


def assess(group, held_ids):
    reasons, members = [], group["members"]
    foods = [m["food"] for m in members]
    ids = {str(m["id"]) for m in members}
    if not group["sampleComplete"]:
        reasons.append("TRUNCATED_GROUP_NOT_APPROVABLE")
    if ids & held_ids:
        reasons.append("R5_HELD_PRODUCT")
    if any(str(f.get("id")) != str(m["id"]) or f.get("is_custom") is True or f.get("publication_status") != "PUBLISHED"
           or f.get("catalog_type") != "BRANDED_PRODUCT" for m, f in zip(members, foods)):
        reasons.append("CURRENT_IDENTITY_OR_PUBLICATION_REVIEW")
    if any(any(k not in f for k in r5.FIELDS) for f in foods):
        reasons.append("NUTRIENT_COLUMNS_UNMEASURED")
    if any(any(f.get(k) is None for k in ("calories", "protein", "fat", "carbs")) for f in foods):
        reasons.append("CORE_NUTRITION_MISSING")
    if any(any(value is not None and (isinstance(value, bool) or not isinstance(value, (int, float))
               or not math.isfinite(value) or value < 0) for value in (f.get(k) for k in r5.FIELDS)) for f in foods):
        reasons.append("INVALID_NUMERIC_PROFILE")
    if len({f.get("nutrition_reference_unit") for f in foods}) != 1 or any(f.get("nutrition_reference_unit") not in {"PER_100G", "PER_100ML"} for f in foods):
        reasons.append("MASS_VOLUME_BASIS_CONFLICT")
    if len({f.get("preparation_state") for f in foods}) != 1:
        reasons.append("PREPARATION_VARIANT")
    if len({fingerprint({k: f.get(k) for k in r5.FIELDS}) for f in foods}) != 1:
        reasons.append("FULL_NUTRIENT_PROFILE_DIFFERENT")
    if len({fingerprint((f.get("serving_size_grams"), f.get("serving_unit"), m["options"])) for f, m in zip(foods, members)}) != 1:
        # Option database IDs are not portion semantics.
        signatures = []
        for f, m in zip(foods, members):
            options = sorted(((o.get("unit_type"), o.get("quantity"), o.get("gram_weight"), o.get("ml_volume"),
                               o.get("is_default"), o.get("quality_status"), o.get("user_visible"), o.get("label"))
                              for o in m["options"]), key=fingerprint)
            signatures.append(fingerprint((f.get("serving_size_grams"), f.get("serving_unit"), options)))
        if len(set(signatures)) != 1:
            reasons.append("SERVING_PROFILE_DIFFERENT")
    codes = [f.get("normalized_barcode") or f.get("barcode") for f in foods]
    if any(not r5.source.r5.valid_gtin(code) for code in codes):
        reasons.append("GTIN_MISSING_OR_INVALID")
    if any(not f.get("source_key") for f in foods):
        reasons.append("SOURCE_IDENTITY_MISSING")
    if any(m["source_evidence_count"] > len(m["source_evidence"]) for m in members):
        reasons.append("SOURCE_EVIDENCE_TRUNCATED")
    state = "REVIEW_BLOCKERS" if reasons else "MATCHING_CURRENT_PROFILE_SOURCE_PACKAGE_REVIEW"
    if not reasons and len(set(codes)) > 1:
        reasons.append("DIFFERENT_VALID_GTINS_KEEP_IDENTITIES_PENDING_PACKAGE_PROOF")
    if not reasons:
        reasons.append("EXACT_SOURCE_VERSION_AND_ADMIN_APPROVAL_REQUIRED")
    field_differences = {}
    for key in r5.FIELDS:
        values = [f.get(key) for f in foods]
        if len({fingerprint(v) for v in values}) > 1:
            known = {fingerprint(v) for v in values if v is not None}
            field_differences[key] = {"state": "KNOWN_VALUES_DIFFER" if len(known) > 1 else "UNKNOWN_VS_KNOWN",
                                      "valuesById": {str(m["id"]): f.get(key) for m, f in zip(members, foods)}}
    return {"brandKey": group["brand_key"], "nameKey": group["name_key"], "brand": group["brand_name"],
            "name": group["representative_name"], "products": group["product_count"], "sampledIds": sorted(ids, key=int),
            "state": state, "reasons": reasons, "currentGroupFingerprint": fingerprint(group),
            "applyAllowed": False, "sourceFreshnessProven": False, "fieldDifferences": field_differences, "members": members}


def select_sources(reviews, budget):
    if not 1 <= budget <= 100:
        raise ValueError("Bounded source budget required")
    preferred = {"Tesco", "M&S", "Dunnes Stores", "VITHIT"}
    chosen, codes = [], set()
    for review in sorted(reviews, key=lambda r: (r["brand"] not in preferred, r["products"], r["brand"], r["name"])):
        if review["state"] != "MATCHING_CURRENT_PROFILE_SOURCE_PACKAGE_REVIEW":
            continue
        foods = [m["food"] for m in review["members"]]
        if any(f.get("data_source") != "OPEN_FOOD_FACTS" or f.get("source_key") != "barcode:" + (f.get("normalized_barcode") or f.get("barcode") or "") for f in foods):
            continue
        group_codes = {f.get("normalized_barcode") or f.get("barcode") for f in foods}
        if len(codes | group_codes) > budget:
            continue
        codes.update(group_codes)
        chosen.append(review)
    return chosen, codes


def run(args):
    csv.field_size_limit(16 * 1024 * 1024)
    proof = json.loads(args.proof.read_text())
    if (proof.get("scope") != "r6-audit" or proof.get("transactionReadOnly") is not True or proof.get("exitCode") != 0
            or proof["sha256"] != r5.source.r5.closure.sha(args.current)):
        raise ValueError("Current AWS read-only R6 checksum proof required")
    with args.current.open(encoding="utf-8-sig", newline="") as stream:
        frames = list(csv.DictReader(stream))
    if len(frames) != proof["rows"] or len({r["id"] for r in frames}) != len(frames):
        raise ValueError("Section count/uniqueness mismatch")
    sections = {r["id"]: json.loads(r["values"]) for r in frames}
    validate_sections(sections)
    sections["category_distribution"] = inherited_primary_counts(sections["category_distribution"], sections["integrity"]["multiplePrimaryPublished"])
    held_ids = set()
    if args.r5_review:
        if r5.source.r5.closure.sha(args.r5_review) != json.loads(args.r5_review.parent.joinpath("summary.json").read_text())["reviewSha256"]:
            raise ValueError("R5 hold manifest changed")
        held_ids = {r["id"] for r in json.loads(args.r5_review.read_text(encoding="utf-8"))["rows"] if r["status"] == "HOLD"}
    reviews = [assess(g, held_ids) for g in sections["duplicate_samples"]]
    args.output.mkdir(parents=True, exist_ok=True)
    chosen, codes = select_sources(reviews, args.source_budget)
    with (args.output / "source-review-queue.csv").open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=["id", "source_key"])
        writer.writeheader()
        writer.writerows({"id": m["id"], "source_key": m["food"]["source_key"]} for r in chosen for m in r["members"])
    (args.output / "source-selected-groups.json").write_text(json.dumps({"rows": chosen, "applyAllowed": False}, ensure_ascii=False) + "\n", encoding="utf-8")
    for filename, rows in (("category-coverage.csv", sections["category_matrix"]),
                           ("category-distribution.csv", sections["category_distribution"]),
                           ("category-residual-source-tags.csv", sections["unassigned_source_tags"]),
                           ("duplicate-group-index.csv", sections["duplicate_group_index"]),
                           ("duplicate-groups.csv", [{k: v for k, v in r.items() if k != "members"} for r in reviews])):
        with (args.output / filename).open("w", encoding="utf-8-sig", newline="") as stream:
            writer = csv.DictWriter(stream, fieldnames=list(rows[0]) if rows else ["empty"])
            writer.writeheader()
            writer.writerows({k: csv_value(v) for k, v in r.items()} for r in rows)
    path = args.output / "review-manifest.json"
    path.write_text(json.dumps({"scope": "R6_REVIEW_ONLY", "measuredAt": proof["time"], "rows": reviews}, ensure_ascii=False) + "\n", encoding="utf-8")
    summary = {"measuredAt": proof["time"], "schemaVersion": proof.get("schemaVersion"),
               "inventory": sections["inventory"], "integrity": sections["integrity"], "duplicates": sections["duplicate_summary"],
               "sampleReviewStates": dict(Counter(r["state"] for r in reviews)),
               "sampleOverlappingReasons": dict(Counter(h for r in reviews for h in r["reasons"])),
               "fieldDifferenceKinds": dict(Counter(d["state"] for r in reviews for d in r["fieldDifferences"].values())),
               "sourceSelectedGroups": len(chosen), "sourceRequestedUniqueGtins": len(codes),
               "sourceIdentityCollisionSamples": len(sections["source_identity_collisions"]),
               "categoryMarketsAreStoredRegionNotFullAvailability": True,
               "databaseWrites": 0, "approvedGroups": 0, "sampleIsNotWholeDuplicateReview": True,
               "snapshotSha256": proof["sha256"], "reviewSha256": r5.source.r5.closure.sha(path)}
    (args.output / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    for name in ("current", "proof", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--r5-review", type=Path)
    parser.add_argument("--source-budget", type=int, default=20)
    run(parser.parse_args())

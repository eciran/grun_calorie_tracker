import copy
import importlib.util
import json
import unittest
from unittest.mock import Mock, patch
from datetime import datetime, timezone
from pathlib import Path


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


review = load("review", "review-r6-catalog-audit.py")
source = load("sources", "review-r6-source-matches.py")


def fixture():
    members = []
    for i, code in ((1, "5449000000996"), (2, "3017620422003")):
        food = {k: None for k in review.r5.FIELDS}
        food.update(id=i, catalog_type="BRANDED_PRODUCT", publication_status="PUBLISHED", is_custom=False,
                    normalized_barcode=code, source_key="barcode:" + code, data_source="OPEN_FOOD_FACTS",
                    nutrition_reference_unit="PER_100G", preparation_state="UNSPECIFIED", calories=60, protein=3, carbs=5, fat=1)
        members.append({"id": i, "food": food, "options": [], "source_evidence": [], "source_evidence_count": 0})
    return {"sampleComplete": True, "members": members, "brand_key": "brand:1", "name_key": "milk",
            "brand_name": "Tesco", "representative_name": "Milk", "product_count": 2}


class CatalogReviewTests(unittest.TestCase):
    def test_category_parent_counts_do_not_look_empty_when_children_are_assigned(self):
        categories = [{"slug": "dairy", "parent_slug": None, "active": True, "primary_products": 0},
                      {"slug": "milk", "parent_slug": "dairy", "active": True, "primary_products": 2}]
        self.assertEqual(2, review.inherited_primary_counts(categories, 0)[0]["primary_products_including_descendants"])
        self.assertIsNone(review.inherited_primary_counts(categories, 1)[0]["primary_products_including_descendants"])
        categories[0]["parent_slug"] = "milk"
        with self.assertRaises(ValueError):
            review.inherited_primary_counts(categories, 0)

    def test_csv_names_cannot_become_spreadsheet_formulas(self):
        self.assertEqual("'=1+2", review.csv_value("=1+2"))
        self.assertEqual("Milk", review.csv_value("Milk"))

    def test_missing_full_index_or_inconsistent_category_counts_fail_closed(self):
        group = fixture()
        group["applyAllowed"] = False
        group["decision"] = "REVIEW_DIFFERENT_GTIN"
        sections = {"inventory": {"published": 2, "withActivePrimary": 2, "withoutActivePrimaryWithTags": 0, "withoutActivePrimaryWithoutTags": 0},
                    "category_matrix": [{"catalog_type": "BRANDED_PRODUCT", "market_region": "UK_IE", "products": 2,
                                         "with_primary": 2, "source_tags_no_primary": 0, "no_tags_no_primary": 0}],
                    "category_distribution": [], "unassigned_source_tags": [], "integrity": {}, "source_identity_collisions": [],
                    "duplicate_summary": {"candidateOnly": True, "groups": 1, "candidateRows": 2,
                                          "decisions": {"REVIEW_DIFFERENT_GTIN": 1}, "sampledGroups": 1, "sampleLimit": 100, "memberLimit": 5},
                    "duplicate_group_index": [group], "duplicate_samples": [group]}
        review.validate_sections(sections)
        bad = copy.deepcopy(sections)
        bad["duplicate_group_index"] = []
        with self.assertRaises(ValueError):
            review.validate_sections(bad)
        bad = copy.deepcopy(sections)
        bad["category_matrix"][0]["with_primary"] = 1
        with self.assertRaises(ValueError):
            review.validate_sections(bad)

    def test_matching_profile_is_not_merge_approval(self):
        result = review.assess(fixture(), set())
        self.assertEqual("MATCHING_CURRENT_PROFILE_SOURCE_PACKAGE_REVIEW", result["state"])
        self.assertFalse(result["applyAllowed"])
        self.assertFalse(result["sourceFreshnessProven"])
        self.assertIn("DIFFERENT_VALID_GTINS_KEEP_IDENTITIES_PENDING_PACKAGE_PROOF", result["reasons"])

    def test_basis_truncation_r5_and_unknown_value_are_preserved(self):
        group = fixture()
        group["members"][1]["food"]["nutrition_reference_unit"] = "PER_100ML"
        self.assertIn("MASS_VOLUME_BASIS_CONFLICT", review.assess(group, set())["reasons"])
        group = fixture()
        group["sampleComplete"] = False
        self.assertIn("TRUNCATED_GROUP_NOT_APPROVABLE", review.assess(group, set())["reasons"])
        self.assertIn("R5_HELD_PRODUCT", review.assess(fixture(), {"1"})["reasons"])
        group = fixture()
        group["members"][1]["food"]["sodium"] = 0
        result = review.assess(group, set())
        self.assertEqual("UNKNOWN_VS_KNOWN", result["fieldDifferences"]["sodium"]["state"])
        self.assertIsNone(group["members"][0]["food"]["sodium"])

    def test_exact_micronutrient_difference_is_not_rounded_away(self):
        group = fixture()
        group["members"][0]["food"]["sodium"] = .001
        group["members"][1]["food"]["sodium"] = .004
        self.assertEqual("KNOWN_VALUES_DIFFER", review.assess(group, set())["fieldDifferences"]["sodium"]["state"])

    def test_source_budget_never_selects_a_partial_group(self):
        candidate = review.assess(fixture(), set())
        self.assertEqual([], review.select_sources([candidate], 1)[0])
        self.assertEqual(1, len(review.select_sources([candidate], 2)[0]))
        held = review.assess(fixture(), {"1"})
        self.assertEqual([], review.select_sources([held], 20)[0])

    def test_source_budget_can_cover_the_whole_matching_queue(self):
        candidates = []
        for i in range(12):
            group = fixture()
            group["representative_name"] = "Milk " + str(i)
            for member in group["members"]:
                # Repeated identities only exercise group selection; production
                # queue integrity is checked before this helper is invoked.
                member["food"]["normalized_barcode"] = str(i) + member["food"]["normalized_barcode"]
                member["food"]["source_key"] = "barcode:" + member["food"]["normalized_barcode"]
            candidate = review.assess(fixture(), set())
            candidate["members"] = group["members"]
            candidate["name"] = group["representative_name"]
            candidates.append(candidate)
        self.assertEqual(10, len(review.select_sources(candidates, 20)[0]))
        self.assertEqual(12, len(review.select_sources(candidates, 100)[0]))
        with self.assertRaises(ValueError):
            review.select_sources(candidates, 101)

    def test_source_review_rejects_stale_future_or_mismatched_snapshot_time(self):
        now = datetime(2026, 10, 13, tzinfo=timezone.utc)
        path = Mock()
        for stamp, summary_stamp in (("2026-10-11T23:00:00Z", "2026-10-11T23:00:00Z"),
                                     ("2026-10-13T01:00:00Z", "2026-10-13T01:00:00Z"),
                                     ("2026-10-12T23:00:00Z", "2026-10-12T22:00:00Z")):
            path.read_text.return_value = json.dumps({"rows": [], "measuredAt": stamp})
            path.parent.joinpath.return_value.read_text.return_value = json.dumps({"measuredAt": summary_stamp, "reviewSha256": "hash"})
            with patch.object(source.source.r5.closure, "sha", return_value="hash"):
                with self.assertRaises(ValueError):
                    source.verified_reviews(path, now)
        path.read_text.return_value = json.dumps({"rows": [], "measuredAt": "2026-10-12T23:00:00Z"})
        path.parent.joinpath.return_value.read_text.return_value = json.dumps({"measuredAt": "2026-10-12T23:00:00Z", "reviewSha256": "hash"})
        with patch.object(source.source.r5.closure, "sha", return_value="hash"):
            self.assertEqual([], source.verified_reviews(path, now)[0])

    def test_fresh_source_unit_conflict_and_missing_known_fact_are_held(self):
        member = fixture()["members"][0]
        code = member["food"]["normalized_barcode"]
        body = {"code": code, "nutrition_data_per": "100ml", "categories_tags": ["en:milks"],
                "nutriments": {"energy-kcal_100g": 60, "proteins_100g": 3, "fat_100g": 1, "carbohydrates_100g": 5}}
        response = {"status": 1, "product": body}
        receipt = {"barcode": code, "product": body, "response": response, "responseFingerprint": review.fingerprint(response),
                   "checkedAt": "2026-10-12T13:00:00Z", "status": "FETCHED", "sourceUrl": source.fetch.API + code + ".json?fields=code"}
        now = datetime(2026, 10, 12, 14, tzinfo=timezone.utc)
        result = source.member_review(member, receipt, now)
        self.assertIn("SOURCE_CURRENT_REFERENCE_UNIT_DIFFERS_R5", result["issues"])
        member["food"]["sodium"] = .01
        result = source.member_review(member, receipt, now)
        self.assertIn("CURRENT_SOURCE_NUTRIENT_COMPARISON_REQUIRED", result["issues"])
        self.assertFalse(result["applyAllowed"])
        self.assertIn("COMPLETE_EXACT_SOURCE_RECEIPT_REQUIRED", source.member_review(member, {**receipt, "responseFingerprint": "bad"}, now)["issues"])

    def test_package_variants_and_missing_composition_never_approve(self):
        members = [{"issues": [], "sourcePackage": ["g", 200]}, {"issues": [], "sourcePackage": ["g", 400]}]
        self.assertEqual("SOURCE_REPORTED_PACKAGE_VARIANTS_KEEP_SEPARATE", source.group_state(members))
        members[1]["sourcePackage"] = ["g", 200]
        self.assertEqual("PACKAGE_OR_COMPOSITION_PROOF_REQUIRED", source.group_state(members))

    def test_package_variant_evidence_survives_an_independent_nutrient_blocker(self):
        members = [{"issues": ["CURRENT_SOURCE_NUTRIENT_COMPARISON_REQUIRED"], "sourcePackage": ["g", 200]},
                   {"issues": [], "sourcePackage": ["g", 400]}]
        self.assertEqual("SOURCE_PROFILE_OR_UNIT_REVIEW_KEEP_SEPARATE", source.group_state(members))
        self.assertEqual(["SOURCE_PACKAGE_SIZE_OR_DIMENSION_DIFFERS"], source.variant_findings(members))
        members[1]["sourcePackage"] = None
        self.assertEqual([], source.variant_findings(members))

    def test_package_text_retains_dimensions_and_ambiguous_multipacks(self):
        self.assertEqual((("ml", 500), None), source.package_measure({"quantity": "500 ml"}))
        self.assertEqual((("g", 500), None), source.package_measure({"quantity": "0.5 kg"}))
        self.assertEqual((None, "SOURCE_PACKAGE_MEASURES_CONFLICT"),
                         source.package_measure({"quantity": "500 ml", "product_quantity": 500, "product_quantity_unit": "g"}))
        self.assertEqual((None, "PACKAGE_MULTIPACK_DETAIL_REVIEW"), source.package_measure({"quantity": "2 x 400 g"}))
        self.assertEqual((None, "PACKAGE_DECIMAL_OR_THOUSANDS_AMBIGUOUS"), source.package_measure({"quantity": "1,000 ml"}))


if __name__ == "__main__":
    unittest.main()

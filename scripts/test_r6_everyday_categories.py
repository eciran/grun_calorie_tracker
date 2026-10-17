import copy
import importlib.util
import unittest
from unittest.mock import patch
from types import SimpleNamespace
from datetime import datetime, timezone
from pathlib import Path

spec = importlib.util.spec_from_file_location("review", Path(__file__).with_name("review-r6-everyday-categories.py"))
review = importlib.util.module_from_spec(spec)
spec.loader.exec_module(review)


class CategoryTests(unittest.TestCase):
    def source(self, code, description):
        return {"foodCategory": {"code": code}, "description": description}

    def test_mayonnaise_is_not_oil_even_if_db_name_claims_oil(self):
        self.assertEqual("sauces-condiments", review.usda_target(self.source("0400", "Mayonnaise, reduced fat, with olive oil")))
        self.assertEqual("cooking-oils", review.usda_target(self.source("0400", "Oil, olive, salad or cooking")))

    def test_juice_not_fruit_and_unknown_fruit_product_held(self):
        self.assertEqual("juice", review.usda_target(self.source("0900", "Orange juice, raw")))
        self.assertEqual("fruit", review.usda_target(self.source("0900", "Bananas, raw")))
        self.assertIsNone(review.usda_target(self.source("0900", "Fruit pie filling")))

    def test_source_word_water_does_not_make_rice_a_drink(self):
        self.assertEqual("grains-starches", review.usda_target(self.source("2000", "Rice, cooked with water")))
        self.assertEqual("breakfast-cereals", review.usda_target(self.source("0800", "Cereals, oats, cooked with water")))

    def test_butter_and_nut_spreads_not_milk_or_whole_nuts(self):
        self.assertEqual("solid-fats", review.usda_target(self.source("0100", "Butter, salted")))
        self.assertEqual("spreads-dips", review.usda_target(self.source("1200", "Nuts, almond butter, plain")))
        self.assertEqual("spreads-dips", review.usda_target(self.source("1600", "Peanut butter, smooth")))

    def test_potato_leaves_not_starchy_potatoes_and_unknown_vegetable_product_held(self):
        self.assertEqual("vegetables", review.usda_target(self.source("1100", "Sweet potato leaves, raw")))
        self.assertEqual("potatoes-starchy-vegetables", review.usda_target(self.source("1100", "Sweet potato, boiled")))
        self.assertIsNone(review.usda_target(self.source("1100", "Vegetable juice cocktail")))

    def fixture(self):
        food = {"id": 1, "catalog_type": "STANDARD_PREPARED_ITEM", "source_key": "GRUN_PREPARED_V1:espresso",
                "data_source": "LOCAL_CURATED", "name": "Espresso", "preparation_state": "PREPARED",
                "market_region": "GLOBAL", "nutrition_basis": "SOURCE_REPORTED", "nutrition_reference_unit": "PER_100ML",
                "calories": 9, "protein": 0.1, "fat": 0.2, "carbs": 1.7}
        row = {"id": "1", "food": food, "assignments": [], "source_tags": [], "evidence": []}
        sources = {"prepared": {food["source_key"]: {"category": "COFFEE", "portion_profile": "ESPRESSO"}}, "prepared_ready": {food["source_key"]: copy.deepcopy(food)}}
        categories = {"hot-drinks": {"id": 2, "active": True, "parent_id": 3}, "beverages": {"id": 3, "active": True, "parent_id": None}}
        return row, sources, categories

    def nominate(self, row, sources, categories, holds=None):
        return review.nominate(row, sources, categories, set(), holds or {})

    def test_internal_manifest_nomination_is_not_live_or_nutrition_approval(self):
        result = self.nominate(*self.fixture())
        self.assertEqual("SAFE_CATEGORY_NOMINATION", result["status"])
        self.assertFalse(result["applyAllowed"])
        self.assertFalse(result["nutritionVerified"])
        self.assertTrue(result["warnings"])

    def test_existing_admin_category_preserved(self):
        row, sources, categories = self.fixture()
        row["assignments"] = [{"slug": "milk", "assignment_source": "ADMIN"}]
        result = self.nominate(row, sources, categories)
        self.assertEqual("REVIEW_REQUIRED", result["status"])
        self.assertEqual(row["assignments"], result["oldAssignments"])

    def test_inactive_ancestor_blocks_nomination(self):
        row, sources, categories = self.fixture()
        categories["beverages"]["active"] = False
        self.assertIn("TARGET_ANCESTOR_INACTIVE", self.nominate(row, sources, categories)["reasons"])

    def test_name_nutrient_unit_drift_blocks_exact_manifest_reuse(self):
        for field, value in (("name", "Espresso cake"), ("calories", 90), ("protein", None), ("nutrition_reference_unit", "PER_100G")):
            row, sources, categories = self.fixture()
            row["food"][field] = value
            with self.subTest(field=field):
                self.assertEqual("REVIEW_REQUIRED", self.nominate(row, sources, categories)["status"])

    def test_unknown_or_broad_mixed_group_never_guessed_from_name(self):
        row, sources, categories = self.fixture()
        sources["prepared"][row["food"]["source_key"]]["category"] = "COLD_DRINK"
        self.assertIn("AMBIGUOUS_OR_MISSING_SOURCE_CATEGORY", self.nominate(row, sources, categories)["reasons"])

    def test_r5_prerequisite_cannot_be_bypassed_by_known_category(self):
        result = self.nominate(*self.fixture(), holds={"1": ["LIQUID_ML_CONTRACT_REVIEW"]})
        self.assertEqual("hot-drinks", result["targetSlug"])
        self.assertEqual("REVIEW_REQUIRED", result["status"])
        self.assertIn("R5_LIQUID_ML_CONTRACT_REVIEW", result["reasons"])

    def test_iced_coffee_and_tea_not_hot_drinks(self):
        for group in ("COFFEE", "HOT_DRINK"):
            self.assertEqual("beverages", review.prepared_target({"category": group, "portion_profile": "COLD_DRINK"}))
        self.assertIsNone(review.prepared_target({"category": "COFFEE", "portion_profile": "UNKNOWN"}))

    def test_egg_and_sandwich_profiles_get_specific_children(self):
        self.assertEqual("eggs", review.prepared_target({"category": "BREAKFAST", "portion_profile": "EGG_PORTION"}))
        self.assertEqual("sandwiches-wraps", review.prepared_target({"category": "LIGHT_MEAL", "portion_profile": "WRAP"}))

    def test_duplicate_or_missing_source_keys_fail_closed(self):
        for rows in (({"key": "same"}, {"key": "same"}), ({"key": ""},)):
            with self.assertRaises(ValueError):
                review.unique_index(rows, "key")

    def test_freshness_future_stale_and_naive_dates_rejected(self):
        now = datetime(2026, 10, 12, 13, tzinfo=timezone.utc)
        self.assertTrue(review.fresh("2026-10-11T21:40:00+00:00", now))
        for timestamp in ("2026-10-13T13:00:00+00:00", "2026-10-10T13:00:00+00:00", "2026-10-12T12:00:00"):
            self.assertFalse(review.fresh(timestamp, now))

    def test_snapshot_query_scope_readonly_and_complete_provenance(self):
        sql = (Path(__file__).with_name("sql") / "export-r6-everyday-category-review.sql").read_text()
        for token in ("READ ONLY", "PUBLISHED", "NOT coalesce(f.is_custom,false)", "source_tags", "assignments", "evidence", "ROLLBACK"):
            self.assertIn(token, sql)
        for forbidden in ("UPDATE ", "INSERT ", "DELETE ", "DROP ", "CREATE "):
            self.assertNotIn(forbidden, sql.upper())

    def snapshot_fixture(self):
        import json
        rows = []
        for kind, count in review.TYPES.items():
            for _ in range(count):
                identifier = str(len(rows) + 1)
                rows.append({"id": identifier, "food": json.dumps({"id": int(identifier), "catalog_type": kind,
                             "source_key": "fixture:" + identifier, "is_custom": False, "publication_status": "PUBLISHED"}),
                             "assignments": "[]", "source_tags": "[]", "evidence": "[]"})
        proof = {"origin": "AWS_READ_ONLY_SNAPSHOT", "transactionReadOnly": True, "exitCode": 0,
                 "scope": "r6-everyday", "sha256": "hash", "rows": 696, "sqlSha256": "hash",
                 "time": "2026-10-12T12:00:00+00:00", "metrics": [{"orphanAssignments": 0, "types": review.TYPES,
                 "categories": [{"id": 1, "slug": "fruit", "active": True, "parent_id": None}]}]}
        return rows, proof

    def verify_fixture(self, rows, proof):
        import json
        with patch.object(review, "read_csv", return_value=rows), patch.object(review, "sha", return_value="hash"):
            return review.verify_snapshot(Path("fixture.csv"), SimpleNamespace(read_text=lambda **kw: json.dumps(proof)),
                                          datetime(2026,10,12,13,tzinfo=timezone.utc))

    def test_complete_696_snapshot_accepted(self):
        self.assertEqual(696, len(self.verify_fixture(*self.snapshot_fixture())[0]))

    def test_count_hash_scope_and_type_drift_fail_closed(self):
        for field, value in (("rows", 695), ("sha256", "bad"), ("scope", "r3-sources"), ("sqlSha256", "bad")):
            rows, proof = self.snapshot_fixture()
            proof[field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.verify_fixture(rows, proof)
        rows, proof = self.snapshot_fixture()
        rows.pop()
        proof["rows"] = len(rows)
        with self.assertRaises(ValueError):
            self.verify_fixture(rows, proof)

    def test_source_key_duplicates_and_taxonomy_cycles_fail_closed(self):
        import json
        rows, proof = self.snapshot_fixture()
        food = json.loads(rows[-1]["food"])
        food["source_key"] = "fixture:1"
        rows[-1]["food"] = json.dumps(food)
        with self.assertRaises(ValueError):
            self.verify_fixture(rows, proof)
        rows, proof = self.snapshot_fixture()
        proof["metrics"][0]["categories"][0]["parent_id"] = 1
        with self.assertRaises(ValueError):
            self.verify_fixture(rows, proof)


if __name__ == "__main__":
    unittest.main()

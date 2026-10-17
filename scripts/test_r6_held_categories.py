import copy
import importlib.util
import unittest
from unittest.mock import patch
from pathlib import Path


spec = importlib.util.spec_from_file_location("held", Path(__file__).with_name("review-r6-held-categories.py"))
held = importlib.util.module_from_spec(spec)
spec.loader.exec_module(held)


def fixture():
    source = {"fdcId": 123, "description": "Spinach, raw", "dataType": "SR Legacy",
              "foodCategory": {"code": "1100"}, "foodPortions": [], "foodNutrients": [
                  {"nutrient": {"id": nid, "unitName": unit}, "amount": value}
                  for nid, unit, value in ((1008, "kcal", 23), (1003, "g", 2.9), (1004, "g", .4), (1005, "g", 3.6))]}
    food = {field: None for field in held.gate.FIELDS}
    food.update(id=1, source_key="USDA_FOODDATA:fdc:123", name="Spinach, raw", catalog_type="GENERIC_INGREDIENT",
                data_source="USDA_FOODDATA", is_custom=False, publication_status="PUBLISHED", preparation_state="RAW",
                nutrition_reference_unit="PER_100G", calories=23, protein=2.9, fat=.4, carbs=3.6)
    row = {"id": "1", "sourceKey": food["source_key"], "targetSlug": "vegetables", "historicalReasons": []}
    current = {"food": food, "options": []}
    category = {"food": copy.deepcopy(food), "assignments": []}
    registry = {"version": "R6_EXACT_EVERYDAY_REVIEW_V1", "ruleGroups": [], "usdaNames": []}
    categories = {"vegetables": {"id": 1, "active": True, "parent_id": None}}
    return row, current, category, source, registry, categories


class HeldCategoryTests(unittest.TestCase):
    def assess(self, values):
        return held.assess(*values)

    def test_compatible_category_never_approves_writes_or_zero_fills(self):
        result = self.assess(fixture())
        self.assertTrue(result["categoryEvidenceCompatible"])
        self.assertEqual("CATEGORY_NOMINATION_AWAITING_EXPLICIT_APPROVAL", result["status"])
        self.assertEqual(21, len(result["nutrients"]))
        self.assertIsNone(result["nutrients"]["sodium"]["current"])
        self.assertFalse(result["applyAllowed"])
        self.assertFalse(result["portionWriteAllowed"])
        self.assertFalse(result["nutritionWriteAllowed"])

    def test_admin_approval_and_portion_gates_are_not_silently_cleared(self):
        values = fixture()
        values[1]["food"]["reviewed_by"] = "admin"
        values[2]["food"]["reviewed_by"] = "admin"
        values[0]["historicalReasons"] = ["R5_PORTION_COUNT_OR_CROSS_DIMENSION_MOBILE_REVIEW"]
        result = self.assess(values)
        self.assertIn("EXISTING_ADMIN_APPROVAL_PROTECTED", result["holds"])
        self.assertIn("R5_PORTION_COUNT_OR_CROSS_DIMENSION_MOBILE_REVIEW", result["holds"])

    def test_unknown_source_is_not_a_known_zero(self):
        values = fixture()
        for index in (1, 2):
            values[index]["food"]["sodium"] = 0
        result = self.assess(values)
        self.assertEqual("SOURCE_VALUE_REQUIRED", result["nutrients"]["sodium"]["state"])
        self.assertIn("FULL_NUTRITION_COMPARISON_REQUIRED", result["holds"])

    def test_source_and_current_identity_drift_fail_closed(self):
        values = fixture()
        values[3]["fdcId"] = 124
        with self.assertRaises(ValueError):
            self.assess(values)
        values = fixture()
        values[1]["food"]["publication_status"] = "DRAFT"
        with self.assertRaises(ValueError):
            self.assess(values)

    def test_different_source_category_is_not_guessed_from_name(self):
        values = fixture()
        values[3]["foodCategory"]["code"] = "1400"
        result = self.assess(values)
        self.assertFalse(result["categoryEvidenceCompatible"])
        self.assertIn("SOURCE_TARGET_CATEGORY_REVIEW", result["holds"])

    def test_exact_new_nomination_requires_identity_and_source_category(self):
        values = fixture()
        values[0]["targetSlug"] = None
        additional = {"version": "R6_HELD_EXACT_CATEGORY_NOMINATIONS_V1", "applyAllowed": False,
                      "entries": [{"id": "1", "sourceKey": values[0]["sourceKey"], "description": "Spinach, raw",
                                   "foodCategoryCode": "1100", "target": "vegetables"}]}
        result = held.assess(*values, additional)
        self.assertTrue(result["categoryEvidenceCompatible"])
        self.assertIsNone(result["previousTargetSlug"])
        self.assertFalse(result["applyAllowed"])
        values[3]["foodCategory"]["code"] = "1400"
        result = held.assess(*values, additional)
        self.assertFalse(result["categoryEvidenceCompatible"])
        self.assertIn("NEW_CATEGORY_NOMINATION_SOURCE_CHANGED", result["holds"])

    def test_coarse_cooked_is_compatible_not_a_raw_cooked_merge(self):
        values = fixture()
        values[3]["description"] = "Spinach, cooked, boiled, drained, without salt"
        for index in (1, 2):
            values[index]["food"].update(name=values[3]["description"], preparation_state="COOKED")
        result = self.assess(values)
        self.assertNotIn("SOURCE_PREPARATION_REVIEW", result["holds"])
        self.assertEqual("SOURCE_SPECIFIC_COMPATIBLE_WITH_COARSE_COOKED", result["preparationComparison"])
        values[3]["description"] = "Spinach, raw"
        result = self.assess(values)
        self.assertIn("SOURCE_PREPARATION_REVIEW", result["holds"])

    def test_reference_preparation_and_cross_snapshot_drift_are_held(self):
        values = fixture()
        values[1]["food"]["nutrition_reference_unit"] = "PER_100ML"
        values[1]["food"]["preparation_state"] = "BOILED"
        result = self.assess(values)
        self.assertIn("CROSS_SNAPSHOT_CURRENT_PROFILE_CHANGED", result["holds"])
        self.assertIn("USDA_NATIVE_REFERENCE_REVIEW", result["holds"])
        self.assertIn("SOURCE_PREPARATION_REVIEW", result["holds"])

    def test_exact_portion_weight_match_is_not_admin_approval(self):
        values = fixture()
        values[3]["foodPortions"] = [{"id": 10, "amount": 1, "gramWeight": 30,
                                      "modifier": "cup", "measureUnit": {"name": "undetermined"}}]
        values[1]["options"] = [{"id": 8, "unit_type": "CUP", "quantity": 1, "gram_weight": 30, "ml_volume": None}]
        result = self.assess(values)
        self.assertEqual([10], result["portionChecks"][0]["sourcePortionIds"])
        self.assertFalse(result["portionWriteAllowed"])
        values[1]["options"][0]["ml_volume"] = 250
        result = self.assess(values)
        self.assertEqual([], result["portionChecks"][0]["sourcePortionIds"])

    def test_category_assignments_and_inactive_targets_are_preserved(self):
        values = fixture()
        values[2]["assignments"] = [{"id": 8}]
        values[5]["vegetables"]["active"] = False
        result = self.assess(values)
        self.assertIn("EXISTING_CATEGORY_ASSIGNMENT_REVIEW", result["holds"])
        self.assertFalse(result["categoryEvidenceCompatible"])

    def test_incomplete_historical_cohort_is_rejected(self):
        with patch.object(held, "read_json", return_value={"status": "HISTORICAL_REVIEW_PREPARATION_ONLY", "rows": []}):
            with self.assertRaises(ValueError):
                held.held_rows(Path("unused.json"))


if __name__ == "__main__":
    unittest.main()

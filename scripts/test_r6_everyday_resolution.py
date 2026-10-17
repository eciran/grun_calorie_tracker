import copy
import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("resolution", Path(__file__).with_name("resolve-r6-everyday-review.py"))
resolution = importlib.util.module_from_spec(spec)
spec.loader.exec_module(resolution)


class ResolutionTests(unittest.TestCase):
    def profile_fixture(self):
        food = {"source_key": "TR:LOCAL_DISH:PREPARED:fixture:classic", "catalog_type": "LOCAL_DISH", "data_source": "LOCAL_CURATED",
                "nutrition_basis": "CALCULATED", "nutrition_reference_unit": "PER_100G", "preparation_state": "PREPARED",
                "market_region": "TR", "name": "Fixture", "calories": 99.9, "protein": 2.4, "fat": 3.2, "carbs": 14.3,
                "sodium": 241.07, "iron": 0.8}
        source = copy.deepcopy(food)
        source.update(calories="99.85", protein="2.36", fat="3.16", carbs="14.29", sodium="241.07", iron="0.8")
        return food, source

    def test_decimal_half_up_matches_import_without_epsilon(self):
        result = resolution.compare_profile(*self.profile_fixture())
        self.assertTrue(result["compatible"])
        self.assertEqual(21, len(result["fields"]))
        self.assertEqual("99.9", result["fields"][0]["normalizedSource"])
        self.assertFalse(result["nutritionWriteAllowed"])
        self.assertFalse(result["independentNutritionVerified"])

    def test_real_difference_not_hidden_by_rounding_tolerance(self):
        for field, value in (("protein", 2.39), ("sodium", 241.08), ("iron", 0.801)):
            food, source = self.profile_fixture()
            food[field] = value
            with self.subTest(field=field):
                self.assertFalse(resolution.compare_profile(food, source)["compatible"])

    def test_unknowns_do_not_become_zeros(self):
        food, source = self.profile_fixture()
        self.assertTrue(any(f["status"] == "UNKNOWN_BOTH" for f in resolution.compare_profile(food, source)["fields"]))
        food["cholesterol"] = 0
        self.assertFalse(resolution.compare_profile(food, source)["compatible"])

    def test_invalid_numeric_and_missing_fact_held(self):
        for value in (True, -1, float("inf"), float("nan"), None):
            food, source = self.profile_fixture()
            food["protein"] = value
            with self.subTest(value=value):
                self.assertFalse(resolution.compare_profile(food, source)["compatible"])

    def test_mixed_basis_or_source_identity_rejected(self):
        for field, value in (("nutrition_reference_unit", "PER_100ML"), ("source_key", "other"), ("name", "Other food"),
                             ("nutrition_basis", "ESTIMATED"), ("preparation_state", "RAW")):
            food, source = self.profile_fixture()
            food[field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                resolution.compare_profile(food, source)

    def fixture(self):
        current = {"id": "1", "food": {"id": 1, "name": "Fixture drink"}, "assignments": [], "source_tags": [], "evidence": []}
        original = {"id": "1", "sourceKey": "GRUN_PREPARED_V1:fixture", "catalogType": "STANDARD_PREPARED_ITEM",
                    "beforeSha256": resolution.everyday.fingerprint(current), "reasons": ["AMBIGUOUS_OR_MISSING_SOURCE_CATEGORY"],
                    "targetSlug": None, "warnings": [], "status": "REVIEW_REQUIRED", "sourceIdentitySha256": "source-hash"}
        rule = {"target": "beverages", "basis": "Exact fixture identity"}
        categories = {"beverages": {"id": 1, "active": True, "parent_id": None}}
        return original, current, rule, categories

    def resolve_fixture(self, original, current, rule, categories):
        return resolution.resolve(original, current, rule, None, None, None, categories)

    def test_r5_admin_gate_preserved_even_with_known_target(self):
        original, current, rule, categories = self.fixture()
        original["reasons"].append("R5_EXISTING_ADMIN_APPROVAL_REVIEW_REQUIRED")
        result = self.resolve_fixture(original, current, rule, categories)
        self.assertEqual("REVIEW_REQUIRED", result["status"])
        self.assertIn("R5_EXISTING_ADMIN_APPROVAL_REVIEW_REQUIRED", result["reasons"])

    def test_source_or_food_drift_never_cleared_by_rule(self):
        original, current, rule, categories = self.fixture()
        current["food"]["name"] = "Changed"
        with self.assertRaises(ValueError):
            self.resolve_fixture(original, current, rule, categories)

    def test_category_rule_does_not_clear_profile_or_identity_gate(self):
        original, current, rule, categories = self.fixture()
        original["reasons"].append("MANIFEST_CORE_PROFILE_DRIFT")
        result = self.resolve_fixture(original, current, rule, categories)
        self.assertEqual("REVIEW_REQUIRED", result["status"])

    def name_fixture(self, decision="EQUIVALENT_IDENTITY"):
        original, current, _, categories = self.fixture()
        original.update(sourceKey="USDA_FOODDATA:fdc:2707170", catalogType="GENERIC_INGREDIENT",
                        reasons=["USDA_CURRENT_NAME_IDENTITY_REVIEW", "AMBIGUOUS_OR_MISSING_SOURCE_CATEGORY"])
        source = {"fdcId": 2707170, "description": "Egg, white, cooked, no added fat", "dataType": "Survey (FNDDS)",
                  "foodAttributes": [{"name": "WWEIA Category number", "value": "2502"},
                                     {"name": "WWEIA Category description", "value": "Eggs and omelets"}],
                  "wweiaFoodCategory": {"wweiaFoodCategoryCode": 999999}}
        name = {"id": "1", "sourceKey": original["sourceKey"], "currentName": current["food"]["name"],
                "sourceDescription": source["description"], "target": "eggs", "decision": decision}
        categories = {"eggs": {"id": 2, "active": True, "parent_id": None}}
        return original, current, source, name, categories

    def test_fndds_uses_attributes_not_database_attribute_id(self):
        original, current, source, name, categories = self.name_fixture()
        result = resolution.resolve(original, current, None, name, source, None, categories)
        self.assertEqual("SAFE_CATEGORY_NOMINATION", result["status"])
        self.assertEqual("2502", result["categoryAttributeProof"]["number"])
        source["foodAttributes"][0]["value"] = "9999"
        with self.assertRaises(ValueError):
            resolution.resolve(original, current, None, name, source, None, categories)

    def test_reviewed_name_correction_is_not_an_applied_patch(self):
        original, current, source, name, categories = self.name_fixture("NAME_CORRECTION_REQUIRED")
        result = resolution.resolve(original, current, None, name, source, None, categories)
        self.assertEqual("REVIEW_REQUIRED", result["status"])
        self.assertFalse(result["applyAllowed"])
        self.assertIn("R4_REVIEWED_NAME_PATCH_NOT_APPLIED", result["reasons"])

    def test_wrong_source_description_rejected(self):
        original, current, source, name, categories = self.name_fixture()
        source["description"] = "Egg cake"
        with self.assertRaises(ValueError):
            resolution.resolve(original, current, None, name, source, None, categories)

    def test_duplicate_exact_rules_rejected(self):
        rule = {"prefix": "key:", "keys": ["a", "a"], "target": "fruit", "basis": "test"}
        with self.assertRaises(ValueError):
            resolution.rules_index({"version": "R6_EXACT_EVERYDAY_REVIEW_V1", "ruleGroups": [rule]})

    def test_plan_is_only_category_proposal_without_live_approval(self):
        original, current, rule, categories = self.fixture()
        result = self.resolve_fixture(original, current, rule, categories)
        plan = resolution.prepare_plan([result], {"1": current}, categories, "time", {})
        self.assertEqual("food_item_categories", plan["allowedTable"])
        self.assertFalse(plan["liveApplyAllowed"])
        self.assertIsNone(plan["backup"])
        self.assertEqual(1, plan["expectedNewAssignments"])
        self.assertIn("nutrition", plan["forbiddenWrites"])

    def test_plan_rejects_duplicate_id_existing_category_or_drift(self):
        original, current, rule, categories = self.fixture()
        result = self.resolve_fixture(original, current, rule, categories)
        with self.assertRaises(ValueError):
            resolution.prepare_plan([result,result], {"1":current}, categories, "time", {})
        current["assignments"] = [{"assignment_source": "ADMIN"}]
        with self.assertRaises(ValueError):
            resolution.prepare_plan([result], {"1":current}, categories, "time", {})

    def test_inactive_ancestor_is_not_a_safe_target(self):
        categories = {"fruit": {"id":1,"active":True,"parent_id":2}, "produce": {"id":2,"active":False,"parent_id":None}}
        self.assertFalse(resolution.target_active("fruit", categories))


if __name__ == "__main__":
    unittest.main()

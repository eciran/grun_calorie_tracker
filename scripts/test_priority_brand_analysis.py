import importlib.util
from pathlib import Path
import unittest


SPEC = importlib.util.spec_from_file_location(
    "priority_brands", Path(__file__).with_name("analyze-priority-brands.py")
)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class PriorityBrandAnalysisTest(unittest.TestCase):
    def test_canonical_and_alias_brand_states(self):
        self.assertEqual(
            MODULE.target_for({"brand": "Pringles", "name": "Original"}),
            ("pringles", "CANONICAL_BRAND"),
        )
        self.assertEqual(
            MODULE.target_for({"brand": "Pringels, Pringles", "name": "Original"}),
            ("pringles", "MULTI_BRAND_RELATION_REVIEW"),
        )

    def test_name_only_does_not_become_canonical(self):
        self.assertEqual(
            MODULE.target_for({"brand": "Peperami", "name": "Doritos Peperami"}),
            ("doritos", "NAME_ONLY_BRAND_REVIEW"),
        )

    def test_package_is_removed_from_identity(self):
        one, _ = MODULE.identity({"name": "Cadbury Dairy Milk 100 g"}, "cadbury")
        two, _ = MODULE.identity({"name": "Cadbury Dairy Milk 200 g"}, "cadbury")
        self.assertEqual(one, two)

    def test_close_package_profiles_are_candidates(self):
        rows = [
            {"package_text": "100 g", "nutrition_reference_unit": "PER_100G", "calories": "500", "protein": "6", "carbs": "50", "fat": "30"},
            {"package_text": "200 g", "nutrition_reference_unit": "PER_100G", "calories": "505", "protein": "6.1", "carbs": "50.5", "fat": "30.2"},
        ]
        decision, spread = MODULE.evidence_decision("cadbury", "dairy milk", rows)
        self.assertEqual(decision, "CLOSE_PROFILE_PACKAGE_FAMILY_CANDIDATE")
        self.assertLessEqual(spread, 0.05)

    def test_generic_brand_name_is_not_family_identity(self):
        rows = [
            {"package_text": "", "nutrition_reference_unit": "PER_100G", "calories": "500", "protein": "6", "carbs": "50", "fat": "30"},
            {"package_text": "", "nutrition_reference_unit": "PER_100G", "calories": "520", "protein": "6", "carbs": "50", "fat": "30"},
        ]
        decision, _ = MODULE.evidence_decision("pringles", "pringles", rows)
        self.assertEqual(decision, "INSUFFICIENT_FLAVOR_OR_PRODUCT_IDENTITY")

    def test_distinct_barcodes_and_packages_form_family_candidate(self):
        rows = [
            {"barcode": "1", "package_text": "35 g", "market_region": "UK_IE", "preparation_state": "PACKAGED", "catalog_type": "BRANDED_PRODUCT", "nutrition_reference_unit": "PER_100G", "calories": "500", "protein": "6", "carbs": "50", "fat": "30"},
            {"barcode": "2", "package_text": "100 g", "market_region": "UK_IE", "preparation_state": "PACKAGED", "catalog_type": "BRANDED_PRODUCT", "nutrition_reference_unit": "PER_100G", "calories": "505", "protein": "6.1", "carbs": "50.5", "fat": "30.2"},
        ]
        self.assertEqual(
            MODULE.family_decision("doritos", "chilli heatwave", rows),
            "FAMILY_CANDIDATE_READY_FOR_SOURCE_REVIEW",
        )

    def test_family_without_package_metadata_stays_open(self):
        rows = [
            {"barcode": "1", "package_text": "", "market_region": "UK_IE", "preparation_state": "PACKAGED", "catalog_type": "BRANDED_PRODUCT", "nutrition_reference_unit": "PER_100G", "calories": "500", "protein": "6", "carbs": "50", "fat": "30"},
            {"barcode": "2", "package_text": "", "market_region": "UK_IE", "preparation_state": "PACKAGED", "catalog_type": "BRANDED_PRODUCT", "nutrition_reference_unit": "PER_100G", "calories": "500", "protein": "6", "carbs": "50", "fat": "30"},
        ]
        self.assertEqual(
            MODULE.family_decision("pringles", "original", rows),
            "FAMILY_PACKAGE_METADATA_REQUIRED",
        )


if __name__ == "__main__":
    unittest.main()

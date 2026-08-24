import csv
import importlib.util
from pathlib import Path
import unittest


MODULE_PATH = Path(__file__).with_name("build-local-food-generic-baseline.py")
SPEC = importlib.util.spec_from_file_location("generic_baseline", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class GenericBaselineTest(unittest.TestCase):
    def test_normalization_is_stable_and_does_not_invent_turkish_alias(self):
        row = {field: "" for field in MODULE.OUTPUT_FIELDS}
        row.update({"fdc_id": "123", "catalog_type": "GENERIC_INGREDIENT", "data_source": "USDA_FOODDATA",
                    "name": "Beans, raw", "alias_en": "beans", "market_region": "GLOBAL",
                    "preparation_state": "RAW", "calories": "100", "protein": "5", "fat": "1",
                    "carbs": "20", "fiber": "4", "sodium": "8", "potassium": "10", "calcium": "5",
                    "serving_size_grams": "100", "serving_unit": "g", "source_note": "USDA"})
        normalized, reasons = MODULE.normalize(row)
        self.assertEqual("USDA_FOODDATA:GENERIC_INGREDIENT:GLOBAL:fdc_123_raw", normalized["source_key"])
        self.assertEqual("", normalized["alias_tr"])
        self.assertIn("MISSING_REVIEWED_TR_ALIAS", reasons)
        self.assertEqual("CC0-1.0", normalized["license_id"])
        self.assertEqual("USDA_FOODDATA_PUBLIC", normalized["source_registry_id"])

    def test_approved_alias_is_promotable_but_quarantine_is_not(self):
        row = {"fdc_id": "123", "catalog_type": "GENERIC_INGREDIENT", "data_source": "USDA_FOODDATA",
               "name": "Beans, raw", "alias_en": "beans", "market_region": "GLOBAL", "preparation_state": "RAW",
               "calories": "100", "protein": "5", "fat": "1", "carbs": "20", "fiber": "4", "sodium": "8",
               "potassium": "10", "calcium": "5", "serving_size_grams": "100", "serving_unit": "g"}
        normalized, reasons = MODULE.normalize(row, {"decision": "APPROVE", "alias_tr": "çiğ fasulye", "display_name_tr": "Çiğ fasulye"})
        self.assertEqual("çiğ fasulye", normalized["alias_tr"])
        self.assertNotIn("MISSING_REVIEWED_TR_ALIAS", reasons)
        _, quarantined = MODULE.normalize(row, {"decision": "QUARANTINE"})
        self.assertIn("SOURCE_IDENTITY_MISMATCH", quarantined)

    def test_approved_preparation_review_replaces_unspecified(self):
        row = {"fdc_id": "8", "catalog_type": "GENERIC_INGREDIENT", "data_source": "USDA_FOODDATA",
               "name": "Milk", "alias_en": "milk", "market_region": "GLOBAL", "preparation_state": "UNSPECIFIED",
               "serving_size_grams": "100", "serving_unit": "g"}
        normalized, reasons = MODULE.normalize(row, {"decision": "APPROVE", "alias_tr": "süt", "display_name_tr": "Süt"},
                                               {"decision": "APPROVE", "preparation_state": "PREPARED"})
        self.assertEqual("PREPARED", normalized["preparation_state"])
        self.assertNotIn("PREPARATION_STATE_REVIEW", reasons)


    def test_missing_required_values_are_triaged(self):
        row = {"fdc_id": "9", "catalog_type": "GENERIC_INGREDIENT", "data_source": "USDA_FOODDATA",
               "name": "Unknown", "alias_en": "unknown", "market_region": "GLOBAL",
               "preparation_state": "UNSPECIFIED", "serving_size_grams": "100", "serving_unit": "g"}
        _, reasons = MODULE.normalize(row)
        self.assertIn("PREPARATION_STATE_REVIEW", reasons)
        self.assertIn("MISSING_REQUIRED_FIBER", reasons)
        self.assertIn("MISSING_REQUIRED_SODIUM", reasons)


if __name__ == "__main__":
    unittest.main()

import copy
import importlib.util
import unittest
from datetime import datetime, timezone
from pathlib import Path

spec = importlib.util.spec_from_file_location("source_options", Path(__file__).with_name("prepare-r5-source-options.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
NOW = datetime(2026, 10, 12, 12, tzinfo=timezone.utc)


def row(**changes):
    return {"id": "1", "source_key": "barcode:5449000000996", "data_source": "OPEN_FOOD_FACTS",
            "barcode": "5449000000996", "normalized_barcode": "5449000000996", "catalog_type": "BRANDED_PRODUCT",
            "nutrition_reference_unit": "PER_100G", "calories": "42.4", "protein": "0", "fat": "0", "carbs": "10.6", **changes}


def evidence(**changes):
    product = {"code": "5449000000996", "categories_tags": ["en:beverages"], "nutrition_data_per": "serving",
        "serving_size": "330 ml", "serving_quantity": 330, "serving_quantity_unit": "ml",
        "nutriments": {"energy-kcal_100g": 42.4, "proteins_100g": 0, "fat_100g": 0, "carbohydrates_100g": 10.6,
                       "energy-kcal_serving": 140, "proteins_serving": 0, "fat_serving": 0, "carbohydrates_serving": 35}, **changes}
    return {"status": "FETCHED", "barcode": "5449000000996", "checkedAt": "2026-10-12T11:00:00Z", "product": product}


def food(**changes):
    return {"fdcId": 123, "dataType": "SR Legacy", "foodNutrients": [
        {"nutrient": {"id": nid, "unitName": unit}, "amount": value}
        for nid, unit, value in ((1008, "kcal", 42.4), (1003, "g", 0), (1004, "g", 0), (1005, "g", 10.6))],
        "foodPortions": [{"id": 10, "amount": 1, "gramWeight": 118, "modifier": "medium (7 inch long)"}], **changes}


def usda_row(**changes):
    return row(source_key="USDA_FOODDATA:fdc:123", data_source="USDA_FOODDATA", **changes)


def v35_evidence():
    values = {"energy-kcal": (42.4, "kcal"), "proteins": (0, "g"), "fat": (0, "g"), "carbohydrates": (10.6, "g")}
    source = {"source": "packaging", "preparation": "as_sold", "per": "100ml", "per_quantity": 100, "per_unit": "ml",
              "nutrients": {name: {"value": value, "unit": unit} for name, (value, unit) in values.items()}}
    aggregate = {"preparation": "as_sold", "per": "100ml", "nutrients": {
        name: {"value": value, "unit": unit, "source": "packaging", "source_index": 0, "source_per": "100ml"}
        for name, (value, unit) in values.items()}}
    return evidence(nutriments={}, nutrition_data_per=None, nutrition={"input_sets": [source], "aggregated_set": aggregate})


class SourceOptionTests(unittest.TestCase):
    def test_v35_reads_explicit_ml_source_when_legacy_nutriments_is_empty(self):
        result = module.off_decision(row(), v35_evidence(), NOW)
        self.assertEqual("SOURCE_BASIS_CORRECTION_REQUIRES_FULL_BEFORE_IMAGE", result["decision"])
        self.assertEqual("PER_100ML", result["sourceReferenceUnit"])
        self.assertIn("v3.5", result["sourceSchema"])
        self.assertEqual(330, result["options"][0]["mlVolume"])

    def test_v35_mass_reference_stays_mass(self):
        value = v35_evidence()
        nutrition = value["product"]["nutrition"]
        nutrition["input_sets"][0].update(per="100g", per_unit="g")
        nutrition["aggregated_set"]["per"] = "100g"
        for nutrient in nutrition["aggregated_set"]["nutrients"].values():
            nutrient["source_per"] = "100g"
        value["product"].update(serving_quantity=100, serving_quantity_unit="g", serving_size="100g")
        result = module.off_decision(row(), value, NOW)
        self.assertEqual("SOURCE_PORTION_READY_FOR_REVIEW", result["decision"])
        self.assertEqual(100, result["options"][0]["gramWeight"])
        self.assertIsNone(result["options"][0]["mlVolume"])

    def test_v35_explicit_serving_input_normalizes_its_own_unit(self):
        value = v35_evidence()
        nutrition = value["product"]["nutrition"]
        source = nutrition["input_sets"][0]
        source.update(per="serving", per_quantity=330)
        for nutrient in source["nutrients"].values():
            nutrient["value"] *= 3.3
        for nutrient in nutrition["aggregated_set"]["nutrients"].values():
            nutrient["source_per"] = "serving"
        self.assertEqual("SOURCE_BASIS_CORRECTION_REQUIRES_FULL_BEFORE_IMAGE", module.off_decision(row(), value, NOW)["decision"])
        conflicting = copy.deepcopy(value)
        conflicting["product"].update(serving_quantity=200, serving_size="200 ml")
        self.assertEqual("V35_PRODUCT_SERVING_CONFLICT", module.off_decision(row(), conflicting, NOW)["decision"])
        source["per_quantity"] = 100
        self.assertEqual("V35_INPUT_AGGREGATE_VALUE_CONFLICT", module.off_decision(row(), value, NOW)["decision"])

    def test_v35_estimated_prepared_and_mixed_sources_are_rejected(self):
        value = v35_evidence()
        value["product"]["nutrition"]["input_sets"][0]["source"] = "estimate"
        self.assertEqual("V35_ACTUAL_AS_SOLD_LABEL_REQUIRED", module.off_decision(row(), value, NOW)["decision"])
        value = v35_evidence()
        value["product"]["nutrition"]["aggregated_set"]["preparation"] = "prepared"
        self.assertEqual("V35_AS_SOLD_NORMALIZED_PROFILE_REQUIRED", module.off_decision(row(), value, NOW)["decision"])
        value = v35_evidence()
        value["product"]["nutrition"]["aggregated_set"]["nutrients"]["fat"]["source_index"] = 1
        self.assertEqual("V35_MIXED_OR_MISSING_SOURCE_PROFILE", module.off_decision(row(), value, NOW)["decision"])

    def test_v35_computed_calories_do_not_replace_actual_label_values(self):
        value = v35_evidence()
        source = value["product"]["nutrition"]["input_sets"][0]["nutrients"]["energy-kcal"]
        source["value_computed"] = source.pop("value")
        self.assertEqual("V35_EXPLICIT_CORE_REQUIRED", module.off_decision(row(), value, NOW)["decision"])

    def test_v35_reference_units_and_raw_input_units_are_cross_checked(self):
        value = v35_evidence()
        source = value["product"]["nutrition"]["input_sets"][0]
        source["nutrients"]["carbohydrates"].update(value=10600, unit="mg")
        self.assertEqual(10.6, module.off_v35_view(value["product"])[0]["core"]["carbs"])
        source["per_unit"] = "g"
        self.assertEqual("V35_REFERENCE_QUANTITY_CONFLICT", module.off_decision(row(), value, NOW)["decision"])

    def test_v35_inequalities_and_legacy_conflicts_require_review(self):
        value = v35_evidence()
        value["product"]["nutrition"]["input_sets"][0]["nutrients"]["fat"]["modifier"] = "<"
        self.assertEqual("V35_SOURCE_UNIT_OR_MODIFIER_REVIEW", module.off_decision(row(), value, NOW)["decision"])
        value = v35_evidence()
        value["product"]["nutriments"] = {"fat_100g": 5}
        self.assertEqual("V35_LEGACY_VALUE_CONFLICT", module.off_decision(row(), value, NOW)["decision"])

    def test_v35_missing_macro_does_not_become_zero(self):
        value = v35_evidence()
        value["product"]["nutrition"]["input_sets"][0]["nutrients"].pop("proteins")
        self.assertEqual("V35_EXPLICIT_CORE_REQUIRED", module.off_decision(row(), value, NOW)["decision"])

    def test_bulk_source_never_inherits_freshness_from_extraction_time(self):
        value = evidence()
        value.update(status="BULK_EXTRACTED", verifiedBulkFile=True)
        value.pop("checkedAt")
        result = module.off_decision(row(), value, NOW)
        self.assertTrue(result["decision"].startswith("BULK_"))
        self.assertFalse(result["sourceFreshnessApproved"])
        self.assertTrue(any("remote origin/time" in gate for gate in result["requiredGates"]))
        value.pop("verifiedBulkFile")
        self.assertEqual("SOURCE_FETCH_REQUIRED", module.off_decision(row(), value, NOW)["decision"])

    def test_full_reply_tampering_is_not_a_valid_source(self):
        value = evidence()
        value["response"] = {"product": copy.deepcopy(value["product"])}
        value["responseFingerprint"] = module.r5.fingerprint(value["response"])
        value["product"]["brands"] = "changed"
        self.assertEqual("SOURCE_RESPONSE_INTEGRITY_REQUIRED", module.off_decision(row(), value, NOW)["decision"])

    def test_ml_serving_has_normalization_proof_but_not_density_or_write_approval(self):
        result = module.off_decision(row(), evidence(), NOW)
        self.assertEqual("SOURCE_BASIS_CORRECTION_REQUIRES_FULL_BEFORE_IMAGE", result["decision"])
        self.assertEqual("PER_100ML", result["sourceReferenceUnit"])
        self.assertEqual(330, result["options"][0]["mlVolume"])
        self.assertIsNone(result["options"][0]["gramWeight"])
        self.assertFalse(result["applyAllowed"])
        self.assertFalse(result["nutritionWriteAllowed"])
        self.assertFalse(result["options"][0]["userVisible"])

    def test_native_ml_option_still_requires_review(self):
        result = module.off_decision(row(nutrition_reference_unit="PER_100ML"), evidence(), NOW)
        self.assertEqual("SOURCE_PORTION_READY_FOR_REVIEW", result["decision"])
        self.assertFalse(result["options"][0]["defaultOption"])

    def test_mass_serving_stays_mass(self):
        result = module.off_decision(row(), evidence(nutrition_data_per="100g", serving_size="100 g", serving_quantity=100, serving_quantity_unit="g"), NOW)
        self.assertEqual("SOURCE_PORTION_READY_FOR_REVIEW", result["decision"])
        self.assertEqual(100, result["options"][0]["gramWeight"])
        self.assertIsNone(result["options"][0]["mlVolume"])

    def test_raw_input_mg_unit_does_not_rescale_off_normalized_grams(self):
        value = evidence()
        value["product"]["nutriments"]["carbohydrates_unit"] = "mg"
        self.assertEqual("SOURCE_BASIS_CORRECTION_REQUIRES_FULL_BEFORE_IMAGE", module.off_decision(row(), value, NOW)["decision"])

    def test_conflicting_unit_or_serving_arithmetic_is_held(self):
        self.assertEqual("SERVING_MEASURES_CONFLICT", module.off_decision(row(), evidence(serving_size="330 g"), NOW)["decision"])
        value = evidence()
        value["product"]["nutriments"]["carbohydrates_serving"] = 10.6
        self.assertEqual("PER_SERVING_NORMALIZATION_CONFLICT", module.off_decision(row(), value, NOW)["decision"])

    def test_powder_and_prepared_forms_win_over_liquid_tag(self):
        for tag in ("en:milk-powders", "en:beverage-preparations", "en:coffee-beans", "en:dried-products-to-be-rehydrated"):
            self.assertEqual("SOURCE_FORM_CONFLICT", module.off_decision(row(), evidence(categories_tags=["en:beverages", tag]), NOW)["decision"])

    def test_generic_100g_and_volume_pack_do_not_prove_label(self):
        self.assertEqual("LIQUID_GENERIC_100G_LABEL_REVIEW_REQUIRED", module.off_decision(row(), evidence(nutrition_data_per="100g", product_quantity_unit="ml"), NOW)["decision"])

    def test_name_does_not_classify_liquid(self):
        self.assertEqual("SOURCE_LIQUID_FORM_REQUIRED", module.off_decision(row(name="Rice cooked with water"), evidence(categories_tags=["en:rice-dishes"]), NOW)["decision"])

    def test_identity_and_clock_fail_closed(self):
        self.assertEqual("SOURCE_IDENTITY_REQUIRED", module.off_decision(row(barcode="wrong", normalized_barcode="wrong"), evidence(), NOW)["decision"])
        for timestamp in ("2026-10-13T00:00:00Z", "2026-09-01T00:00:00Z", "invalid", "2026-10-12T11:00:00"):
            value = evidence()
            value["checkedAt"] = timestamp
            self.assertIn(module.off_decision(row(), value, NOW)["decision"], {"SOURCE_TIME_REQUIRED", "SOURCE_TIME_OUTSIDE_REVIEW_WINDOW"})

    def test_missing_calories_or_drift_is_not_reconstructed(self):
        value = evidence(nutrition_data_per="100ml")
        del value["product"]["nutriments"]["energy-kcal_100g"]
        self.assertEqual("SOURCE_CORE_INCOMPLETE", module.off_decision(row(), value, NOW)["decision"])
        self.assertEqual("CURRENT_CORE_DIFFERS_FROM_SOURCE", module.off_decision(row(calories="200"), evidence(), NOW)["decision"])

    def test_explicit_units_and_multi_packs(self):
        self.assertEqual(("ml", 250), module.text_measure("0.25 L"))
        self.assertEqual(("g", 500), module.text_measure("0,5 kg"))
        for text in ("3x330ml", "100 g / 100 ml", "1 cup", "1 slice"):
            self.assertIsNone(module.text_measure(text))
        for value in (True, "NaN", "Infinity", -1, 0):
            self.assertIsNone(module.measure(value, "ml"))

    def test_exact_medium_source_piece_produces_half_and_quarter(self):
        result = module.usda_decision(usda_row(), food(), set())
        self.assertEqual("SOURCE_PORTIONS_READY_FOR_REVIEW", result["decision"])
        self.assertEqual([118, 59, 29.5], [o["gramWeight"] for o in result["options"]])
        self.assertEqual([1, .5, .25], [o["quantity"] for o in result["options"]])
        self.assertTrue(all(o["mlVolume"] is None and not o["userVisible"] for o in result["options"]))

    def test_cup_is_source_mass_not_assumed_glass_volume(self):
        option, reason = module.source_portion({"id": 1, "amount": 1, "gramWeight": 249, "measureUnit": {"name": "cup"}})
        self.assertIsNone(reason)
        self.assertEqual(249, option["gramWeight"])
        self.assertIsNone(option["mlVolume"])
        self.assertEqual("ABD ölçü kabı (249 g)", option["labels"]["TR"].split(" ", 1)[1])

    def test_direct_ml_measure_has_exact_product_mass(self):
        option, _ = module.source_portion({"id": 93847, "amount": 1, "gramWeight": 1, "modifier": "ml"})
        self.assertEqual((1, 1), (option["gramWeight"], option["mlVolume"]))

    def test_qualified_measures_are_not_misnamed_as_whole_food(self):
        for description in ("cup, chopped", "cup cubes", "stalk, large", "slice (1 inch thick)", "fruit, with peel"):
            self.assertEqual((None, "SOURCE_SHAPE_OR_QUALIFIER_LABEL_REVIEW"), module.source_portion({"id": 1, "amount": 1, "gramWeight": 100, "modifier": description}))

    def test_usda_exact_identity_and_nutrient_profile(self):
        self.assertEqual("SOURCE_IDENTITY_REQUIRED", module.usda_decision(usda_row(), food(fdcId=124), set())["decision"])
        self.assertEqual("CURRENT_CORE_DIFFERS_FROM_SOURCE", module.usda_decision(usda_row(fat="5"), food(), set())["decision"])

    def test_foundation_energy_uses_explicit_entry_not_macro_estimate(self):
        value = food()
        value["foodNutrients"][0]["nutrient"]["id"] = 2048
        self.assertEqual(42.4, module.usda_core(value)["calories"])
        value["foodNutrients"].pop(0)
        self.assertIsNone(module.usda_core(value)["calories"])

    def test_duplicate_or_excess_source_options_remain_in_review(self):
        p = {"id": 1, "amount": 1, "gramWeight": 100, "modifier": "cup"}
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            module.usda_decision(usda_row(), food(foodPortions=[p, p]), set())
        duplicate = {**p, "id": 2}
        result = module.usda_decision(usda_row(), food(foodPortions=[p, duplicate]), set())
        self.assertEqual("EQUIVALENT_SOURCE_OPTIONS_REQUIRE_DEDUP_REVIEW", result["decision"])
        result = module.usda_decision(usda_row(), food(foodPortions=[{**p, "id": i, "gramWeight": 100+i} for i in range(1, 22)]), set())
        self.assertEqual("OPTION_LIMIT_REVIEW_NO_SILENT_TRUNCATION", result["decision"])

    def test_r3_r4_gates_stay_attached(self):
        value = usda_row()
        result = module.usda_decision(value, food(), {value["source_key"]})
        self.assertTrue(any("R3/R4" in g for g in result["requiredGates"]))
        self.assertTrue(any("classification" in g for g in result["requiredGates"]))


if __name__ == "__main__":
    unittest.main()

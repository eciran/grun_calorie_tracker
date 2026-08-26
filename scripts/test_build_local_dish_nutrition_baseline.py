import importlib.util
from pathlib import Path
import unittest


MODULE_PATH = Path(__file__).with_name("build-local-dish-nutrition-baseline.py")
SPEC = importlib.util.spec_from_file_location("local_dish_baseline", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def row(tr, en, category="LEGUME_DISH"):
    return {"display_name_tr": tr, "display_name_en": en, "category": category}


class LocalDishNutritionBaselineTest(unittest.TestCase):
    def test_pastirma_is_not_the_plain_bean_profile(self):
        plain = MODULE.estimated_profile(row("Kuru fasulye", "Turkish white bean stew"), "LEGUME_DISH")
        pastirma = MODULE.estimated_profile(row("Pastırmalı kuru fasulye", "White bean stew with pastirma"), "LEGUME_DISH")
        self.assertGreater(pastirma["protein"], plain["protein"])
        self.assertGreater(pastirma["fat"], plain["fat"])
        self.assertGreater(pastirma["sodium"], plain["sodium"])
        self.assertGreater(pastirma["vitamin_b12"], plain["vitamin_b12"])

    def test_specific_meat_word_is_not_double_counted_as_generic_meat(self):
        beef = MODULE.estimated_profile(row("Dana yemeği", "Beef meat stew", "MAIN_DISH"), "MAIN_DISH")
        self.assertEqual(17, beef["protein"])
        self.assertEqual(11, beef["fat"])

    def test_material_variants_receive_distinct_profiles(self):
        vegetable = MODULE.estimated_profile(row("Sebzeli lazanya", "Vegetable lasagne", "PASTA_DISH"), "PASTA_DISH")
        spinach = MODULE.estimated_profile(row("Ispanaklı tam tahıllı lazanya", "Spinach wholemeal lasagne", "PASTA_DISH"), "PASTA_DISH")
        self.assertNotEqual(vegetable, spinach)

    def test_root_vegetable_is_not_the_generic_mixed_vegetable_profile(self):
        mixed = MODULE.estimated_profile(row("Fasulyeli sebze güveci", "Mixed bean vegetable casserole", "VEGETABLE_DISH"), "VEGETABLE_DISH")
        root = MODULE.estimated_profile(row("Fasulyeli kök sebze güveci", "Root vegetable bean casserole", "VEGETABLE_DISH"), "VEGETABLE_DISH")
        self.assertNotEqual(mixed, root)

    def test_region_name_alone_does_not_invent_nutrition_difference(self):
        elazig = MODULE.estimated_profile(row("Elazığ Döğme Pilavı", "Elazig wheat pilaf", "GRAIN_DISH"), "GRAIN_DISH")
        tunceli = MODULE.estimated_profile(row("Tunceli Döğme Pilavı", "Tunceli wheat pilaf", "GRAIN_DISH"), "GRAIN_DISH")
        self.assertEqual(elazig, tunceli)


if __name__ == "__main__":
    unittest.main()

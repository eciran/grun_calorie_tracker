import importlib.util
import unittest
from pathlib import Path


SPEC = importlib.util.spec_from_file_location(
    "off_snapshot", Path(__file__).with_name("extract-package-family-off-snapshot.py")
)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class PackageFamilyOffSnapshotTest(unittest.TestCase):
    def test_package_normalization(self):
        self.assertEqual((1500.0, "ml"), MODULE.package("Bottle 1.5 L"))
        self.assertEqual((244.8, "g"), MODULE.package("9 x 27.2 g"))
        self.assertEqual((1000.0, "ml"), MODULE.package("1 litre"))
        self.assertEqual((340.0, "g"), MODULE.package("340gm"))
        self.assertEqual((141.747616, "g"), MODULE.package("5 x 1 oz"))
        self.assertEqual((453.59237, "g"), MODULE.package("1 lb"))
        self.assertIsNone(MODULE.package("8.5 fl oz"))
        self.assertIsNone(MODULE.package("2 pints"))

    def test_tolerance(self):
        self.assertTrue(MODULE.close(100, 109))
        self.assertFalse(MODULE.close(100, 120))

    def test_name_tokens_ignore_package_units(self):
        self.assertEqual({"fanta", "orange"}, MODULE.tokens("Fanta Orange 330 ml"))

    def test_multipack_normalizes_to_total_quantity(self):
        self.assertEqual(MODULE.package("4 x 45 g"), MODULE.package("180 g"))

    def test_quantity_text_supplies_package_when_catalog_name_does_not(self):
        self.assertIsNone(MODULE.package("Dairy Milk"))
        self.assertEqual((95.0, "g"), MODULE.package("95 g"))

    def test_family_nutrition_compatibility_allows_label_rounding_only(self):
        base = {"calories": "500", "protein": "6", "carbs": "58", "fat": "25"}
        rounded = {"calories": "504", "protein": "6.4", "carbs": "58.8", "fat": "24.5"}
        conflict = {"calories": "390", "protein": "3", "carbs": "75", "fat": "8"}
        self.assertTrue(MODULE.family_nutrition_compatible([base, rounded]))
        self.assertFalse(MODULE.family_nutrition_compatible([base, conflict]))


if __name__ == "__main__":
    unittest.main()

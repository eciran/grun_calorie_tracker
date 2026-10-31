import importlib.util
from pathlib import Path
import unittest


SPEC = importlib.util.spec_from_file_location(
    "off_review", Path(__file__).with_name("verify-package-families-off.py")
)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class OffEvidenceTest(unittest.TestCase):
    def test_package_units_are_normalized(self):
        self.assertEqual(MODULE.normalized_package("1.5 L"), [(1, "1500", "ml")])
        self.assertEqual(MODULE.normalized_package("1500 ml"), [(1, "1500", "ml")])
        self.assertEqual(MODULE.normalized_package("1 kg"), [(1, "1000", "g")])

    def test_missing_is_not_zero(self):
        result = MODULE.nutrition_comparison(
            {"calories": "0", "protein": "0", "carbs": "0", "fat": "0"},
            {"nutriments": {"energy-kcal_100g": 0}},
        )
        states = {row["field"]: row["state"] for row in result["fields"]}
        self.assertEqual(states["calories"], "MATCH")
        self.assertEqual(states["protein"], "MISSING")

    def test_nutrition_tolerance_is_bounded(self):
        result = MODULE.nutrition_comparison(
            {"calories": "100", "protein": "4", "carbs": "10", "fat": "2"},
            {"nutriments": {
                "energy-kcal_100g": 101, "proteins_100g": 4.1,
                "carbohydrates_100g": 10.1, "fat_100g": 5,
            }},
        )
        self.assertEqual(result["differentCount"], 1)


if __name__ == "__main__":
    unittest.main()

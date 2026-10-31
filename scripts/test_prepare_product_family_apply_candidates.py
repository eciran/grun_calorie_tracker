import importlib.util
import unittest
from pathlib import Path


SPEC = importlib.util.spec_from_file_location(
    "prepare_families", Path(__file__).with_name("prepare-product-family-apply-candidates.py")
)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ProductFamilyApplyCandidateTest(unittest.TestCase):
    def test_parses_mass_and_volume_packages(self):
        self.assertEqual((400.0, "GRAM"), MODULE.package_from_name("Cheese 400 Gr")[:2])
        self.assertEqual((1500.0, "MILLILITER"), MODULE.package_from_name("Water 1.5 L")[:2])

    def test_removes_only_package_from_display_name(self):
        self.assertEqual("Eker Kaşar Peyniri", MODULE.family_display_name("Eker Kaşar Peyniri 400 Gr"))

    def test_rejects_material_nutrition_difference(self):
        rows = [
            {"calories": "300", "protein": "20", "carbs": "1", "fat": "25"},
            {"calories": "300", "protein": "10", "carbs": "1", "fat": "25"},
        ]
        self.assertFalse(MODULE.nutrition_matches(rows))


if __name__ == "__main__":
    unittest.main()

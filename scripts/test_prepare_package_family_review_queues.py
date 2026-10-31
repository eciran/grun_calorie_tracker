import importlib.util
import unittest
from pathlib import Path


SPEC = importlib.util.spec_from_file_location(
    "queues", Path(__file__).with_name("prepare-package-family-review-queues.py")
)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def row(calories, protein, carbs, fat):
    return {"calories": calories, "protein": protein, "carbs": carbs, "fat": fat}


class PackageFamilyReviewQueueTest(unittest.TestCase):
    def test_nutrition_levels_distinguish_rounding_from_formula_change(self):
        base = row(500, 6, 58, 25)
        self.assertEqual("COMPATIBLE", MODULE.nutrition_level([base, row(504, 6.4, 58.8, 24.5)]))
        self.assertEqual("MINOR_VARIANCE", MODULE.nutrition_level([base, row(545, 7.5, 66, 28)]))
        self.assertEqual("MAJOR_CONFLICT", MODULE.nutrition_level([base, row(320, 2, 75, 5)]))

    def test_same_package_compatible_products_go_to_duplicate_review(self):
        self.assertEqual(
            "DUPLICATE_SEARCH_COLLAPSE_REVIEW",
            MODULE.queue_decision("OFF_SAME_NORMALIZED_PACKAGE_REVIEW", "COMPATIBLE"),
        )

    def test_cross_member_conflict_is_not_family_ready(self):
        self.assertEqual(
            "FAMILY_FORMULA_CONFLICT",
            MODULE.queue_decision("OFF_CROSS_MEMBER_NUTRITION_REVIEW", "MAJOR_CONFLICT"),
        )


if __name__ == "__main__":
    unittest.main()

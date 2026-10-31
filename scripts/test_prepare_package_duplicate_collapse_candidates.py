import importlib.util
import unittest
from pathlib import Path


SPEC = importlib.util.spec_from_file_location(
    "collapse", Path(__file__).with_name("prepare-package-duplicate-collapse-candidates.py")
)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def product(product_id, name="Same Item", serving="100", calories="50"):
    return {
        "id": str(product_id), "brand": "Test Brand", "brand_id": "",
        "display_name": name, "serving_size_grams": serving, "serving_unit": "GRAM",
        "nutrition_reference_unit": "PER_100G", "calories": calories,
        "protein": "3", "carbs": "5", "fat": "2",
    }


class PackageDuplicateCollapseCandidateTest(unittest.TestCase):
    def test_backend_identity_matches_catalog_sql_contract(self):
        self.assertEqual(
            ("legacy:testbrand", "sameitem"),
            MODULE.backend_identity(product(1)),
        )

    def test_exact_candidate_requires_identity_and_core_match_but_preserves_serving_variants(self):
        self.assertTrue(MODULE.exact_collapse_candidate([product(1), product(2)]))
        self.assertTrue(MODULE.exact_collapse_candidate([product(1), product(2, serving="150")]))
        self.assertEqual(
            "NUTRITION_TOLERANCE_REVIEW",
            MODULE.collapse_candidate_level([product(1), product(2, calories="52")]),
        )
        self.assertFalse(MODULE.exact_collapse_candidate([product(1), product(2, calories="70")]))
        self.assertFalse(MODULE.exact_collapse_candidate([product(1), product(2, name="Other Item")]))


if __name__ == "__main__":
    unittest.main()

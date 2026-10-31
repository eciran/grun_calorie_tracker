import importlib.util
import unittest
from pathlib import Path


SPEC = importlib.util.spec_from_file_location(
    "review", Path(__file__).with_name("prepare-package-duplicate-collapse-review.py")
)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class PackageDuplicateCollapseReviewTest(unittest.TestCase):
    def test_survivor_rank_prefers_verification_then_quality_and_usage(self):
        raw = {"verification_status": "RAW_IMPORTED", "quality_score": "90", "usage_count": "5", "search_selection_count": "3"}
        verified = {"verification_status": "VERIFIED", "quality_score": "50", "usage_count": "0", "search_selection_count": "0"}
        self.assertGreater(MODULE.survivor_rank(verified), MODULE.survivor_rank(raw))

    def test_missing_values_are_zero(self):
        self.assertEqual((1, 0, 0, 0), MODULE.survivor_rank({"verification_status": "RAW_IMPORTED"}))


if __name__ == "__main__":
    unittest.main()

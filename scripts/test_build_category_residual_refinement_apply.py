import importlib.util
import unittest
from pathlib import Path


PATH = Path(__file__).with_name("build-category-residual-refinement-apply.py")
SPEC = importlib.util.spec_from_file_location("residual_apply", PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


BASE = {"rules": [{
    "sourceTag": "en:milks", "categorySlug": "milk", "primaryPriority": 1,
    "decision": "REVIEW_REQUIRED"
}]}
REFINEMENTS = {"version": 2, "rules": [{
    "id": "milk-v2", "sourceReviewCategory": "milk", "targetCategory": "milk",
    "requiredAnyTags": ["en:goat-milks"], "excludedAnyTags": []
}]}


class ResidualApplyTest(unittest.TestCase):
    def test_rehearsal_has_exact_gate_primary_exclusion_and_rollback(self):
        sql = MODULE.build(BASE, REFINEMENTS, 12, False)
        self.assertIn("expected 12", sql)
        self.assertIn("assigned.primary_category", sql)
        self.assertIn("conditional-category-refinement-v2", sql)
        self.assertIn("ROLLBACK;", sql)
        self.assertNotIn("COMMIT;", sql)

    def test_commit_is_explicit(self):
        sql = MODULE.build(BASE, REFINEMENTS, 12, True)
        self.assertIn("'APPLY' mode", sql)
        self.assertTrue(sql.endswith("COMMIT;\n"))

    def test_rejects_invalid_expected_count(self):
        with self.assertRaisesRegex(ValueError, "positive"):
            MODULE.build(BASE, REFINEMENTS, 0, False)


if __name__ == "__main__":
    unittest.main()

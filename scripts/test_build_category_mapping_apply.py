import importlib.util
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("build-category-mapping-apply.py")
SPEC = importlib.util.spec_from_file_location("category_mapping_apply_builder", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class CategoryMappingApplyBuilderTest(unittest.TestCase):
    def setUp(self):
        self.decisions = {
            "defaultDecision": "AUTO_SAFE",
            "rules": [
                {"sourceTag": "en:fruits", "categorySlug": "fruit", "primaryPriority": 10},
                {"sourceTag": "en:vegetables", "categorySlug": "vegetables",
                 "primaryPriority": 20, "decision": "REVIEW_REQUIRED"}
            ]
        }

    def test_rehearsal_rolls_back_and_contains_exact_gates(self):
        sql = MODULE.build(self.decisions, 10, 11, 1, False)
        self.assertIn("REHEARSAL_ROLLBACK", sql)
        self.assertTrue(sql.rstrip().endswith("ROLLBACK;"))
        self.assertIn("expected 10", sql)
        self.assertIn("expected 11", sql)
        self.assertIn("Conflicting existing primary category", sql)

    def test_commit_is_explicit_and_excludes_review_rules_from_insert(self):
        sql = MODULE.build(self.decisions, 10, 11, 1, True)
        self.assertIn("'APPLY' AS mode", sql)
        self.assertTrue(sql.rstrip().endswith("COMMIT;"))
        self.assertIn("WHERE rules.decision = 'AUTO_SAFE'", sql)

    def test_rejects_invalid_expected_counts(self):
        with self.assertRaisesRegex(ValueError, "positive"):
            MODULE.build(self.decisions, 0, 11, 1, False)


if __name__ == "__main__":
    unittest.main()

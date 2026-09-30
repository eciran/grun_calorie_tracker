import importlib.util
import unittest
from pathlib import Path


PATH = Path(__file__).with_name("build-category-residual-cohort-report.py")
SPEC = importlib.util.spec_from_file_location("residual_report", PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ResidualCohortReportTest(unittest.TestCase):
    def test_excludes_products_that_already_have_a_primary_category(self):
        sql = MODULE.build({"defaultDecision": "AUTO_SAFE", "rules": [
            {"sourceTag": "en:milks", "categorySlug": "milk", "primaryPriority": 10,
             "decision": "REVIEW_REQUIRED"}
        ]})
        self.assertIn("BEGIN TRANSACTION READ ONLY", sql)
        self.assertIn("NOT EXISTS", sql)
        self.assertIn("assigned.primary_category", sql)
        self.assertIn("tag_rank <= 30", sql)
        self.assertIn("sample_rank <= 20", sql)
        self.assertNotIn("INSERT INTO food_", sql)
        self.assertNotIn("UPDATE ", sql)

    def test_rejects_duplicate_tags(self):
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            MODULE.build({"rules": [
                {"sourceTag": "en:milks", "categorySlug": "milk", "primaryPriority": 10},
                {"sourceTag": "en:milks", "categorySlug": "milk", "primaryPriority": 20}
            ]})


if __name__ == "__main__":
    unittest.main()

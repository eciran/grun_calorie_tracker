import importlib.util
import unittest
from pathlib import Path


PATH = Path(__file__).with_name("build-category-review-cohort-report.py")
SPEC = importlib.util.spec_from_file_location("review_report", PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ReviewCohortReportTest(unittest.TestCase):
    def test_builds_read_only_evidence_report(self):
        sql = MODULE.build({"defaultDecision": "AUTO_SAFE", "rules": [
            {"sourceTag": "en:milks", "categorySlug": "milk", "primaryPriority": 10,
             "decision": "REVIEW_REQUIRED"}
        ]})
        self.assertIn("BEGIN TRANSACTION READ ONLY", sql)
        self.assertIn("'CO_TAG'", sql)
        self.assertIn("'SAMPLE'", sql)
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

import importlib.util
import unittest
from pathlib import Path


PATH = Path(__file__).with_name("build-active-category-review-analysis.py")
SPEC = importlib.util.spec_from_file_location("active_category_review", PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ActiveCategoryReviewAnalysisTest(unittest.TestCase):
    def test_report_is_read_only_and_uses_active_issue_queue(self):
        sql = MODULE.build(sample_limit=25, tag_limit=30, signature_limit=20)
        self.assertIn("BEGIN TRANSACTION READ ONLY", sql)
        self.assertIn("issue.issue_type = 'MISSING_CANONICAL_CATEGORY'", sql)
        self.assertIn("issue.resolved = false", sql)
        self.assertIn("assigned.primary_category", sql)
        self.assertIn("'SOURCE_TAG'", sql)
        self.assertIn("'TAG_SIGNATURE'", sql)
        self.assertIn("samples.sample_rank <= 25", sql)
        self.assertNotIn("INSERT INTO", sql.upper())
        self.assertNotIn("UPDATE ", sql.upper())
        self.assertNotIn("DELETE FROM", sql.upper())

    def test_limits_are_bounded(self):
        with self.assertRaisesRegex(ValueError, "sample_limit"):
            MODULE.build(sample_limit=0)
        with self.assertRaisesRegex(ValueError, "tag_limit"):
            MODULE.build(tag_limit=1001)
        with self.assertRaisesRegex(ValueError, "signature_limit"):
            MODULE.build(signature_limit=501)


if __name__ == "__main__":
    unittest.main()

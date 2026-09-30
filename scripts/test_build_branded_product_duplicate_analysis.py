import importlib.util
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("build-branded-product-duplicate-analysis.py")
SPEC = importlib.util.spec_from_file_location("branded_duplicate_report", SCRIPT)
report = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(report)


class BrandedProductDuplicateAnalysisTest(unittest.TestCase):
    def test_report_is_read_only_and_does_not_merge(self):
        sql = report.build(250, 50)
        self.assertIn("BEGIN TRANSACTION READ ONLY", sql)
        self.assertIn("item.catalog_type = 'BRANDED_PRODUCT'", sql)
        self.assertIn("AUTO_SAFE_SAME_GTIN", sql)
        self.assertIn("REVIEW_DIFFERENT_GTIN", sql)
        self.assertIn("ORDER BY variant_signal ASC", sql)
        self.assertNotIn("CREATE TEMP TABLE", sql)
        self.assertNotIn("DELETE FROM", sql.upper())
        self.assertNotIn("UPDATE food_items", sql)

    def test_limits_are_bounded(self):
        with self.assertRaises(ValueError):
            report.build(0, 50)
        with self.assertRaises(ValueError):
            report.build(250, 101)


if __name__ == "__main__":
    unittest.main()

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
        self.assertIn("CANDIDATE_SAME_GTIN", sql)
        self.assertNotIn("AUTO_SAFE", sql)
        self.assertIn("REVIEW_DIFFERENT_GTIN", sql)
        self.assertIn("ORDER BY variant_signal ASC", sql)
        self.assertNotIn("CREATE TEMP TABLE", sql)
        self.assertNotIn("DELETE FROM", sql.upper())
        self.assertNotIn("UPDATE food_items", sql)

    def test_basis_exact_core_and_partial_source_identity_stay_review_only(self):
        sql = report.build(250, 50)
        self.assertIn("item.nutrition_reference_unit", sql)
        self.assertNotIn("round(calories::numeric, 1)", sql)
        self.assertIn("missing_source_key_count", sql)
        self.assertEqual(3, sql.count("THEN 'REVIEW_VARIANT_CONFLICT'"))
        self.assertEqual(3, sql.count("THEN 'REVIEW_MISSING_SOURCE_KEY'"))
        self.assertEqual(3, sql.count("THEN 'REVIEW_INCOMPLETE_PROFILE'"))
        self.assertLess(sql.index("REVIEW_VARIANT_CONFLICT"), sql.index("CANDIDATE_SAME_GTIN"))

    def test_limits_are_bounded(self):
        with self.assertRaises(ValueError):
            report.build(0, 50)
        with self.assertRaises(ValueError):
            report.build(250, 101)


if __name__ == "__main__":
    unittest.main()

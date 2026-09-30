import importlib.util
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("build-canonical-brand-backfill.py")
SPEC = importlib.util.spec_from_file_location("canonical_brand_backfill", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class CanonicalBrandBackfillTest(unittest.TestCase):
    def test_key_matches_separator_and_case_variants(self):
        self.assertEqual("vithit", MODULE.brand_key("Vit-Hit"))
        self.assertEqual("vithit", MODULE.brand_key("VIT HIT"))
        self.assertEqual("m&s", MODULE.brand_key("M & S"))

    def test_key_preserves_semantic_characters(self):
        self.assertNotEqual(MODULE.brand_key("M&S"), MODULE.brand_key("Marks And Spencer"))
        self.assertNotEqual(MODULE.brand_key("7UP"), MODULE.brand_key("7DAYS"))

    def test_placeholder_is_not_a_brand(self):
        with self.assertRaises(ValueError):
            MODULE.brand_key("-")

    def test_sql_is_bounded_and_preserves_legacy_brand(self):
        sql = MODULE.build_sql([
            {"canonicalName": "VITHIT", "aliases": ["Vit Hit"]}
        ], 7)
        self.assertIn("expected 7 rows", sql)
        self.assertIn("SET brand_id = matches.brand_id", sql)
        self.assertNotIn("SET brand =", sql)
        self.assertIn("item.is_custom IS NOT TRUE", sql)
        self.assertEqual(1, sql.count("'vithit'"))


if __name__ == "__main__":
    unittest.main()

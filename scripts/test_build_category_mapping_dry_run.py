import importlib.util
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("build-category-mapping-dry-run.py")
SPEC = importlib.util.spec_from_file_location("category_mapping_builder", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class CategoryMappingBuilderTest(unittest.TestCase):
    def test_builds_read_only_sql(self):
        sql = MODULE.build({"rules": [
            {"sourceTag": "en:fruits", "categorySlug": "fruit", "primaryPriority": 10}
        ]})
        self.assertIn("BEGIN TRANSACTION READ ONLY", sql)
        self.assertIn("('en:fruits', 'fruit', 10, 'REVIEW_REQUIRED')", sql)
        self.assertIn("'DECISION'", sql)
        self.assertIn("'APPLY_GATE'", sql)
        self.assertIn("safe_assignments", sql)
        self.assertIn("'PAIR'", sql)
        self.assertIn("'TIE'", sql)
        self.assertIn("'SAMPLE'", sql)
        self.assertNotIn("INSERT INTO", sql)
        self.assertNotIn("UPDATE ", sql)

    def test_rejects_duplicate_source_tag(self):
        with self.assertRaisesRegex(ValueError, "Duplicate source tag"):
            MODULE.build({"rules": [
                {"sourceTag": "en:fruits", "categorySlug": "fruit", "primaryPriority": 10},
                {"sourceTag": "EN:FRUITS", "categorySlug": "fruit", "primaryPriority": 20}
            ]})

    def test_rejects_non_off_tag(self):
        with self.assertRaisesRegex(ValueError, "Only explicit OFF English tags"):
            MODULE.build({"rules": [
                {"sourceTag": "fruit", "categorySlug": "fruit", "primaryPriority": 10}
            ]})

    def test_accepts_auto_safe_default_and_rejects_unknown_decision(self):
        sql = MODULE.build({
            "defaultDecision": "AUTO_SAFE",
            "rules": [
                {"sourceTag": "en:fruits", "categorySlug": "fruit", "primaryPriority": 10}
            ]
        })
        self.assertIn("('en:fruits', 'fruit', 10, 'AUTO_SAFE')", sql)

        with self.assertRaisesRegex(ValueError, "Invalid decision"):
            MODULE.build({"rules": [
                {"sourceTag": "en:fruits", "categorySlug": "fruit",
                 "primaryPriority": 10, "decision": "APPLY_NOW"}
            ]})

    def test_builds_focused_read_only_apply_gate(self):
        sql = MODULE.build_apply_gate({
            "defaultDecision": "AUTO_SAFE",
            "rules": [
                {"sourceTag": "en:fruits", "categorySlug": "fruit", "primaryPriority": 10},
                {"sourceTag": "en:vegetables", "categorySlug": "vegetables",
                 "primaryPriority": 20, "decision": "REVIEW_REQUIRED"}
            ]
        })
        self.assertIn("BEGIN TRANSACTION READ ONLY", sql)
        self.assertIn("'APPLY_GATE'", sql)
        self.assertIn("safe_assignments", sql)
        self.assertNotIn("INSERT INTO", sql)
        self.assertNotIn("UPDATE ", sql)


if __name__ == "__main__":
    unittest.main()

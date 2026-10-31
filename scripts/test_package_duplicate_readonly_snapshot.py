import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


SPEC = importlib.util.spec_from_file_location(
    "collector", Path(__file__).with_name("collect-r3-source-snapshot.py")
)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class PackageDuplicateReadonlySnapshotTest(unittest.TestCase):
    @staticmethod
    def temporary_directory():
        Path("target").mkdir(exist_ok=True)
        return tempfile.TemporaryDirectory(dir=Path("target"))

    def test_query_is_bounded_and_read_only(self):
        with self.temporary_directory() as directory:
            manifest = Path(directory) / "selection.jsonl"
            manifest.write_text(json.dumps({"productIds": [7, 3]}) + "\n", encoding="utf-8")
            sql = MODULE.package_duplicate_review_sql(manifest)
        self.assertIn("REPEATABLE READ READ ONLY", sql)
        self.assertIn("WHERE f.id IN (3,7)", sql)
        self.assertIn("ROLLBACK", sql)
        self.assertNotIn("UPDATE ", sql.upper())
        self.assertNotIn("DELETE ", sql.upper())
        self.assertNotIn("INSERT ", sql.upper())

    def test_empty_selection_is_rejected(self):
        with self.temporary_directory() as directory:
            manifest = Path(directory) / "selection.jsonl"
            manifest.write_text("", encoding="utf-8")
            with self.assertRaises(ValueError):
                MODULE.package_duplicate_review_sql(manifest)


if __name__ == "__main__":
    unittest.main()

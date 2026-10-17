import csv
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("r4", Path(__file__).with_name("build-r4-naming-review.py"))
r4 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(r4)
ROOT = Path(__file__).resolve().parents[1]


class NamingReviewTests(unittest.TestCase):
    def setUp(self):
        self.registry = json.loads((ROOT / "src/main/resources/catalog/source-name-decisions-r4-v1.json").read_text(encoding="utf-8"))
        self.catalog = []
        self.foods = {}
        self.r3 = {"decisions": []}
        for index, entry in enumerate(self.registry["entries"]):
            row = {"id": str(index + 1), "source_key": entry["sourceKey"], "name": "Old Label " + str(index), "preparation_state": "RAW", "calories": "100"}
            food = {"fdcId": int(r4.review.fdc_id(entry["sourceKey"])), "description": entry["sourceDescription"], "dataType": "SR Legacy"}
            self.catalog.append(row)
            self.foods[str(food["fdcId"])] = food
            self.r3["decisions"].append({"sourceKey": entry["sourceKey"], "beforeFingerprint": r4.review.fingerprint(row), "sourceFingerprint": r4.review.fingerprint(food), "findings": ["NAMING_REVIEW"]})

    def build(self):
        return r4.build_decisions(self.catalog, self.foods, self.r3, self.registry)

    def test_all_16_decisions_have_both_languages_and_no_fact_or_merge_write(self):
        decisions = self.build()
        self.assertEqual(16, len(decisions))
        for decision in decisions:
            self.assertEqual({"EN", "TR"}, set(decision["localizations"]))
            self.assertEqual({"name", "display_name", "short_display_name", "preparation_state"}, set(decision["changes"]))
            self.assertFalse(decision["applyAllowed"])
            self.assertFalse(decision["nutritionWriteAllowed"])
            self.assertFalse(decision["mergeAllowed"])
            self.assertIn("localizations", decision["freshBeforeImagesRequired"])

    def test_missing_or_duplicate_registry_row_fails_closed(self):
        self.registry["entries"][-1] = self.registry["entries"][0]
        with self.assertRaises(ValueError):
            self.build()

    def test_official_source_description_mismatch_fails_closed(self):
        self.registry["entries"][0]["sourceDescription"] = "Similar but different cut"
        with self.assertRaises(ValueError):
            self.build()

    def test_stale_catalog_before_image_is_not_accepted(self):
        self.catalog[0]["calories"] = "101"
        with self.assertRaises(ValueError):
            self.build()

    def test_source_hash_change_is_not_accepted(self):
        next(iter(self.foods.values()))["dataType"] = "Foundation"
        with self.assertRaises(ValueError):
            self.build()

    def test_dry_rice_cannot_be_resolved_as_cooked(self):
        next(entry for entry in self.registry["entries"] if entry["sourceKey"].endswith(":168876"))["targetState"] = "COOKED"
        with self.assertRaises(ValueError):
            self.build()

    def test_unknown_heat_state_remains_unknown_and_salt_is_not_zero_sodium(self):
        decisions = self.build()
        cashew = next(d for d in decisions if d["sourceKey"].endswith(":170163"))
        self.assertEqual("UNSPECIFIED", cashew["changes"]["preparation_state"])
        self.assertIn("No Added Salt", cashew["changes"]["display_name"])
        self.assertNotIn("sodium", cashew["changes"])

    def test_unicode_is_composed_not_transliterated(self):
        self.assertEqual("Çiğ Süt", r4.clean("C\u0327ig\u0306\u00a0Su\u0308t"))

    def test_markup_controls_and_unknown_brand_are_not_auto_repaired(self):
        row = {"display_name": "<b>milk</b>\u202e", "brand": "ACME Foods", "display_name_tr": "Cig Tavuk"}
        findings, proposals = r4.format_findings(row, set(row))
        self.assertIn("DISPLAY_NAME_HTML_REVIEW", findings)
        self.assertIn("TR_DIACRITICS_REVIEW", findings)
        self.assertNotIn("display_name", proposals)
        self.assertNotIn("brand", proposals)

    def test_missing_snapshot_columns_are_unmeasured_not_missing_localizations(self):
        findings, _ = r4.format_findings({"display_name": "Milk"}, {"display_name"})
        self.assertNotIn("MISSING_TR_LOCALIZATION", findings)

    def test_streaming_20000_rows_has_exhaustive_counts(self):
        with tempfile.TemporaryDirectory(dir=ROOT / "outputs") as directory:
            root = Path(directory)
            source = root / "catalog.csv"
            with source.open("w", encoding="utf-8", newline="") as stream:
                writer = csv.DictWriter(stream, fieldnames=["id", "name", "display_name"])
                writer.writeheader()
                for index in range(20000):
                    writer.writerow({"id": str(index + 1), "name": "Milk", "display_name": "Milk"})
            summary = r4.stream_dry_run(source, root / "review.csv")
            self.assertEqual(20000, summary["rows"])
            self.assertFalse(summary["brandMeasured"])
            self.assertFalse(summary["localizationsMeasured"]["TR"])
            with (root / "review.csv").open(encoding="utf-8-sig", newline="") as stream:
                self.assertEqual(20000, sum(1 for _ in csv.DictReader(stream)))

    def test_batch_sql_is_readonly_and_keyset_bounded(self):
        sql = (ROOT / "scripts/sql/export-product-naming-batch.sql").read_text(encoding="utf-8")
        self.assertIn("REPEATABLE READ READ ONLY", sql)
        self.assertIn("NOT coalesce(f.is_custom, false)", sql)
        self.assertIn("f.id > :'after_id'", sql)
        self.assertIn("least(1000", sql)
        self.assertIn("ROLLBACK", sql)


if __name__ == "__main__":
    unittest.main()

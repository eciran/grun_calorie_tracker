import csv
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent
VALIDATOR_PATH = ROOT / "scripts" / "validate-local-food-catalog.py"
SPEC = importlib.util.spec_from_file_location("local_food_validator", VALIDATOR_PATH)
VALIDATOR = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(VALIDATOR)

CONTRACT = ROOT / "automation" / "local-food-catalog-aug08" / "local-food-catalog-v1.json"
REGISTRY = ROOT / "automation" / "local-food-catalog-aug08" / "source-registry-v1.json"
FIXTURE = ROOT / "automation" / "local-food-catalog-aug08" / "fixtures" / "d1-contract-smoke.csv"


class LocalFoodCatalogValidatorTest(unittest.TestCase):

    def validate(self, path: Path, release_class: str = "REVIEW"):
        return VALIDATOR.validate_catalog(path, CONTRACT, REGISTRY, release_class)

    def read_fixture(self):
        with FIXTURE.open("r", encoding="utf-8", newline="") as handle:
            reader = csv.DictReader(handle)
            return reader.fieldnames, list(reader)

    def write_rows(self, headers, rows):
        temporary = tempfile.NamedTemporaryFile("w", encoding="utf-8", newline="", suffix=".csv", delete=False)
        with temporary:
            writer = csv.DictWriter(temporary, fieldnames=headers)
            writer.writeheader()
            writer.writerows(rows)
        return Path(temporary.name)

    def test_review_fixture_passes_with_four_independent_dish_variants(self):
        report = self.validate(FIXTURE)
        self.assertEqual("PASS", report["result"], report["errors"])
        self.assertEqual(5, report["metrics"]["rows"])
        self.assertEqual(4, report["metrics"]["catalogTypeCounts"]["LOCAL_DISH"])
        self.assertEqual(4, report["metrics"]["uniqueCanonicalLocalDishes"])

    def test_production_blocks_pending_estimated_profiles(self):
        report = self.validate(FIXTURE, "PRODUCTION")
        self.assertEqual("FAIL", report["result"])
        codes = [entry["code"] for entry in report["errors"]]
        self.assertEqual(4, codes.count("ESTIMATED_APPROVAL_REQUIRED"))
        self.assertEqual(4, codes.count("SOURCE_NOT_PRODUCTION_ALLOWED"))

    def test_duplicate_family_variant_is_rejected_even_with_different_source_key(self):
        headers, rows = self.read_fixture()
        duplicate = dict(rows[-1])
        duplicate["source_key"] = "TR:LOCAL_DISH:PREPARED:kuru_fasulye:zeytinyagli_alt"
        rows.append(duplicate)
        path = self.write_rows(headers, rows)
        try:
            report = self.validate(path)
        finally:
            path.unlink(missing_ok=True)
        self.assertIn("DUPLICATE_CANONICAL_IDENTITY", [entry["code"] for entry in report["errors"]])

    def test_calculated_profile_requires_versioned_method_but_not_ingredient_links(self):
        headers, rows = self.read_fixture()
        calculated = rows[1]
        calculated["nutrition_basis"] = "CALCULATED"
        calculated["estimation_method"] = ""
        calculated["confidence_score"] = ""
        calculated["calculation_method"] = "Yield-adjusted reference recipe"
        calculated["calculation_version"] = "calc-v1"
        calculated["ingredient_links_json"] = ""
        path = self.write_rows(headers, [calculated])
        try:
            passing = self.validate(path)
            calculated["calculation_method"] = ""
            failing_path = self.write_rows(headers, [calculated])
            try:
                failing = self.validate(failing_path)
            finally:
                failing_path.unlink(missing_ok=True)
        finally:
            path.unlink(missing_ok=True)
        self.assertEqual("PASS", passing["result"], passing["errors"])
        self.assertIn("CALCULATION_METHOD_REQUIRED", [entry["code"] for entry in failing["errors"]])


if __name__ == "__main__":
    unittest.main()

import importlib.util
import unittest
from pathlib import Path


SPEC = importlib.util.spec_from_file_location(
    "coverage", Path(__file__).with_name("analyze-package-family-coverage.py")
)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def group(decision="REVIEW_IDENTITY_OR_PACKAGE_EVIDENCE", packages=("35 g", "100 g")):
    barcodes = ("5449000000996", "4006381333931")
    return {
        "decision": decision,
        "products": [
            {
                "catalog_type": "BRANDED_PRODUCT", "publication_status": "PUBLISHED",
                "market_region": "UK_IE", "preparation_state": "UNSPECIFIED",
                "nutrition_reference_unit": "PER_100G", "barcode": barcodes[index],
                "package_text": package,
            }
            for index, package in enumerate(packages)
        ],
    }


class PackageFamilyCoverageTest(unittest.TestCase):
    def test_explicit_packages_are_review_candidates(self):
        self.assertEqual("EXPLICIT_PACKAGE_REVIEW", MODULE.classify(group()))

    def test_missing_package_metadata_is_not_discarded(self):
        self.assertEqual("PACKAGE_METADATA_REQUIRED", MODULE.classify(group(packages=("", ""))))

    def test_nutrition_difference_has_separate_queue(self):
        self.assertEqual(
            "PACKAGE_AND_NUTRITION_EVIDENCE_REQUIRED",
            MODULE.classify(group("REVIEW_NUTRITION_DIFFERENCE", ("", ""))),
        )

    def test_same_barcode_is_held(self):
        candidate = group()
        candidate["products"][1]["barcode"] = candidate["products"][0]["barcode"]
        self.assertEqual("HELD_BARCODE_EVIDENCE", MODULE.classify(candidate))

    def test_invalid_gtin_is_held_before_package_review(self):
        candidate = group()
        candidate["products"][1]["barcode"] = "4006381333932"
        self.assertEqual("HELD_INVALID_GTIN", MODULE.classify(candidate))

    def test_gtin_checksum_supports_common_lengths(self):
        self.assertTrue(MODULE.valid_gtin("96385074"))
        self.assertTrue(MODULE.valid_gtin("036000291452"))
        self.assertTrue(MODULE.valid_gtin("4006381333931"))
        self.assertTrue(MODULE.valid_gtin("10012345000017"))
        self.assertFalse(MODULE.valid_gtin("4006381333932"))


if __name__ == "__main__":
    unittest.main()

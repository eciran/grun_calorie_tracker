import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("identity_review", Path(__file__).with_name("build-product-source-identity-review.py"))
review = importlib.util.module_from_spec(spec)
spec.loader.exec_module(review)


class IdentityReviewTests(unittest.TestCase):
    def row(self, **fields):
        return {"source_key": "USDA_FOODDATA:fdc:123", "catalog_type": "BRANDED_PRODUCT", **fields}

    def source(self, **fields):
        return {"123": [{"selectedDataType": "Foundation", "identity": "banana", **fields}]}

    def test_exact_source_proposes_type_but_never_applies(self):
        result = review.classify(self.row(), self.source())
        self.assertEqual("GENERIC_INGREDIENT", result["targetType"])
        self.assertEqual("fdc_123", result["proposedCompositionVariantKey"])
        self.assertFalse(result["applyAllowed"])

    def test_branded_remains_branded_without_gtin(self):
        result = review.classify(self.row(), self.source(selectedDataType="Branded"))
        self.assertEqual("BRANDED_PRODUCT", result["targetType"])
        self.assertIsNone(result["proposedConceptKey"])

    def test_no_or_ambiguous_source_is_not_evidence(self):
        self.assertEqual("SOURCE_REQUIRED", review.classify(self.row(), {})["decision"])
        sources = self.source()
        sources["123"] *= 2
        self.assertEqual("SOURCE_REQUIRED", review.classify(self.row(), sources)["decision"])

    def test_conflicting_metadata_and_gtin_require_review(self):
        self.assertEqual("SOURCE_REQUIRED", review.classify(self.row(source_data_type="Branded"), self.source())["decision"])
        self.assertEqual("ADMIN_REVIEW", review.classify(self.row(barcode="1234567890123"), self.source())["decision"])

    def test_source_identity_is_exact_and_not_name_based(self):
        for invalid in ("USDA_FOODDATA:fdc:0", "USDA_FOODDATA:fdc:abc", "COFID_2021:123"):
            self.assertIsNone(review.fdc_id(invalid))

    def test_same_name_with_different_basis_or_state_cannot_merge(self):
        index = review.index_catalog([{"source_key": "USDA:fdc:123", "name": "Milk", "preparation_state": "RAW", "nutrition_reference_unit": "PER_100G"}], [])
        source = {"source_key": "COFID_2021:1", "name": "Milk", "preparation_state": "PREPARED", "nutrition_reference_unit": "PER_100ML"}
        result = review.overlaps(source, index)
        self.assertEqual("OVERLAP_REVIEW", result["decision"])
        self.assertFalse(result["applyAllowed"])
        self.assertEqual("DIFFERENT", result["candidates"][0]["basisParity"])
        self.assertEqual("DIFFERENT", result["candidates"][0]["stateParity"])

    def test_no_known_match_is_not_a_new_product_approval(self):
        result = review.overlaps({"source_key": "COFID_2021:1", "name": "Banana", "preparation_state": "RAW"}, {})
        self.assertEqual("NO_KNOWN_MATCH_REVIEW", result["decision"])
        self.assertFalse(result["applyAllowed"])

    def test_unknown_preparation_requires_source(self):
        result = review.overlaps({"source_key": "COFID_2021:1", "name": "Banana", "preparation_state": "UNSPECIFIED"}, {})
        self.assertEqual("SOURCE_REQUIRED", result["decision"])

    def test_multiple_same_name_targets_are_preserved_as_ambiguous(self):
        rows = [{"source_key": "test:" + str(i), "name": "Milk"} for i in range(3)]
        result = review.overlaps({"source_key": "COFID_2021:1", "name": "Milk"}, review.index_catalog(rows, []))
        self.assertEqual(3, result["candidateCount"])

    def test_display_edit_changes_guard_hash_but_not_source_proposal(self):
        before = review.classify(self.row(name="Banana"), self.source())
        after = review.classify(self.row(name="Muz"), self.source())
        self.assertNotEqual(before["beforeFingerprint"], after["beforeFingerprint"])
        self.assertEqual(before["proposedConceptKey"], after["proposedConceptKey"])


if __name__ == "__main__":
    unittest.main()

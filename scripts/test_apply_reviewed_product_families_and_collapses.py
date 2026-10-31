import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


SCRIPT = Path(__file__).with_name("apply-reviewed-product-families-and-collapses.py")
SPEC = importlib.util.spec_from_file_location("apply_reviewed_product_families_and_collapses", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ApplyReviewedProductFamiliesAndCollapsesTest(unittest.TestCase):
    def test_resume_skips_checkpointed_duplicate(self):
        duplicate = {
            "brandKey": "brand", "nameKey": "name", "candidateFingerprint": "a" * 64,
            "decisionPayload": {}, "collapsePayload": {},
        }
        receipt = {
            "version": "PRODUCT_FAMILY_COLLAPSE_APPLY_V1", "duplicateScope": 183,
            "familyScope": 366, "duplicateApplications": [{
                "brandKey": "brand", "nameKey": "name", "candidateFingerprint": "a" * 64,
                "response": {},
            }], "familyApplications": [], "completed": False, "failure": "expired token",
        }
        with tempfile.TemporaryDirectory(dir=Path.cwd() / "outputs") as directory:
            path = Path(directory) / "receipt.json"
            path.write_text(json.dumps(receipt), encoding="utf-8")
            with patch.object(MODULE, "call") as api:
                result = MODULE.apply("https://example.test", "token", [duplicate], [], path)
            api.assert_not_called()
            self.assertTrue(result["completed"])
            self.assertNotIn("failure", result)
            self.assertEqual(1, len(result["duplicateApplications"]))


if __name__ == "__main__":
    unittest.main()

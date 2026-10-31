import importlib.util
from pathlib import Path
import unittest


SPEC = importlib.util.spec_from_file_location(
    "evidence_queue", Path(__file__).with_name("finalize-package-evidence-queue.py")
)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class EvidenceQueueTest(unittest.TestCase):
    def test_zero_profile_requires_present_values(self):
        self.assertTrue(MODULE.all_zero({"calories": 0, "protein": 0, "carbs": 0, "fat": 0}))
        self.assertFalse(MODULE.all_zero({"calories": 0, "protein": "", "carbs": 0, "fat": 0}))

    def test_gtin_checksum(self):
        self.assertTrue(MODULE.valid_gtin("5449000137173"))
        self.assertFalse(MODULE.valid_gtin("5449000137174"))
        self.assertFalse(MODULE.valid_gtin(""))


if __name__ == "__main__":
    unittest.main()

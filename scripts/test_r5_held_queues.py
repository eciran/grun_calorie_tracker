import importlib.util
import copy
import unittest
from datetime import datetime, timezone
from pathlib import Path

spec = importlib.util.spec_from_file_location("audit", Path(__file__).with_name("audit-r5-held-queues.py"))
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)
spec = importlib.util.spec_from_file_location("fresh", Path(__file__).with_name("review-r5-refreshed-evidence.py"))
fresh = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fresh)


class HeldQueueTests(unittest.TestCase):
    def test_exclusive_queue_counts_dont_add_overlapping_holds(self):
        rows = [{"id": "1", "holds": ["EXISTING_ADMIN_APPROVAL_REVIEW_REQUIRED", "FULL_NUTRITION_COMPARISON_REQUIRED"]},
                {"id": "2", "holds": ["R3_R4_CLASSIFICATION_OR_PREPARATION_GATE", "FULL_NUTRITION_COMPARISON_REQUIRED"]}]
        self.assertEqual({"NUTRIENT_COMPARISON": 1, "CLASSIFICATION_PREPARATION": 1}, audit.partition(rows))
        self.assertEqual(2, sum(audit.partition(rows).values()))
        with self.assertRaises(ValueError):
            audit.partition(rows + rows[:1])

    def test_unproven_stale_future_and_naive_times_cannot_be_fresh(self):
        now = datetime(2026, 10, 12, 12, tzinfo=timezone.utc)
        for stamp, state in ((None, "TIME_UNPROVEN"), ("2026-10-12T11:00:00", "TIME_UNPROVEN"),
                             ("2026-10-10T12:00:00Z", "RECHECK_REQUIRED"),
                             ("2026-10-13T12:00:00Z", "FUTURE_INVALID"),
                             ("2026-10-12T11:00:00Z", "FRESH_24H")):
            self.assertEqual(state, audit.freshness(stamp, now))

    def test_fresh_rebuild_never_borrows_missing_fields_from_old_source(self):
        code = "5449000000996"
        now = datetime(2026, 10, 12, 12, tzinfo=timezone.utc)
        product = {"code": code, "categories_tags": ["en:beverages"], "nutrition_data_per": "100ml"}
        response = {"status": 1, "product": product}
        receipt = {"barcode": code, "sourceUrl": audit.fetch.API + code + ".json?fields=code", "product": product,
                   "response": response, "responseFingerprint": audit.gate.source.r5.fingerprint(response),
                   "status": "FETCHED", "checkedAt": "2026-10-12T11:00:00Z"}
        row = {"id": "1", "source_key": "barcode:" + code, "normalized_barcode": code,
               "nutrition_reference_unit": "PER_100G", "data_source": "OPEN_FOOD_FACTS"}
        previous = {"sourceFingerprint": "old", "sourceCore": {"calories": 42}, "options": [{"mlVolume": 200}]}
        result = fresh.rebuild(row, receipt, previous, now)
        self.assertFalse(result["applyAllowed"])
        self.assertNotEqual("SOURCE_BASIS_CORRECTION_REQUIRES_FULL_BEFORE_IMAGE", result["decision"])
        self.assertEqual([], result["options"])
        for change in ({"checkedAt": "2026-10-10T00:00:00Z"}, {"sourceUrl": "https://untrusted.invalid"},
                       {"product": {"code": "other"}}, {"responseFingerprint": "bad"}):
            with self.assertRaises(ValueError):
                fresh.rebuild(row, {**copy.deepcopy(receipt), **change}, previous, now)


if __name__ == "__main__":
    unittest.main()

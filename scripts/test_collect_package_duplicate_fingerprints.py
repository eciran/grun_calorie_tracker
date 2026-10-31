import importlib.util
import json
import unittest
import urllib.error
from pathlib import Path
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location(
    "collector", Path(__file__).with_name("collect-package-duplicate-fingerprints.py")
)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class FingerprintCollectorTest(unittest.TestCase):
    def test_live_member_change_holds_payload(self):
        review = {
            "groups": [{
                "groupId": "G1", "brandKey": "brand:1", "nameKey": "item",
                "productIds": [1, 2], "barcodes": ["1", "2"], "proposedSurvivorId": 1,
            }]
        }
        root = Path("target/fingerprint-collector-test")
        root.mkdir(parents=True, exist_ok=True)
        source, output = root / "review.json", root / "output.json"
        source.write_text(json.dumps(review), encoding="utf-8")
        live = {"candidateFingerprint": "a" * 64, "candidates": [{"productId": 1}],
                "storedDecision": None, "searchCollapse": None}
        with patch.object(MODULE, "get_json", return_value=live):
            result = MODULE.collect(source, output, "https://example.test", "secret")
        self.assertEqual(0, result["liveVerified"])
        self.assertIn("LIVE_MEMBER_SET_CHANGED", result["groups"][0]["holdReasons"])
        self.assertIsNone(result["groups"][0]["decisionPayload"])
        self.assertNotIn("secret", output.read_text(encoding="utf-8"))

    def test_exact_live_group_prepares_but_does_not_apply_payload(self):
        review = {
            "groups": [{
                "groupId": "G1", "brandKey": "brand:1", "nameKey": "item",
                "productIds": [1, 2], "barcodes": ["1", "2"], "proposedSurvivorId": 1,
            }]
        }
        root = Path("target/fingerprint-collector-ready-test")
        root.mkdir(parents=True, exist_ok=True)
        source, output = root / "review.json", root / "output.json"
        source.write_text(json.dumps(review), encoding="utf-8")
        live = {"candidateFingerprint": "b" * 64,
                "candidates": [{"productId": 1}, {"productId": 2}],
                "storedDecision": None, "searchCollapse": None}
        with patch.object(MODULE, "get_json", return_value=live):
            result = MODULE.collect(source, output, "https://example.test", "secret")
        self.assertEqual(1, result["liveVerified"])
        self.assertFalse(result["groups"][0]["applyAllowed"])
        self.assertEqual(0, result["catalogWrites"])
        self.assertEqual("SURVIVOR_SELECTED", result["groups"][0]["decisionPayload"]["decision"])

    def test_missing_exact_endpoint_uses_strict_list_fallback(self):
        missing = urllib.error.HTTPError("https://example.test/exact", 404, "missing", {}, None)
        page = {"content": [{"brandKey": "brand:1", "nameKey": "item", "candidateFingerprint": "c" * 64}]}
        with patch.object(MODULE, "request_json", side_effect=[missing, page]):
            result = MODULE.get_json("https://example.test", "secret", "brand:1", "item", "Item")
        self.assertEqual("c" * 64, result["candidateFingerprint"])

    def test_list_fallback_rejects_non_exact_result(self):
        missing = urllib.error.HTTPError("https://example.test/exact", 404, "missing", {}, None)
        page = {"content": [{"brandKey": "brand:1", "nameKey": "other"}]}
        with patch.object(MODULE, "request_json", side_effect=[missing, page]):
            with self.assertRaises(ValueError):
                MODULE.get_json("https://example.test", "secret", "brand:1", "item", "Item")


if __name__ == "__main__":
    unittest.main()

import argparse
import csv
import gzip
import importlib.util
import io
import json
import tempfile
import unittest
import urllib.error
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import patch


def load(name, file):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(file))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


fetch = load("fetch_source", "fetch-r5-off-evidence.py")
bulk = load("bulk_source", "extract-r5-off-bulk-evidence.py")
CODE = "5449000000996"


def queue(root):
    path = root / "queue.csv"
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=["id", "source_key"])
        writer.writeheader()
        writer.writerows([{"id": "1", "source_key": "barcode:" + CODE},
                         {"id": "2", "source_key": "barcode:" + CODE},
                         {"id": "3", "source_key": "barcode:5449000000997"}])
    return path


class SourceCollectionTests(unittest.TestCase):
    def setUp(self):
        quiet = patch("sys.stdout", new_callable=io.StringIO)
        quiet.start()
        self.addCleanup(quiet.stop)

    def test_legacy_collectors_cannot_select_old_too_fast_delay(self):
        for filename in ("research-liquid-nutrition-evidence.ps1", "research-missing-serving-liquid-evidence.ps1", "research-off-liquid-category-batch.ps1"):
            script = Path(__file__).with_name(filename).read_text(encoding="utf-8-sig")
            self.assertIn("[ValidateRange(4500, 60000)]", script)
            self.assertRegex(script, r"\[int\]\s*\$DelayMilliseconds = 4500")
            self.assertNotIn("Remove-Item -LiteralPath $itemPath -Force", script)

    def test_exact_valid_gtin_dedup_no_code_invention(self):
        with tempfile.TemporaryDirectory() as tmp:
            q = queue(Path(tmp))
            self.assertEqual([(CODE, ["1", "2"])], fetch.candidates(q, None))
            self.assertEqual({CODE}, bulk.target_codes([q]))

    def test_cache_requires_origin_full_response_hash_identity_and_time(self):
        product = {"code": CODE}
        response = {"product": product, "status": 1}
        body = {"sourceUrl": fetch.API + CODE + ".json?fields=code", "barcode": CODE, "product": product,
                "response": response, "responseFingerprint": fetch.options.r5.fingerprint(response),
                "status": "FETCHED", "checkedAt": "2026-10-12T00:00:00Z"}
        now = datetime(2026, 10, 12, 1, tzinfo=timezone.utc)
        self.assertTrue(fetch.cached_valid(body, CODE, now))
        for changes in ({"sourceUrl": "https://other.invalid/"}, {"responseFingerprint": "bad"},
                        {"checkedAt": "2026-10-13T00:00:00Z"}, {"product": {"code": "other"}}):
            self.assertFalse(fetch.cached_valid({**body, **changes}, CODE, now))

    def test_fetch_keeps_single_reply_not_partial_field_join(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            q, out = queue(root), root / "evidence"
            data = {"status": 1, "product": {"code": CODE, "categories_tags": ["en:beverages"]}}
            url = fetch.API + CODE + ".json?fields=" + fetch.FIELDS
            class Reply(io.BytesIO):
                def geturl(self):
                    return url
            with patch.object(fetch.urllib.request, "urlopen", return_value=Reply(json.dumps(data).encode())) as call:
                fetch.run(argparse.Namespace(queue=q, output=out, priority_evidence=None, max_requests=1, delay_ms=4500))
            body = json.loads((out / "items" / ("barcode-" + CODE + ".json")).read_text(encoding="utf-8"))
            self.assertEqual(data, body["response"])
            self.assertEqual(data["product"], body["product"])
            self.assertNotIn("nutriments", body["product"])
            self.assertEqual(1, call.call_count)

    def test_rate_limit_or_outage_stops_and_retains_error_receipt(self):
        for status in (403, 429, 503):
            with tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                args = argparse.Namespace(queue=queue(root), output=root / "out", priority_evidence=None, max_requests=10, delay_ms=4500)
                error = urllib.error.HTTPError("url", status, "blocked", {}, None)
                with patch.object(fetch.urllib.request, "urlopen", side_effect=error) as call:
                    fetch.run(args)
                self.assertEqual(1, call.call_count)
                summary = json.loads((args.output / "summary.json").read_text())
                self.assertEqual(1, summary["counts"]["STOPPED_ON_PROVIDER_OR_TRANSPORT_ERROR"])
                self.assertFalse(summary["fullQueueCompleted"])

    def test_too_fast_or_unbounded_batch_rejected(self):
        for delay, requests in ((4499, 1), (4500, 0), (4500, 101)):
            with self.assertRaises(ValueError):
                fetch.run(argparse.Namespace(delay_ms=delay, max_requests=requests))

    def extract(self, root, products, max_lines=0):
        archive = root / "source.jsonl.gz"
        with gzip.open(archive, "wt", encoding="utf-8") as stream:
            for product in products:
                stream.write(json.dumps(product) + "\n")
        args = argparse.Namespace(archive=archive, queue=[queue(root)], output=root / "extract", max_lines=max_lines)
        bulk.run(args)
        return args

    def test_bulk_complete_scan_pins_whole_bodies_without_freshness_claim(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            args = self.extract(root, [{"code": CODE, "nutriments": {"calcium_100g": .12}}, {"code": "123"}])
            rows, _ = fetch.options.load_bulk(args.output)
            self.assertEqual(1, len(rows))
            self.assertFalse(rows[0]["sourceFreshnessApproved"])
            self.assertIsNone(rows[0]["remoteSnapshotTime"])
            self.assertEqual(.12, rows[0]["product"]["nutriments"]["calcium_100g"])
            summary = json.loads((args.output / "summary.json").read_text())
            self.assertEqual(2, summary["dumpLinesRead"])
            self.assertEqual(fetch.options.r5.closure.sha(args.archive), summary["archiveSha256"])

    def test_nested_code_is_not_root_identity(self):
        with tempfile.TemporaryDirectory() as tmp:
            args = self.extract(Path(tmp), [{"code": "123", "nested": {"code": CODE}}])
            summary = json.loads((args.output / "summary.json").read_text())
            self.assertEqual(0, summary.get("matchedProducts", 0))
            self.assertEqual([CODE], summary["targetsNotFound"])
            self.assertEqual([], fetch.options.load_bulk(args.output)[0])

    def test_incomplete_scan_and_duplicate_gtin_are_never_accepted(self):
        for products, limit in (([{"code": CODE}, {"code": "123"}], 1), ([{"code": CODE}, {"code": CODE}], 0)):
            with tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                with self.assertRaises(ValueError):
                    self.extract(root, products, limit)
                self.assertFalse((root / "extract/summary.json").exists())
                self.assertTrue((root / "extract/products.jsonl.partial").exists())

    def test_tampered_body_or_completion_report_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            args = self.extract(Path(tmp), [{"code": CODE}])
            summary_path = args.output / "summary.json"
            summary = json.loads(summary_path.read_text())
            summary["fullScanComplete"] = False
            summary_path.write_text(json.dumps(summary))
            with self.assertRaisesRegex(ValueError, "Complete pinned"):
                fetch.options.load_bulk(args.output)
            summary["fullScanComplete"] = True
            summary_path.write_text(json.dumps(summary))
            (args.output / "products.jsonl").write_text('{}\n')
            with self.assertRaisesRegex(ValueError, "Complete pinned"):
                fetch.options.load_bulk(args.output)


if __name__ == "__main__":
    unittest.main()

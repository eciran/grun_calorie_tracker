import base64
import gzip
import hashlib
import importlib.util
import json
import unittest
import tempfile
from pathlib import Path

spec = importlib.util.spec_from_file_location("snapshot", Path(__file__).with_name("collect-r3-source-snapshot.py"))
snapshot = importlib.util.module_from_spec(spec)
spec.loader.exec_module(snapshot)


class TransportTests(unittest.TestCase):
    def test_release_reader_is_select_only_and_exact_bounded_ids(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/"decisions.jsonl"
            path.write_text('\n'.join(json.dumps({"id":str(i)}) for i in (1,2,3)),encoding="utf-8")
            sql=snapshot.release_sql(path)
            self.assertIn("READ ONLY",sql)
            self.assertIn("f.id IN (1,2,3)",sql)
            for forbidden in ("CREATE ","INSERT ","UPDATE ","DELETE ","DROP "):
                self.assertNotIn(forbidden,sql.upper())
            path.write_text('{"id":"1"}\n{"id":"1"}',encoding="utf-8")
            with self.assertRaises(ValueError):snapshot.release_sql(path)

    def fixture(self):
        rows = [{"kind": "start", "readonly": "on", "count": 3},
                *[{"kind": "row", "row": {"id": i, "name": "Süt"}} for i in range(3)],
                {"kind": "end", "count": 3}]
        body = "\n".join(json.dumps(r, ensure_ascii=False) for r in rows).encode()
        encoded = base64.b64encode(gzip.compress(body)).decode()
        chunks = [encoded[i:i + 40] for i in range(0, len(encoded), 40)]
        return [{"kind": "transport", "chunks": len(chunks), "sha256": hashlib.sha256(body).hexdigest()},
                *[{"kind": "chunk", "sequence": i + 1, "data": c} for i, c in enumerate(chunks)]]

    def test_ordered_transport_retains_unicode_and_exact_count(self):
        value = snapshot.decode_transport(list(reversed(self.fixture())))
        rows, _ = snapshot.snapshot_rows(value)
        self.assertEqual(3, len(rows))
        self.assertEqual("Süt", rows[0]["name"])

    def test_jsonb_field_order_does_not_hide_rows(self):
        frames = [{"time": "now", "count": 1, "readonly": "on", "kind": "start"},
                  {"row": {"values": {"products": 4}, "id": "inventory"}, "kind": "row"},
                  {"count": 1, "kind": "end"}]
        body = "\n".join(json.dumps(f) for f in frames).encode()
        packed = [{"kind": "transport", "chunks": 1, "sha256": hashlib.sha256(body).hexdigest()},
                  {"kind": "chunk", "sequence": 1, "data": base64.b64encode(gzip.compress(body)).decode()}]
        rows, _ = snapshot.snapshot_rows(snapshot.decode_transport(packed))
        self.assertEqual("inventory", rows[0]["id"])

    def test_lost_chunk_fails_closed(self):
        with self.assertRaises(ValueError):
            snapshot.decode_transport(self.fixture()[:-1])

    def test_duplicate_chunk_sequence_fails_closed(self):
        fixture = self.fixture()
        fixture[-1]["sequence"] = 1
        with self.assertRaises(ValueError):
            snapshot.decode_transport(fixture)

    def test_checksum_drift_fails_closed(self):
        fixture = self.fixture()
        fixture[0]["sha256"] = "0" * 64
        with self.assertRaises(ValueError):
            snapshot.decode_transport(fixture)

    def test_row_loss_even_after_transport_success_fails_closed(self):
        with self.assertRaises(ValueError):
            snapshot.snapshot_rows([{"kind": "start", "readonly": "on", "count": 2},
                {"kind": "row", "row": {"id": 1}}, {"kind": "end", "count": 2}])


if __name__ == "__main__":
    unittest.main()

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


SCRIPT = Path(__file__).with_name("preview-product-family-candidates.py")
SPEC = importlib.util.spec_from_file_location("preview_product_family_candidates", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class PreviewProductFamilyCandidatesTest(unittest.TestCase):
    def test_preview_reports_valid_and_held_without_apply(self):
        source = {
            "previewCount": 2,
            "payloads": [
                {"groupId": "PF-1", "reviewMode": "FULL_FAMILY", "request": {"members": [{}, {}]}},
                {"groupId": "PF-2", "reviewMode": "FULL_FAMILY", "request": {"members": [{}, {}]}},
            ],
        }
        responses = [
            (200, {"errors": [], "warnings": []}),
            (200, {"errors": ["Brand mismatch"], "warnings": []}),
        ]
        with tempfile.TemporaryDirectory(dir=Path.cwd() / "outputs") as directory:
            root = Path(directory)
            input_path = root / "input.json"
            output_path = root / "output.json"
            input_path.write_text(json.dumps(source), encoding="utf-8")
            with patch.object(MODULE, "post_preview", side_effect=responses):
                report = MODULE.preview(input_path, output_path, "https://example.test", "secret")

            self.assertEqual(2, report["candidatesRequested"])
            self.assertEqual(1, report["valid"])
            self.assertEqual(1, report["held"])
            self.assertEqual(0, report["catalogWrites"])
            self.assertFalse(report["applyCalled"])
            self.assertNotIn("secret", output_path.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()

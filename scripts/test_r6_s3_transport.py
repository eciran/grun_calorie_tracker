import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("s3", Path(__file__).with_name("run-r6-s3-category-batch.py"))
s3 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(s3)


class TransportTests(unittest.TestCase):
    def test_post_operation_query_is_marker_bound_and_read_only(self):
        spec = importlib.util.spec_from_file_location("integrity", Path(__file__).with_name("r6-post-operation-integrity.py"))
        integrity = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(integrity)
        sql = integrity.query("r6-approved-012345abcdef")
        self.assertIn("READ ONLY", sql)
        self.assertIn("NOT_AUTHENTICATED_API_OR_RANKING", sql)
        for forbidden in ("UPDATE ", "DELETE ", "INSERT ", "DROP "):
            self.assertNotIn(forbidden, sql.upper())
        with self.assertRaises(ValueError):
            integrity.query("'; SELECT *")

    def test_signed_query_escapes_are_not_printf_format_strings(self):
        sql = s3.download_script("https://owned.s3.eu-west-1.amazonaws.com/approved.sql.gz?credential=x%2Fy&token=z%3D", "a" * 64)
        self.assertIn("printf '%b' 'GET", sql)
        self.assertIn("credential=x%2Fy&token=z%3D", sql)
        for gate in ("-verify_return_error", "-verify_hostname", "-CApath", "^HTTP/1.1 200", "sha256sum -c", "timeout 60"):
            self.assertIn(gate, sql)
        self.assertNotIn("psql", sql)

    def test_bad_hosts_injection_and_invalid_digests_refused(self):
        for url in ("http://owned.s3.eu-west-1.amazonaws.com/a", "https://evil.example/a", "https://owned.s3.eu-west-1.amazonaws.com/a?x=';"):
            with self.assertRaises(ValueError):
                s3.download_script(url, "a" * 64)
        with self.assertRaises(ValueError):
            s3.download_script("https://owned.s3.eu-west-1.amazonaws.com/a", "x" * 64)


if __name__ == "__main__":
    unittest.main()

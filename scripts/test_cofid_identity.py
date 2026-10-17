"""R3 regression checks; no network or catalog writes."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("cofid_builder", Path(__file__).with_name("build-cofid-catalog-candidates.py"))
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)


class CofidIdentityTests(unittest.TestCase):
    def test_trace_is_not_a_measured_zero(self):
        self.assertEqual("", builder.number("Tr"))
        self.assertEqual("0", builder.number(0))

    def test_nonfinite_and_negative_values_are_unknown(self):
        for value in ("nan", "inf", "-inf", -1):
            with self.subTest(value=value):
                self.assertEqual("", builder.number(value))

    def test_distinct_formulations_keep_distinct_display_names(self):
        dry = builder.display_name("Wine, white, dry")[0]
        medium = builder.display_name("Wine, white, medium")[0]
        self.assertNotEqual(dry, medium)
        self.assertNotEqual(builder.display_name("Lemonade")[0], builder.display_name("Lemonade, homemade")[0])


if __name__ == "__main__":
    unittest.main()

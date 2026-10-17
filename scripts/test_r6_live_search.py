import importlib.util
import unittest
from pathlib import Path
from urllib.request import Request

spec = importlib.util.spec_from_file_location("acceptance", Path(__file__).with_name("verify-r6-live-search.py"))
acceptance = importlib.util.module_from_spec(spec)
spec.loader.exec_module(acceptance)


class AcceptanceTests(unittest.TestCase):
    def test_exact_category_type_and_direct_child_membership(self):
        taxonomy = {1: {"id": 1, "parent_id": None}, 2: {"id": 2, "parent_id": 1},
                    3: {"id": 3, "parent_id": 2}, 4: {"id": 4, "parent_id": None}}
        rows = [{"food_item_id": i, "category_id": c} for i, c in ((10, 1), (11, 2), (12, 3), (13, 4), (14, 2))]
        foods = {i: {"catalog_type": "GENERIC_INGREDIENT" if i != 14 else "LOCAL_DISH"} for i in range(10, 15)}
        self.assertEqual({10, 11}, acceptance.expected_ids(rows, foods, taxonomy, 1, "GENERIC_INGREDIENT"))
        self.assertEqual({11, 12}, acceptance.expected_ids(rows, foods, taxonomy, 2, "GENERIC_INGREDIENT"))
        self.assertEqual({14}, acceptance.expected_ids(rows, foods, taxonomy, 1, "LOCAL_DISH"))

    def test_credential_bearing_external_or_non_https_redirect_refused(self):
        handler = acceptance.SameOriginRedirect()
        request = Request(acceptance.ORIGIN, headers={"Authorization": "Bearer dummy-test-only"})
        for target in ("https://evil.example/", "http://api-staging.gruncalorietracker.com/", "https://api-staging.gruncalorietracker.com.evil.example/"):
            with self.assertRaises(ValueError):
                handler.redirect_request(request, None, 302, "Found", {}, target)

    def test_only_fixed_https_api_origin_and_21_nutrition_fields(self):
        self.assertEqual("https://api-staging.gruncalorietracker.com", acceptance.ORIGIN)
        self.assertEqual(21, len(acceptance.NUTRIENTS))
        self.assertEqual("vitamin_b12", acceptance.NUTRIENTS["vitaminB12"])


if __name__ == "__main__":
    unittest.main()

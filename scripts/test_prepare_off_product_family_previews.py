import importlib.util
import unittest
from pathlib import Path


SPEC = importlib.util.spec_from_file_location(
    "previews", Path(__file__).with_name("prepare-off-product-family-previews.py")
)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class OffProductFamilyPreviewTest(unittest.TestCase):
    def test_display_name_removes_package_without_losing_variant(self):
        self.assertEqual("Dairy Milk Pots Of Joy", MODULE.display_name("Dairy Milk Pots Of Joy - 120 g (2 x 60 G)"))

    def test_display_name_removes_repeated_separator_and_trailing_brand(self):
        self.assertEqual(
            "Gelée D'Aloe Vera",
            MODULE.display_name("Gelée D'Aloe Vera - 473 ML - Lily of the Desert", "Lily of the Desert"),
        )

    def test_family_key_removes_leading_brand(self):
        self.assertEqual("cadbury:dairy-milk", MODULE.family_key("Cadbury", "Cadbury Dairy Milk"))

    def test_duplicate_family_keys_are_held_for_collision_review(self):
        payloads = [
            {"groupId": "one", "request": {"familyKey": "brand:item"}},
            {"groupId": "two", "request": {"familyKey": "brand:item"}},
            {"groupId": "three", "request": {"familyKey": "brand:other"}},
        ]
        ready, collisions = MODULE.split_family_key_collisions(payloads)
        self.assertEqual(["three"], [payload["groupId"] for payload in ready])
        self.assertEqual(["one", "two"], [payload["groupId"] for payload in collisions])

    def test_compatible_family_key_collisions_are_consolidated(self):
        def payload(group, product, quantity, name, calories):
            return {
                "groupId": group, "previewPath": "/preview", "applyPath": "/apply",
                "request": {"familyKey": "brand:item", "displayName": name,
                            "members": [{"productId": product, "packageQuantity": quantity,
                                         "packageUnit": "GRAM", "packageLabel": f"{quantity} g",
                                         "defaultVariant": True}]},
                "source": {"snapshot": "off.csv.gz", "lastModifiedValues": ["1"]},
                "_coreProfiles": [("PER_100G", calories, "2", "10", "1")],
            }
        ready, held = MODULE.consolidate_family_key_collisions([
            payload("one", 1, 100, "Item", "100"),
            payload("two", 2, 200, "Brand Item", "105"),
        ])
        self.assertEqual(0, len(held))
        self.assertEqual(1, len(ready))
        self.assertEqual([1, 2], [member["productId"] for member in ready[0]["request"]["members"]])
        self.assertEqual("Brand Item", ready[0]["request"]["displayName"])
        self.assertEqual(1, sum(member["defaultVariant"] for member in ready[0]["request"]["members"]))

    def test_incompatible_collision_remains_held(self):
        base = {
            "previewPath": "/preview", "applyPath": "/apply",
            "request": {"familyKey": "brand:item", "displayName": "Item",
                        "members": [{"productId": 1, "packageQuantity": 100,
                                     "packageUnit": "GRAM", "packageLabel": "100 g",
                                     "defaultVariant": True}]},
            "source": {"snapshot": "off.csv.gz", "lastModifiedValues": ["1"]},
        }
        left = {**base, "groupId": "one", "_coreProfiles": [("PER_100G", "100", "2", "10", "1")]}
        right = {**base, "groupId": "two", "request": {**base["request"], "members": [
            {"productId": 2, "packageQuantity": 200, "packageUnit": "GRAM",
             "packageLabel": "200 g", "defaultVariant": True}]},
                 "_coreProfiles": [("PER_100G", "150", "2", "10", "1")]}
        ready, held = MODULE.consolidate_family_key_collisions([left, right])
        self.assertEqual(0, len(ready))
        self.assertEqual(2, len(held))

    def test_same_package_size_collision_remains_held(self):
        def item(group, product):
            return {
                "groupId": group, "previewPath": "/preview", "applyPath": "/apply",
                "request": {"familyKey": "brand:item", "displayName": "Item", "members": [{
                    "productId": product, "packageQuantity": 300, "packageUnit": "GRAM",
                    "packageLabel": "300 g", "defaultVariant": True}]},
                "source": {"snapshot": "off.csv.gz", "lastModifiedValues": ["1"]},
                "_coreProfiles": [("PER_100G", "100", "2", "10", "1")],
            }
        ready, held = MODULE.consolidate_family_key_collisions([item("one", 1), item("two", 2)])
        self.assertEqual(0, len(ready))
        self.assertEqual(2, len(held))


if __name__ == "__main__":
    unittest.main()

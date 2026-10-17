import copy
import importlib.util
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

spec = importlib.util.spec_from_file_location("r7_presentation", Path(__file__).with_name("prepare-r7-source-presentation.py"))
review = importlib.util.module_from_spec(spec)
spec.loader.exec_module(review)


class SourcePresentationTests(unittest.TestCase):
    def row(self, identifier="181140"):
        food = {k: None for k in review.gate.FIELDS}
        food.update(id=int(identifier), source_key="USDA_FOODDATA:fdc:" + review.SCOPE[identifier],
                    data_source="USDA_FOODDATA", publication_status="PUBLISHED", is_custom=False,
                    catalog_type="BRANDED_PRODUCT", source_data_type=None, preparation_state="BOILED",
                    nutrition_reference_unit="PER_100G", name="Old Broccoli", display_name="Old Broccoli",
                    short_display_name="Old Broccoli", calories=28.0, protein=3.0, fat=0.1, carbs=5.0)
        return {"id": identifier, "food": food, "assignments": [], "name_review": {
            "aliases": [], "localizations": [{"id": 1, "language": "EN", "active": True,
                "display_name": "Old Broccoli", "short_display_name": "Old Broccoli"},
                {"id": 2, "language": "TR", "active": True,
                "display_name": "Brokoli", "short_display_name": "Brokoli"}],
            "options": [{"id": 10, "food_item_id": int(identifier), "gram_weight": 100, "ml_volume": None}],
            "option_localizations": []}}

    def body(self, identifier="181140"):
        key = "USDA_FOODDATA:fdc:" + review.SCOPE[identifier]
        return {"fdcId": int(review.SCOPE[identifier]), "description": review.registry()[key]["sourceDescription"],
                "dataType": "SR Legacy", "foodCategory": {"id": 11, "description": "Vegetables and Vegetable Products"},
                "foodNutrients": [{"nutrient": {"id": nid, "unitName": unit}, "amount": value}
                    for nid, unit, value in [(1008, "kcal", 28), (1003, "g", 3), (1004, "g", .1), (1005, "g", 5)]]}

    def test_classification_and_locales_do_not_change_source_nutrition_or_portions(self):
        row = self.row()
        before = copy.deepcopy(row)
        plan = review.review_record(row, self.body(), review.registry())
        self.assertEqual(before, row)
        self.assertEqual("GENERIC_INGREDIENT", plan["foodChanges"]["catalog_type"])
        self.assertEqual("SR Legacy", plan["foodChanges"]["source_data_type"])
        self.assertFalse(set(plan["foodChanges"]) & set(review.gate.FIELDS))
        self.assertNotIn("source_key", plan["foodChanges"])
        self.assertNotIn("canonical_food_key", plan["foodChanges"])
        self.assertIn("fdc_169969", plan["identityProposal"]["canonical_food_key"])
        self.assertFalse(plan["liveApplyAllowed"])
        self.assertEqual(21, len(plan["numericComparison"]))
        self.assertEqual("vegetables", plan["categoryProposal"]["targetSlug"])
        self.assertIn("ACTIVE_TAXONOMY_PRIMARY_CATEGORY_REBASE_REQUIRED", plan["reviewHolds"])

    def test_wrong_source_id_subtype_or_description_is_rejected(self):
        for key, value in (("fdcId", 170380), ("dataType", "Branded"), ("description", "Broccoli, raw")):
            with self.subTest(key=key):
                body = self.body()
                body[key] = value
                with self.assertRaises(ValueError): review.review_record(self.row(), body, review.registry())

    def test_existing_brand_or_barcode_cannot_be_erased_as_generic(self):
        for key in ("brand", "brand_id", "canonical_brand_id", "barcode", "normalized_barcode"):
            with self.subTest(key=key):
                row = self.row()
                row["food"][key] = "existing-claim"
                with self.assertRaises(ValueError): review.review_record(row, self.body(), review.registry())

    def test_assigned_identity_or_incomplete_numeric_snapshot_needs_separate_review(self):
        row = self.row()
        row["food"]["canonical_food_key"] = "C2:existing"
        with self.assertRaises(ValueError): review.review_record(row, self.body(), review.registry())
        row = self.row()
        del row["food"]["sodium"]
        with self.assertRaises(ValueError): review.review_record(row, self.body(), review.registry())

    def test_broken_fragments_retired_but_valid_complete_alias_kept(self):
        row = self.row()
        row["name_review"]["aliases"] = [{"id": i, "food_item_id": 181140, "active": True, "alias": text}
            for i, text in enumerate(["boiled broccoli (frozen", "chopped)", "broccoli (frozen, chopped)"], 20)]
        plan = review.review_record(row, self.body(), review.registry())
        self.assertEqual([20, 21], [a["before"]["id"] for a in plan["aliasActions"]])
        self.assertEqual("broccoli (frozen, chopped)", row["name_review"]["aliases"][2]["alias"])

    def test_off_exact_brand_source_translation_without_ml_or_parent_rewrite(self):
        row = self.row()
        row["id"] = "49340"
        row["food"].update(id=49340, source_key="barcode:" + review.OFF_CODE, barcode=review.OFF_CODE,
            normalized_barcode=review.OFF_CODE, data_source="ADMIN_IMPORT", brand="Activia", market_region="TR")
        row["name_review"]["aliases"] = []
        row["name_review"]["options"] = []
        body = {"code": review.OFF_CODE, "brands": "Activia",
            "product_name_en": "Probiotic Shot, Banana and Strawberry",
            "product_name_tr": "Activiashot Shot Çilek & Muz Probiyotik İçecek 80 ml",
            "nutrition_data_per": "100g", "nutriments": {}, "categories_tags": ["en:beverages"]}
        plan = review.review_record(row, body, review.registry())
        self.assertEqual({}, plan["foodChanges"])
        self.assertTrue(any("Çilek ve Muz" in a["changes"]["display_name"] for a in plan["localizationActions"]))
        self.assertFalse(plan["mlConversionAllowed"])
        for field, value in (("code", "other-code"), ("brands", "Other Brand"), ("product_name_tr", "Different flavor")):
            changed = {**body, field: value}
            with self.subTest(field=field), self.assertRaises(ValueError):
                review.review_record(row, changed, review.registry())

    def test_receipt_freshness_and_balanced_nested_aliases(self):
        now = datetime.now(timezone.utc)
        review.fresh(now.isoformat(), now)
        for stamp in ((now - timedelta(days=2)).isoformat(), (now + timedelta(minutes=1)).isoformat(), "2026-10-13T12:00:00"):
            with self.assertRaises(ValueError): review.fresh(stamp, now)
        self.assertFalse(review.unbalanced("Broccoli (Frozen (Chopped), Drained)"))
        self.assertTrue(review.unbalanced("Broccoli ) Frozen ("))


if __name__ == "__main__":
    unittest.main()

import copy
import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("r5", Path(__file__).with_name("build-r5-serving-review.py"))
r5 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(r5)


def row(**changes):
    return {"id": "1", "source_key": "OPEN_FOOD_FACTS:5449000000996", "data_source": "OPEN_FOOD_FACTS",
            "name": "Milk", "nutrition_reference_unit": "PER_100G", "barcode": "5449000000996",
            "normalized_barcode": "5449000000996", "categories": ["en:beverages"], "options": [],
            "option_localizations": [], "evidence": [], "serving_size_grams": "",
            "calories": "42.4", "protein": "0", "fat": "0", "carbs": "10.6", **changes}


class ServingReviewTests(unittest.TestCase):
    def test_name_does_not_invent_liquid_state(self):
        result = r5.triage(row(name="Rice cooked with water", categories=["en:rice-dishes"]))
        self.assertEqual("NAMED_SERVING_SOURCE_REQUIRED", result["queue"])
        self.assertFalse(result["applyAllowed"])

    def test_beverage_category_does_not_invent_density(self):
        result = r5.triage(row())
        self.assertEqual("MASS_VOLUME_SOURCE_REQUIRED", result["queue"])
        self.assertIsNone(result["externalProof"])

    def test_existing_ml_option_without_grams_is_a_bridge_queue_not_proof_of_density(self):
        option = {"id": 1, "quality_status": "VERIFIED", "user_visible": True, "quantity": 1.0, "ml_volume": 250, "gram_weight": None}
        result = r5.triage(row(data_source="USDA_FOODDATA", categories=[], options=[option]))
        self.assertEqual("MASS_VOLUME_SOURCE_REQUIRED", result["queue"])
        self.assertIn("PROVIDER_MASS_BRIDGE", result["reason"])
        self.assertFalse(result["applyAllowed"])

    def test_powder_conflict_wins_over_volume_basis(self):
        result = r5.triage(row(categories=["en:milks", "en:milk-powders"], nutrition_reference_unit="PER_100ML"))
        self.assertEqual("FORM_OR_IDENTITY_REVIEW", result["queue"])

    def test_native_volume_is_available_not_recognized_as_source_certification(self):
        result = r5.triage(row(nutrition_reference_unit="PER_100ML"))
        self.assertEqual("NATIVE_VOLUME_REVIEW", result["queue"])
        self.assertEqual("ML_ALREADY_NATIVE_SOURCE_CERTIFICATION_SEPARATE", result["reason"])

    def test_invalid_secondary_conversion_and_quantity_are_unusable(self):
        option = {"quality_status": "VERIFIED", "user_visible": True, "quantity": 1.0, "gram_weight": 100.0, "ml_volume": None}
        self.assertTrue(r5.option_usable(row(), option))
        for value in (float("nan"), float("inf"), 0, -1, True):
            self.assertFalse(r5.option_usable(row(), {**option, "ml_volume": value}))
            self.assertFalse(r5.option_usable(row(), {**option, "quantity": value}))

    def test_numeric_and_gtin_guards(self):
        self.assertTrue(r5.valid_gtin("5449000000996"))
        self.assertFalse(r5.valid_gtin("5449000000997"))
        for value in (True, "NaN", "Infinity", -1, None):
            self.assertIsNone(r5.number(value))

    def test_missing_basis_stays_in_review(self):
        self.assertEqual("FORM_OR_IDENTITY_REVIEW", r5.triage(row(nutrition_reference_unit=""))["queue"])

    def test_cached_off_candidate_never_grants_all_nutrient_or_live_approval(self):
        evidence = {"status": "FETCHED", "barcode": "5449000000996", "checkedAt": "2026-10-06T23:14:02Z",
                    "product": {"code": "5449000000996", "categories_tags": ["en:beverages"], "nutrition_data_per": "100ml",
                        "nutriments": {"energy-kcal_100g": 42.4, "energy-kcal_unit": "kcal", "proteins_100g": 0,
                            "proteins_unit": "g", "fat_100g": 0, "fat_unit": "g", "carbohydrates_100g": 10.6, "carbohydrates_unit": "g"}}}
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "barcode-5449000000996.json"
            path.write_text(json.dumps(evidence), encoding="utf-8")
            reason, proof = r5.off_candidate(row(), path, "2026-10-12T00:00:00Z")
            self.assertEqual("EXTERNAL_NATIVE_VOLUME_CANDIDATE", reason)
            self.assertFalse(proof["allNutrientUnitCorrectionApproved"])
            self.assertFalse(proof["applyAllowed"])
            evidence["product"]["code"] = "5449000000997"
            path.write_text(json.dumps(evidence), encoding="utf-8")
            self.assertEqual("OFF_IDENTITY_CONFLICT", r5.off_candidate(row(), path, "2026-10-12T00:00:00Z")[0])

    def test_snapshot_hash_changes_with_localization_or_default(self):
        value = row()
        changed = row(option_localizations=[{"label": "Başka", "active": True}])
        self.assertNotEqual(r5.fingerprint(value), r5.fingerprint(changed))

    def test_six_source_portions_use_exact_weights_and_never_fake_ml(self):
        registry = json.loads((ROOT / "src/main/resources/catalog/source-name-decisions-r4-v1.json").read_text(encoding="utf-8"))
        entries = [e for e in registry["entries"] if e["servingReviewRequired"]]
        foods, rows, decisions = {}, [], []
        for index, entry in enumerate(entries):
            key = r5.closure.review.fdc_id(entry["sourceKey"])
            source = {"fdcId": int(key), "foodNutrients": [
                {"nutrient": {"id": 1008, "unitName": "kcal"}, "amount": 100},
                *[{"nutrient": {"id": i, "unitName": "g"}, "amount": 1} for i in (1003, 1004, 1005)]],
                "foodPortions": [{"id": index + 1, "amount": 0.25, "gramWeight": 48.0, "modifier": "cup"}]}
            foods[key] = source
            rows.append(row(id=str(index), source_key=entry["sourceKey"], data_source="USDA_FOODDATA", calories="100", protein="1", fat="1", carbs="1"))
            decisions.append({"id": str(index), "sourceKey": entry["sourceKey"], "sourceFingerprint": r5.closure.review.fingerprint(source),
                "servingReviewRequired": True, "changes": {"preparation_state": entry["targetState"]}, "localizations": {}})
        manifest = {"decisions": decisions}
        result = r5.six_decisions(rows, foods, manifest)
        self.assertEqual(6, len(result))
        for d in result:
            p = d["sourcePortions"][0]
            self.assertEqual(48, p["gramWeight"])
            self.assertEqual(0.25, p["quantity"])
            self.assertIsNone(p["mlVolume"])
            self.assertFalse(d["applyAllowed"])
            self.assertFalse(p["defaultOption"])
        rows[0]["calories"] = "200"
        with self.assertRaisesRegex(ValueError, "composition drift"):
            r5.six_decisions(rows, foods, manifest)

    def test_missing_priority_identity_fails_closed(self):
        with self.assertRaises(ValueError):
            r5.six_decisions([], {}, {"decisions": []})

    def test_direct_ml_source_pair_is_profile_specific_and_never_inferred_from_cup(self):
        option = {"id": 20, "quantity": 1, "ml_volume": 250, "gram_weight": None, "user_visible": True, "quality_status": "VERIFIED"}
        value = row(source_key="USDA_FOODDATA:fdc:174158", data_source="USDA_FOODDATA", calories="0", protein="0", fat="0", carbs="0", options=[option])
        food = {"fdcId": 174158, "foodPortions": [{"id": 93847, "amount": 1, "gramWeight": 1, "modifier": "ml"}],
                "foodNutrients": [{"nutrient": {"id": 1008, "unitName": "kcal"}, "amount": 0},
                    *[{"nutrient": {"id": i, "unitName": "g"}, "amount": 0} for i in (1003, 1004, 1005)]]}
        plans = r5.direct_source_pair_plans(value, food)
        self.assertEqual(250, plans[0]["gramWeight"])
        self.assertFalse(plans[0]["applyAllowed"])
        changed = copy.deepcopy(food)
        changed["foodPortions"][0]["modifier"] = "cup"
        self.assertEqual([], r5.direct_source_pair_plans(value, changed))
        changed = copy.deepcopy(food)
        changed["fdcId"] = 174159
        self.assertEqual([], r5.direct_source_pair_plans(value, changed))
        self.assertEqual([], r5.direct_source_pair_plans(row(**{**value, "calories": "100"}), food))

    def test_legacy_sql_generator_is_disabled_before_any_file_access(self):
        result = subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
                                str(ROOT / "scripts/build-liquid-ml-apply-package.ps1"), "-ReconciliationPath", "missing.csv"],
                               capture_output=True, text=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Legacy ml SQL generation is disabled", result.stderr)


if __name__ == "__main__":
    unittest.main()

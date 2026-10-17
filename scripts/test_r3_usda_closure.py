import importlib.util
import json
import math
import tempfile
import unittest
from datetime import datetime, timezone
from unittest.mock import Mock
from pathlib import Path


def module(filename, name):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


closure = module("close-r3-usda-review.py", "closure")
fetch = module("fetch-r3-usda-evidence.py", "fetch")
collector = module("collect-r3-source-snapshot.py", "collector")


class SourceClosureTests(unittest.TestCase):
    def test_resumed_source_batch_cannot_get_a_new_freshness_timestamp(self):
        now = datetime(2026, 10, 13, tzinfo=timezone.utc)
        path = Mock()
        path.read_bytes.return_value = b"source body"
        digest = fetch.hashlib.sha256(b"source body").hexdigest()
        path.with_suffix.return_value.exists.return_value = True
        for stamp in ("2026-10-11T23:00:00Z", "2026-10-13T01:00:00Z", "2026-10-12T23:00:00"):
            path.with_suffix.return_value.read_text.return_value = json.dumps({"sha256": digest, "ids": ["123"], "fetchedAt": stamp})
            with self.assertRaises(ValueError):
                fetch.cached_batch_receipt(path, ["123"], now)
        stamp = "2026-10-12T23:00:00Z"
        path.with_suffix.return_value.read_text.return_value = json.dumps({"sha256": digest, "ids": ["123"], "fetchedAt": stamp})
        self.assertEqual(stamp, fetch.cached_batch_receipt(path, ["123"], now)["fetchedAt"])
        with self.assertRaises(ValueError):
            fetch.cached_batch_receipt(path, ["456"], now)
        path.read_bytes.return_value = b"changed source body"
        with self.assertRaises(ValueError):
            fetch.cached_batch_receipt(path, ["123"], now)
        path.with_suffix.return_value.exists.return_value = False
        with self.assertRaises(ValueError):
            fetch.cached_batch_receipt(path, ["123"], now)

    def row(self, **extra):
        return {"id": "1", "source_key": "USDA_FOODDATA:fdc:123", "catalog_type": "BRANDED_PRODUCT",
                "data_source": "USDA_FOODDATA", "name": "Raw Banana", "preparation_state": "RAW",
                "nutrition_reference_unit": "PER_100G", "calories": "89", "protein": "1.09", "fat": "0.33", "carbs": "22.84", **extra}

    def food(self, **extra):
        return {"fdcId": 123, "dataType": "SR Legacy", "description": "Bananas, raw",
                "foodNutrients": [{"nutrient": {"id": nutrient, "unitName": unit}, "amount": value}
                    for nutrient, unit, value in [(1008, "kcal", 89), (1003, "g", 1.09), (1004, "g", 0.33), (1005, "g", 22.84)]], **extra}

    def test_exact_source_corrects_type_not_values_or_merge(self):
        result = closure.decide(self.row(), self.food(), [self.row()])
        self.assertEqual("GENERIC_INGREDIENT", result["proposedType"])
        self.assertEqual("KEEP_SEPARATE_SOURCE_PROFILE", result["mergeDecision"])
        self.assertEqual([], result["findings"])
        self.assertFalse(result["applyAllowed"])
        self.assertFalse(result["nutritionWriteAllowed"])
        self.assertFalse(result["deleteAllowed"])

    def test_equal_names_macros_different_fdc_ids_remain_separate(self):
        target = self.row(id="2", source_key="USDA_FOODDATA:fdc:456")
        result = closure.decide(self.row(), self.food(), [self.row(), target])
        self.assertEqual([], result["sameSourceTargets"])
        self.assertEqual("KEEP_SEPARATE_SOURCE_PROFILE", result["mergeDecision"])

    def test_same_fdc_different_prefix_requires_reference_review(self):
        target = self.row(id="2", source_key="USDA:fdc:123")
        result = closure.decide(self.row(), self.food(), [self.row(), target])
        self.assertEqual("SAME_SOURCE_REQUIRES_REFERENCE_REVIEW", result["mergeDecision"])
        self.assertEqual("2", result["sameSourceTargets"][0]["id"])
        self.assertFalse(result["deleteAllowed"])

    def test_wrong_source_subtype_or_gtin_fails_closed(self):
        for row, food in [(self.row(), self.food(fdcId=456)), (self.row(), self.food(dataType="Branded")),
                          (self.row(barcode="1234567890123"), self.food()),
                          (self.row(source_data_type="Foundation"), self.food())]:
            with self.assertRaises(ValueError):
                closure.decide(row, food, [row])

    def test_raw_and_cooked_not_interchangeable(self):
        result = closure.decide(self.row(preparation_state="BOILED"), self.food(), [self.row()])
        self.assertIn("PREPARATION_SOURCE_REVIEW", result["findings"])

    def test_added_solution_not_marination(self):
        result = closure.decide(self.row(name="Roasted Chicken Marinated", preparation_state="ROASTED"),
            self.food(description="Chicken, with added solution, cooked, roasted"), [self.row()])
        self.assertIn("ADDED_SOLUTION_IS_NOT_MARINADE", result["findings"])

    def test_alternative_source_states_retained_not_collapsed(self):
        self.assertEqual(["BAKED", "BOILED"], closure.source_states("Squash, cooked, boiled, drained, or baked"))
        self.assertEqual([], closure.source_states("Sunflower seed butter"))

    def test_missing_calories_not_fabricated_from_macros(self):
        food = self.food()
        food["foodNutrients"] = food["foodNutrients"][1:]
        self.assertIsNone(closure.source_core(food)["calories"])

    def test_nonfinite_negative_or_wrong_unit_is_not_evidence(self):
        for amount, unit in [(math.nan, "kcal"), (-1, "kcal"), (89, "kJ"), (True, "kcal")]:
            food = self.food()
            food["foodNutrients"][0]["amount"] = amount
            food["foodNutrients"][0]["nutrient"]["unitName"] = unit
            self.assertIsNone(closure.source_core(food)["calories"])

    def test_volume_basis_not_compared_with_mass(self):
        result = closure.decide(self.row(nutrition_reference_unit="PER_100ML"), self.food(), [self.row()])
        self.assertIn("SOURCE_MASS_BASIS_REQUIRES_BRIDGE", result["findings"])
        self.assertEqual({}, result["coreDifferences"])

    def test_numeric_diff_goes_to_review_not_overwrite(self):
        result = closure.decide(self.row(calories="100"), self.food(), [self.row()])
        self.assertIn("SOURCE_CORE_DIFFERENCE_REVIEW", result["findings"])
        self.assertFalse(result["nutritionWriteAllowed"])

    def test_source_concept_does_not_turn_light_meat_into_breast(self):
        self.assertEqual("chicken_light_meat", closure.source_concept("Chicken, light meat, meat only, cooked, roasted"))
        self.assertEqual("chicken_breast", closure.source_concept("Chicken, breast, cooked, roasted"))

    def test_butter_seed_and_edible_part_are_separate_concepts(self):
        self.assertEqual("cashew_butter", closure.source_concept("Nuts, cashew butter, plain"))
        self.assertEqual("cashew", closure.source_concept("Nuts, cashew nuts, raw"))
        self.assertEqual("potato_skin", closure.source_concept("Potatoes, boiled, cooked in skin, skin, without salt"))
        self.assertEqual("potato", closure.source_concept("Potatoes, boiled, cooked in skin, flesh, without salt"))

    def test_dry_parboiled_rice_is_not_ready_cooked(self):
        result = closure.decide(self.row(source_key="USDA_FOODDATA:fdc:168876", preparation_state="BOILED"),
            self.food(fdcId=168876, description="Rice, brown, parboiled, dry, UNCLE BEN'S"), [self.row()])
        self.assertIn("DRY_SOURCE_PRESENTED_AS_READY_COOKED", result["findings"])
        self.assertEqual("brown_rice", result["proposedConceptKey"])
        self.assertFalse(result["portionWriteAllowed"])

    def test_unknown_concept_remains_unmapped_not_name_slug(self):
        self.assertIsNone(closure.source_concept("Mystery food, raw"))

    def test_request_coverage_rejects_missing_duplicate_unexpected_ids(self):
        for foods in [[], [self.food(), self.food()], [self.food(fdcId=456)]]:
            with self.assertRaises(ValueError):
                fetch.validate_batch(["123"], foods)
        self.assertEqual(1, len(fetch.validate_batch(["123"], [self.food()])))

    def test_exact_evidence_integrity_and_tamper_gate(self):
        with tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parents[1] / "outputs") as directory:
            root = Path(directory)
            catalog = root / "catalog.csv"
            catalog.write_text("id,source_key,data_source\n1,USDA_FOODDATA:fdc:123,USDA_FOODDATA\n", encoding="utf-8")
            proof = root / "proof.json"
            proof.write_text(json.dumps({"origin": "AWS_READ_ONLY_SNAPSHOT", "transactionReadOnly": True,
                "exitCode": 0, "rows": 1, "sha256": closure.sha(catalog)}), encoding="utf-8")
            batch = root / "batch.json"
            batch.write_text(json.dumps([self.food()]), encoding="utf-8")
            evidence = root / "evidence.json"
            body = {"version": "R3_EXACT_FDC_EVIDENCE_V1", "source": "https://api.nal.usda.gov/fdc/v1/foods",
                    "catalogSha256": closure.sha(catalog), "matchedCount": 1, "requestedIds": ["123"],
                    "foods": [self.food()], "batchFiles": [{"file": "batch.json", "sha256": closure.sha(batch), "ids": ["123"]}]}
            evidence.write_text(json.dumps(body), encoding="utf-8")
            self.assertEqual(1, len(closure.verify_inputs(catalog, proof, evidence)[2]))
            body["foods"][0]["description"] = "Different description"
            evidence.write_text(json.dumps(body), encoding="utf-8")
            with self.assertRaises(ValueError):
                closure.verify_inputs(catalog, proof, evidence)

    def test_complete_readonly_snapshot_count_gate(self):
        records = [{"kind": "start", "readonly": "on", "count": 1}, {"kind": "row", "row": {"id": 1}}, {"kind": "end", "count": 1}]
        self.assertEqual(1, len(collector.snapshot_rows(records)[0]))

    def test_truncated_readonly_snapshot_is_not_accepted(self):
        with self.assertRaises(ValueError):
            collector.snapshot_rows([{"kind": "start", "readonly": "on", "count": 1}, {"kind": "row", "row": {"id": 1}}])

    def test_duplicate_rows_and_non_readonly_transactions_rejected(self):
        for readonly in ("on", "off"):
            records = [{"kind": "start", "readonly": readonly, "count": 2},
                       {"kind": "row", "row": {"id": 1}}, {"kind": "row", "row": {"id": 1}}, {"kind": "end", "count": 2}]
            with self.assertRaises(ValueError):
                collector.snapshot_rows(records)

    def test_different_header_footer_count_rejected(self):
        with self.assertRaises(ValueError):
            collector.snapshot_rows([{"kind": "start", "readonly": "on", "count": 1},
                {"kind": "row", "row": {"id": 1}}, {"kind": "end", "count": 2}])


if __name__ == "__main__":
    unittest.main()

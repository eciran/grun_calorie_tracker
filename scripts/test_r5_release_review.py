import copy
import importlib.util
import json
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path

spec = importlib.util.spec_from_file_location("release_review", Path(__file__).with_name("close-r5-release-review.py"))
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)
NOW = datetime(2026, 10, 12, 11, tzinfo=timezone.utc)


def fixture():
    body = {"code": "5449000000996", "categories_tags": ["en:beverages"], "nutrition_data_per": "100ml",
            "nutriments": {"energy-kcal_100g": 42.4, "proteins_100g": 0, "fat_100g": 0, "carbohydrates_100g": 10.6,
                           "sodium_100g": .039, "calcium_100g": .1935}}
    food = {field: None for field in gate.FIELDS}
    food.update(id=1, source_key="barcode:5449000000996", data_source="OPEN_FOOD_FACTS", is_custom=False,
                publication_status="PUBLISHED", nutrition_reference_unit="PER_100G", nutrition_basis="SOURCE_REPORTED",
                barcode="5449000000996", normalized_barcode="5449000000996", calories=42.4, protein=0, fat=0, carbs=10.6,
                sodium=.039, calcium=.194, verification_status="RAW_IMPORTED")
    decision = {"id": "1", "sourceKey": food["source_key"], "sourceFingerprint": gate.source.r5.fingerprint(body),
                "decision": "SOURCE_BASIS_CORRECTION_REQUIRES_FULL_BEFORE_IMAGE", "options": []}
    current = {"id": "1", "food": food, "options": [], "option_localizations": [], "categories": [], "markets": [],
               "evidence": [], "references": {"recipes": 0, "templates": 0, "plans": 0}}
    return decision, current, body


def usda_fixture():
    d, c, _ = fixture()
    body = {"fdcId": 123, "dataType": "SR Legacy", "description": "Quinoa, cooked",
            "foodCategory": {"description": "Cereal Grains and Pasta"},
            "foodNutrients": [{"nutrient": {"id": i, "unitName": u}, "amount": a}
                             for i, u, a in ((1008, "kcal", 42.4), (1003, "g", 0), (1004, "g", 0), (1005, "g", 10.6))],
            "foodPortions": [{"id": 99, "amount": 1, "gramWeight": 185,
                              "measureUnit": {"name": "cup"}}]}
    c["food"].update(source_key="USDA_FOODDATA:fdc:123", data_source="USDA_FOODDATA", name=body["description"],
                     catalog_type="GENERIC_INGREDIENT", preparation_state="COOKED", reviewed_by="prior-admin",
                     verification_status="VERIFIED", sodium=None, calcium=None, barcode=None, normalized_barcode=None)
    option, error = gate.source.source_portion(body["foodPortions"][0])
    assert error is None
    d.update(sourceKey=c["food"]["source_key"], sourceFingerprint=gate.source.r5.fingerprint(body), options=[option])
    return d, c, body


class ReleaseReviewTests(unittest.TestCase):
    def test_matching_two_zero_forms_does_not_choose_or_convert_a_unit(self):
        d, c, b = usda_fixture()
        c['food']['vitamin_a'] = 0
        b['foodNutrients'] += [{'nutrient': {'id': nid, 'unitName': unit}, 'amount': 0} for nid, unit in ((1104, 'IU'), (1106, 'ug'))]
        variants = gate.typed_usda_variants(c['food'], b)['vitamin_a']
        self.assertEqual([1104, 1106], variants['matchingSourceIds'])
        self.assertIsNone(variants['selectedCandidate'])
        self.assertFalse(variants['conversionAllowed'])
        self.assertFalse(variants['legacyUnitAttested'])

    def test_bad_alternative_cannot_hide_behind_a_matching_old_source_field(self):
        for defect in ('duplicate', 'unit', 'invalid'):
            d, c, b = usda_fixture()
            c['food']['vitamin_a'] = 0
            b['foodNutrients'].append({'nutrient': {'id': 1104, 'unitName': 'IU'}, 'amount': 0})
            extra = {'nutrient': {'id': 1106, 'unitName': 'ug'}, 'amount': 32}
            b['foodNutrients'].append(extra)
            if defect == 'duplicate': b['foodNutrients'].append(copy.deepcopy(extra))
            elif defect == 'unit': extra['nutrient']['unitName'] = 'mg'
            else: extra['amount'] = True
            d['sourceFingerprint'] = gate.source.r5.fingerprint(b)
            with self.subTest(defect=defect):
                result = gate.review(d, c, b, '2026-10-12T01:00:00Z', NOW, approve_additive_usda=True)
                self.assertEqual('SOURCE_VARIANT_CONFLICT', result['nutrients']['vitamin_a']['state'])
                self.assertEqual('HOLD', result['status'])
                self.assertFalse(result['nutritionWriteAllowed'])

    def test_vitamin_d_ius_are_not_compared_as_if_they_were_micrograms(self):
        d, c, b = usda_fixture()
        c['food']['vitamin_d'] = 38.4
        b['foodNutrients'] += [{'nutrient': {'id': nid, 'unitName': unit}, 'amount': value}
                              for nid, unit, value in ((1110, 'IU', 38.4), (1114, 'ug', .96))]
        d['sourceFingerprint'] = gate.source.r5.fingerprint(b)
        result = gate.review(d, c, b, '2026-10-12T01:00:00Z', NOW, approve_additive_usda=True)
        self.assertEqual('UNIT_OR_FORM_ATTESTATION_REQUIRED', result['nutrients']['vitamin_d']['state'])
        self.assertEqual([1110], result['nutrientVariants']['vitamin_d']['matchingSourceIds'])
        self.assertIsNone(result['nutrientVariants']['vitamin_d']['selectedCandidate'])
        self.assertEqual('HOLD', result['status'])

    def test_missing_source_stays_missing_and_unknown_current_stays_unknown(self):
        d, c, b = usda_fixture()
        c['food']['vitamin_a'] = 3
        missing = gate.review(d, c, b, '2026-10-12T01:00:00Z', NOW, approve_additive_usda=True)
        self.assertEqual('SOURCE_VALUE_REQUIRED', missing['nutrients']['vitamin_a']['state'])
        self.assertEqual([], missing['nutrientVariants']['vitamin_a']['candidates'])
        c['food']['vitamin_a'] = None
        b['foodNutrients'].append({'nutrient': {'id': 1106, 'unitName': 'ug'}, 'amount': 32})
        d['sourceFingerprint'] = gate.source.r5.fingerprint(b)
        unknown = gate.review(d, c, b, '2026-10-12T01:00:00Z', NOW, approve_additive_usda=True)
        self.assertEqual('UNKNOWN_CURRENT_RETAINED', unknown['nutrients']['vitamin_a']['state'])
        self.assertIsNone(c['food']['vitamin_a'])
    def test_rae_source_is_present_but_cannot_attest_the_legacy_vitamin_unit(self):
        d, c, b = usda_fixture()
        c['food']['vitamin_a'] = 32
        b['foodNutrients'].append({'nutrient': {'id': 1106, 'name': 'Vitamin A, RAE', 'unitName': 'ug'}, 'amount': 32})
        d['sourceFingerprint'] = gate.source.r5.fingerprint(b)
        r = gate.review(d, c, b, '2026-10-12T01:00:00Z', NOW, approve_additive_usda=True)
        self.assertEqual('UNIT_OR_FORM_ATTESTATION_REQUIRED', r['nutrients']['vitamin_a']['state'])
        self.assertFalse(r['nutrientVariants']['vitamin_a']['legacyUnitAttested'])
        self.assertEqual('HOLD', r['status'])
        self.assertEqual(32, c['food']['vitamin_a'])
        self.assertFalse(r['nutritionWriteAllowed'])

    def test_total_sugars_source_id_is_reported_without_silent_alias_or_zero_fill(self):
        d, c, b = usda_fixture()
        c['food']['sugar'] = 4.81
        b['foodNutrients'].append({'nutrient': {'id': 1063, 'name': 'Sugars, Total', 'unitName': 'g'}, 'amount': 4.81})
        d['sourceFingerprint'] = gate.source.r5.fingerprint(b)
        r = gate.review(d, c, b, '2026-10-12T01:00:00Z', NOW, approve_additive_usda=True)
        self.assertEqual('SOURCE_DEFINITION_REVIEW_REQUIRED', r['nutrients']['sugar']['state'])
        self.assertEqual(1063, r['nutrientVariants']['sugar']['candidates'][0]['nutrientId'])
        self.assertEqual('HOLD', r['status'])
        self.assertFalse(r['liveApplyApproved'])
    def test_exact_source_liquid_pair_preserves_mass_basis_and_unlocks_only_that_product(self):
        d, c, b = usda_fixture()
        b.update(description='Water, bottled, generic', foodCategory={'description': 'Beverages'})
        c['food'].update(name=b['description'], preparation_state='UNSPECIFIED')
        b['foodPortions'] = [{'id': 77, 'amount': 1, 'gramWeight': 1, 'modifier': 'ml'}]
        d = gate.source.usda_decision({**c['food'], 'id': '1'}, b, set())
        before = copy.deepcopy(c)
        r = gate.review(d, c, b, '2026-10-12T01:00:00Z', NOW, approve_additive_usda=True)
        self.assertEqual('READY_FOR_BACKUP_REHEARSAL', r['status'])
        self.assertEqual((1, 1), (r['options'][0]['gramWeight'], r['options'][0]['mlVolume']))
        self.assertEqual({}, r['changes'])
        self.assertEqual(before, c)
        d['options'][0]['mlVolume'] = 250
        self.assertEqual('HOLD', gate.review(d, c, b, '2026-10-12T01:00:00Z', NOW, approve_additive_usda=True)['status'])

    def test_source_defined_multi_amount_is_not_a_guessed_portion(self):
        d, c, b = usda_fixture()
        b['foodPortions'][0].update(amount=3, gramWeight=85, measureUnit={'name': 'oz'})
        d.update(sourceFingerprint=gate.source.r5.fingerprint(b),
                 options=[gate.source.source_portion(b['foodPortions'][0])[0]])
        r = gate.review(d, c, b, '2026-10-12T01:00:00Z', NOW, approve_additive_usda=True)
        self.assertEqual('READY_FOR_BACKUP_REHEARSAL', r['status'])
        self.assertEqual(85, r['options'][0]['gramWeight'])
        self.assertFalse(r['liveApplyApproved'])

    def test_exact_medium_piece_fractions_can_pass_append_only_source_gate(self):
        d, c, b = usda_fixture()
        b['description'] = 'Bananas, raw'
        c['food'].update(name=b['description'], preparation_state='RAW')
        b['foodPortions'] = [{'id': 77, 'amount': 1, 'gramWeight': 118, 'modifier': 'medium (7 inch long)'}]
        d = gate.source.usda_decision({**c['food'], 'id': '1'}, b, set())
        before = copy.deepcopy(c)
        r = gate.review(d, c, b, '2026-10-12T01:00:00Z', NOW, approve_additive_usda=True)
        self.assertEqual('READY_FOR_BACKUP_REHEARSAL', r['status'])
        self.assertEqual([118, 59, 29.5], [o['gramWeight'] for o in r['options']])
        self.assertEqual(before, c)
        self.assertEqual({}, r['changes'])
        self.assertFalse(r['nutritionWriteAllowed'])

    def test_fraction_provenance_and_full_parent_profile_cannot_be_bypassed(self):
        for change in ('weight', 'parent', 'fraction', 'label', 'ambiguous', 'numeric', 'time'):
            d, c, b = usda_fixture()
            b['description'] = 'Bananas, raw'
            c['food'].update(name=b['description'], preparation_state='RAW')
            b['foodPortions'] = [{'id': 77, 'amount': 1, 'gramWeight': 118, 'modifier': 'medium'}]
            d = gate.source.usda_decision({**c['food'], 'id': '1'}, b, set())
            if change == 'weight': d['options'][1]['gramWeight'] = 60
            elif change == 'parent': d['options'][1]['derivation']['parentPortionId'] = 78
            elif change == 'fraction': d['options'][1]['derivation']['fraction'] = .25
            elif change == 'label': d['options'][1]['labels']['TR'] = 'Tam adet'
            elif change == 'ambiguous':
                b['foodPortions'].append({'id': 78, 'amount': 1, 'gramWeight': 130, 'modifier': 'medium'})
                d['sourceFingerprint'] = gate.source.r5.fingerprint(b)
            elif change == 'numeric': c['food']['protein'] = 9
            checked = '2026-10-10T01:00:00Z' if change == 'time' else '2026-10-12T01:00:00Z'
            with self.subTest(change=change):
                r = gate.review(d, c, b, checked, NOW, approve_additive_usda=True)
                self.assertEqual('HOLD', r['status'])
                self.assertFalse(r['liveApplyApproved'])

    def test_exact_fraction_is_not_a_volume_conversion_or_automatic_admin_approval(self):
        d, c, b = usda_fixture()
        b['description'] = 'Bananas, raw'
        c['food'].update(name=b['description'], preparation_state='RAW')
        b['foodPortions'] = [{'id': 77, 'amount': 1, 'gramWeight': 118, 'modifier': 'medium'}]
        d = gate.source.usda_decision({**c['food'], 'id': '1'}, b, set())
        self.assertEqual('HOLD', gate.review(d, c, b, '2026-10-12T01:00:00Z', NOW)['status'])
        d['options'][1]['mlVolume'] = 59
        self.assertEqual('HOLD', gate.review(d, c, b, '2026-10-12T01:00:00Z', NOW, approve_additive_usda=True)['status'])

    def test_explicit_append_only_approval_retains_curated_values_and_defaults(self):
        d, c, b = usda_fixture()
        before = copy.deepcopy(c)
        self.assertEqual("HOLD", gate.review(d, c, b, "2026-10-12T01:00:00Z", NOW)["status"])
        r = gate.review(d, c, b, "2026-10-12T01:00:00Z", NOW, approve_additive_usda=True)
        self.assertEqual("READY_FOR_BACKUP_REHEARSAL", r["status"])
        self.assertEqual({}, r["changes"])
        self.assertEqual(before, c)
        self.assertEqual("RETAIN_EXISTING_APPROVAL_APPEND_ONLY", r["approvalPolicy"])

    def test_additive_approval_cannot_override_identity_preparation_numeric_or_fraction_gates(self):
        for change in ("name", "preparation", "nutrient", "weight", "fraction", "type", "liquid", "stale"):
            d, c, b = usda_fixture()
            checked = "2026-10-12T01:00:00Z"
            if change == "name":
                c["food"]["name"] = "Olive Oil"
            elif change == "preparation":
                c["food"]["preparation_state"] = "RAW"
            elif change == "nutrient":
                c["food"]["protein"] = 9
            elif change == "weight":
                d["options"][0]["gramWeight"] = 199
            elif change == "fraction":
                d["options"][0]["quantity"] = .5
            elif change == "type":
                c["food"]["catalog_type"] = "BRANDED_PRODUCT"
            elif change == "liquid":
                b["foodCategory"]["description"] = "Beverages"
                d["sourceFingerprint"] = gate.source.r5.fingerprint(b)
            else:
                checked = "2026-10-10T01:00:00Z"
            with self.subTest(change=change):
                self.assertEqual("HOLD", gate.review(d, c, b, checked, NOW, approve_additive_usda=True)["status"])

    def test_already_existing_source_option_is_no_change_without_admin_rewrite(self):
        d, c, b = usda_fixture()
        c["options"] = [{"unit_type": "CUP", "quantity": 1, "gram_weight": 185, "ml_volume": None,
                         "label": d["options"][0]["labels"]["EN"], "is_default": True,
                         "quality_status": "VERIFIED", "user_visible": True}]
        r = gate.review(d, c, b, "2026-10-12T01:00:00Z", NOW, approve_additive_usda=True)
        self.assertEqual("NO_CHANGE", r["status"])
        self.assertEqual([], r["options"])
        self.assertTrue(c["options"][0]["is_default"])
        c["options"][0]["user_visible"] = False
        self.assertEqual("HOLD", gate.review(d, c, b, "2026-10-12T01:00:00Z", NOW, approve_additive_usda=True)["status"])
        c["options"][0].update(user_visible=True, quality_status="NEEDS_REVIEW")
        self.assertEqual("HOLD", gate.review(d, c, b, "2026-10-12T01:00:00Z", NOW, approve_additive_usda=True)["status"])

    def test_explicit_additive_approval_never_unlocks_off_basis_changes(self):
        d, c, b = fixture()
        c["food"]["reviewed_by"] = "admin"
        self.assertEqual("HOLD", gate.review(d, c, b, "2026-10-12T01:00:00Z", NOW, approve_additive_usda=True)["status"])

    def test_full_comparison_nominates_no_nutrient_writes_or_live_approval(self):
        d, c, b = fixture()
        r = gate.review(d, c, b, "2026-10-12T01:00:00Z", NOW)
        self.assertEqual("READY_FOR_BACKUP_REHEARSAL", r["status"])
        self.assertEqual(21, len(r["nutrients"]))
        self.assertFalse(r["nutritionWriteAllowed"])
        self.assertFalse(r["liveApplyApproved"])
        self.assertEqual("PER_100ML", r["changes"]["nutrition_reference_unit"])
        self.assertEqual("MATCH", r["nutrients"]["calcium"]["state"])

    def test_one_micronutrient_difference_blocks_whole_reference_change(self):
        d, c, b = fixture()
        c["food"]["sodium"] = 39
        r = gate.review(d, c, b, "2026-10-12T01:00:00Z", NOW)
        self.assertEqual("HOLD", r["status"])
        self.assertEqual("DIFFERS", r["nutrients"]["sodium"]["state"])

    def test_missing_source_cannot_clear_current_nutrient_or_become_zero(self):
        d, c, b = fixture()
        c["food"]["vitamin_c"] = 3
        r = gate.review(d, c, b, "2026-10-12T01:00:00Z", NOW)
        self.assertEqual("SOURCE_VALUE_REQUIRED", r["nutrients"]["vitamin_c"]["state"])
        self.assertEqual("HOLD", r["status"])

    def test_unmeasured_column_is_not_unknown_null(self):
        d, c, b = fixture()
        del c["food"]["vitamin_c"]
        r = gate.review(d, c, b, "2026-10-12T01:00:00Z", NOW)
        self.assertEqual("UNMEASURED_CURRENT_FIELD", r["nutrients"]["vitamin_c"]["state"])
        self.assertEqual("HOLD", r["status"])

    def test_unknown_current_retained_without_source_fill(self):
        d, c, b = fixture()
        c["food"]["calcium"] = None
        r = gate.review(d, c, b, "2026-10-12T01:00:00Z", NOW)
        self.assertEqual("UNKNOWN_CURRENT_RETAINED", r["nutrients"]["calcium"]["state"])
        self.assertNotIn("calcium", r["changes"])

    def test_stale_future_or_missing_source_time_blocks(self):
        for checked in (None, "2026-10-10T01:00:00Z", "2026-10-13T01:00:00Z"):
            d, c, b = fixture()
            self.assertEqual("HOLD", gate.review(d, c, b, checked, NOW)["status"])

    def test_reference_dependents_existing_options_and_curated_rows_stay_protected(self):
        for changed in ("references", "options", "reviewed_by", "verified", "calculated"):
            d, c, b = fixture()
            if changed == "references":
                c["references"]["recipes"] = 1
            elif changed == "options":
                c["options"] = [{"unit_type": "SERVING", "quantity": 1, "gram_weight": 250, "ml_volume": None}]
            elif changed == "reviewed_by":
                c["food"]["reviewed_by"] = "admin"
            elif changed == "verified":
                c["food"]["verification_status"] = "VERIFIED"
            else:
                c["food"]["nutrition_basis"] = "CALCULATED"
            self.assertEqual("HOLD", gate.review(d, c, b, "2026-10-12T01:00:00Z", NOW)["status"])

    def test_source_body_drift_and_exact_gtin_conflict_are_not_accepted(self):
        d, c, b = fixture()
        b["last_modified_t"] = 1
        self.assertEqual("HOLD", gate.review(d, c, b, "2026-10-12T01:00:00Z", NOW)["status"])
        d, c, b = fixture()
        c["food"]["normalized_barcode"] = "5449000000997"
        self.assertEqual("HOLD", gate.review(d, c, b, "2026-10-12T01:00:00Z", NOW)["status"])

    def test_bulk_or_held_source_never_enters_release_selection(self):
        d, _, _ = fixture()
        self.assertEqual([d], gate.eligible_selection([d]))
        self.assertEqual([], gate.eligible_selection([{**d, "evidenceOrigin": "CACHED_OFF_DUMP"}]))
        self.assertEqual([], gate.eligible_selection([{**d, "decision": "CURRENT_CORE_DIFFERS_FROM_SOURCE"}]))

    def test_legacy_alias_conflict_and_invalid_values_do_not_pass(self):
        _, c, b = fixture()
        b["nutriments"].update({"vitamin-c_100g": .003, "vitamin_c_100g": .004})
        with self.assertRaisesRegex(ValueError, "aliases"):
            gate.all_off_fields(b)
        values = {f: None for f in gate.FIELDS}
        for bad in (float("nan"), float("inf"), -1, True):
            c["food"]["sodium"] = bad
            self.assertEqual("INVALID_CURRENT", gate.comparison(c["food"], values, "OPEN_FOOD_FACTS")["sodium"]["state"])


if __name__ == "__main__":
    unittest.main()

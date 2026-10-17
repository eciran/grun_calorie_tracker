import copy
import importlib.util
import unittest
from datetime import timedelta
from pathlib import Path


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


review = module('r5_reattest_tests', 'review-r5-typed-reattestation.py')
fixtures = module('r5_reattest_name_fixtures', 'test_r5_source_names.py')
NOW = fixtures.NOW


def contract():
    root = Path(__file__).resolve().parents[1]
    return review.names.prereq.followup.read(root / 'src/main/resources/catalog/usda-typed-nutrient-contract-r5-v1.json')


def inputs(value=6):
    row, entry, body = fixtures.inputs()
    before = row['before']
    before['food']['vitamin_a'] = value
    before['evidence'] = [{'id': 11, 'field_name': 'VITAMIN_A', 'numeric_value': value,
                           'provider': 'USDA_FOODDATA', 'external_id': row['sourceKey'], 'basis': 'PER_100_G'}]
    body['foodNutrients'] += [
        {'nutrient': {'id': 1104, 'number': '318', 'name': 'Vitamin A, IU', 'unitName': 'IU'}, 'amount': 20},
        {'nutrient': {'id': 1106, 'number': '320', 'name': 'Vitamin A, RAE', 'unitName': 'µg'}, 'amount': 6},
    ]
    row['beforeFingerprint'] = review.fingerprint(before)
    row['sourceFingerprint'] = review.fingerprint(body)
    body_check = copy.deepcopy(before)
    body_check['food'].update(row['metadataChanges'])
    decision = review.gate.source.usda_decision({**body_check['food'], 'id': row['id']}, body, set())
    checked = review.gate.review(decision, body_check, body, row['sourceCheckedAt'], NOW, approve_additive_usda=True)
    row['projectedOptions'] = checked['options']
    named = review.names.review_row(row, entry, body, NOW)
    seed = {'source_key': row['sourceKey'], 'vitamin_a': '6', 'source_note': 'Historical candidate only'}
    return named, body, seed


class TypedReattestationTests(unittest.TestCase):
    def test_exact_source_proposal_is_not_legacy_unit_certification(self):
        row, body, seed = inputs()
        original = copy.deepcopy((row, body, seed))
        result = review.review_row(row, body, contract(), NOW, seed)
        self.assertEqual(original, (row, body, seed))
        self.assertEqual('DIRECT_RAE_SOURCE_REATTESTATION_PREPARED', result['sourceReviewState'])
        typed = result['newSourceEvidenceProposal']
        self.assertEqual((1106, '320', 'ug', 'VITAMIN_A_RAE', 'MCG_RAE', 6),
                         (typed['sourceNutrientId'], typed['sourceNutrientNumber'], typed['normalizedSourceUnit'],
                          typed['semantic'], typed['canonicalUnit'], typed['sourceAmount']))
        self.assertFalse(typed['numericConversion'])
        self.assertFalse(typed['legacyDatabaseUnitAttested'])
        self.assertFalse(result['legacyUnitAttested'])
        self.assertEqual({}, result['numericChanges'])
        self.assertEqual(row['before'], result['before'])
        self.assertFalse(result['nutritionWriteAllowed'])
        self.assertFalse(result['applyAllowed'])
        self.assertFalse(result['currentEvidenceReplaced'])

    def test_numeric_matching_iu_is_not_selected_without_exact_rae(self):
        _, body, _ = inputs()
        body['foodNutrients'] = [n for n in body['foodNutrients'] if n['nutrient']['id'] != 1106]
        body['foodNutrients'][-1]['amount'] = 6
        proposal, error = review.source_rae(body, review.EXACT_A)
        self.assertIsNone(proposal)
        self.assertEqual('MISSING_OR_DUPLICATE_EXACT_RAE_SOURCE', error)

    def test_two_zero_forms_do_not_attest_a_legacy_scalar(self):
        _, body, _ = inputs()
        for n in body['foodNutrients']:
            if n['nutrient']['id'] in (1104, 1106): n['amount'] = 0
        proposal, error = review.source_rae(body, review.EXACT_A)
        self.assertIsNone(error)
        self.assertEqual(1106, proposal['sourceNutrientId'])
        self.assertEqual(0, proposal['sourceAmount'])
        self.assertFalse(proposal['legacyDatabaseUnitAttested'])

    def test_duplicate_id_wrong_unit_name_number_and_bad_amounts_are_rejected(self):
        for defect in ('duplicate', 'unit', 'name', 'number', 'bool', 'null', 'nan', 'negative', 'string'):
            _, body, _ = inputs()
            target = body['foodNutrients'][-1]
            if defect == 'duplicate': body['foodNutrients'].append(copy.deepcopy(target))
            elif defect == 'unit': target['nutrient']['unitName'] = 'IU'
            elif defect == 'name': target['nutrient']['name'] = 'Retinol'
            elif defect == 'number': target['nutrient']['number'] = '318'
            else: target['amount'] = {'bool': True, 'null': None, 'nan': float('nan'), 'negative': -1, 'string': '6'}[defect]
            with self.subTest(defect=defect):
                value, error = review.source_rae(body, review.EXACT_A)
                self.assertIsNone(value)
                self.assertIsNotNone(error)

    def test_micro_symbols_are_normalized_without_numerical_conversion(self):
        for unit in ('µg', 'μg', 'ug', 'UG'):
            _, body, _ = inputs()
            body['foodNutrients'][-1]['nutrient']['unitName'] = unit
            value, error = review.source_rae(body, review.EXACT_A)
            with self.subTest(unit=unit):
                self.assertIsNone(error)
                self.assertEqual(6, value['sourceAmount'])
                self.assertEqual('ug', value['normalizedSourceUnit'])
                self.assertFalse(value['numericConversion'])

    def test_canonical_number_change_is_never_hidden_as_unit_fix(self):
        row, body, seed = inputs(value=6.04)
        result = review.review_row(row, body, contract(), NOW, seed)
        self.assertEqual('HELD', result['sourceReviewState'])
        self.assertIn('CANONICAL_NUMBER_DIFFERENCE_REQUIRES_EXPLICIT_NUTRITION_REVIEW', result['sourceReviewHolds'])
        self.assertEqual({}, result['numericChanges'])
        self.assertEqual(6.04, result['before']['food']['vitamin_a'])

    def test_missing_current_value_does_not_get_filled(self):
        row, body, seed = inputs(value=None)
        row['projectedWorkQueue'] = review.QUEUE
        result = review.review_row(row, body, contract(), NOW, seed)
        self.assertEqual('HELD', result['sourceReviewState'])
        self.assertIn('CURRENT_VALUE_UNKNOWN_OR_INVALID_DO_NOT_FILL', result['sourceReviewHolds'])
        self.assertIsNone(result['before']['food']['vitamin_a'])

    def test_all_old_holds_and_typed_storage_requirement_remain(self):
        row, body, _ = inputs()
        result = review.review_row(row, body, contract(), NOW)
        self.assertTrue(set(row['projectedHolds']) <= set(result['releaseHolds']))
        self.assertIn('FULL_NUTRITION_COMPARISON_REQUIRED', result['releaseHolds'])
        self.assertIn('TYPED_NUTRIENT_PROVENANCE_STORAGE_AND_CURRENT_REBASE_REQUIRED', result['releaseHolds'])
        self.assertFalse(result['newSourceEvidenceProposal']['databaseStorageSchemaAvailable'])

    def test_seed_number_match_is_only_corroboration_not_executed_import_proof(self):
        row, body, seed = inputs()
        result = review.review_row(row, body, contract(), NOW, seed)
        observed = result['historicalSeedCorroboration']
        self.assertTrue(observed['sameVitaminANumber'])
        self.assertFalse(observed['provesImportExecuted'])
        self.assertFalse(observed['certifiesLegacyUnit'])
        self.assertFalse(result['legacyUnitAttested'])

    def test_old_evidence_owner_basis_value_or_typed_claim_must_be_rebased(self):
        for field, value in (('external_id', 'USDA_FOODDATA:fdc:999'), ('numeric_value', 7),
                             ('basis', 'PER_100_ML'), ('unit', 'IU')):
            row, body, seed = inputs()
            row['before']['evidence'][0][field] = value
            row['beforeFingerprint'] = review.fingerprint(row['before'])
            with self.subTest(field=field):
                self.assertEqual('HELD', review.review_row(row, body, contract(), NOW, seed)['sourceReviewState'])

    def test_stale_future_or_timezone_free_receipts_cannot_be_renewed(self):
        row, body, _ = inputs()
        for stamp in ('2026-10-10T10:00:00Z', '2026-10-13T10:00:00Z', '2026-10-12T10:00:00'):
            row['sourceCheckedAt'] = stamp
            with self.subTest(stamp=stamp):
                result = review.review_row(row, body, contract(), NOW)
                self.assertEqual('HELD', result['sourceReviewState'])
                self.assertEqual(stamp, result['sourceCheckedAt'])

    def test_tampered_source_or_before_image_and_unexpected_parent_change_reject(self):
        for defect in ('source', 'before', 'parent', 'apply'):
            row, body, _ = inputs()
            if defect == 'source': body['description'] = 'Other food'
            elif defect == 'before': row['before']['food']['vitamin_a'] = 4
            elif defect == 'parent': row['foodChanges']['vitamin_a'] = 5
            else: row['applyAllowed'] = True
            with self.subTest(defect=defect), self.assertRaises(ValueError):
                review.review_row(row, body, contract(), NOW)

    def test_contract_cannot_choose_iu_or_allow_conversion_by_configuration(self):
        for defect in ('id', 'unit', 'permission', 'basis'):
            value = contract()
            if defect == 'id': value['fields']['vitamin_a']['nutrientId'] = 1104
            elif defect == 'unit': value['fields']['vitamin_a']['sourceUnit'] = 'iu'
            elif defect == 'basis': value['basis'] = 'PER_100ML'
            else: value['policy']['allowIuConversion'] = True
            with self.subTest(defect=defect), self.assertRaises(ValueError):
                review.contract_spec(value)

    def test_arbitrary_f4_comparison_change_cannot_be_hidden(self):
        row, body, _ = inputs()
        row['numericComparison']['vitamin_a']['state'] = 'MATCH'
        with self.assertRaises(ValueError):
            review.review_row(row, body, contract(), NOW)


if __name__ == '__main__':
    unittest.main()

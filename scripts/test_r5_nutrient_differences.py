import copy
import importlib.util
import unittest
import tempfile
from types import SimpleNamespace
from datetime import datetime, timedelta, timezone
from pathlib import Path


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


review = module('r5_difference_tests', 'review-r5-nutrient-differences.py')
fixtures = module('r5_difference_fixtures', 'test_r5_release_review.py')
NOW = datetime(2026, 10, 15, 12, tzinfo=timezone.utc)
STAMP = '2026-10-15T00:10:00+00:00'


def inputs(provider='USDA_FOODDATA', field='vitamin_e', current=0, amount=.11):
    _, before, body = fixtures.usda_fixture() if provider == 'USDA_FOODDATA' else fixtures.fixture()
    if provider == 'USDA_FOODDATA':
        nid, num, name, unit, _, _ = review.USDA_FIELDS[field]
        body['foodNutrients'].append({'nutrient': {'id': nid, 'number': num, 'name': name, 'unitName': unit}, 'amount': amount})
    else:
        field = 'sodium'
        body['nutriments']['sodium_unit'] = 'g'
        body['nutriments']['sodium_value'] = body['nutriments']['sodium_100g']
    before['food'][field] = current
    values = review.gate.all_usda_fields(body) if provider == 'USDA_FOODDATA' else review.gate.all_off_fields(body)
    row = {'id': '1', 'sourceKey': before['food']['source_key'], 'before': before,
           'beforeFingerprint': review.fingerprint(before), 'sourceFingerprint': review.fingerprint(body),
           'sourceCheckedAt': STAMP, 'workQueue': review.QUEUE, 'disposition': 'HELD',
           'status': 'REVIEW_ONLY_ARCHIVED_BASELINE', 'applyAllowed': False, 'liveApplyApproved': False,
           'nutritionWriteAllowed': False, 'legacyMicronutrientUnitsCertified': False,
           'nutrients': review.gate.comparison(before['food'], values, provider),
           'options': [], 'changes': {}, 'holds': ['FULL_NUTRITION_COMPARISON_REQUIRED', 'EXISTING_ADMIN_APPROVAL_REVIEW_REQUIRED']}
    return row, body


class NutrientDifferenceReviewTests(unittest.TestCase):
    def test_exact_usda_optional_fact_is_not_current_unit_or_zero_attestation(self):
        row, body = inputs()
        old = copy.deepcopy((row, body))
        result = review.review_row(row, body, STAMP, NOW)
        item = result['fieldReviews'][0]
        self.assertEqual('USDA_CURRENT_ZERO_SOURCE_POSITIVE_REVIEW', item['reviewQueue'])
        self.assertEqual((1109, 'MG', '0.11'), (item['sourceFact']['sourceNutrientId'], item['sourceFact']['canonicalSourceUnit'], item['sourceFact']['canonicalSourceAmount']))
        self.assertFalse(item['currentZeroProvenMeasured'])
        self.assertFalse(item['sourceFactIsCurrentDatabaseUnitProof'])
        self.assertFalse(item['existingTypedLedgerSupportsThisField'])
        self.assertEqual(old, (row, body))

    def test_all_three_source_ids_have_distinct_forms_and_units(self):
        for field, unit in [('potassium', 'MG'), ('vitamin_e', 'MG'), ('vitamin_b12', 'MCG')]:
            row, body = inputs(field=field)
            fact = review.review_row(row, body, STAMP, NOW)['fieldReviews'][0]['sourceFact']
            self.assertEqual(unit, fact['canonicalSourceUnit'])
            self.assertFalse(fact['sourceUnitConversionApplied'])

    def test_no_numeric_nearest_name_or_iu_selection(self):
        _, body = inputs()
        body['foodNutrients'][-1]['nutrient']['id'] = 9999
        with self.assertRaises(ValueError): review.usda_fact('vitamin_e', body)

    def test_duplicate_wrong_number_wrong_name_wrong_unit_and_non_integer_id_fail(self):
        for defect in ('duplicate', 'number', 'name', 'unit', 'float-id'):
            _, body = inputs()
            nutrient = body['foodNutrients'][-1]['nutrient']
            if defect == 'duplicate': body['foodNutrients'].append(copy.deepcopy(body['foodNutrients'][-1]))
            if defect == 'number': nutrient['number'] = '999'
            if defect == 'name': nutrient['name'] = 'Vitamin E, IU'
            if defect == 'unit': nutrient['unitName'] = 'IU'
            if defect == 'float-id': nutrient['id'] = 1109.0
            with self.subTest(defect=defect), self.assertRaises(ValueError): review.usda_fact('vitamin_e', body)

    def test_invalid_source_numbers_never_become_zero(self):
        for value in (None, True, '0.11', -1, float('nan'), float('inf'), 10 ** 1000):
            _, body = inputs(); body['foodNutrients'][-1]['amount'] = value
            with self.subTest(value=value), self.assertRaises(ValueError): review.usda_fact('vitamin_e', body)

    def test_micro_symbols_normalize_without_conversion(self):
        for unit in ('UG', '\u00b5g', '\u03bcg'):
            _, body = inputs(field='vitamin_b12'); body['foodNutrients'][-1]['nutrient']['unitName'] = unit
            fact = review.usda_fact('vitamin_b12', body)
            self.assertEqual('ug', fact['normalizedSourceUnit']); self.assertFalse(fact['sourceUnitConversionApplied'])

    def test_nonzero_mineral_difference_is_not_auto_rounded_to_match(self):
        row, body = inputs(field='potassium', current=1460, amount=1464)
        item = review.review_row(row, body, STAMP, NOW)['fieldReviews'][0]
        self.assertEqual('USDA_NONZERO_SOURCE_PROFILE_DIFFERENCE_REVIEW', item['reviewQueue'])
        self.assertEqual(1460, item['currentScalar']); self.assertEqual('1464', item['sourceFact']['canonicalSourceAmount'])
        self.assertEqual('DIFFERS', item['legacyComparison']['state'])

    def test_explicit_off_grams_are_converted_only_in_new_source_fact(self):
        row, body = inputs('OPEN_FOOD_FACTS'); original = copy.deepcopy(row)
        fact = review.review_row(row, body, STAMP, NOW)['fieldReviews'][0]['sourceFact']
        self.assertEqual(('0.039', '39.000', 'MG'), (fact['sourceAmount'], fact['canonicalSourceAmount'], fact['canonicalSourceUnit']))
        self.assertTrue(fact['sourceUnitConversionApplied'])
        self.assertEqual(original, row); self.assertEqual(0, row['before']['food']['sodium'])

    def test_absent_fiber_is_not_source_zero_or_a_value_fill(self):
        row, body = inputs('OPEN_FOOD_FACTS'); row['before']['food']['sodium'] = .039; row['before']['food']['fiber'] = 0
        row['beforeFingerprint'] = review.fingerprint(row['before'])
        row['nutrients'] = review.gate.comparison(row['before']['food'], review.gate.all_off_fields(body), 'OPEN_FOOD_FACTS')
        result = review.review_row(row, body, STAMP, NOW); item = result['fieldReviews'][0]
        self.assertEqual('fiber', item['field']); self.assertIsNone(item['sourceFact'])
        self.assertEqual('MISSING_SOURCE_OPTIONAL_VALUE_RETAIN_CURRENT', item['reviewQueue'])
        self.assertFalse(item['missingSourceMeansZero']); self.assertEqual({}, result['nutritionChanges'])

    def test_declared_source_zero_is_distinct_from_absence(self):
        _, body = inputs('OPEN_FOOD_FACTS'); body['nutriments'].update(fiber_100g=0, fiber_unit='g')
        fact = review.off_fact('fiber', body)
        self.assertEqual('0', fact['sourceAmount']); self.assertIsNotNone(fact)

    def test_off_ambiguous_basis_units_values_and_inequalities_fail(self):
        for defect in ('basis', 'unit', 'display-value', 'modifier'):
            _, body = inputs('OPEN_FOOD_FACTS')
            if defect == 'basis': body['nutrition_data_per'] = 'serving'
            if defect == 'unit': body['nutriments']['sodium_unit'] = 'mg'
            if defect == 'display-value': body['nutriments']['sodium_value'] = 39
            if defect == 'modifier': body['nutriments']['sodium_modifier'] = '<'
            with self.subTest(defect=defect), self.assertRaises(ValueError): review.off_fact('sodium', body)

    def test_receipt_age_is_never_restamped(self):
        row, body = inputs()
        for now, stamp in ((NOW + timedelta(days=2), STAMP), (NOW - timedelta(days=2), STAMP),
                           (NOW, '2026-10-15T00:10:00'), (NOW, None), (NOW, NOW.isoformat())):
            with self.subTest(stamp=stamp), self.assertRaises(ValueError): review.review_row(row, body, stamp, now)

    def test_parent_source_and_full_comparison_drift_fail(self):
        for defect in ('before', 'source', 'comparison'):
            row, body = inputs()
            if defect == 'before': row['before']['food']['protein'] = 99
            if defect == 'source': body['description'] = 'Another food'
            if defect == 'comparison': row['nutrients']['protein']['current'] = 99
            with self.subTest(defect=defect), self.assertRaises(ValueError): review.review_row(row, body, STAMP, NOW)

    def test_other_queue_approval_or_certification_cannot_be_reused(self):
        for key, value in [('workQueue', 'OTHER'), ('applyAllowed', True), ('liveApplyApproved', True),
                           ('legacyMicronutrientUnitsCertified', True), ('nutritionWriteAllowed', True), ('disposition', 'SUPERSEDED')]:
            row, body = inputs(); row[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError): review.review_row(row, body, STAMP, NOW)

    def test_existing_admin_holds_portions_and_all21_comparisons_are_preserved(self):
        row, body = inputs(); row['options'] = [{'reviewedOption': 9}]; row['changes'] = {'reviewedPendingMetadata': 'retained'}
        result = review.review_row(row, body, STAMP, NOW)
        self.assertEqual(row['holds'], result['originalHolds']); self.assertTrue(set(row['holds']) <= set(result['releaseHolds']))
        self.assertEqual(row['nutrients'], result['originalNumericComparison'])
        self.assertEqual(row['options'], result['originalOptions']); self.assertEqual(row['changes'], result['originalChanges'])
        self.assertFalse(result['applyAllowed']); self.assertFalse(result['nutritionWriteAllowed'])

    def test_other_nutrients_and_unknown_current_are_not_silently_included(self):
        _, body = inputs()
        with self.assertRaises(ValueError): review.usda_fact('vitamin_a', body)
        row, body = inputs(); row['before']['food']['vitamin_e'] = None
        row['beforeFingerprint'] = review.fingerprint(row['before'])
        row['nutrients'] = review.gate.comparison(row['before']['food'], review.gate.all_usda_fields(body), 'USDA_FOODDATA')
        with self.assertRaises(ValueError): review.review_row(row, body, STAMP, NOW)

    def test_existing_output_is_refused_before_any_other_file_access(self):
        with tempfile.TemporaryDirectory() as folder:
            args = SimpleNamespace(output=Path(folder))
            with self.assertRaisesRegex(ValueError, 'New output directory required'): review.run(args)


if __name__ == '__main__':
    unittest.main()

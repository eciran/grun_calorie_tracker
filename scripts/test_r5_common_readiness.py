import copy
import unittest
from pathlib import Path
from importlib.util import module_from_spec, spec_from_file_location

spec = spec_from_file_location('r5_common_tests', Path(__file__).with_name('rehearse-r5-common-readiness.py'))
r = module_from_spec(spec)
spec.loader.exec_module(r)
p = r.prepare


def plan():
    decisions = p.common.names.held.read_json(p.REGISTRY)['entries']
    sources = {d['id']: d['sourceKey'] for d in p.common.names.held.read_json(p.common.names.REGISTRY)['entries']}
    sources.update(p.common.PROTECTED)
    rows = [{'id': i, 'food': {'id': int(i), 'source_key': source}, 'name_review': {'options': []}}
            for i, source in sources.items()]
    by_id = {row['id']: row for row in rows}
    replacements = []
    for d in decisions:
        before = {'id': d['optionId'], 'food_item_id': int(d['foodId']), 'gram_weight': d['oldGrams'],
                  'ml_volume': None, 'quantity': 1, 'unit_type': d['unit'], 'label': 'Historical label',
                  'user_visible': True, 'is_default': True,
                  'quality_status': 'NEEDS_REVIEW' if d['optionId'] == 128 else 'VERIFIED'}
        by_id[d['foodId']]['name_review']['options'].append(before)
        portion = {'gramWeight': d['sourceGrams'], 'amount': 1, 'modifier': d['sourceModifier']}
        replacements.append({'foodId': d['foodId'], 'sourceKey': sources[d['foodId']], 'before': before,
                             'retireChanges': {'user_visible': False, 'is_default': False},
                             'newOption': {'label': d['en'], 'unit_type': d['unit'], 'quantity': 1,
                                           'gram_weight': d['sourceGrams'], 'ml_volume': None, 'is_default': True,
                                           'source': 'ADMIN', 'quality_status': 'VERIFIED', 'user_visible': True},
                             'labels': {'EN': d['en'], 'TR': d['tr']}, 'sourcePortion': portion,
                             'sourcePortionSha256': p.fingerprint(portion)})
    for row in replacements: row['beforeSha256'] = p.fingerprint(by_id[row['foodId']])
    return {'version': 'R5_COMMON_READINESS_PREPARATION_V1', 'schemaVersion': '305',
            'scope': 'R5_DEPENDENCIES_OF_EXACT_R6_COMMON_OPERATION_ONLY', 'liveApplyAllowed': False,
            'approval': None, 'backup': None, 'numericWritesAllowed': False, 'mlConversionAllowed': False,
            'globalR5Completed': False, 'legacyMicronutrientUnitsCertified': False,
            'currentRows': rows, 'replacements': replacements,
            'optionDependenciesBefore': {'optionDependencies': []}}


class CommonR5Tests(unittest.TestCase):
    def test_exact_13_additions_do_not_mutate_original_rows(self):
        value = plan()
        original = copy.deepcopy(value)
        p.validate(value)
        sql = r.option_apply(value, 'r6-rehearsal-123456abcdef')
        self.assertEqual(original, value)
        self.assertEqual(13, sql.count('SET user_visible=false,is_default=false'))
        self.assertEqual(13, sql.count('WITH added AS'))
        self.assertEqual(26, sql.count('INSERT INTO food_item_serving_option_localizations'))
        self.assertNotIn('SET gram_weight', sql)
        self.assertNotIn('UPDATE food_items', sql)
        self.assertNotIn('DELETE FROM', sql)

    def test_scope_approval_and_global_completion_cannot_be_inferred(self):
        for field, value in [('liveApplyAllowed', True), ('approval', 'yes'), ('backup', {}),
                             ('numericWritesAllowed', True), ('mlConversionAllowed', True),
                             ('globalR5Completed', True), ('legacyMicronutrientUnitsCertified', True),
                             ('scope', 'ENTIRE_CATALOG')]:
            current = plan()
            current[field] = value
            with self.assertRaises(ValueError): p.validate(current)

    def test_missing_duplicate_or_wrong_parent_is_rejected(self):
        for mode in ('missing', 'duplicate', 'wrong'):
            current = plan()
            if mode == 'missing': current['currentRows'].pop()
            if mode == 'duplicate': current['currentRows'][-1] = current['currentRows'][0]
            if mode == 'wrong': current['currentRows'][0]['id'] = '9000000'
            with self.assertRaises(ValueError): p.validate(current)

    def test_option_membership_and_retirement_are_exact(self):
        for field, value in [('ml_volume', 200), ('gram_weight', 1), ('quality_status', 'VERIFIED')]:
            current = plan()
            current['replacements'][0]['before'][field] = value
            with self.assertRaises(ValueError): p.validate(current)
        current = plan()
        current['replacements'][0]['retireChanges']['gram_weight'] = 15
        with self.assertRaises(ValueError): p.validate(current)

    def test_unproven_ml_pair_wrong_source_and_changed_default_are_rejected(self):
        for field, value in [('ml_volume', 245), ('gram_weight', 15.1), ('is_default', False), ('source', 'GUESSED')]:
            current = plan()
            current['replacements'][0]['newOption'][field] = value
            with self.assertRaises(ValueError): p.validate(current)

    def test_full_source_portion_hash_not_just_weight_is_guarded(self):
        current = plan()
        current['replacements'][0]['sourcePortion']['id'] = 999
        with self.assertRaises(ValueError): p.validate(current)

    def test_option_limit_cannot_be_bypassed_by_append(self):
        current = plan()
        parent = next(row for row in current['currentRows'] if row['id'] == current['replacements'][0]['foodId'])
        parent['name_review']['options'].extend([{'id': i} for i in range(10000,10020)])
        current['replacements'][0]['beforeSha256'] = p.fingerprint(parent)
        with self.assertRaises(ValueError): p.validate(current)

    def test_unsafe_option_fk_lock_identifiers_are_rejected(self):
        meta = {'optionDependencies': [{'table': 'food_logs; DROP TABLE food_items', 'column': 'serving_option_id',
                                        'schema': 'public', 'supported': True}]}
        with self.assertRaises(ValueError): r.option_guard(plan(), meta)

    def test_combined_apply_requires_identical_current_rows(self):
        current = plan()
        with self.assertRaises(ValueError):
            r.apply_sql({'schemaVersion': '305', 'currentRows': []}, current, {}, [], {}, 'owner', 'expiry')

    def test_only_additive_unassigned_307_is_compatible(self):
        current = plan()['currentRows']
        p.common.validate_schema('305', current)
        with self.assertRaises(ValueError): p.common.validate_schema('307', current)
        for row in current:
            row['food'].update({k: None for k in p.common.ADDITIVE_IDENTITY_FIELDS})
        p.common.validate_schema('307', current)
        for version in ('306', '308', None):
            with self.assertRaises(ValueError): p.common.validate_schema(version, current)
        current[0]['food']['food_concept_key'] = 'already_assigned'
        with self.assertRaises(ValueError): p.common.validate_schema('307', current)

    def test_number_match_is_not_legacy_unit_or_conversion_authority(self):
        body = {'foodNutrients': [{'nutrient': {'id': 1104, 'unitName': 'IU'}, 'amount': 298},
                                 {'nutrient': {'id': 1106, 'unitName': 'ug'}, 'amount': 15}]}
        value = p.vitamin_a({'vitamin_a': 15}, body)
        self.assertEqual('SOURCE_NUMBER_MATCH_UNIT_UNATTESTED', value['state'])
        self.assertFalse(value['legacyUnitAttested'])
        self.assertFalse(value['conversionAllowed'])
        self.assertEqual([False, True], [c['sameNumberWithinRounding'] for c in value['sourceCandidates']])
        body['foodNutrients'][0]['amount'] = 70
        body['foodNutrients'][1]['amount'] = 8
        value = p.vitamin_a({'vitamin_a': 70}, body)
        self.assertEqual([True, False], [c['sameNumberWithinRounding'] for c in value['sourceCandidates']])

    def test_missing_vitamin_a_remains_unknown_even_when_source_zero(self):
        value = p.vitamin_a({'vitamin_a': None}, {'foodNutrients': [{'nutrient': {'id': 1104, 'unitName': 'IU'}, 'amount': 0}]})
        self.assertIsNone(value['current'])
        self.assertEqual('UNKNOWN_RETAINED', value['state'])
        self.assertFalse(value['numericWriteAllowed'])

    def test_invalid_ambiguous_or_mistyped_vitamin_a_is_rejected(self):
        for amount in (float('nan'), float('inf'), -1, True):
            with self.assertRaises(ValueError): p.vitamin_a({'vitamin_a': amount}, {})
        nutrient = {'nutrient': {'id': 1106, 'unitName': 'IU'}, 'amount': 15}
        with self.assertRaises(ValueError): p.vitamin_a({}, {'foodNutrients': [nutrient]})
        nutrient['nutrient']['unitName'] = 'ug'
        with self.assertRaises(ValueError): p.vitamin_a({}, {'foodNutrients': [nutrient, nutrient]})

    def test_exact_unique_source_measure_required(self):
        decision = {'sourceGrams': 15, 'sourceModifier': 'tablespoon'}
        portion = {'amount': 1, 'gramWeight': 15, 'modifier': 'tablespoon'}
        self.assertEqual(portion, p.source_portion({'foodPortions': [portion]}, decision))
        for portions in ([], [portion, portion], [{**portion, 'amount': .5}], [{**portion, 'gramWeight': 13.5}]):
            with self.assertRaises(ValueError): p.source_portion({'foodPortions': portions}, decision)


if __name__ == '__main__': unittest.main()

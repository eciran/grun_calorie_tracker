import copy
import unittest
from pathlib import Path
from importlib.util import module_from_spec, spec_from_file_location


spec = spec_from_file_location('feta_tests', Path(__file__).with_name('review-r6-feta-aliases.py'))
feta = module_from_spec(spec)
spec.loader.exec_module(feta)


def fixture():
    old = {'id': 807, 'food_item_id': 179893, 'alias': 'feta cheese', 'normalized_alias': 'feta cheese',
           'language': 'EN', 'active': True, 'alias_type': 'ADMIN_MANUAL', 'source': 'admin-import', 'created_at': '2026-08-21'}
    rows = []
    for identifier, (source_id, description, subtype) in feta.SOURCES.items():
        food = {'id': int(identifier), 'name': 'Feta Cheese' if identifier == '180991' else description,
                'display_name': 'Feta Cheese' if identifier == '180991' else 'Prepared Feta Cheese',
                'source_key': 'USDA_FOODDATA:fdc:' + source_id, 'data_source': 'USDA_FOODDATA',
                'catalog_type': 'GENERIC_INGREDIENT', 'is_custom': False, 'publication_status': 'PUBLISHED',
                'nutrition_reference_unit': 'PER_100G', 'preparation_state': 'UNSPECIFIED' if identifier == '180991' else 'PREPARED',
                'calories': 265 if identifier == '180991' else 273}
        locale = {'id': 4385, 'food_item_id': 180991, 'language': 'TR', 'display_name': 'Beyaz Peynir (Feta)',
                  'short_display_name': 'Beyaz Peynir (Feta)', 'source': 'admin-import', 'active': True,
                  'created_at': '2026-08-26', 'updated_at': '2026-08-26'}
        before = {'id': identifier, 'food': food, 'assignments': [], 'source_tags': [], 'evidence': [],
                  'name_review': {'aliases': [old] if identifier == '179893' else [],
                     'localizations': [locale] if identifier == '180991' else [], 'options': [], 'option_localizations': []}}
        rows.append({'id': identifier, 'sourceDescription': description, 'sourceType': subtype, 'expectedBefore': before,
                     'beforeSha256': feta.fingerprint(before), 'foodNameChanges': {},
                     'aliasActions': [{'action': 'ADD_SOURCE_QUALIFIED_ALIAS', 'language': 'TR', 'alias': 'Feta Peyniri',
                                       'normalized_alias': 'feta peyniri'}] if identifier == '180991' else [],
                     'localizationActions': [{'action': 'UPDATE_EXACT_ACTIVE_LOCALIZATION', 'before': locale,
                         'changes': {'display_name': 'Feta Peyniri', 'short_display_name': 'Feta Peyniri'}}] if identifier == '180991' else []})
    return {'version': 'R6_FETA_ALIAS_PREPARATION_V1', 'schemaVersion': '305', 'approval': None, 'backup': None,
            'liveApplyAllowed': False, 'rows': rows, 'aliasClaimsBefore': [old]}


def receipts(plan, owner):
    result = []
    for row in plan['rows']:
        after = copy.deepcopy(row['expectedBefore'])
        if row['id'] == '180991':
            after['name_review']['localizations'][0].update(row['localizationActions'][0]['changes'], updated_at='2026-10-13')
            after['name_review']['aliases'].append({'id': 999, 'food_item_id': 180991, 'language': 'TR',
                'alias': 'Feta Peyniri', 'normalized_alias': 'feta peyniri', 'alias_type': 'ADMIN_MANUAL',
                'source': owner, 'active': True, 'created_at': '2026-10-13'})
        result.append(after)
    return result


class FetaAliasTests(unittest.TestCase):
    def test_exact_scope_keeps_source_variants_and_does_not_mutate_inputs(self):
        plan = fixture()
        original = copy.deepcopy(plan)
        feta.validate(plan)
        self.assertEqual(original, plan)
        self.assertEqual([807], [a['id'] for a in plan['aliasClaimsBefore']])

    def test_source_ids_descriptions_subtypes_and_names_cannot_drift(self):
        for field, value in [('sourceDescription', 'Cheese, cheddar'), ('sourceType', 'Branded')]:
            plan = fixture()
            plan['rows'][0][field] = value
            with self.assertRaises(ValueError):
                feta.validate(plan)
        for field in ('source_key', 'name', 'preparation_state', 'nutrition_reference_unit'):
            plan = fixture()
            plan['rows'][1]['expectedBefore']['food'][field] = 'changed'
            plan['rows'][1]['beforeSha256'] = feta.fingerprint(plan['rows'][1]['expectedBefore'])
            with self.assertRaises(ValueError):
                feta.validate(plan)

    def test_unrelated_alias_locale_key_and_nutrition_operations_rejected(self):
        for field in ('name', 'canonical_food_key', 'calories', 'serving_unit', 'source_key'):
            plan = fixture()
            plan['rows'][0]['foodNameChanges'] = {field: 'changed'}
            with self.assertRaises(ValueError):
                feta.validate(plan)
        for field in ('aliasActions', 'localizationActions'):
            plan = fixture()
            plan['rows'][1][field] = [{'action': 'CHANGE'}]
            with self.assertRaises(ValueError):
                feta.validate(plan)

    def test_no_english_alias_insert_or_existing_alias_deactivation(self):
        for action in ({'action': 'DEACTIVATE_EXACT_MISLEADING_ALIAS', 'before': {}},
                       {'action': 'ADD_SOURCE_QUALIFIED_ALIAS', 'language': 'EN', 'alias': 'Feta Cheese', 'normalized_alias': 'feta cheese'}):
            plan = fixture()
            plan['rows'][0]['aliasActions'] = [action]
            with self.assertRaises(ValueError):
                feta.validate(plan)

    def test_external_alias_missing_new_global_claim_or_owner_change_fails(self):
        for value in ([], [{**fixture()['aliasClaimsBefore'][0], 'food_item_id': 1}],
                      fixture()['aliasClaimsBefore'] + [{'id': 123, 'normalized_alias': 'feta peyniri'}]):
            plan = fixture()
            plan['aliasClaimsBefore'] = value
            with self.assertRaises(ValueError):
                feta.validate(plan)

    def test_only_existing_active_owned_tr_locale_can_change(self):
        for changes in ({'id': 1}, {'language': 'EN'}, {'food_item_id': 179893}, {'active': False}):
            plan = fixture()
            plan['rows'][0]['localizationActions'][0]['before'].update(changes)
            plan['rows'][0]['beforeSha256'] = feta.fingerprint(plan['rows'][0]['expectedBefore'])
            with self.assertRaises(ValueError):
                feta.validate(plan)

    def test_invalid_scope_or_live_approval_is_not_rehearsal_authorization(self):
        for field, value in [('schemaVersion', '307'), ('liveApplyAllowed', True), ('approval', 'yes'), ('backup', 'unchecked')]:
            plan = fixture()
            plan[field] = value
            with self.assertRaises(ValueError):
                feta.validate(plan)
        plan = fixture()
        plan['rows'][1] = copy.deepcopy(plan['rows'][0])
        with self.assertRaises(ValueError):
            feta.validate(plan)

    def test_undo_is_receipt_bound_and_never_updates_external_alias_or_food(self):
        plan = fixture()
        owner = 'r6-rehearsal-012345abcdef'
        after = receipts(plan, owner)
        feta.verify(plan, after, owner)
        sql = feta.undo_sql(plan, after, owner, '2026-10-14T10:00:00+00:00')
        self.assertIn("current_database()<>'r6_rehearsal'", sql)
        self.assertIn('Global Feta alias claims changed', sql)
        self.assertIn('SHARE ROW EXCLUSIVE', sql)
        self.assertNotIn('UPDATE food_items SET', sql)
        self.assertNotIn('UPDATE food_item_search_aliases SET', sql)
        self.assertIn('WHERE id=999 AND source=', sql)

    def test_changed_food_serving_category_external_alias_or_owned_alias_blocks_undo(self):
        owner = 'r6-rehearsal-012345abcdef'
        for mutation in ('food', 'portion', 'category', 'external', 'owner'):
            plan = fixture()
            after = receipts(plan, owner)
            if mutation == 'food':
                after[1]['food']['calories'] += 1
            elif mutation == 'portion':
                after[1]['name_review']['options'].append({'id': 1})
            elif mutation == 'category':
                after[1]['assignments'].append({'category_id': 1})
            elif mutation == 'external':
                after[1]['name_review']['aliases'][0]['active'] = False
            else:
                after[0]['name_review']['aliases'][0]['source'] = 'not-owned'
            with self.assertRaises(ValueError):
                feta.undo_sql(plan, after, owner, '2026-10-14T10:00:00+00:00')


if __name__ == '__main__':
    unittest.main()

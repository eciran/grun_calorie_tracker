import copy
import json
import tempfile
import unittest
from pathlib import Path
from importlib.util import module_from_spec, spec_from_file_location


spec = spec_from_file_location('identity_tests_prepare', Path(__file__).with_name('prepare-r6-identity-repairs.py'))
prepare = module_from_spec(spec)
spec.loader.exec_module(prepare)
proof = prepare.module('identity_tests_rehearsal', 'rehearse-r6-identity-repairs.py')


def plan_fixture():
    registry = {r['id']: r for r in prepare.names.held.read_json(prepare.names.REGISTRY)['entries']}
    rows, claims = [], []
    for identifier, expected in prepare.IDENTITIES.items():
        decision = registry[identifier]
        aliases = [{'id': i, 'food_item_id': int(identifier), 'alias': 'Old misleading alias',
                    'normalized_alias': 'old misleading alias ' + str(i), 'language': 'EN' if i == 402 else 'TR',
                    'active': True, 'alias_type': 'ADMIN_MANUAL', 'source': 'old', 'created_at': '2026-10-01'}
                   for i in ([401, 402] if identifier == '49682' else [2524])]
        locales = [{'id': int(identifier) + i, 'food_item_id': int(identifier), 'language': language,
                    'display_name': decision['currentName'], 'short_display_name': decision['currentName'],
                    'active': True, 'source': 'old', 'created_at': '2026-10-01', 'updated_at': '2026-10-01'}
                   for i, language in enumerate(('EN', 'TR'))]
        food = {'id': int(identifier), 'name': decision['currentName'], 'display_name': decision['currentName'],
                'short_display_name': decision['currentName'], 'canonical_food_key': expected['old'],
                'source_key': decision['sourceKey'], 'catalog_type': 'GENERIC_INGREDIENT', 'data_source': 'USDA_FOODDATA',
                'is_custom': False, 'publication_status': 'PUBLISHED', 'preparation_state': expected['state'], 'calories': 361}
        before = {'id': identifier, 'food': food, 'assignments': [], 'source_tags': [], 'evidence': [],
                  'name_review': {'aliases': aliases, 'localizations': locales, 'options': [], 'option_localizations': []}}
        alias_actions = [{'action': 'DEACTIVATE_EXACT_MISLEADING_ALIAS', 'before': a} for a in aliases]
        for language, label in (('EN', decision['en']), ('TR', decision['tr'])):
            alias_actions.append({'action': 'ADD_SOURCE_QUALIFIED_ALIAS', 'language': language,
                                 'alias': label, 'normalized_alias': prepare.names.aliases.normalize_alias(label)})
        rows.append({'id': identifier, 'sourceKey': decision['sourceKey'], 'sourceDescription': expected['sourceDescription'],
                     'expectedBefore': before, 'beforeSha256': prepare.fingerprint(before),
                     'proposedNames': {'EN': decision['en'], 'TR': decision['tr']},
                     'foodChanges': {**{k: decision['en'] for k in ('name', 'display_name', 'short_display_name')},
                                     'canonical_food_key': expected['new']},
                     'localizationActions': [{'action': 'UPDATE_EXACT_ACTIVE_LOCALIZATION', 'before': locale,
                         'changes': {k: decision['en'] if locale['language'] == 'EN' else decision['tr']
                                     for k in ('display_name', 'short_display_name')}} for locale in locales],
                     'aliasActions': alias_actions})
        claims.append({'id': int(identifier), 'source_key': decision['sourceKey'], 'canonical_food_key': expected['old'],
                       'is_custom': False, 'publication_status': 'PUBLISHED'})
    claims.append({'id': 179890, 'source_key': 'USDA_FOODDATA:fdc:171413', 'canonical_food_key': prepare.IDENTITIES['49682']['old'],
                   'is_custom': False, 'publication_status': 'PUBLISHED'})
    refs = [{'schema': 'public', 'table': table, 'column': column, 'constraint': 'fk_' + table, 'supported': True,
             'count': str(count), 'fingerprint': 'a' * 32}
            for table, column, count in [('food_logs', 'food_id', 1), ('recipe_ingredients', 'food_item_id', 1),
                                          ('food_item_localizations', 'food_item_id', 4), ('food_item_search_aliases', 'food_item_id', 3)]]
    deps = {'identityDependencies': refs, 'identityKeyClaims': claims, 'identityKeyIndexes': [{'name': 'nonunique_legacy_index'}],
            'canonicalResolutions': [{'canonical_food_key': prepare.IDENTITIES['49682']['old'], 'primary_food_item_id': 179890}],
            'historyLinks': [{'id': 49682, 'recipes': 1, 'logs': 1}, {'id': 181002, 'recipes': 0, 'logs': 0}]}
    return {'version': 'R6_TWO_IDENTITY_REPAIR_PREPARATION_V1', 'liveApplyAllowed': False, 'approval': None, 'backup': None,
            'schemaVersion': '305', 'rows': rows, 'dependencyBefore': deps}


def receipt_fixture(plan, owner):
    rows = []
    for row in plan['rows']:
        after = copy.deepcopy(row['expectedBefore'])
        after['food'].update(row['foodChanges'])
        for action in row['localizationActions']:
            next(l for l in after['name_review']['localizations'] if l['id'] == action['before']['id']).update(action['changes'], updated_at='2026-10-13')
        for a in after['name_review']['aliases']:
            a['active'] = False
        for i, action in enumerate(a for a in row['aliasActions'] if a['action'].startswith('ADD')):
            after['name_review']['aliases'].append({'id': 8000000 + int(row['id']) + i, 'food_item_id': int(row['id']),
                **{k: action[k] for k in ('language', 'alias', 'normalized_alias')}, 'source': owner,
                'alias_type': 'ADMIN_MANUAL', 'active': True, 'created_at': '2026-10-13'})
        rows.append(after)
    deps = copy.deepcopy(plan['dependencyBefore'])
    for claim in deps['identityKeyClaims']:
        if str(claim['id']) in prepare.IDENTITIES:
            claim['canonical_food_key'] = prepare.IDENTITIES[str(claim['id'])]['new']
    next(r for r in deps['identityDependencies'] if r['table'] == 'food_item_search_aliases')['count'] = '7'
    return {'kind': 'identity_receipts', 'owner': owner, 'rows': rows, 'dependencies': deps,
            'dependencyBeforeSha256': prepare.fingerprint(plan['dependencyBefore'])}


class IdentityRepairTests(unittest.TestCase):
    def test_exact_plan_preserves_source_numeric_ids_and_separate_oil_winner(self):
        plan = plan_fixture()
        unchanged = copy.deepcopy(plan)
        prepare.validate_plan(plan)
        self.assertEqual(unchanged, plan)
        self.assertFalse(plan['liveApplyAllowed'])

    def test_new_target_claim_blocks_preparation_even_unpublished_custom_product(self):
        plan = plan_fixture()
        plan['dependencyBefore']['identityKeyClaims'].append({'id': 999, 'source_key': 'other',
            'canonical_food_key': prepare.IDENTITIES['49682']['new'], 'is_custom': True, 'publication_status': 'DRAFT'})
        with self.assertRaises(ValueError):
            prepare.validate_plan(plan)

    def test_duplicate_source_key_blocks_preparation(self):
        plan = plan_fixture()
        plan['dependencyBefore']['identityKeyClaims'].append({**plan['dependencyBefore']['identityKeyClaims'][0], 'id': 999})
        with self.assertRaises(ValueError):
            prepare.validate_plan(plan)

    def test_oil_winner_resolution_cannot_be_reassigned_or_removed(self):
        for value in ([], [{'canonical_food_key': prepare.IDENTITIES['49682']['old'], 'primary_food_item_id': 49682}]):
            plan = plan_fixture()
            plan['dependencyBefore']['canonicalResolutions'] = value
            with self.assertRaises(ValueError):
                prepare.validate_plan(plan)

    def test_numeric_nutrient_unit_and_source_updates_are_forbidden(self):
        for field in ('calories', 'serving_unit', 'source_key', 'id', 'preparation_state', 'food_concept_key'):
            plan = plan_fixture()
            plan['rows'][0]['foodChanges'][field] = 'changed'
            with self.assertRaises(ValueError):
                prepare.validate_plan(plan)

    def test_incomplete_before_image_wrong_source_or_key_cannot_pass(self):
        for field in ('source_key', 'canonical_food_key', 'preparation_state', 'catalog_type', 'publication_status'):
            plan = plan_fixture()
            plan['rows'][0]['expectedBefore']['food'][field] = 'changed'
            plan['rows'][0]['beforeSha256'] = prepare.fingerprint(plan['rows'][0]['expectedBefore'])
            with self.assertRaises(ValueError):
                prepare.validate_plan(plan)

    def test_source_description_and_localized_name_are_exact_not_fuzzy(self):
        for field in ('sourceDescription', 'proposedNames'):
            plan = plan_fixture()
            plan['rows'][0][field] = 'changed' if field == 'sourceDescription' else {'EN': 'Olive Oil', 'TR': 'Zeytinyağı'}
            with self.assertRaises(ValueError):
                prepare.validate_plan(plan)

    def test_unknown_composite_or_unsafe_reference_cannot_be_skipped(self):
        for change in ({'supported': False}, {'table': 'food_logs;delete'}, {'column': 'food_id;drop'},
                       {'fingerprint': ''}, {'count': '-1'}, {'schema': 'private'}):
            plan = plan_fixture()
            plan['dependencyBefore']['identityDependencies'][0].update(change)
            with self.assertRaises(ValueError):
                prepare.validate_plan(plan)

    def test_per_product_history_totals_must_match_complete_fk_measurement(self):
        plan = plan_fixture()
        plan['dependencyBefore']['historyLinks'][0]['logs'] = 0
        with self.assertRaises(ValueError):
            prepare.validate_plan(plan)

    def test_exact_alias_ids_and_owned_active_locale_pair_only(self):
        plan = plan_fixture()
        plan['rows'][0]['aliasActions'][0]['before']['id'] = 400
        with self.assertRaises(ValueError):
            prepare.validate_plan(plan)
        plan = plan_fixture()
        plan['rows'][0]['localizationActions'][0]['before']['food_item_id'] = 1
        with self.assertRaises(ValueError):
            prepare.validate_plan(plan)

    def test_wrong_subset_repeated_id_live_approval_or_schema_drift_blocks(self):
        for field, value in (('liveApplyAllowed', True), ('approval', 'yes'), ('schemaVersion', '307'), ('backup', 'unverified')):
            plan = plan_fixture()
            plan[field] = value
            with self.assertRaises(ValueError):
                prepare.validate_plan(plan)
        plan = plan_fixture()
        plan['rows'][1] = copy.deepcopy(plan['rows'][0])
        with self.assertRaises(ValueError):
            prepare.validate_plan(plan)

    def test_receipts_and_undo_preserve_protected_fields_and_owned_aliases(self):
        plan = plan_fixture()
        owner = 'r6-rehearsal-012345abcdef'
        receipt = receipt_fixture(plan, owner)
        proof.verify(plan, receipt, owner, plan['dependencyBefore'])
        sql = proof.undo_sql(plan, receipt, plan['dependencyBefore'], owner, '2026-10-14T01:00:00+00:00')
        self.assertIn("current_database()<>'r6_rehearsal'", sql)
        self.assertIn('Dependency/key/history drift', sql)
        self.assertIn('SHARE ROW EXCLUSIVE', sql)
        self.assertNotIn('UPDATE food_logs', sql)
        self.assertNotIn('UPDATE recipe_ingredients', sql)

    def test_missing_or_tampered_receipts_cannot_generate_undo(self):
        owner = 'r6-rehearsal-012345abcdef'
        for change in ('owner', 'nutrition', 'portion', 'alias', 'oil', 'history', 'before_receipt'):
            plan = plan_fixture()
            receipt = receipt_fixture(plan, owner)
            if change == 'owner':
                receipt['owner'] = 'other'
            elif change == 'nutrition':
                receipt['rows'][0]['food']['calories'] += 1
            elif change == 'portion':
                receipt['rows'][0]['name_review']['options'].append({'id': 1})
            elif change == 'alias':
                receipt['rows'][0]['name_review']['aliases'][-1]['source'] = 'not-owned'
            elif change == 'oil':
                receipt['dependencies']['canonicalResolutions'] = []
            elif change == 'history':
                receipt['dependencies']['identityDependencies'][0]['fingerprint'] = 'b' * 32
            else:
                receipt['dependencyBeforeSha256'] = 'unbound'
            with self.assertRaises(ValueError):
                proof.verify(plan, receipt, owner, plan['dependencyBefore'])

    def test_read_only_query_is_exact_bounded_and_has_complete_dependency_and_history_proofs(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'selection.jsonl'
            path.write_text(''.join(json.dumps({'id': int(i), 'requestedAliases': [{'language': 'EN', 'normalized_alias': "o'brien"}]}) + '\n' for i in prepare.IDENTITIES), encoding='utf-8')
            sql = prepare.names.collector.identity_review_sql(path)
            self.assertIn('REPEATABLE READ READ ONLY', sql)
            self.assertIn("'o''brien'", sql)
            self.assertIn("c.confrelid='food_items'::regclass", sql)
            self.assertIn('canonicalResolutions', sql)
            self.assertIn('historyLinks', sql)
            self.assertTrue(sql.rstrip().endswith('ROLLBACK;'))
            self.assertNotIn('CREATE ', sql)
            path.write_text(json.dumps({'id': 49682, 'requestedAliases': []}), encoding='utf-8')
            with self.assertRaises(ValueError):
                prepare.names.collector.identity_review_sql(path)


if __name__ == '__main__':
    unittest.main()

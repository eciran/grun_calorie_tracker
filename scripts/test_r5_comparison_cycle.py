import copy
import csv
import importlib.util
import json
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


cycle = module('r5_cycle_tests', 'audit-r5-comparison-cycle.py')
query = module('r5_query_tests', 'r5-current-comparison-query.py')
fixture = module('r5_cycle_fixtures', 'test_r5_release_review.py')
NOW = datetime(2026, 10, 15, 12, tzinfo=timezone.utc)


class ComparisonCycleTests(unittest.TestCase):
    def args(self, output, resume=False):
        return SimpleNamespace(review=Path('outputs/r5-nutrient-holds-20261015/review-final/source-review.json'),
            usda_folder=[Path('outputs/r5-source-portion-followup-20261015', f) for f in ('usda-fresh', 'usda-large-fresh')],
            off_folder=Path('outputs/r5-source-portion-followup-20261015/off-fresh/items'), snapshot=None,
            snapshot_proof=None, archived=True, resume=resume, output=output)

    def run_archived_fixture(self, args, time=NOW):
        class FixtureClock(datetime):
            @classmethod
            def now(cls, tz=None): return time
        # Fixture clock tests resume/expiry without altering any real source receipt.
        with patch.object(cycle, 'datetime', FixtureClock), patch('builtins.print'):
            cycle.run(args)

    def test_four_real_archived_stages_resume_without_changing_receipts(self):
        with tempfile.TemporaryDirectory() as folder:
            output = Path(folder)/'proof'
            self.run_archived_fixture(self.args(output))
            hashes = {p.name: cycle.sha(p) for p in output.glob('stage-*.json')}
            self.run_archived_fixture(self.args(output, True))
            self.assertEqual(hashes, {p.name: cycle.sha(p) for p in output.glob('stage-*.json')})
            report = cycle.read(output/'cycle-report.json')
            self.assertEqual(4, report['completedStages']); self.assertEqual(5502, report['stageSummaries'][0]['fieldComparisons'])
            self.assertFalse(report['stageSummaries'][0]['currentDatabaseMeasured'])
            self.assertFalse(report['applyAllowed']); self.assertFalse(report['globalR5Done'])
            with self.assertRaises(ValueError): self.run_archived_fixture(self.args(output))

    def test_resume_refuses_expiry_code_drift_and_tampered_stage(self):
        with tempfile.TemporaryDirectory() as folder:
            output = Path(folder)/'proof'
            self.run_archived_fixture(self.args(output))
            with self.assertRaises(ValueError): self.run_archived_fixture(self.args(output, True), NOW + timedelta(days=2))
            real = cycle.sha
            with patch.object(cycle, 'sha', side_effect=lambda p: '0'*64 if p.name == 'r5-qualified-source-portions.py' else real(p)):
                with self.assertRaises(ValueError): self.run_archived_fixture(self.args(output, True))
            path = output/'stage-2.json'; path.write_text(path.read_text(encoding='utf-8')+' ', encoding='utf-8')
            with self.assertRaises(ValueError): self.run_archived_fixture(self.args(output, True))

    def test_resume_rejects_nonsequential_or_external_stage_paths(self):
        with tempfile.TemporaryDirectory() as folder:
            output = Path(folder)/'proof'
            self.run_archived_fixture(self.args(output))
            path = output/'checkpoint.json'; checkpoint = cycle.read(path)
            checkpoint['stages'][0]['file'] = '../outside.json'
            cycle.save(path, checkpoint)
            with self.assertRaises(ValueError): self.run_archived_fixture(self.args(output, True))

    def test_no_current_claim_without_current_snapshot_and_original_stale_data_stays_held(self):
        with tempfile.TemporaryDirectory() as folder:
            args = self.args(Path(folder)/'no-current'); args.archived = False
            with self.assertRaises(ValueError): self.run_archived_fixture(args)
            output = Path(folder)/'stale-proof'
            self.run_archived_fixture(self.args(output), NOW + timedelta(days=2))
            first = cycle.read(output/'stage-1.json'); final = cycle.read(output/'stage-4.json')
            self.assertEqual(0, first['summary']['freshSourceProducts'])
            self.assertEqual(262, final['summary']['holds']['SOURCE_REFRESH_REQUIRED'])
            self.assertEqual(0, final['summary']['releaseReadyProducts'])

    def data(self):
        decision, current, body = fixture.usda_fixture()
        original = {'id': '1', 'sourceKey': decision['sourceKey'], 'before': copy.deepcopy(current),
                    'workQueue': 'NUTRIENT_UNIT_FORM_OR_DEFINITION_REVIEW', 'holds': ['OLD_HOLD'],
                    'sourceFingerprint': cycle.gate.source.r5.fingerprint(body)}
        body['dataType'] = 'SR Legacy'
        current['food']['id'] = 1
        original['before']['food']['id'] = 1
        return original, current, (body, '2026-10-15T11:00:00Z')

    def compared(self):
        old, current, source = self.data()
        return cycle.compare_row(old, current, source, False, NOW), source[0]

    def test_all_fields_and_original_holds_retained_without_unit_approval(self):
        old, current, source = self.data()
        before = copy.deepcopy(current)
        row = cycle.compare_row(old, current, source, False, NOW)
        self.assertEqual(before, current)
        self.assertEqual(21, len(row['numericComparisons']))
        self.assertEqual(['OLD_HOLD'], row['originalHolds'])
        self.assertFalse(row['currentDatabaseMeasured'])
        self.assertIsNone(row['runtimeApprovalFingerprint'])
        self.assertFalse(row['nutritionWriteAllowed'])

    def test_changed_product_id_source_key_or_provider_refused(self):
        for field, value in [('id', 2), ('source_key', 'USDA_FOODDATA:fdc:999'), ('data_source', 'OTHER')]:
            old, current, source = self.data()
            current['food'][field] = value
            if field == 'id':
                with self.assertRaises(ValueError): cycle.compare_row(old, current, source, True, NOW)
            else:
                row = cycle.compare_row(old, current, source, True, NOW)
                self.assertFalse(row['sourceBindingMatchesCurrent'])
                self.assertTrue(all(f is None for f in row['sourceFacts'].values()))
                self.assertFalse(row['sourceFresh'])
                self.assertEqual([], cycle.portion_review(row, source[0])['sourceProposals'])

    def test_all_parent_nutrients_and_name_drift_reported_but_usage_ignored(self):
        old, current, source = self.data()
        for field in cycle.FIELDS: current['food'][field] = 123
        current['food']['name'] = 'New name'
        current['food']['usage_count'] = 99
        row = cycle.compare_row(old, current, source, True, NOW)
        self.assertTrue(set(cycle.FIELDS).issubset(row['archivedParentDrift']))
        self.assertIn('name', row['archivedParentDrift'])
        self.assertNotIn('usage_count', row['archivedParentDrift'])

    def test_stale_future_naive_and_malformed_times_are_never_fresh(self):
        for value in ('2026-10-13T11:00:00Z', '2026-10-16T11:00:00Z', '2026-10-15T11:00:00', None, 'bad'):
            self.assertFalse(cycle.fresh(value, NOW))
        self.assertTrue(cycle.fresh('2026-10-15T11:00:00Z', NOW))

    def test_decimal_and_bool_source_amount_guards(self):
        for value in (True, None, '1', float('nan'), float('inf'), -1, 1e101):
            with self.assertRaises(ValueError): cycle.numeric(value)
        self.assertEqual(0, cycle.numeric(0))

    def test_exact_source_forms_duplicates_and_numbers_not_nearest_match(self):
        body = {'foodNutrients': [{'nutrient': {'id': 1106, 'number': '320', 'unitName': 'UG', 'name': 'Vitamin A, RAE'}, 'amount': 6}]}
        self.assertEqual('MCG_RAE', cycle.source_fact('vitamin_a', 'USDA_FOODDATA', body)['canonicalUnit'])
        for prop, value in [('number', '318'), ('unitName', 'IU'), ('name', 'Vitamin A')]:
            bad = copy.deepcopy(body); bad['foodNutrients'][0]['nutrient'][prop] = value
            with self.assertRaises(ValueError): cycle.source_fact('vitamin_a', 'USDA_FOODDATA', bad)
        body['foodNutrients'] *= 2
        with self.assertRaises(ValueError): cycle.source_fact('vitamin_a', 'USDA_FOODDATA', body)

    def test_energy_definition_priority_is_explicit_and_not_selected_by_current_number(self):
        body = {'foodNutrients': [{'nutrient': {'id': nid, 'number': num, 'unitName': 'kcal'}, 'amount': amount}
                                for nid, num, amount in [(2048, '958', 90), (2047, '957', 80), (1008, '208', 100)]]}
        self.assertEqual(1008, cycle.source_fact('calories', 'USDA_FOODDATA', body)['sourceNutrientId'])
        body['foodNutrients'].pop()
        self.assertEqual(2048, cycle.source_fact('calories', 'USDA_FOODDATA', body)['sourceNutrientId'])

    def test_off_sodium_native_basis_and_missing_fiber_remain_separate(self):
        body = {'nutrition_data_per': '100ml', 'nutriments': {'sodium_100g': .005, 'sodium_unit': 'g'}}
        fact = cycle.source_fact('sodium', 'OPEN_FOOD_FACTS', body)
        self.assertEqual('5.000', fact['canonicalAmount']); self.assertEqual('PER_100ML', fact['basis'])
        self.assertIsNone(cycle.source_fact('fiber', 'OPEN_FOOD_FACTS', body))
        body['nutriments'].update({'fiber_100g': 0, 'fiber_unit': 'g'})
        self.assertEqual('0', cycle.source_fact('fiber', 'OPEN_FOOD_FACTS', body)['canonicalAmount'])

    def test_off_inequality_wrong_unit_display_conflict_and_a_form_refused(self):
        base = {'nutrition_data_per': '100g', 'nutriments': {'sodium_100g': .005, 'sodium_unit': 'g'}}
        for key, value in [('sodium_unit', 'mg'), ('sodium_modifier', '<'), ('sodium_value', .006), ('sodium_100g', True)]:
            body = copy.deepcopy(base); body['nutriments'][key] = value
            with self.assertRaises(ValueError): cycle.source_fact('sodium', 'OPEN_FOOD_FACTS', body)
        body = {'nutrition_data_per': '100g', 'nutriments': {'vitamin-a_100g': .001, 'vitamin-a_unit': 'g'}}
        with self.assertRaises(ValueError): cycle.source_fact('vitamin_a', 'OPEN_FOOD_FACTS', body)

    def test_inequality_does_not_disappear_when_current_value_is_unknown(self):
        row, body = self.compared()
        row['before']['food']['fiber'] = None
        row['sourceFactHolds']['fiber'] = 'Inequality'
        review = cycle.unit_review(row, body)
        fiber = next(f for f in review['fields'] if f['field'] == 'fiber')
        self.assertEqual('SOURCE_UNIT_OR_FORM_REVIEW', fiber['state'])
        self.assertFalse(fiber['legacyUnitAttested'])

    def test_source_options_keep_old_ids_labels_defaults_and_no_ml_guess(self):
        row, body = self.compared()
        body['foodPortions'] = [{'id': 55, 'amount': 1, 'gramWeight': 140, 'modifier': 'cup, diced', 'measureUnit': {'name': 'undetermined'}}]
        row['before']['options'] = [{'id': 99, 'label': 'Old option', 'gram_weight': 140, 'ml_volume': None, 'default_option': True}]
        saved = copy.deepcopy(row['before'])
        out = cycle.portion_review(row, body)
        self.assertEqual(saved, row['before'])
        self.assertEqual(saved['options'], out['oldOptionsRetained'])
        self.assertIsNone(out['sourceProposals'][0]['sourceProposal']['mlVolume'])
        self.assertIn('ABD ölçü kabı', out['sourceProposals'][0]['sourceProposal']['labels']['TR'])
        self.assertNotIn('bardak', out['sourceProposals'][0]['sourceProposal']['labels']['TR'])

    def test_final_gate_never_clears_historical_or_runtime_approval_holds(self):
        row, _ = self.compared()
        out = cycle.stage(4, [row], {})
        self.assertIn('OLD_HOLD', out['rows'][0]['holds'])
        self.assertIn('FRESH_CURRENT_DB_REQUIRED', out['rows'][0]['holds'])
        self.assertEqual(0, out['summary']['releaseReadyProducts'])

    def test_qualified_portions_keep_skin_shape_and_reject_unknown_dimensions(self):
        for descriptor in ('fillet', 'thigh without skin', 'slice, thin', 'cup slices', 'cup, chopped'):
            portion = {'id': 1, 'amount': 1, 'gramWeight': 110, 'modifier': descriptor, 'measureUnit': {'name': 'undetermined'}}
            option, error = cycle.qualified.proposal(portion)
            self.assertIsNone(error); self.assertEqual(110, option['gramWeight']); self.assertIsNone(option['mlVolume'])
            self.assertEqual(descriptor, option['sourceDescriptor']); self.assertFalse(option['userVisible'])
        for descriptor in ('fl oz', 'cup water unknown', 'package yields', 'RACC'):
            option, error = cycle.qualified.proposal({'id': 1, 'amount': 1, 'gramWeight': 110, 'modifier': descriptor})
            self.assertIsNone(option); self.assertIsNotNone(error)

    def test_query_is_exact_readonly_bounded_and_hashes_history_instead_of_exposing_it(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)/'ids.jsonl'
            path.write_text('{"id":2}\n{"id":1}', encoding='utf-8')
            sql = query.build(path)
            self.assertIn('f.id IN (1,2)', sql)
            self.assertIn('REPEATABLE READ READ ONLY', sql)
            self.assertEqual(29, sql.count("'rowHashes'"))
            self.assertIn('LIMIT 4097', sql); self.assertIn('<=4096', sql)
            for term in ('INSERT ', 'UPDATE ', 'DELETE ', 'CREATE ', 'DROP ', 'TRUNCATE ', 'FOR UPDATE'):
                self.assertNotIn(term, sql.upper())
            self.assertNotIn('FROM food_logs', sql)
            self.assertIn('to_jsonb(d)', sql)
            for invalid in ('{"id":true}', '{"id":"1);DROP TABLE food_items"}', '{"id":1}\n{"id":1}', ''):
                path.write_text(invalid, encoding='utf-8')
                with self.assertRaises(ValueError): query.build(path)

    def test_duplicate_json_keys_and_nonfinite_values_are_not_silently_accepted(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)/'bad.json'
            for text in ('{"id":1,"id":2}', '{"value":NaN}'):
                path.write_text(text)
                with self.assertRaises(ValueError): cycle.read(path)

    def test_current_snapshot_requires_exact_count_hash_age_schema_and_readonly_receipt(self):
        with tempfile.TemporaryDirectory() as folder:
            path, proof_path = Path(folder)/'fixture.csv', Path(folder)/'fixture-proof.json'
            with path.open('w', encoding='utf-8', newline='') as stream:
                writer = csv.DictWriter(stream, fieldnames=['id', 'food'])
                writer.writeheader(); writer.writerow({'id': 1, 'food': json.dumps({'id': 1})})
            proof = {'origin': 'AWS_READ_ONLY_SNAPSHOT', 'transactionReadOnly': True, 'sha256': cycle.sha(path),
                     'scope': 'r5-comparison', 'schemaVersion': '311', 'time': '2026-10-15T11:00:00Z', 'rows': 1, 'exitCode': 0}
            # Synthetic receipt for parser checks ONLY; no AWS data or real measurement is claimed.
            cycle.save(proof_path, proof)
            self.assertEqual(1, cycle.load_snapshot(path, proof_path, [{'id': '1'}], NOW)['1']['id'])
            for key, value in [('sha256', 'bad'), ('rows', 2), ('schemaVersion', '999'), ('exitCode', 1),
                               ('transactionReadOnly', False), ('time', '2026-10-12T11:00:00Z')]:
                cycle.save(proof_path, {**proof, key: value})
                with self.assertRaises(ValueError): cycle.load_snapshot(path, proof_path, [{'id': '1'}], NOW)


if __name__ == '__main__': unittest.main()

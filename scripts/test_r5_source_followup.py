import copy
import io
import importlib.util
import json
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import Mock, patch as mock_patch


def module(name, file):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(file))
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


followup = module('followup_tests', 'review-r5-source-followup.py')
fixtures = module('followup_fixtures', 'test_r5_release_review.py')
fdc = module('followup_fdc_tests', 'fetch-r3-usda-evidence.py')
patch = module('followup_patch_tests', 'build-r5-controlled-patch.py')
NOW = datetime(2026, 10, 12, 11, tzinfo=timezone.utc)


class FollowupTests(unittest.TestCase):
    def test_work_queue_is_exclusive_and_does_not_hide_other_blockers(self):
        base = {'disposition': 'HELD', 'holds': ['CURRENT_NAME_SOURCE_IDENTITY_REVIEW', 'FULL_NUTRITION_COMPARISON_REQUIRED'],
                'nutrients': {'vitamin_a': {'state': 'UNIT_OR_FORM_ATTESTATION_REQUIRED'}}}
        self.assertEqual('SOURCE_IDENTITY_AND_NAME_REVIEW', followup.work_queue(base))
        base['holds'].append('R3_R4_CLASSIFICATION_OR_PREPARATION_GATE')
        self.assertEqual('CLASSIFICATION_OR_PREPARATION_PREREQUISITE', followup.work_queue(base))
        base['disposition'] = 'SUPERSEDED_BASELINE_REBASE_REQUIRED'
        self.assertEqual('REBASE_LATER_APPLIED_RECORDS', followup.work_queue(base))
        base['disposition'] = 'ARCHIVED_CONTRACT_PASS_REBASE_REQUIRED'
        self.assertEqual('PREPARED_REBASE_AND_APPROVAL', followup.work_queue(base))
    def inputs(self):
        decision, current, body = fixtures.usda_fixture()
        return {'id': '1', 'sourceKey': decision['sourceKey'], 'before': current}, decision, body

    def test_fresh_source_never_grants_live_or_current_database_approval(self):
        original, previous, body = self.inputs()
        before = copy.deepcopy(original)
        result = followup.review_row(original, previous, body, '2026-10-12T10:00:00Z', NOW)
        self.assertEqual('ARCHIVED_CONTRACT_PASS_REBASE_REQUIRED', result['disposition'])
        self.assertTrue(result['currentDatabaseBeforeImageRequired'])
        self.assertFalse(result['liveApplyApproved'])
        self.assertFalse(result['applyAllowed'])
        self.assertFalse(result['nutritionWriteAllowed'])
        self.assertFalse(result['legacyMicronutrientUnitsCertified'])
        self.assertEqual(before, original)
        self.assertEqual('REVIEW_ONLY_ARCHIVED_BASELINE', result['status'])
        with self.assertRaises(ValueError):
            patch.generate([result], 'r5-unsafe-source', '2026-10-12T12:00:00Z')

    def test_later_applied_receipt_quarantines_an_otherwise_passing_old_row(self):
        original, previous, body = self.inputs()
        result = followup.review_row(original, previous, body, '2026-10-12T10:00:00Z', NOW, superseded=True)
        self.assertEqual('SUPERSEDED_BASELINE_REBASE_REQUIRED', result['disposition'])
        self.assertFalse(result['applyAllowed'])

    def test_new_source_profile_is_rebuilt_but_current_values_are_not_filled(self):
        original, previous, body = self.inputs()
        body['foodNutrients'][1]['amount'] = 9
        result = followup.review_row(original, previous, body, '2026-10-12T10:00:00Z', NOW)
        self.assertTrue(result['sourceBodyChanged'])
        self.assertEqual('HELD', result['disposition'])
        self.assertEqual(0, result['before']['food']['protein'])
        self.assertIn('FULL_NUTRITION_COMPARISON_REQUIRED', result['holds'])

    def test_stale_source_or_wrong_historical_identity_cannot_pass(self):
        original, previous, body = self.inputs()
        result = followup.review_row(original, previous, body, '2026-10-10T10:00:00Z', NOW)
        self.assertEqual('HELD', result['disposition'])
        original['sourceKey'] = 'USDA_FOODDATA:fdc:999'
        with self.assertRaises(ValueError):
            followup.review_row(original, previous, body, '2026-10-12T10:00:00Z', NOW)

    def test_bad_usda_batch_bound_is_rejected_before_any_file_or_key_access(self):
        from types import SimpleNamespace
        for size in (0, 21, True, .5):
            with self.subTest(size=size), self.assertRaises(ValueError):
                fdc.fetch(SimpleNamespace(batch_size=size))

    def test_small_source_batches_keep_exact_ids_receipts_and_credentials_private(self):
        from types import SimpleNamespace
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            catalog = root / 'queue.csv'
            catalog.write_text('source_key\n' + ''.join(f'USDA_FOODDATA:fdc:{i}\n' for i in range(1, 6)))
            args = SimpleNamespace(batch_size=2, catalog=catalog, output=root / 'out',
                                   env_file=root / '.env', resolved_address=None, resume=False)
            opener = Mock()
            def response(request, timeout):
                ids = json.loads(request.data)['fdcIds']
                return io.BytesIO(json.dumps([{'fdcId': i, 'description': f'Food {i}', 'dataType': 'SR Legacy'} for i in ids]).encode())
            opener.open.side_effect = response
            with mock_patch.object(fdc, 'load_key', return_value='test-secret-not-for-output'), \
                    mock_patch.object(fdc.urllib.request, 'build_opener', return_value=opener), \
                    mock_patch('sys.stdout', new_callable=io.StringIO):
                fdc.fetch(args)
            result = json.loads(args.output.joinpath('evidence.json').read_text())
            self.assertEqual(5, result['matchedCount'])
            self.assertEqual([2, 2, 1], [len(b['ids']) for b in result['batchFiles']])
            self.assertEqual(['1', '2', '3', '4', '5'], result['requestedIds'])
            self.assertEqual(3, opener.open.call_count)
            self.assertNotIn('test-secret-not-for-output', args.output.joinpath('evidence.json').read_text())


if __name__ == '__main__': unittest.main()

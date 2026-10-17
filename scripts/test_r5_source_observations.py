import copy
import importlib.util
import json
import unittest
from datetime import datetime, timezone, timedelta
from pathlib import Path

spec = importlib.util.spec_from_file_location('r5_source_observation_preparation', Path(__file__).with_name('prepare-r5-source-observations.py'))
tool = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tool)


class SourceObservationPreviewTest(unittest.TestCase):
    def setUp(self):
        self.row = copy.deepcopy(json.loads(Path('outputs/r5-nutrient-differences-20261015/review-accepted/typed-difference-review.json').read_text(encoding='utf-8'))['rows'][0])
        bodies = {}
        for folder in ('usda-fresh', 'usda-large-fresh'):
            for path in Path('outputs/r5-source-portion-followup-20261015', folder).glob('batch-*.json'):
                if not path.name.endswith('.receipt.json'):
                    for body in json.loads(path.read_text(encoding='utf-8')):
                        bodies['USDA_FOODDATA:fdc:' + str(body['fdcId'])] = body
        if self.row['provider'] == 'USDA_FOODDATA':
            self.body = bodies[self.row['sourceKey']]
        else:
            code = self.row['sourceKey'].removeprefix('barcode:')
            self.body = json.loads(Path('outputs/r5-source-portion-followup-20261015/off-fresh/items', 'barcode-' + code + '.json').read_text(encoding='utf-8'))['product']
        self.stamp = self.row['sourceCheckedAt']
        # Fixture clock only: this does not refresh the archived receipt.
        self.now = datetime.fromisoformat(self.stamp.replace('Z', '+00:00')) + timedelta(seconds=60)

    def test_original_time_and_holds_preserved_no_approval_or_archived_fingerprint_reuse(self):
        original = copy.deepcopy(self.row)
        rows, missing = tool.pack(self.row, self.body, self.stamp, self.now)
        self.assertTrue(rows or missing)
        self.assertEqual(original, self.row)
        for row in rows:
            request = row['previewRequest']
            self.assertFalse(request['approved'])
            self.assertIsNone(request['expectedCurrentFingerprint'])
            self.assertEqual(self.stamp, request['observedAt'])
            self.assertEqual(self.row['releaseHolds'], row['releaseHolds'])
            self.assertFalse(row['nutritionWriteAllowed'])

    def test_expired_future_or_changed_timestamp_refused(self):
        for now in (self.now + timedelta(days=2), self.now - timedelta(days=2)):
            with self.assertRaises(ValueError):
                tool.pack(self.row, self.body, self.stamp, now)
        with self.assertRaises(ValueError):
            tool.pack(self.row, self.body, self.now.isoformat(), self.now)

    def test_changed_source_identity_or_approval_refused(self):
        for key, value in (('applyAllowed', True), ('sourceFingerprint', 'bad'), ('sourceKey', 'barcode:999')):
            row = copy.deepcopy(self.row)
            row[key] = value
            with self.assertRaises(ValueError):
                tool.pack(row, self.body, self.stamp, self.now)

    def test_changed_fact_or_old_snapshot_refused(self):
        for key in ('sourceFact', 'before'):
            row = copy.deepcopy(self.row)
            if key == 'before':
                row[key]['food']['name'] = 'changed'
            else:
                row['fieldReviews'][0]['sourceFact'] = {'fabricated': True}
            with self.assertRaises(ValueError):
                tool.pack(row, self.body, self.stamp, self.now)


if __name__ == '__main__':
    unittest.main()

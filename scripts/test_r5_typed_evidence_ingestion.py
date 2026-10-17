import copy
import importlib.util
import json
import unittest
from datetime import timedelta
from pathlib import Path


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


ingestion = module('r5_ingestion_test', 'prepare-r5-typed-evidence-ingestion.py')
fixture = module('r5_ingestion_fixture', 'test_r5_typed_reattestation.py')


class TypedEvidenceIngestionTests(unittest.TestCase):
    def inputs(self):
        named, body, seed = fixture.inputs()
        row = fixture.review.review_row(named, body, fixture.contract(), fixture.NOW, seed)
        return row, body

    def test_preview_is_not_approval_or_old_snapshot_reuse(self):
        row, body = self.inputs()
        original = copy.deepcopy((row, body))
        result = ingestion.pack_row(row, body, row['sourceCheckedAt'], fixture.NOW)
        request = result['previewRequest']
        self.assertIsNone(request['expectedCurrentFingerprint'])
        self.assertFalse(request['approved'])
        self.assertFalse(result['applyAllowed'])
        self.assertFalse(result['legacyUnitAttested'])
        self.assertEqual('PRODUCT_PARENT_LEGACY_AND_FK_DEPENDENCIES_V2', result['requiredRuntimeSnapshotScope'])
        self.assertEqual(row['existingHolds'], result['existingHolds'])
        self.assertEqual(row['releaseHolds'], result['releaseHolds'])
        self.assertEqual(body, json.loads(request['sourceJson']))
        self.assertEqual(row['sourceCheckedAt'], request['observedAt'])
        self.assertEqual(original, (row, body))

    def test_expired_future_or_restamped_receipt_is_refused(self):
        row, body = self.inputs()
        for stamp, now in ((row['sourceCheckedAt'], fixture.NOW + timedelta(days=2)),
                           (row['sourceCheckedAt'], fixture.NOW - timedelta(days=2)),
                           (fixture.NOW.isoformat(), fixture.NOW)):
            with self.assertRaises(ValueError): ingestion.pack_row(row, body, stamp, now)

    def test_source_before_proposal_permission_drift_is_refused(self):
        for defect in ('source', 'before', 'proposal', 'permission', 'hold', 'legacy'):
            row, body = self.inputs()
            if defect == 'source': body['description'] = 'Another food'
            if defect == 'before': row['before']['food']['vitamin_a'] = 7
            if defect == 'proposal': row['newSourceEvidenceProposal']['canonicalUnit'] = 'IU'
            if defect == 'permission': row['applyAllowed'] = True
            if defect == 'hold': row['sourceReviewHolds'] = ['CONFLICT']
            if defect == 'legacy': row['legacyUnitAttested'] = True
            with self.subTest(defect=defect), self.assertRaises(ValueError):
                ingestion.pack_row(row, body, row['sourceCheckedAt'], fixture.NOW)


if __name__ == '__main__':
    unittest.main()

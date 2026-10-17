import copy
import importlib.util
import io
import json
import tempfile
import unittest
from datetime import datetime
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


review = module('r5_prereq_tests', 'review-r5-classification-prerequisites.py')
fixtures = module('r5_prereq_fixtures', 'test_r5_release_review.py')
NOW = fixtures.NOW


class FixedClock(datetime):
    @classmethod
    def now(cls, tz=None):
        return NOW


def inputs():
    _, current, body = fixtures.usda_fixture()
    current['food'].update(name='Cooked Quinoa', display_name='Cooked Quinoa',
                           catalog_type='BRANDED_PRODUCT', market_region='GLOBAL')
    old = {k: str(current['food'][k]) for k in ('id', 'source_key', 'name', 'display_name',
           'data_source', 'catalog_type', 'preparation_state', 'nutrition_reference_unit', 'market_region')}
    old.update({k: str(current['food'][k]) for k in review.NUMERIC})
    old.update(barcode='', normalized_barcode='', source_data_type='')
    r3 = {'id': '1', 'sourceKey': old['source_key'], 'before': old,
          'beforeFingerprint': review.fingerprint(old), 'sourceFingerprint': review.fingerprint(body),
          'sourceDescription': body['description'], 'sourceDataType': body['dataType'], 'sourceFdcId': '123',
          'classificationDecision': 'RECLASSIFY_TO_GENERIC', 'proposedType': 'GENERIC_INGREDIENT',
          'mergeDecision': 'KEEP_SEPARATE_SOURCE_PROFILE', 'sameSourceTargets': [],
          'allowedFutureFields': ['catalog_type', 'source_data_type'], 'findings': []}
    decision = review.gate.source.usda_decision({**current['food'], 'id': '1'}, body, set())
    row = review.gate.review(decision, current, body, '2026-10-12T10:00:00Z', NOW, approve_additive_usda=True)
    row.update(workQueue=review.QUEUE, disposition='HELD', applyAllowed=False,
               sourceCheckedAt='2026-10-12T10:00:00Z')
    return row, r3, body


def naming(r3, body, target='COOKED', serving=False):
    return {'id': '1', 'sourceKey': r3['sourceKey'], 'before': copy.deepcopy(r3['before']),
            'beforeFingerprint': r3['beforeFingerprint'], 'sourceFingerprint': review.fingerprint(body),
            'decision': 'SOURCE_NAMING_RESOLVED', 'stateRationale': 'Explicit reviewed source state',
            'changes': {'name': body['description'], 'display_name': 'Cooked Quinoa',
                        'short_display_name': 'Cooked Quinoa', 'preparation_state': target},
            'localizations': {'EN': {'displayName': 'Cooked Quinoa', 'shortDisplayName': 'Cooked Quinoa'},
                              'TR': {'displayName': 'Pişmiş Kinoa', 'shortDisplayName': 'Pişmiş Kinoa'}},
            'servingReviewRequired': serving}


class PrerequisiteTests(unittest.TestCase):
    def call(self, row=None, r3=None, body=None, r4=None, now=NOW):
        if row is None:
            row, r3, body = inputs()
        return review.link(row, r3, r4, body, now)

    def test_baseline_proves_classification_name_and_approval_blockers(self):
        row, _, _ = inputs()
        for hold in ('R3_R4_CLASSIFICATION_OR_PREPARATION_GATE', 'ADDITIVE_SOURCE_TYPE_OR_BASIS_REVIEW',
                     'CURRENT_NAME_SOURCE_IDENTITY_REVIEW', 'EXISTING_ADMIN_APPROVAL_REVIEW_REQUIRED'):
            self.assertIn(hold, row['holds'])

    def test_exact_r3_links_only_classification_not_name_approval(self):
        result = self.call()
        self.assertEqual('LINKED_FOR_LOCAL_PROJECTION', result['prerequisiteState'])
        self.assertEqual({'catalog_type': 'GENERIC_INGREDIENT'}, result['metadataChanges'])
        self.assertEqual({'catalog_type': 'BRANDED_PRODUCT'}, result['undoMetadataValues'])
        self.assertNotIn('R3_R4_CLASSIFICATION_OR_PREPARATION_GATE', result['projectedHolds'])
        self.assertIn('CURRENT_NAME_SOURCE_IDENTITY_REVIEW', result['projectedHolds'])
        self.assertIn('EXISTING_ADMIN_APPROVAL_REVIEW_REQUIRED', result['projectedHolds'])
        self.assertFalse(result['applyAllowed'])
        self.assertFalse(result['liveApplyApproved'])
        self.assertFalse(result['nutritionWriteAllowed'])
        self.assertTrue(result['currentDatabaseBeforeImageRequired'])

    def test_r4_naming_links_but_retains_alias_serving_and_admin_gates(self):
        row, r3, body = inputs()
        result = self.call(row, r3, body, naming(r3, body, serving=True))
        self.assertEqual(body['description'], result['metadataChanges']['name'])
        self.assertNotIn('CURRENT_NAME_SOURCE_IDENTITY_REVIEW', result['projectedHolds'])
        for hold in ('R4_SOURCE_SERVING_REVIEW_REQUIRED', 'EXISTING_ADMIN_APPROVAL_REVIEW_REQUIRED',
                     'CURRENT_METADATA_ALIAS_AND_ADMIN_APPROVAL_REQUIRED'):
            self.assertIn(hold, result['projectedHolds'])
        self.assertIn('search_aliases', result['freshBeforeImagesRequired'])
        self.assertEqual('Pişmiş Kinoa', result['localizationPlan']['TR']['displayName'])

    def test_existing_coarse_cooked_is_not_silently_narrowed_to_steamed(self):
        row, r3, body = inputs()
        body['description'] = 'Quinoa, cooked, steamed'
        row['sourceFingerprint'] = r3['sourceFingerprint'] = review.fingerprint(body)
        r3['sourceDescription'] = body['description']
        result = self.call(row, r3, body)
        self.assertEqual('LINKED_FOR_LOCAL_PROJECTION', result['prerequisiteState'])
        self.assertNotIn('preparation_state', result['metadataChanges'])

    def test_unknown_or_multiple_state_needs_explicit_r4(self):
        for description in ('Quinoa, dry', 'Quinoa, boiled or baked'):
            row, r3, body = inputs()
            body['description'] = description
            row['sourceFingerprint'] = r3['sourceFingerprint'] = review.fingerprint(body)
            r3['sourceDescription'] = description
            result = self.call(row, r3, body)
            self.assertEqual('HELD', result['prerequisiteState'])
            self.assertEqual({}, result['metadataChanges'])

    def test_source_or_review_fingerprint_drift_is_not_merged(self):
        for defect in ('source', 'r5_before', 'r3_before', 'r3_source'):
            row, r3, body = inputs()
            if defect == 'source': body['foodPortions'][0]['gramWeight'] = 200
            elif defect == 'r5_before': row['before']['food']['name'] = 'Different product'
            elif defect == 'r3_before': r3['before']['name'] = 'Different product'
            else: r3['sourceFingerprint'] = '0' * 64
            with self.subTest(defect=defect):
                result = self.call(row, r3, body)
                self.assertEqual('HELD', result['prerequisiteState'])
                self.assertEqual({}, result['metadataChanges'])

    def test_r3_metadata_drift_and_missing_nonempty_field_block(self):
        for field in ('name', 'source_data_type', 'calories'):
            row, r3, body = inputs()
            r3['before'][field] = 'Old value'
            r3['beforeFingerprint'] = review.fingerprint(r3['before'])
            with self.subTest(field=field):
                result = self.call(row, r3, body)
                self.assertIn('R3_R5_HISTORICAL_METADATA_DRIFT', result['prerequisiteBlockers'])

    def test_gtin_brand_non_mass_or_wrong_source_type_block(self):
        for field, value in (('barcode', '12345678'), ('brand', 'Acme'), ('brand_id', 1),
                             ('nutrition_reference_unit', 'PER_100ML'), ('data_source', 'OPEN_FOOD_FACTS'),
                             ('is_custom', True), ('publication_status', 'WITHDRAWN'),
                             ('nutrition_basis', 'CALCULATED_RECIPE')):
            row, r3, body = inputs()
            row['before']['food'][field] = value
            row['beforeFingerprint'] = review.fingerprint(row['before'])
            with self.subTest(field=field):
                self.assertEqual('HELD', self.call(row, r3, body)['prerequisiteState'])
        row, r3, body = inputs()
        body['dataType'] = 'Branded'
        row['sourceFingerprint'] = review.fingerprint(body)
        self.assertEqual('HELD', self.call(row, r3, body)['prerequisiteState'])

    def test_manufacturer_named_sr_source_is_not_automatically_generic(self):
        row, r3, body = inputs()
        body['brandOwner'] = 'Manufacturer'
        row['sourceFingerprint'] = r3['sourceFingerprint'] = review.fingerprint(body)
        result = self.call(row, r3, body)
        self.assertIn('SOURCE_NAMED_BRAND_CLASSIFICATION_REVIEW', result['prerequisiteBlockers'])
        self.assertIn('USDA_FOODDATA:fdc:168876', review.SOURCE_NAMED_BRANDS)

    def test_r4_undo_values_restore_the_original_metadata_exactly(self):
        row, r3, body = inputs()
        result = self.call(row, r3, body, naming(r3, body))
        original = copy.deepcopy(row['before']['food'])
        projected = copy.deepcopy(original)
        projected.update(result['metadataChanges'])
        for key, value in result['undoMetadataValues'].items():
            if key in original:
                projected[key] = value
            else:
                projected.pop(key, None)
        self.assertEqual(original, projected)

    def test_unresolved_r3_findings_or_same_source_references_block(self):
        for defect in ('findings', 'same_source', 'missing_r3'):
            row, r3, body = inputs()
            if defect == 'findings': r3['findings'] = ['DRY_SOURCE_PRESENTED_AS_READY_COOKED']
            elif defect == 'same_source': r3['sameSourceTargets'] = [{'id': '2'}]
            else: r3 = None
            with self.subTest(defect=defect):
                self.assertEqual('HELD', self.call(row, r3, body)['prerequisiteState'])

    def test_superseded_or_approved_inputs_never_project(self):
        for defect in ('superseded', 'apply', 'live'):
            row, r3, body = inputs()
            if defect == 'superseded': row['disposition'] = 'SUPERSEDED_BASELINE_REBASE_REQUIRED'
            else: row['applyAllowed' if defect == 'apply' else 'liveApplyApproved'] = True
            with self.subTest(defect=defect):
                self.assertEqual('HELD', self.call(row, r3, body)['prerequisiteState'])

    def test_stale_future_and_malformed_source_timestamps_stay_held(self):
        for stamp in ('2026-10-10T10:00:00Z', '2026-10-13T10:00:00Z', 'invalid'):
            row, r3, body = inputs()
            row['sourceCheckedAt'] = stamp
            with self.subTest(stamp=stamp):
                self.assertEqual('HELD', self.call(row, r3, body)['prerequisiteState'])

    def test_r4_wrong_identity_extra_nutrient_write_or_source_mismatch_block(self):
        for defect in ('identity', 'field', 'source', 'before', 'missing_r3'):
            row, r3, body = inputs()
            r4 = naming(r3, body)
            if defect == 'identity': r4['id'] = '2'
            elif defect == 'field': r4['changes']['calories'] = 99
            elif defect == 'source': r4['sourceFingerprint'] = '0' * 64
            elif defect == 'before': r4['before']['name'] = 'Other'
            else: r3 = None
            with self.subTest(defect=defect):
                result = self.call(row, r3, body, r4)
                self.assertEqual('HELD', result['prerequisiteState'])
                self.assertEqual({}, result['metadataChanges'])

    def test_nutrition_and_all_child_records_remain_bit_for_bit_unchanged(self):
        row, r3, body = inputs()
        row['before']['evidence'] = [{'sentinel': 'source evidence'}]
        row['before']['references']['recipes'] = 7
        row['beforeFingerprint'] = review.fingerprint(row['before'])
        before = copy.deepcopy((row, r3, body))
        result = self.call(row, r3, body, naming(r3, body))
        self.assertEqual(before, (row, r3, body))
        self.assertEqual(row['before'], result['before'])
        self.assertEqual(set(result['metadataChanges']) - {'catalog_type'}, {'name', 'short_display_name'})
        self.assertTrue(result['nutrientComparisonUnchanged'])
        self.assertTrue(result['portionsAndReferencesUnchanged'])

    def test_typed_nutrition_hold_is_not_cleared_by_metadata_resolution(self):
        row, r3, body = inputs()
        row['before']['food']['vitamin_a'] = 32
        body['foodNutrients'].append({'nutrient': {'id': 1106, 'unitName': 'ug'}, 'amount': 32})
        row['beforeFingerprint'] = review.fingerprint(row['before'])
        row['sourceFingerprint'] = r3['sourceFingerprint'] = review.fingerprint(body)
        decision = review.gate.source.usda_decision({**row['before']['food'], 'id': '1'}, body, set())
        baseline = review.gate.review(decision, row['before'], body, row['sourceCheckedAt'], NOW, approve_additive_usda=True)
        row.update(nutrients=baseline['nutrients'], nutrientVariants=baseline['nutrientVariants'], holds=baseline['holds'])
        result = self.call(row, r3, body, naming(r3, body))
        self.assertIn('FULL_NUTRITION_COMPARISON_REQUIRED', result['projectedHolds'])
        self.assertEqual('NUTRIENT_UNIT_FORM_OR_DEFINITION_REVIEW', result['projectedWorkQueue'])
        self.assertFalse(result['nutritionWriteAllowed'])

    def test_duplicate_ids_or_keys_are_rejected(self):
        for field in ('id', 'sourceKey'):
            with self.subTest(field=field), self.assertRaises(ValueError):
                review.unique_index([{field: 'x'}, {field: 'x'}], field)
        with self.assertRaises(ValueError):
            review.unique_index([{'id': None}], 'id')

    def test_snapshot_numeric_comparison_does_not_use_rounding_or_booleans(self):
        self.assertTrue(review.same_snapshot_value('calories', '100.0', 100))
        self.assertFalse(review.same_snapshot_value('calories', '100.0', 100.01))
        self.assertFalse(review.same_snapshot_value('calories', '0', False))
        self.assertFalse(review.same_snapshot_value('calories', 'NaN', 'NaN'))


class ManifestTests(unittest.TestCase):
    def write(self, path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value), encoding='utf-8')

    def save(self, path, value, digest_key):
        self.write(path, value)
        self.write(path.parent / 'summary.json', {digest_key: review.closure.sha(path)})

    def inputs(self, root):
        row, decision, body = inputs()
        folder = root / 'sources'
        path = folder / 'batch-001.json'
        self.write(path, [body])
        receipt = path.with_suffix('.receipt.json')
        self.write(receipt, {'ids': ['123'], 'sha256': review.closure.sha(path),
                             'fetchedAt': row['sourceCheckedAt']})
        registry = root / 'registry.json'
        self.write(registry, {'version': 'catalog-naming-r4-v1', 'entries': []})
        r3_path, r4_path, source_path = root / 'r3/manifest.json', root / 'r4/manifest.json', root / 'r5/review.json'
        common = {k: 'test-sha-' + k for k in ('catalog', 'proof', 'evidence')}
        self.save(r3_path, {'ruleset': 'R3_EXACT_SOURCE_COMPATIBILITY_V1', 'decisions': [decision],
                           'inputHashes': common}, 'manifestSha256')
        self.save(r4_path, {'ruleset': 'catalog-naming-r4-v1', 'decisions': [],
                           'inputHashes': {**common, 'r3_manifest': review.closure.sha(r3_path),
                                           'registry': review.closure.sha(registry)}}, 'manifestSha256')
        summary = {'scope': 'HISTORICAL_HELD_SELECTION_ONLY', 'currentDatabaseMeasured': False,
                   'liveApplyAllowed': False, 'gateRuleset': review.gate.RULESET,
                   'inputHashes': {str(p): review.closure.sha(p) for p in (path, receipt)}}
        self.save(source_path, {'summary': summary, 'rows': [row]}, 'reviewSha256')
        return SimpleNamespace(review=source_path, r3_manifest=r3_path, r4_manifest=r4_path,
                               registry=registry, usda_folder=[folder], expected_records=1, output=root / 'new-output')

    def run_review(self, args):
        with patch.object(review, 'datetime', FixedClock), patch('sys.stdout', new_callable=io.StringIO):
            review.run(args)

    def test_full_manifest_run_preserves_input_and_exports_no_live_permission(self):
        with tempfile.TemporaryDirectory() as temp:
            args = self.inputs(Path(temp))
            before = args.review.read_bytes()
            self.run_review(args)
            result = review.followup.read(args.output / 'prerequisite-review.json')
            self.assertEqual(before, args.review.read_bytes())
            self.assertEqual(1, result['summary']['selectedProducts'])
            self.assertEqual(0, result['summary']['releaseReadyProducts'])
            self.assertFalse(result['summary']['liveApplyAllowed'])
            self.assertFalse(result['summary']['currentDatabaseMeasured'])
            self.assertEqual('LINKED_FOR_LOCAL_PROJECTION', result['rows'][0]['prerequisiteState'])

    def test_tampered_input_digest_fails_before_output_creation(self):
        for name in ('review', 'r3_manifest', 'r4_manifest', 'registry'):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temp:
                args = self.inputs(Path(temp))
                path = getattr(args, name)
                path.write_text(path.read_text() + ' ', encoding='utf-8')
                with self.assertRaises(ValueError):
                    self.run_review(args)
                self.assertFalse(args.output.exists())

    def test_wrong_count_duplicate_source_or_missing_batch_fails_without_partial_output(self):
        for defect in ('count', 'duplicate', 'missing'):
            with self.subTest(defect=defect), tempfile.TemporaryDirectory() as temp:
                args = self.inputs(Path(temp))
                if defect == 'count': args.expected_records = 2
                elif defect == 'duplicate': args.usda_folder.append(args.usda_folder[0])
                else: args.usda_folder = []
                with self.assertRaises(ValueError):
                    self.run_review(args)
                self.assertFalse(args.output.exists())

    def test_existing_output_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as temp:
            args = self.inputs(Path(temp))
            args.output.mkdir()
            marker = args.output / 'keep.txt'
            marker.write_text('preserve')
            with self.assertRaises(ValueError):
                self.run_review(args)
            self.assertEqual('preserve', marker.read_text())

    def test_fresh_but_restamped_receipt_is_not_the_pinned_r5_evidence(self):
        with tempfile.TemporaryDirectory() as temp:
            args = self.inputs(Path(temp))
            receipt = args.usda_folder[0] / 'batch-001.receipt.json'
            value = review.followup.read(receipt)
            value['fetchedAt'] = '2026-10-12T10:30:00Z'
            self.write(receipt, value)
            with self.assertRaisesRegex(ValueError, 'pinned R5 evidence'):
                self.run_review(args)
            self.assertFalse(args.output.exists())

    def test_registry_names_cannot_change_even_with_a_new_r4_summary(self):
        with tempfile.TemporaryDirectory() as temp:
            args = self.inputs(Path(temp))
            r3 = review.followup.read(args.r3_manifest)['decisions'][0]
            row, _, body = inputs()
            r4 = naming(r3, body)
            entry = {'sourceKey': row['sourceKey'], 'sourceDescription': body['description'],
                     'displayNameEn': 'Cooked Quinoa', 'displayNameTr': 'Pişmiş Kinoa', 'targetState': 'COOKED',
                     'stateRationale': r4['stateRationale'], 'servingReviewRequired': False}
            self.write(args.registry, {'version': 'catalog-naming-r4-v1', 'entries': [entry]})
            manifest = review.followup.read(args.r4_manifest)
            manifest['inputHashes']['registry'] = review.closure.sha(args.registry)
            manifest['decisions'] = [r4]
            r4['localizations']['TR']['displayName'] = 'Wrong product'
            self.save(args.r4_manifest, manifest, 'manifestSha256')
            with self.assertRaisesRegex(ValueError, 'registry drift'):
                self.run_review(args)
            self.assertFalse(args.output.exists())


if __name__ == '__main__':
    unittest.main()

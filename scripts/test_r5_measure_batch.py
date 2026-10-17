import copy
import importlib.util
import unittest
from pathlib import Path


spec = importlib.util.spec_from_file_location('r5_measure_batch_tests', Path(__file__).with_name('review-r5-measure-batch.py'))
batch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(batch)


class MeasureBatchProofTests(unittest.TestCase):
    def portion(self, identity, descriptor, amount=1, grams=28):
        return {'id': identity, 'amount': amount, 'gramWeight': grams, 'modifier': descriptor,
                'measureUnit': {'name': 'undetermined'}}

    def wrapper(self, option):
        return {'sourceProposal': option, 'equivalentOptionIds': [], 'labelConflictOptionIds': [],
            'holds': ['CURRENT_RUNTIME_REBASE_AND_EXPLICIT_PORTION_APPROVAL_REQUIRED', 'PRIOR_HOLD'],
            **batch.PROHIBITIONS}

    def data(self):
        original = self.portion(55, 'leaf')
        old, _ = batch.qualified.proposal(original)
        pending = self.portion(56, 'slice (1 oz)')
        new, _ = batch.qualified.proposal(pending)
        parent = {'id': '1', 'sourceKey': 'USDA_FOODDATA:fdc:123', 'originalHolds': ['PRIOR_HOLD'],
            'sourceFingerprint': 'source-hash', 'before': {'food': {'id': 1, 'name': 'Test food'},
                'options': [{'id': 99, 'label': 'Old'}], 'option_localizations': []}}
        baseline = [{'rows': [parent]}, {'rows': [{'id': '1', 'fields': ['unchanged']}]},
            {'rows': [{'id': '1', 'oldOptionsRetained': [{'id': 99, 'label': 'Old'}],
                'optionLocalizationsRetained': [], 'sourceProposals': [self.wrapper(old)],
                'unsupportedSourceMeasures': [{'sourcePortion': pending, 'reason': 'OLD_REASON'}],
                **batch.PROHIBITIONS}]}, {'rows': [{'id': '1', 'holds': ['RELEASE_HOLD']}]}]
        current = copy.deepcopy(baseline)
        current[2]['rows'][0]['sourceProposals'].append(self.wrapper(new))
        current[2]['rows'][0]['unsupportedSourceMeasures'] = []
        return baseline, current

    def test_delta_preserves_before_images_and_prepares_only_exact_pending_source(self):
        baseline, current = self.data()
        before = copy.deepcopy((baseline, current))
        result = batch.compare(baseline, current)
        self.assertEqual(1, result['summary']['additionalMeasuresPrepared'])
        self.assertEqual(0, result['summary']['remainingMeasures'])
        self.assertEqual({'EXACT_SOURCE_MASS_DESCRIPTOR': 1}, result['summary']['newMeasuresByFamily'])
        self.assertEqual(before, (baseline, current))
        self.assertFalse(result['summary']['applyAllowed'])

    def test_changes_to_parent_old_options_labels_units_or_release_rows_are_rejected(self):
        for stage, key in ((0, 'before'), (1, 'fields'), (3, 'holds')):
            baseline, current = self.data()
            current[stage]['rows'][0][key] = []
            with self.subTest(stage=stage), self.assertRaises(ValueError):
                batch.compare(baseline, current)
        for field in ('oldOptionsRetained', 'optionLocalizationsRetained'):
            baseline, current = self.data()
            current[2]['rows'][0][field] = [{'changed': True}]
            with self.subTest(field=field), self.assertRaises(ValueError):
                batch.compare(baseline, current)

    def test_old_candidate_may_gain_but_never_lose_a_hold(self):
        baseline, current = self.data()
        current[2]['rows'][0]['sourceProposals'][0]['holds'].append('NEW_HOLD')
        proof = batch.compare(baseline, current)
        self.assertEqual({'NEW_HOLD': 1}, proof['summary']['additionalHoldEventsOnOldCandidates'])
        current[2]['rows'][0]['sourceProposals'][0]['holds'].remove('PRIOR_HOLD')
        with self.assertRaises(ValueError):
            batch.compare(baseline, current)

    def test_changed_mass_count_volume_source_or_context_cannot_be_smuggled_in(self):
        for field, value in (('gramWeight', 29), ('quantity', 2), ('mlVolume', 28),
                             ('sourcePortionFingerprint', 'other'), ('sourceContextFingerprint', 'other'),
                             ('sourcePortionId', 999), ('userVisible', True)):
            baseline, current = self.data()
            current[2]['rows'][0]['sourceProposals'][1]['sourceProposal'][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                batch.compare(baseline, current)

    def test_approval_and_false_as_numeric_zero_are_rejected(self):
        for field, value in (('applyAllowed', True), ('nutritionWriteAllowed', True),
                             ('legacyUnitAttested', True), ('runtimeApprovalFingerprint', 'token'),
                             ('liveApplyApproved', True), ('applyAllowed', 0)):
            baseline, current = self.data()
            current[2]['rows'][0]['sourceProposals'][1][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                batch.compare(baseline, current)

    def test_measure_cannot_disappear_without_exact_candidate_and_duplicate_is_refused(self):
        baseline, current = self.data()
        current[2]['rows'][0]['sourceProposals'].pop()
        with self.assertRaises(ValueError):
            batch.compare(baseline, current)
        baseline, current = self.data()
        current[2]['rows'][0]['sourceProposals'].append(copy.deepcopy(current[2]['rows'][0]['sourceProposals'][1]))
        with self.assertRaises(ValueError):
            batch.compare(baseline, current)

    def test_current_equivalence_decisions_cannot_be_replaced(self):
        baseline, current = self.data()
        current[2]['rows'][0]['sourceProposals'][0]['equivalentOptionIds'] = [99]
        with self.assertRaises(ValueError):
            batch.compare(baseline, current)

    def test_two_fraction_bases_do_not_manufacture_a_whole_block(self):
        baseline, current = self.data()
        measures = [self.portion(60, 'block', .25, 81), self.portion(61, 'block', .2, 91)]
        held = [{'sourcePortion': p, 'reason': 'OLD_REASON'} for p in measures]
        baseline[2]['rows'][0]['unsupportedSourceMeasures'].extend(copy.deepcopy(held))
        current[2]['rows'][0]['unsupportedSourceMeasures'] = copy.deepcopy(held)
        result = batch.compare(baseline, current)
        self.assertEqual(2, result['summary']['remainingMeasures'])
        self.assertEqual(1, result['summary']['fractionBaseReviewProducts'])
        self.assertFalse(result['fractionBaseReviews'][0]['wholeSizeMayBeDerived'])
        self.assertEqual(measures, result['fractionBaseReviews'][0]['sourcePortionsPreserved'])

    def feta(self):
        rows = batch.cycle.read(Path('outputs/r5-source-semantics-20261015/review-accepted/stage-1.json'))['rows']
        parent = copy.deepcopy(next(row for row in rows if row['id'] == '180991'))
        portion_rows = batch.cycle.read(Path('outputs/r5-source-semantics-20261015/review-accepted/stage-3.json'))['rows']
        portion_row = next(row for row in portion_rows if row['id'] == '180991')
        candidate = copy.deepcopy(next(w['sourceProposal'] for w in portion_row['sourceProposals']
                                       if w['sourceProposal'].get('sourcePortionId') == 92490))
        old = copy.deepcopy(next(o for o in parent['before']['options'] if o['id'] == 3708))
        return parent, self.wrapper(candidate), [old]

    def test_feta_choice_recommends_existing_id_without_retyping_appending_or_approving(self):
        parent, proposal, old = self.feta()
        before = copy.deepcopy((parent, proposal, old))
        result = batch.existing_choice('180991', parent, proposal, old)
        self.assertEqual('PREFER_EXISTING_APPROVED_OPTION_SKIP_NEW_DUPLICATE_NO_DATA_CHANGE', result['decision'])
        self.assertFalse(result['appendNewOption'])
        self.assertFalse(result['changeExistingOption'])
        self.assertFalse(result['applyAllowed'])
        self.assertEqual(3708, result['existingOptionId'])
        self.assertEqual(before, (parent, proposal, old))

    def test_feta_choice_is_not_a_generic_type_alias_and_rejects_current_drift(self):
        for field, value in (('id', 3709), ('unit_type', 'CUP'), ('quantity', 2), ('gram_weight', 28.36),
                ('ml_volume', 28), ('label', 'Changed'), ('quality_status', 'NEEDS_REVIEW'),
                ('user_visible', False), ('is_default', False), ('source', 'OTHER')):
            parent, proposal, old = self.feta()
            old[0][field] = value
            with self.subTest(field=field):
                result = batch.existing_choice('180991', parent, proposal, old)
                self.assertNotIn('existingOptionId', result)
                self.assertFalse(result['applyAllowed'])
        parent, proposal, old = self.feta()
        self.assertNotIn('existingOptionId', batch.existing_choice('180992', parent, proposal, old))
        parent['sourceKey'] = 'USDA_FOODDATA:fdc:999'
        self.assertNotIn('existingOptionId', batch.existing_choice('180991', parent, proposal, old))

    def test_feta_changed_source_measure_or_nutrition_basis_stays_review_only(self):
        for field, value in (('sourcePortionId', 92491), ('sourcePortionFingerprint', 'changed'),
                             ('unitType', 'CUP'), ('quantity', 2), ('gramWeight', 28.36), ('mlVolume', 28)):
            parent, proposal, old = self.feta()
            proposal['sourceProposal'][field] = value
            with self.subTest(field=field):
                self.assertNotIn('existingOptionId', batch.existing_choice('180991', parent, proposal, old))
        parent, proposal, old = self.feta()
        parent['before']['food']['nutrition_reference_unit'] = 'PER_100ML'
        self.assertNotIn('existingOptionId', batch.existing_choice('180991', parent, proposal, old))

    def test_f31_existing_choice_is_bound_to_complete_dependency_before_image(self):
        for mutation in ('locale', 'nutrient', 'other-option', 'dependency', 'source-profile'):
            parent, proposal, old = self.feta()
            if mutation == 'locale':
                parent['before']['option_localizations'][0]['label'] = 'Changed'
            elif mutation == 'nutrient':
                parent['before']['food']['calories'] = 0
            elif mutation == 'other-option':
                parent['before']['options'][0]['label'] = 'Changed'
            elif mutation == 'dependency':
                parent['before']['additional-unmeasured-dependency'] = []
            else:
                parent['before']['food']['name'] = 'Another source profile'
            with self.subTest(mutation=mutation):
                result = batch.existing_choice('180991', parent, proposal, old)
                self.assertNotIn('existingOptionId', result)
                self.assertFalse(result['applyAllowed'])

    def test_f31_noop_choice_cannot_reuse_an_edited_candidate(self):
        for field, value in (('sourceDescriptor', 'other'), ('labels', {'EN': 'same', 'TR': 'other'}),
                             ('userVisible', True), ('qualityStatus', 'VERIFIED')):
            parent, proposal, old = self.feta()
            proposal['sourceProposal'][field] = value
            with self.subTest(field=field):
                self.assertNotIn('existingOptionId', batch.existing_choice('180991', parent, proposal, old))


if __name__ == '__main__':
    unittest.main()

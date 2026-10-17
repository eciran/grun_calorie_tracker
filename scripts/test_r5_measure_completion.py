import copy
import importlib.util
import unittest
from pathlib import Path
from unittest.mock import patch


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


qualified = module('r5_completed_measure_tests', 'r5-qualified-source-portions.py')
cycle = module('r5_completed_cycle_tests', 'audit-r5-comparison-cycle.py')
fixtures = module('r5_completed_cycle_fixtures', 'test_r5_comparison_cycle.py')


class MeasureCompletionTests(unittest.TestCase):
    def portion(self, descriptor, amount=1, grams=140):
        return {'id': 55, 'amount': amount, 'gramWeight': grams, 'modifier': descriptor,
                'measureUnit': {'name': 'undetermined'}}

    def test_exact_dimensions_keep_all_sizes_without_estimating_other_sizes(self):
        for descriptor in ('slice, large (1/4" thick)', 'slice, medium (1/8" thick)',
                'slice, thin (3-1/2" dia x 1/2" thick)', 'slice (4-2/3" dia x 3/4" thick)',
                'piece (4-1/2" x 2-1/8" x 7/8")', 'pat (1" sq, 1/3" high)',
                'pods (3" long)', 'spears (1/2" base)', 'potato (2-1/2" dia, sphere)',
                'fruit (2-5/8" dia)'):
            with self.subTest(descriptor=descriptor):
                portion = self.portion(descriptor, 2, 110)
                before = copy.deepcopy(portion)
                option, reason = qualified.proposal(portion)
                self.assertIsNone(reason)
                self.assertEqual((2, 110, None), (option['quantity'], option['gramWeight'], option['mlVolume']))
                self.assertEqual(descriptor, option['sourceDescriptor'])
                self.assertIn('inç', option['labels']['TR'])
                self.assertEqual(before, portion)
                self.assertNotIn('derivation', option)

    def test_mass_descriptors_use_source_grams_not_a_unit_conversion(self):
        for descriptor, grams in (('lb', 453.6), ('slice (1 oz)', 28), ('patty, 4 oz', 114),
                ('wedge (1.33 oz)', 38), ('oz (49 kernels)', 28.35), ('oz (85 seeds)', 28.35)):
            with self.subTest(descriptor=descriptor):
                option, reason = qualified.proposal(self.portion(descriptor, 1, grams))
                self.assertIsNone(reason)
                self.assertEqual(grams, option['gramWeight'])
                self.assertEqual(1, option['quantity'])
                self.assertIsNone(option['mlVolume'])
                self.assertNotIn('derivation', option)

    def test_spoon_and_slice_qualifiers_remain_visible(self):
        for descriptor, tr in (('tbsp chopped', 'doğranmış'), ('serving 1 tbsp', 'kaşığı'),
                ('slice, large', 'büyük'), ('slice, regular', 'normal'), ('slice, snack-size', 'atıştırmalık'),
                ('bunch', 'demet')):
            option, reason = qualified.proposal(self.portion(descriptor))
            self.assertIsNone(reason)
            self.assertIn(tr, option['labels']['TR'])
            self.assertIsNone(option['mlVolume'])

    def test_malformed_dimensions_and_added_qualifiers_stay_held(self):
        for descriptor in ('slice (0" dia x 1/2" thick)', 'slice (1/0" dia x 1/2" thick)',
                'slice (3-4/2" dia x 1/2" thick)', 'slice (4" dia x -1" thick)',
                'slice (100000" dia x 1" thick)', 'pods (3" long) drained unknown',
                'slice (1 oz), boneless', 'oz (0 kernels)', 'patty, 4 fl oz', 'fl oz', 'quart'):
            with self.subTest(descriptor=descriptor):
                option, reason = qualified.proposal(self.portion(descriptor))
                self.assertIsNone(option)
                self.assertIsNotNone(reason)

    def food(self, descriptor, name):
        portion = self.portion(descriptor)
        return portion, {'fdcId': 123, 'dataType': 'SR Legacy', 'description': name, 'foodPortions': [copy.deepcopy(portion)]}

    def test_context_specific_words_require_exact_source_profile_and_portion_membership(self):
        cases = (('sprout', 'Brussels sprouts, cooked, boiled, drained, without salt', 'Brüksel lahanası'),
                 ('jumbo', 'Egg, whole, raw, fresh', 'jumbo yumurta'),
                 ('cup (4.86 large eggs)', 'Egg, whole, raw, fresh', '4.86 büyük yumurta'),
                 ('fruit without seeds', 'Oranges, raw, with peel', 'kabuğu dahil'))
        for descriptor, name, wording in cases:
            with self.subTest(descriptor=descriptor):
                portion, food = self.food(descriptor, name)
                option, reason = qualified.proposal(portion, food)
                self.assertIsNone(reason)
                self.assertIn(wording, option['labels']['TR'])
                self.assertEqual(cycle.digest(food), option['sourceContextFingerprint'])
                self.assertFalse(option['userVisible'])
                self.assertEqual((140, None), (option['gramWeight'], option['mlVolume']))
                self.assertIsNone(qualified.proposal(portion)[0])

    def test_context_cannot_be_replaced_by_similar_name_or_changed_source_measure(self):
        portion, food = self.food('sprout', 'Brussels sprouts, cooked, boiled, drained, without salt')
        for field, value in (('description', 'Bean sprouts, raw'), ('description', 'Brussels sprouts, raw'), ('description', []),
                ('fdcId', True), ('dataType', 'Branded'), ('foodPortions', []),
                ('foodPortions', [dict(portion, gramWeight=141)]), ('foodPortions', [portion, portion])):
            with self.subTest(field=field, value=value):
                changed = copy.deepcopy(food)
                changed[field] = value
                option, reason = qualified.proposal(portion, changed)
                self.assertIsNone(option)
                self.assertIsNotNone(reason)

    def test_context_does_not_approve_yield_packaging_or_bones(self):
        for descriptor in ('bird', 'bird whole', 'roast', 'can', 'block', 'bottle', 'RACC',
                'NLEA serving', 'unit, yield from 1 lb raw', 'drumstick bone and skin'):
            portion, food = self.food(descriptor, 'Chicken, meat only, cooked, roasted')
            self.assertIsNone(qualified.proposal(portion, food)[0])

    def test_pending_reason_uses_tokens_not_stick_inside_drumstick(self):
        descriptor = 'leg (Sum of drumstick+thigh+back meat only)'
        self.assertEqual('EDIBLE_PART_AND_PROFILE_REQUIRE_REVIEW', qualified.pending_reason(self.portion(descriptor)))
        self.assertEqual('PACKAGE_OR_BLOCK_SIZE_NOT_UNIVERSAL', qualified.pending_reason(self.portion('stick')))
        self.assertEqual('EDIBLE_PART_AND_PROFILE_REQUIRE_REVIEW', qualified.pending_reason(self.portion('oz, boneless')))
        self.assertEqual('YIELD_AND_PREPARATION_QUALIFIERS_REQUIRE_REVIEW', qualified.pending_reason(self.portion('package (10 oz) yields')))


class PortionCollisionTests(unittest.TestCase):
    def data(self):
        row, body = fixtures.ComparisonCycleTests().compared()
        body['foodPortions'] = [{'id': 55, 'amount': 1, 'gramWeight': 140, 'modifier': 'cup, diced',
                                'measureUnit': {'name': 'undetermined'}}]
        return row, body

    def test_tr_label_conflict_and_label_only_type_conflict_are_reported(self):
        row, body = self.data()
        option = cycle.portion_review(row, body)['sourceProposals'][0]['sourceProposal']
        row['before']['options'] = [{'id': 99, 'label': 'Different English', 'gram_weight': 150,
            'ml_volume': None, 'quantity': 1, 'unit_type': 'CUP'}]
        row['before']['option_localizations'] = [{'serving_option_id': 99, 'language': 'TR',
            'label': option['labels']['TR'], 'active': True}]
        before = copy.deepcopy(row)
        result = cycle.portion_review(row, body)['sourceProposals'][0]
        self.assertIn('EXISTING_LOCALIZED_LABEL_CONFLICT', result['holds'])
        self.assertEqual([99], result['localizedLabelConflictOptionIds'])
        self.assertEqual(before, row)
        row['before']['options'][0].update(gram_weight=140, quantity=2)
        result = cycle.portion_review(row, body)['sourceProposals'][0]
        self.assertIn('EXISTING_LOCALIZED_LABEL_CONFLICT', result['holds'])

    def test_same_amount_does_not_prove_same_descriptor_or_allow_collapse(self):
        row, body = self.data()
        body['foodPortions'].append(dict(body['foodPortions'][0], id=56, modifier='cup, mashed'))
        out = cycle.portion_review(row, body)
        self.assertEqual(2, len(out['sourceProposals']))
        for result in out['sourceProposals']:
            self.assertIn('SOURCE_MEASURE_EQUIVALENCE_REVIEW_REQUIRED', result['holds'])
            self.assertFalse(result['applyAllowed'])

    def test_reused_descriptor_with_different_source_mass_holds_both_candidates(self):
        row, body = self.data()
        body['foodPortions'].append(dict(body['foodPortions'][0], id=56, gramWeight=141))
        out = cycle.portion_review(row, body)
        for result in out['sourceProposals']:
            self.assertIn('SOURCE_DESCRIPTOR_WEIGHT_CONFLICT', result['holds'])

    def test_option_budget_reports_every_candidate_without_truncation(self):
        row, body = self.data()
        body['foodPortions'] = [dict(body['foodPortions'][0], id=i+1, gramWeight=i+1) for i in range(21)]
        out = cycle.portion_review(row, body)
        self.assertEqual(21, len(out['sourceProposals']))
        self.assertTrue(all('SOURCE_OPTION_BUDGET_REVIEW_NO_TRUNCATION' in r['holds'] for r in out['sourceProposals']))

    def test_current_options_are_included_in_budget_and_none_are_removed(self):
        row, body = self.data()
        row['before']['options'] = [{'id': i+1, 'label': f'Old {i}', 'unit_type': 'SERVING',
                                    'quantity': 1, 'gram_weight': 10, 'ml_volume': None} for i in range(20)]
        before = copy.deepcopy(row)
        result = cycle.portion_review(row, body)
        self.assertEqual(20, len(result['oldOptionsRetained']))
        self.assertIn('SOURCE_OPTION_BUDGET_REVIEW_NO_TRUNCATION', result['sourceProposals'][0]['holds'])
        self.assertEqual(before, row)

    def test_inactive_labels_do_not_conflict_and_orphan_active_labels_stay_held(self):
        row, body = self.data()
        row['before']['option_localizations'] = [{'serving_option_id': 99, 'language': 'TR',
            'label': 'Old inactive', 'active': False}]
        result = cycle.portion_review(row, body)['sourceProposals'][0]
        self.assertNotIn('CURRENT_OPTION_LOCALIZATION_COVERAGE_INCOMPLETE', result['holds'])
        row['before']['option_localizations'][0]['active'] = True
        result = cycle.portion_review(row, body)['sourceProposals'][0]
        self.assertIn('CURRENT_OPTION_LOCALIZATION_COVERAGE_INCOMPLETE', result['holds'])

    def test_current_portion_and_source_serving_are_not_blindly_treated_as_equal(self):
        row, body = self.data()
        body['foodPortions'][0]['modifier'] = 'oz'
        option = cycle.portion_review(row, body)['sourceProposals'][0]['sourceProposal']
        row['before']['options'] = [{'id': 99, 'label': option['labels']['EN'], 'unit_type': 'PORTION',
                                    'quantity': 1, 'gram_weight': 140, 'ml_volume': None}]
        result = cycle.portion_review(row, body)['sourceProposals'][0]
        self.assertEqual([], result['equivalentOptionIds'])
        self.assertEqual([99], result['localizedLabelConflictOptionIds'])
        self.assertIn('EXISTING_LOCALIZED_LABEL_CONFLICT', result['holds'])

    def test_turkish_case_and_whitespace_normalize_but_diacritics_are_retained(self):
        row, body = self.data()
        candidate = {'unitType': 'SERVING', 'quantity': 1, 'gramWeight': 140, 'mlVolume': None,
            'labels': {'EN': 'Example', 'TR': '1 DİLİM (140 g)'}, 'defaultOption': False,
            'qualityStatus': 'NEEDS_REVIEW', 'userVisible': False}
        row['before']['options'] = [{'id': 99, 'label': 'Other', 'unit_type': 'SERVING',
                                    'quantity': 1, 'gram_weight': 141, 'ml_volume': None}]
        row['before']['option_localizations'] = [{'serving_option_id': 99, 'language': 'TR',
            'label': '  1 dilim  (140 g) ', 'active': True}]
        with patch.object(cycle.gate.source, 'usda_portion_options', return_value=([candidate], [])):
            self.assertEqual([99], cycle.portion_review(row, body)['sourceProposals'][0]['localizedLabelConflictOptionIds'])
            candidate['labels']['TR'] = 'süt'
            row['before']['option_localizations'][0]['label'] = 'sut'
            self.assertEqual([], cycle.portion_review(row, body)['sourceProposals'][0]['localizedLabelConflictOptionIds'])

    def test_candidate_localized_label_collision_keeps_both_unapproved(self):
        row, body = self.data()
        first = {'sourcePortionId': 1, 'unitType': 'SERVING', 'quantity': 1, 'gramWeight': 140,
                 'mlVolume': None, 'labels': {'EN': 'Same', 'TR': 'Aynı'}, 'defaultOption': False,
                 'qualityStatus': 'NEEDS_REVIEW', 'userVisible': False}
        other = {**first, 'sourcePortionId': 2, 'gramWeight': 141}
        with patch.object(cycle.gate.source, 'usda_portion_options', return_value=([first, other], [])):
            result = cycle.portion_review(row, body)
        self.assertEqual(2, len(result['sourceProposals']))
        for proposal in result['sourceProposals']:
            self.assertIn('SOURCE_LOCALIZED_LABEL_CONFLICT', proposal['holds'])
            self.assertFalse(proposal['applyAllowed'])


if __name__ == '__main__':
    unittest.main()

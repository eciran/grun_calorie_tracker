import copy
import importlib.util
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path


spec = importlib.util.spec_from_file_location('r5_complete_portion_tests', Path(__file__).with_name('audit-r5-portion-integrity.py'))
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)
NOW = datetime(2026, 10, 15, 20, tzinfo=timezone.utc)


class CompletePortionAuditTests(unittest.TestCase):
    def data(self):
        portion = {'id': 55, 'amount': 1, 'gramWeight': 28, 'modifier': 'leaf',
                   'measureUnit': {'name': 'undetermined'}}
        body = {'fdcId': 123, 'dataType': 'SR Legacy', 'description': 'A source profile', 'foodPortions': [portion]}
        option, reason = audit.cycle.qualified.proposal(portion, body)
        self.assertIsNone(reason)
        wrapper = {'sourceProposal': option, 'holds': ['CURRENT_RUNTIME_REBASE_AND_EXPLICIT_PORTION_APPROVAL_REQUIRED'],
                   'equivalentOptionIds': [], 'localizedLabelConflictOptionIds': [], **audit.PROHIBITIONS}
        return body, [wrapper], []

    def test_f28_complete_direct_measure_is_preserved_and_not_mutated(self):
        body, proposals, held = self.data()
        before = copy.deepcopy((body, proposals, held))
        result = audit.usda_coverage(body, proposals, held)
        self.assertEqual({'sourceMeasures': 1, 'directCandidates': 1, 'derivedCandidates': 0, 'heldMeasures': 0}, result)
        self.assertEqual(before, (body, proposals, held))

    def test_f28_source_measure_cannot_disappear_duplicate_or_exist_in_both_queues(self):
        body, proposals, held = self.data()
        for ps, hs in (([], []), (proposals + proposals, []),
                       (proposals, [{'sourcePortion': body['foodPortions'][0]}])):
            with self.subTest(proposals=len(ps), held=len(hs)), self.assertRaises(ValueError):
                audit.usda_coverage(body, ps, hs)
        self.assertEqual(1, audit.usda_coverage(body, [], [{'sourcePortion': body['foodPortions'][0]}])['heldMeasures'])

    def test_f28_changed_source_count_mass_fingerprint_descriptor_and_implicit_ml_are_refused(self):
        for field, value in (('quantity', 2), ('gramWeight', 29), ('mlVolume', 28),
                             ('sourcePortionId', True), ('sourcePortionId', 56),
                             ('sourcePortionFingerprint', 'changed'), ('sourceDescriptor', 'leaf cooked')):
            body, proposals, held = self.data()
            proposals[0]['sourceProposal'][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                audit.usda_coverage(body, proposals, held)

    def test_f28_source_footnote_cannot_be_approved_by_changing_only_its_hash(self):
        body, proposals, held = self.data()
        body['foodPortions'][0]['footnote'] = 'including bone'
        proposals[0]['sourceProposal']['sourcePortionFingerprint'] = audit.cycle.digest(body['foodPortions'][0])
        with self.assertRaises(ValueError):
            audit.usda_coverage(body, proposals, held)

    def test_f28_actual_ml_measure_is_distinct_from_cup_or_water_in_name(self):
        body, proposals, held = self.data()
        body['description'] = 'Rice cooked with water'
        source = body['foodPortions'][0]
        source.update(modifier='ml', amount=200, gramWeight=210)
        option, reason = audit.cycle.gate.source.source_portion(source)
        self.assertIsNone(reason)
        proposals[0]['sourceProposal'] = option
        self.assertEqual(1, audit.usda_coverage(body, proposals, held)['directCandidates'])
        source['modifier'] = 'cup'
        option, _ = audit.cycle.gate.source.source_portion(source)
        option['mlVolume'] = 200
        proposals[0]['sourceProposal'] = option
        with self.assertRaises(ValueError):
            audit.usda_coverage(body, proposals, held)

    def derived_data(self):
        body, proposals, _ = self.data()
        body['foodPortions'][0]['modifier'] = 'medium'
        options, held = audit.cycle.gate.source.usda_portion_options(body)
        wrappers = [dict(proposals[0], sourceProposal=option) for option in options]
        return body, wrappers, held

    def test_f28_half_quarter_need_exact_retained_whole_and_weight(self):
        body, proposals, held = self.derived_data()
        self.assertEqual(2, audit.usda_coverage(body, proposals, held)['derivedCandidates'])
        for mutation in ('parent', 'mass', 'fraction', 'type'):
            body, proposals, held = self.derived_data()
            if mutation == 'parent':
                proposals.pop(0)
            elif mutation == 'mass':
                proposals[1]['sourceProposal']['gramWeight'] = 15
            elif mutation == 'fraction':
                proposals[1]['sourceProposal']['derivation']['fraction'] = .3
            else:
                proposals[0]['sourceProposal']['unitType'] = 'SERVING'
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                audit.usda_coverage(body, proposals, held)

    def test_f28_all_candidates_not_only_additions_must_be_invisible_and_bilingual(self):
        for field, value in (('userVisible', True), ('defaultOption', True), ('qualityStatus', 'VERIFIED'),
                             ('unitType', 'UNKNOWN'), ('quantity', True), ('gramWeight', 0),
                             ('labels', {'EN': 'Missing translation'})):
            body, proposals, held = self.data()
            proposals[0]['sourceProposal'][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                audit.usda_coverage(body, proposals, held)

    def test_f28_label_cannot_claim_different_mass_or_a_drinking_volume(self):
        for labels in ({'EN': '1 leaf (29 g)', 'TR': '1 yaprak (29 g)'},
                       {'EN': '1 cup (28 ml)', 'TR': '1 bardak (28 ml)'}):
            body, proposals, held = self.data()
            proposals[0]['sourceProposal']['labels'] = labels
            with self.subTest(labels=labels), self.assertRaises(ValueError):
                audit.usda_coverage(body, proposals, held)

    def collision_row(self, second_weight=28):
        _, wrappers, _ = self.data()
        first = wrappers[0]
        second = copy.deepcopy(first)
        second['sourceProposal'].update(sourcePortionId=56, gramWeight=second_weight)
        return {'sourceProposals': [first, second], 'oldOptionsRetained': [], 'optionLocalizationsRetained': []}

    def test_f29_equal_weights_are_not_automatically_interchangeable(self):
        row = self.collision_row()
        row['sourceProposals'][1]['sourceProposal']['sourceDescriptor'] = 'another shape'
        with self.assertRaises(ValueError):
            audit.collision_checks(row)
        for wrapper in row['sourceProposals']:
            wrapper['holds'].append('SOURCE_MEASURE_EQUIVALENCE_REVIEW_REQUIRED')
        self.assertEqual({'SOURCE_MEASURE_EQUIVALENCE_REVIEW_REQUIRED': 1}, audit.collision_checks(row))

    def test_f29_same_descriptor_different_weights_requires_symmetric_holds(self):
        row = self.collision_row(29)
        expected = {'SOURCE_DESCRIPTOR_WEIGHT_CONFLICT', 'SOURCE_LOCALIZED_LABEL_CONFLICT'}
        row['sourceProposals'][0]['holds'].extend(expected)
        with self.assertRaises(ValueError):
            audit.collision_checks(row)
        row['sourceProposals'][1]['holds'].extend(expected)
        self.assertEqual(dict.fromkeys(expected, 1), audit.collision_checks(row))

    def test_f29_turkish_diacritics_are_not_ascii_stripped(self):
        self.assertEqual(audit.label_key('DİLİM', 'TR'), audit.label_key('dilim', 'TR'))
        self.assertEqual(audit.label_key('  1\u00a0dilim ', 'TR'), '1 dilim')
        self.assertNotEqual(audit.label_key('süt', 'TR'), audit.label_key('sut', 'TR'))

    def test_f29_existing_portion_label_type_mismatch_cannot_be_silently_aliased(self):
        row = self.collision_row()
        row['sourceProposals'] = row['sourceProposals'][:1]
        option = row['sourceProposals'][0]['sourceProposal']
        row['oldOptionsRetained'] = [{'id': 9, 'unit_type': 'PORTION', 'quantity': 1,
                                     'gram_weight': 28, 'ml_volume': None, 'label': option['labels']['EN']}]
        with self.assertRaises(ValueError):
            audit.collision_checks(row)
        wrapper = row['sourceProposals'][0]
        wrapper['localizedLabelConflictOptionIds'] = [9]
        wrapper['holds'].append('EXISTING_LOCALIZED_LABEL_CONFLICT')
        self.assertEqual({'EXISTING_LOCALIZED_LABEL_CONFLICT': 1}, audit.collision_checks(row))

    def test_f29_orphan_active_locale_requires_a_hold_but_inactive_history_is_retained(self):
        row = self.collision_row()
        row['sourceProposals'] = row['sourceProposals'][:1]
        row['optionLocalizationsRetained'] = [{'serving_option_id': 99, 'language': 'TR', 'label': 'Eski', 'active': False}]
        self.assertEqual({}, audit.collision_checks(row))
        row['optionLocalizationsRetained'][0]['active'] = True
        with self.assertRaises(ValueError):
            audit.collision_checks(row)
        row['sourceProposals'][0]['holds'].append('CURRENT_OPTION_LOCALIZATION_COVERAGE_INCOMPLETE')
        self.assertEqual({}, audit.collision_checks(row))

    def test_f30_stale_future_naive_times_never_become_fresh_or_approved(self):
        body, _, _ = self.data()
        for stamp in ('2026-10-13T20:00:00Z', '2026-10-16T20:00:00Z', '2026-10-15T19:00:00', None, 'invalid'):
            parent = {'sourceFingerprint': audit.cycle.digest(body), 'sourceCheckedAt': stamp}
            with self.subTest(stamp=stamp):
                result = audit.source_freshness(parent, body, stamp, NOW)
                self.assertFalse(result['freshAtAudit'])
                self.assertEqual('SOURCE_REFRESH_REQUIRED', result['requiredAction'])
                self.assertFalse(result['applyAllowed'])

    def test_f30_changed_body_or_restamped_decision_requires_new_review(self):
        body, _, _ = self.data()
        stamp = '2026-10-15T19:00:00Z'
        parent = {'sourceFingerprint': audit.cycle.digest(body), 'sourceCheckedAt': stamp}
        result = audit.source_freshness(parent, body, stamp, NOW)
        self.assertTrue(result['freshAtAudit'])
        self.assertEqual(stamp, result['sourceCheckedAt'])
        with self.assertRaises(ValueError):
            audit.source_freshness(parent, body, '2026-10-15T19:01:00Z', NOW)
        body['description'] = 'Changed profile'
        with self.assertRaises(ValueError):
            audit.source_freshness(parent, body, stamp, NOW)

    def test_f30_complete_frozen_sources_expire_without_rewriting_a_single_receipt(self):
        folder = Path('outputs/r5-source-semantics-20261015/review-accepted')
        stages, report = audit.batch.load_cycle(folder)
        sources = audit.load_sources(report)
        before = {key: audit.cycle.digest(value) for key, value in sources.items()}
        current = audit.audit(stages, sources, NOW)
        expired = audit.audit(stages, sources, NOW + timedelta(days=2))
        self.assertEqual(262, current['summary']['freshSourcesAtAudit'])
        self.assertEqual(0, expired['summary']['freshSourcesAtAudit'])
        self.assertEqual(573, expired['summary']['candidates'])
        self.assertEqual(237, expired['summary']['existingOptionsRetained'])
        self.assertEqual(474, expired['summary']['existingOptionLabelsRetained'])
        self.assertEqual(0, expired['summary']['releaseReadyProducts'])
        self.assertEqual(before, {key: audit.cycle.digest(value) for key, value in sources.items()})

    def test_f32_cross_stage_selection_drift_is_refused_before_an_integrity_claim(self):
        stages = [{'rows': [{'id': '1'}]}, {'rows': [{'id': '2'}]},
                  {'rows': [{'id': '1'}]}, {'rows': [{'id': '1'}]}]
        with self.assertRaises(ValueError):
            audit.audit(stages, {}, NOW)


if __name__ == '__main__':
    unittest.main()

import copy
import importlib.util
import unittest
from pathlib import Path


spec = importlib.util.spec_from_file_location('r5_semantics_qualified_tests', Path(__file__).with_name('r5-qualified-source-portions.py'))
qualified = importlib.util.module_from_spec(spec)
spec.loader.exec_module(qualified)


class SourceMeasureSemanticsTests(unittest.TestCase):
    def source(self, identity, description, descriptor, grams=146, amount=1):
        portion = {'id': 55, 'amount': amount, 'gramWeight': grams, 'modifier': descriptor,
                   'measureUnit': {'id': 9999, 'name': 'undetermined'}}
        food = {'fdcId': identity, 'description': description, 'dataType': 'SR Legacy',
                'foodPortions': [copy.deepcopy(portion)]}
        return portion, food

    def test_source_yield_uses_final_edible_weight_not_the_input_pack_or_raw_weight(self):
        cases = (
            (171054, 'Chicken, broilers or fryers, meat only, cooked, roasted',
             'unit (yield from 1 lb ready-to-cook chicken)', 146, 'pişmiş'),
            (169209, 'Asparagus, frozen, cooked, boiled, drained, without salt',
             'package (10 oz) yields', 293, 'süzülmüş'),
            (171506, 'Turkey, Ground, cooked', 'patty (4 oz, raw) (yield after cooking)', 82, 'çiğ'),
            (167755, 'Raspberries, raw', 'pint as purchased, yields', 312, 'yenebilir'),
        )
        for identity, name, descriptor, grams, qualifier in cases:
            with self.subTest(identity=identity):
                portion, food = self.source(identity, name, descriptor, grams)
                before = copy.deepcopy((portion, food))
                option, reason = qualified.proposal(portion, food)
                self.assertIsNone(reason)
                self.assertEqual(grams, option['gramWeight'])
                self.assertIsNone(option['mlVolume'])
                self.assertIn(qualifier, option['labels']['TR'])
                self.assertEqual('FINAL_EDIBLE_SOURCE_GRAMS', option['sourceWeightMeaning'])
                self.assertEqual(before, (portion, food))
                self.assertNotIn('derivation', option)

    def test_package_and_drained_contents_are_source_specific_not_universal_container_sizes(self):
        cases = (
            (170850, 'Cheese, provolone', 'package (6 oz)', 170),
            (173708, 'Fish, tuna, light, canned in oil, drained solids', 'can (12.5 oz), drained', 321),
            (173430, 'Butter, without salt', 'stick', 113),
            (170904, 'Kefir, lowfat, plain, LIFEWAY', 'bottle', 971),
        )
        for identity, name, descriptor, grams in cases:
            with self.subTest(identity=identity):
                portion, food = self.source(identity, name, descriptor, grams)
                option, reason = qualified.proposal(portion, food)
                self.assertIsNone(reason)
                self.assertEqual('SERVING', option['unitType'])
                self.assertEqual(grams, option['gramWeight'])
                self.assertIsNone(option['mlVolume'])
                self.assertIn('kaynak', option['labels']['TR'])
                self.assertFalse(option['userVisible'])
                self.assertFalse(option['defaultOption'])

    def test_edible_part_and_skin_bone_distinctions_survive(self):
        cases = (
            (172380, 'Chicken, broilers or fryers, leg, meat only, cooked, roasted',
             'drumstick bone and skin', 'deri'),
            (175139, 'Fish, sardine, Atlantic, canned in oil, drained solids with bone', 'sardines', 'kılçığı'),
            (173691, 'Fish, salmon, sockeye, raw', 'oz, boneless', 'kemiksiz'),
            (171109, 'Chicken, cornish game hens, meat only, cooked, roasted', 'bird whole', 'yalnızca et'),
        )
        for identity, name, descriptor, qualifier in cases:
            portion, food = self.source(identity, name, descriptor)
            option, reason = qualified.proposal(portion, food)
            self.assertIsNone(reason)
            self.assertIn(qualifier, option['labels']['TR'])
            self.assertIn('sourceContextFingerprint', option)

    def test_solid_cubic_inches_never_create_a_liquid_volume(self):
        portion, food = self.source(173420, 'Cheese, feta', 'cubic inch', 17)
        option, reason = qualified.proposal(portion, food)
        self.assertIsNone(reason)
        self.assertEqual('SERVING', option['unitType'])
        self.assertIsNone(option['mlVolume'])
        self.assertIn('kübik inç', option['labels']['TR'])

    def test_type_profile_exact_id_and_cook_state_cannot_be_substituted(self):
        portion, food = self.source(171054, 'Chicken, broilers or fryers, meat only, cooked, roasted',
                                    'unit (yield from 1 lb ready-to-cook chicken)')
        for field, value in (('fdcId', 171055), ('fdcId', True), ('dataType', 'Foundation'),
                             ('description', 'Chicken, broilers or fryers, meat only, raw'),
                             ('description', 'Chicken, broilers or fryers, meat and skin, cooked, roasted'),
                             ('foodPortions', []), ('foodPortions', [portion, portion]),
                             ('foodPortions', [dict(portion, gramWeight=147)])):
            changed = copy.deepcopy(food)
            changed[field] = value
            with self.subTest(field=field, value=value):
                self.assertIsNone(qualified.proposal(portion, changed)[0])

    def test_measure_footnotes_extra_qualifiers_and_explicit_unit_conflicts_stay_held(self):
        for field, value in (('footnote', 'Special yield caution'), ('modifier', 'package (6 oz), unknown'),
                             ('measureUnit', {'name': 'ml'})):
            portion, food = self.source(170850, 'Cheese, provolone', 'package (6 oz)', 170)
            portion[field] = value
            food['foodPortions'] = [copy.deepcopy(portion)]
            with self.subTest(field=field):
                self.assertIsNone(qualified.proposal(portion, food)[0])

    def test_no_policy_guesses_for_fluid_standard_unknown_cup_or_fraction_base(self):
        for identity, name, descriptor in (
            (174158, 'Water, bottled, generic', 'fl oz'),
            (170882, 'Milk, sheep, fluid', 'quart'),
            (172448, 'Tofu, firm, prepared with calcium sulfate and magnesium chloride (nigari)', 'block'),
            (328637, 'Cheese, cheddar', 'cup'),
            (325036, 'Cheese, parmesan, grated', 'RACC'),
        ):
            portion, food = self.source(identity, name, descriptor)
            self.assertIsNone(qualified.proposal(portion, food)[0])

    def test_reference_only_policy_retains_source_and_never_creates_household_options(self):
        for descriptor in ('RACC', 'NLEA serving'):
            portion, food = self.source(123, 'Test reference food', descriptor, 55)
            if descriptor == 'RACC':
                portion.pop('modifier')
                portion['measureUnit'] = {'id': 1121, 'name': 'RACC'}
                food['foodPortions'] = [copy.deepcopy(portion)]
            before = copy.deepcopy(portion)
            decision = qualified.semantics.reference_disposition(portion)
            self.assertFalse(decision['householdSuggestionAllowed'])
            self.assertFalse(decision['deleteSourceMeasure'])
            self.assertEqual(qualified.fingerprint(portion), decision['sourcePortionFingerprint'])
            self.assertIsNone(qualified.proposal(portion, food)[0])
            self.assertEqual(before, portion)

    def test_reference_policy_does_not_hide_invalid_or_qualified_measures(self):
        for field, value in (('id', True), ('amount', 0), ('gramWeight', float('nan')),
                ('modifier', 'NLEA serving, yields'), ('modifier', True),
                ('measureUnit', None), ('footnote', 'Unreviewed warning')):
            portion, _ = self.source(123, 'Test reference food', 'NLEA serving')
            portion[field] = value
            with self.subTest(field=field):
                self.assertIsNone(qualified.semantics.reference_disposition(portion))

    def test_all_interpretation_rules_are_source_specific_not_a_global_word_mapping(self):
        for identity, (name, rules) in qualified.semantics.RULES.items():
            for descriptor in rules:
                with self.subTest(identity=identity, descriptor=descriptor):
                    portion, food = self.source(identity, name, descriptor)
                    option, reason = qualified.proposal(portion, food)
                    self.assertIsNone(reason)
                    self.assertEqual(qualified.fingerprint(food), option['sourceContextFingerprint'])
                    self.assertEqual(qualified.fingerprint(portion), option['sourcePortionFingerprint'])
                    self.assertEqual('NEEDS_REVIEW', option['qualityStatus'])
                    self.assertIsNone(option['mlVolume'])
                    self.assertFalse(option['userVisible'])
                    self.assertFalse(option['defaultOption'])
                    self.assertIsNone(qualified.proposal(portion)[0])
                    food['description'] += ', unknown extra qualifier'
                    self.assertIsNone(qualified.proposal(portion, food)[0])

    def test_refuse_is_not_translated_as_bone_when_source_does_not_declare_bone(self):
        for identity in (168233, 168250, 167904):
            name, rules = qualified.semantics.RULES[identity]
            for descriptor in rules:
                if 'refuse' not in descriptor:
                    continue
                portion, food = self.source(identity, name, descriptor)
                option, reason = qualified.proposal(portion, food)
                self.assertIsNone(reason)
                self.assertIn('ayıklama sonrası', option['labels']['TR'])
                self.assertNotIn('kemik', option['labels']['TR'])


if __name__ == '__main__':
    unittest.main()

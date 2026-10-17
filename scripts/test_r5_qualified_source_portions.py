import copy
import hashlib
import importlib.util
import json
import unittest
from pathlib import Path


spec = importlib.util.spec_from_file_location('qualified_portion_tests', Path(__file__).with_name('r5-qualified-source-portions.py'))
qualified = importlib.util.module_from_spec(spec)
spec.loader.exec_module(qualified)


class QualifiedSourcePortionTests(unittest.TestCase):
    def portion(self, descriptor, amount=1, grams=140):
        return {'id': 55, 'amount': amount, 'gramWeight': grams, 'modifier': descriptor,
                'measureUnit': {'id': 9999, 'name': 'undetermined'}}

    def test_source_defined_food_shapes_are_not_turned_into_drinking_glasses(self):
        for descriptor in ('cup, pieces or slices', 'cup, drained', 'cup shells', 'cup rotini',
                           'cup elbows', 'cup spaghetti', 'cup penne', 'cup farfalle', 'cup lasagna',
                           'cup, crumbs', 'cup, crumbled', 'cup, halves and whole', 'cup whole kernels',
                           'cup, strips', 'cup, pureed', 'cup, halves'):
            with self.subTest(descriptor=descriptor):
                option, reason = qualified.proposal(self.portion(descriptor))
                self.assertIsNone(reason)
                self.assertEqual('CUP', option['unitType'])
                self.assertIn('US measuring cup', option['labels']['EN'])
                self.assertIn('ABD ölçü kabı', option['labels']['TR'])
                self.assertNotIn('bardak', option['labels']['TR'])
                self.assertIsNone(option['mlVolume'])

    def test_exact_count_descriptors_keep_source_count_and_total_mass(self):
        for descriptor, amount, grams in (('leaves', 2, .1), ('leaf', 1, 48), ('leek', 1, 89),
                ('carrot', 1, 46), ('tomatoes', 5, 49.7), ('mushroom', 1, 12),
                ('kernel', 1, .7), ('spears', 4, 60), ('berries', 50, 68), ('raspberries', 10, 19),
                ('melon', 1, 1640)):
            with self.subTest(descriptor=descriptor):
                portion = self.portion(descriptor, amount, grams)
                before = copy.deepcopy(portion)
                option, reason = qualified.proposal(portion)
                self.assertIsNone(reason)
                self.assertEqual('SERVING', option['unitType'])
                self.assertEqual((amount, grams, None), (option['quantity'], option['gramWeight'], option['mlVolume']))
                self.assertEqual(descriptor, option['sourceDescriptor'])
                self.assertEqual(before, portion)
                self.assertNotIn('derivation', option)
                self.assertNotIn('PIECE', option['unitType'])

    def test_qualifiers_have_distinct_translations_instead_of_plain_cup(self):
        expected = {'cup, pieces or slices': 'parça veya dilim', 'cup, drained': 'süzülmüş',
                    'cup, crumbled': 'ufalanmış', 'cup, pureed': 'püre',
                    'cup, halves': 'yarımlar', 'cup whole kernels': 'bütün iç taneler',
                    'cup penne': 'penne', 'cup, halves and whole': 'yarım ve bütün'}
        for descriptor, wording in expected.items():
            with self.subTest(descriptor=descriptor):
                option, reason = qualified.proposal(self.portion(descriptor))
                self.assertIsNone(reason)
                self.assertIn(wording, option['labels']['TR'])

    def test_new_options_remain_invisible_unapproved_source_specific_mass_only(self):
        for descriptor in ('leaf', 'cup penne', 'cup, crumbled'):
            with self.subTest(descriptor=descriptor):
                portion = self.portion(descriptor)
                option, reason = qualified.proposal(portion)
                self.assertIsNone(reason)
                self.assertFalse(option['defaultOption'])
                self.assertFalse(option['userVisible'])
                self.assertEqual('NEEDS_REVIEW', option['qualityStatus'])
                self.assertTrue(option['sourceSpecificNotUniversalMeasure'])
                expected = hashlib.sha256(json.dumps(portion, ensure_ascii=False, sort_keys=True,
                    separators=(',', ':'), allow_nan=False).encode()).hexdigest()
                self.assertEqual(expected, option['sourcePortionFingerprint'])

    def test_unresolved_yield_package_bone_regulatory_and_volume_stay_held(self):
        for descriptor in ('fl oz', 'quart', 'cubic inch', 'lb unknown', 'RACC', 'NLEA serving',
                           'fruit yields', 'bottle', 'can', 'package (10 oz)', 'package (10 oz) yields',
                           'unit, yield from 1 lb raw', 'drumstick bone and skin', 'bird whole',
                           'slice (1 oz) unknown', 'cup (4.86 large eggs)', 'spears (1/2" base) unknown',
                           'fruit without seeds', 'slice, snack-size unknown', 'sprout'):
            with self.subTest(descriptor=descriptor):
                option, reason = qualified.proposal(self.portion(descriptor))
                self.assertIsNone(option)
                self.assertIsNotNone(reason)

    def test_allowlist_is_exact_not_a_substring_or_arbitrary_qualifier(self):
        for descriptor in ('cup shells raw unknown', 'cup, drained with oil', 'cup, pureed sweetened',
                           'cup, halves and whole extra', 'whole kernels', 'cup water',
                           'leaves cooked', 'rotten tomatoes', 'spears bone and skin',
                           'mushroom package', 'berries yields'):
            with self.subTest(descriptor=descriptor):
                option, reason = qualified.proposal(self.portion(descriptor))
                self.assertIsNone(option)
                self.assertIsNotNone(reason)

    def test_known_explicit_unit_must_not_be_discarded(self):
        for name in ('g', 'ml', 'fl oz', 'RACC'):
            portion = self.portion('leaves')
            portion['measureUnit']['name'] = name
            with self.subTest(name=name):
                option, reason = qualified.proposal(portion)
                self.assertIsNone(option)
                self.assertIsNotNone(reason)

    def test_shape_only_words_do_not_invent_a_food_identity(self):
        for descriptor in ('spears', 'kernel', 'cup shells'):
            option, reason = qualified.proposal(self.portion(descriptor))
            self.assertIsNone(reason)
            for guessed_food in ('kuşkonmaz', 'fıstık', 'makarna'):
                self.assertNotIn(guessed_food, option['labels']['TR'])

    def test_conflicting_description_is_not_silently_dropped(self):
        for descriptor, other in (('leaf', 'leaves cooked'), ('cup penne', 'cup, drained'),
                                   ('fillet', 'fillet with skin')):
            portion = self.portion(descriptor)
            portion['portionDescription'] = other
            option, reason = qualified.proposal(portion)
            self.assertIsNone(option)
            self.assertIsNotNone(reason)

    def test_invalid_descriptor_or_unit_structure_fails_closed(self):
        for field, value in (('measureUnit', None), ('measureUnit', 'cup'),
                             ('measureUnit', {'name': True}), ('modifier', True),
                             ('portionDescription', ['leaf'])):
            portion = self.portion('leaf')
            portion[field] = value
            with self.subTest(field=field, value=value):
                option, reason = qualified.proposal(portion)
                self.assertIsNone(option)
                self.assertIsNotNone(reason)

    def test_missing_or_invalid_source_values_do_not_produce_guessed_weights(self):
        for field, values in {'id': (None, True, 0, -1, '55'),
                              'amount': (None, True, 0, -1, float('nan'), float('inf'), '1'),
                              'gramWeight': (None, True, 0, -1, float('nan'), float('inf'), '140')}.items():
            for value in values:
                with self.subTest(field=field, value=value):
                    portion = self.portion('leaf')
                    portion[field] = value
                    option, reason = qualified.proposal(portion)
                    self.assertIsNone(option)
                    self.assertIsNotNone(reason)


if __name__ == '__main__':
    unittest.main()

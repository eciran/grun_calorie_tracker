import copy
import importlib.util
import unittest
from pathlib import Path


spec = importlib.util.spec_from_file_location('r5_integrity_qualified', Path(__file__).with_name('r5-qualified-source-portions.py'))
qualified = importlib.util.module_from_spec(spec)
spec.loader.exec_module(qualified)
spec = importlib.util.spec_from_file_location('r5_integrity_base', Path(__file__).with_name('prepare-r5-source-options.py'))
base = importlib.util.module_from_spec(spec)
spec.loader.exec_module(base)


class QualifiedIntegrityTests(unittest.TestCase):
    def portion(self, descriptor='leaf', amount=1, grams=105):
        return {'id': 55, 'amount': amount, 'gramWeight': grams, 'modifier': descriptor,
                'measureUnit': {'id': 9999, 'name': 'undetermined'}}

    def food(self, portion):
        return {'fdcId': 328637, 'dataType': 'Foundation', 'description': 'Cheese, cheddar',
                'foodPortions': [copy.deepcopy(portion)]}

    def test_f23_structured_shredded_cup_preserves_form_and_exact_mass_not_ml(self):
        portion = self.portion()
        portion.pop('modifier')
        portion.update(portionDescription='shredded', measureUnit={'id': 1000, 'name': 'cup', 'abbreviation': 'cup'})
        option, reason = qualified.proposal(portion, self.food(portion))
        self.assertIsNone(reason)
        self.assertEqual(('CUP', 1, 105, None),
                         tuple(option[k] for k in ('unitType', 'quantity', 'gramWeight', 'mlVolume')))
        self.assertIn('shredded', option['labels']['EN'])
        self.assertIn('rendelenmiş', option['labels']['TR'])
        self.assertNotIn('bardak', option['labels']['TR'])
        self.assertFalse(option['userVisible'])

    def test_f24_nonempty_footnotes_do_not_disappear_in_generic_shape_rules(self):
        for note in ('including bones', 'as purchased', 1, [], {}):
            portion = dict(self.portion(), footnote=note)
            with self.subTest(note=note):
                option, reason = qualified.proposal(portion)
                self.assertIsNone(option)
                self.assertEqual('SOURCE_FOOTNOTE_REQUIRES_REVIEW', reason)

    def test_f24_invalid_top_level_source_returns_hold_not_exception(self):
        for portion in (None, [], 'leaf', 1):
            with self.subTest(portion=portion):
                option, reason = qualified.proposal(portion)
                self.assertIsNone(option)
                self.assertIsNotNone(reason)

    def test_f25_plain_allowlist_cannot_bypass_complete_source_membership(self):
        portion = self.portion()
        for field, value in (('foodPortions', []), ('foodPortions', [dict(portion, gramWeight=106)]),
                             ('foodPortions', [portion, portion]), ('fdcId', True), ('dataType', 'Branded')):
            food = self.food(portion)
            food[field] = value
            with self.subTest(field=field, value=value):
                self.assertIsNone(qualified.proposal(portion, food)[0])

    def test_f25_exact_member_still_allowed_without_mutating_source(self):
        portion = self.portion()
        food = self.food(portion)
        before = copy.deepcopy((portion, food))
        self.assertIsNone(qualified.proposal(portion, food)[1])
        self.assertEqual(before, (portion, food))

    def test_f26_decimal_labels_do_not_round_or_use_exponents(self):
        for amount, grams in ((0.3333333, 123.456789), (1, 0.0000001), (1000000, 1000000)):
            with self.subTest(amount=amount):
                option, reason = qualified.proposal(self.portion(amount=amount, grams=grams))
                self.assertIsNone(reason)
                number = qualified.number_label
                for label in option['labels'].values():
                    self.assertTrue(label.startswith(number(amount) + ' '))
                    self.assertTrue(label.endswith('(' + number(grams) + ' g)'))
                self.assertNotIn('e', number(grams).lower())

    def test_f27_label_contract_rejects_missing_blank_control_and_overflow(self):
        for labels in ({'EN': 'leaf'}, {'EN': 'leaf', 'TR': ''}, {'EN': 'a\nleaf', 'TR': 'yaprak'},
                       {'EN': 'a\u202eleaf', 'TR': 'yaprak'}, {'EN': 'a' * 121, 'TR': 'yaprak'},
                       {'EN': True, 'TR': 'yaprak'}):
            with self.subTest(labels=labels):
                self.assertFalse(qualified.valid_labels(labels))
        self.assertTrue(qualified.valid_labels({'EN': '1 slice (28.35 g)', 'TR': '1 dilim (28.35 g)'}))

    def test_f24_legacy_parser_also_retains_footnotes_as_holds(self):
        portion = dict(self.portion('slice'), footnote='with skin')
        self.assertIsNone(base.source_portion(portion)[0])

    def test_f24_legacy_parser_rejects_noninteger_ids_and_conflicting_descriptions(self):
        for changes in ({'id': True}, {'id': '55'}, {'modifier': True},
                        {'measureUnit': None}, {'portionDescription': 'with skin'}):
            with self.subTest(changes=changes):
                self.assertIsNone(base.source_portion(dict(self.portion('slice'), **changes))[0])

    def test_f26_legacy_parser_and_derived_halves_keep_exact_decimal_labels(self):
        food = {'foodPortions': [self.portion('medium', grams=123.456789)]}
        options, holds = base.usda_portion_options(food)
        self.assertEqual([], holds)
        self.assertEqual(3, len(options))
        for option in options:
            self.assertTrue(option['labels']['EN'].endswith('(' + qualified.number_label(option['gramWeight']) + ' g)'))

    def test_f27_legacy_parser_control_character_cannot_hide_in_size_suffix(self):
        for descriptor in ('medium (\u202ewith skin)', 'medium (' + 'x' * 121 + ')'):
            with self.subTest(descriptor=descriptor):
                self.assertIsNone(base.source_portion(self.portion(descriptor))[0])


if __name__ == '__main__':
    unittest.main()

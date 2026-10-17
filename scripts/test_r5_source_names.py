import copy
import importlib.util
import unittest
from datetime import timedelta
from pathlib import Path


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


names = module('r5_name_tests', 'prepare-r5-source-names.py')
fixtures = module('r5_name_fixtures', 'test_r5_classification_prerequisites.py')
NOW = fixtures.NOW


def inputs():
    row, r3, body = fixtures.inputs()
    row = names.prereq.link(row, r3, None, body, NOW)
    return row, {'fdcId': '123', 'sourceDescription': body['description'],
                 'displayNameEn': 'Cooked Quinoa', 'displayNameTr': 'Pişmiş Kinoa'}, body


class NameTests(unittest.TestCase):
    def test_localized_names_and_qualified_aliases_do_not_grant_live_permission(self):
        row, entry, body = inputs()
        before = copy.deepcopy((row, entry, body))
        result = names.review_row(row, entry, body, NOW)
        self.assertEqual(before, (row, entry, body))
        self.assertEqual('Quinoa, cooked', result['foodChanges']['name'])
        self.assertEqual('Pişmiş Kinoa', result['localizationProposals']['TR']['displayName'])
        self.assertEqual(['Quinoa, cooked', 'Quinoa (Cooked)', 'Kinoa (Pişmiş)'], [a['alias'] for a in result['aliasProposals']])
        self.assertNotIn('CURRENT_NAME_SOURCE_IDENTITY_REVIEW', result['projectedHolds'])
        for hold in ('EXISTING_ADMIN_APPROVAL_REVIEW_REQUIRED', 'CURRENT_ALIAS_COLLISION_AND_BEFORE_IMAGE_REQUIRED'):
            self.assertIn(hold, result['projectedHolds'])
        for field in ('applyAllowed', 'nutritionWriteAllowed', 'liveApplyApproved', 'currentAliasesMeasured',
                      'currentLocalizationsMeasured', 'runtimeRegistryEnabled'):
            self.assertFalse(result[field])
        self.assertEqual([], result['oldAliasActions'])
        self.assertTrue(all(not a['active'] and not a['applyAllowed'] for a in result['aliasProposals']))

    def test_protected_source_qualifiers_are_not_dropped(self):
        for description, en, tr in (
            ('Beans, navy, mature seeds, cooked, boiled, without salt', 'Boiled Navy Beans (Mature Seeds)', 'Haşlanmış Navy Fasulyesi (Olgun Tohum)'),
            ('Pasta, homemade, made without egg, cooked', 'Cooked Homemade Pasta', 'Pişmiş Ev Yapımı Makarna'),
            ('Oranges, raw, with peel', 'Raw Orange', 'Çiğ Portakal'),
            ('Beef, tenderloin, boneless, trimmed to 0" fat, choice, cooked, grilled', 'Grilled Beef Tenderloin (Boneless, Choice)', 'Izgara Dana Bonfile (Kemiksiz, Choice)'),
            ('Spinach, frozen, chopped or leaf, cooked, boiled, drained, without salt', 'Boiled Spinach (Frozen, Drained, Unsalted)', 'Haşlanmış Ispanak (Dondurulmuş, Süzülmüş, Tuzsuz)'),
            ('Noodles, egg, enriched, cooked', 'Cooked Egg Noodles', 'Pişmiş Yumurtalı Erişte'),
        ):
            with self.subTest(description=description), self.assertRaises(ValueError):
                names.check_qualifiers(description, en, tr)

    def test_salted_and_unsalted_do_not_match_as_substrings(self):
        for en, tr in (('Boiled Lentils (Mature Seeds, Unsalted)', 'Haşlanmış Mercimek (Olgun Tohum, Tuzsuz)'),
                       ('Boiled Lentils (Mature Seeds)', 'Haşlanmış Mercimek (Olgun Tohum)')):
            with self.subTest(en=en), self.assertRaises(ValueError):
                names.check_qualifiers('Lentils, mature seeds, cooked, boiled, with salt', en, tr)

    def test_mature_french_beans_cannot_become_fresh_green_beans(self):
        with self.assertRaises(ValueError):
            names.check_qualifiers('Beans, french, mature seeds, cooked, boiled, without salt',
                                   'Boiled Green Beans (Mature Seeds, Unsalted)',
                                   'Haşlanmış Taze Fasulye (Olgun Tohum, Tuzsuz)')

    def test_species_cut_and_cultivar_cannot_disappear_in_either_language(self):
        for description, en, tr in (
            ('Fish, salmon, chum, raw', 'Raw Salmon', 'Çiğ Somon'),
            ('Fish, haddock, raw', 'Raw Haddock', 'Çiğ Mezgit'),
            ('Fish, mackerel, king, raw', 'Raw Mackerel', 'Çiğ Uskumru'),
            ('Pears, raw, red anjou', 'Raw Pear', 'Çiğ Armut'),
            ('Beef, round, eye of round, roast, choice, cooked, roasted', 'Roasted Beef (Choice)', 'Fırınlanmış Dana Eti (Choice)'),
        ):
            with self.subTest(description=description), self.assertRaises(ValueError):
                names.check_qualifiers(description, en, tr)

    def test_unproven_claims_and_ready_cooked_dry_profile_fail(self):
        for description, en, tr in (
            ('Pears, raw', 'Raw Pear (Unsalted)', 'Çiğ Armut (Tuzsuz)'),
            ('Pears, raw', 'Raw Fat-Free Pear', 'Çiğ Armut'),
            ('Buckwheat groats, roasted, dry', 'Cooked Roasted Buckwheat Groats (Dry)', 'Pişmiş Kavrulmuş Karabuğday Taneleri (Kuru)'),
            ('Seeds, sesame seeds, whole, roasted and toasted', 'Roasted and Toasted Sesame Seeds (Whole)', 'Kızartılmış Bütün Susam'),
            ('Pears, raw', 'Raw Cooked Pear', 'Çiğ Pişmiş Armut'),
            ('Quinoa, cooked', 'Boiled Quinoa', 'Haşlanmış Kinoa'),
            ('Fish, haddock, raw', 'Raw Haddock (Grilled)', 'Çiğ Haddock Balığı (Izgara)'),
        ):
            with self.subTest(en=en), self.assertRaises(ValueError):
                names.check_qualifiers(description, en, tr)

    def test_preparation_rotation_preserves_all_words_and_qualifiers(self):
        for language, label, prefix in (
            ('EN', 'Dry-Roasted Cashews (No Added Salt)', 'Dry-Roasted'),
            ('EN', 'Cooked Roasted Buckwheat Groats', 'Cooked'),
            ('TR', 'Buharda Pişmiş Tatlı Patates Yaprağı (Tuzsuz)', 'Buharda Pişmiş'),
            ('TR', 'Kuru Kavrulmuş Ay Çekirdeği İçi (Tuzsuz)', 'Kuru Kavrulmuş'),
        ):
            alias = names.rotate_preparation(label, language)
            self.assertEqual(label[len(prefix) + 1:] + ' (' + prefix + ')', alias)
            normalize = names.prereq.closure.review.match_name
            self.assertEqual(sorted(normalize(label).split()), sorted(normalize(alias).split()))

    def test_unicode_format_balancing_length_and_html_guards(self):
        for label in (' Ceviz', 'Çiğ  Muz', 'Muz\u200b', '<b>Muz</b>', 'Muz )(', 'A' * 256, 'C\u0327ig\u0306 Muz'):
            with self.subTest(label=label), self.assertRaises(ValueError):
                names.valid_text(label)
        names.valid_text('Çiğ Elma (Kabuksuz)')

    def test_wrong_source_id_body_or_description_cannot_join(self):
        for defect in ('fdc', 'description', 'fingerprint', 'brand', 'before'):
            row, entry, body = inputs()
            if defect == 'fdc': entry['fdcId'] = '999'
            elif defect == 'description': entry['sourceDescription'] = 'Different'
            elif defect == 'fingerprint': body['foodPortions'][0]['gramWeight'] = 200
            elif defect == 'brand': row['before']['food']['brand'] = 'Brand'
            else: row['before']['food']['calories'] = 10
            with self.subTest(defect=defect), self.assertRaises(ValueError):
                names.review_row(row, entry, body, NOW)

    def test_superseded_or_held_or_unexpected_parent_change_rejected(self):
        for defect in ('held', 'queue', 'apply', 'field'):
            row, entry, body = inputs()
            if defect == 'held': row['prerequisiteState'] = 'HELD'
            elif defect == 'queue': row['projectedWorkQueue'] = 'REBASE_LATER_APPLIED_RECORDS'
            elif defect == 'apply': row['applyAllowed'] = True
            else: row['metadataChanges']['preparation_state'] = 'RAW'
            with self.subTest(defect=defect), self.assertRaises(ValueError):
                names.review_row(row, entry, body, NOW)

    def test_stale_or_future_source_is_not_renewed_by_name_review(self):
        row, entry, body = inputs()
        for now in (NOW + timedelta(days=2), NOW - timedelta(days=2)):
            with self.subTest(now=now), self.assertRaises(ValueError):
                names.review_row(row, entry, body, now)

    def test_old_aliases_references_values_and_options_are_never_written(self):
        row, entry, body = inputs()
        before = copy.deepcopy(row['before'])
        result = names.review_row(row, entry, body, NOW)
        self.assertEqual(before, result['before'])
        self.assertEqual(row['projectedOptions'], result['projectedOptions'])
        food = copy.deepcopy(before['food'])
        food.update(result['foodChanges'])
        for field in names.gate.FIELDS:
            self.assertEqual(before['food'][field], food[field])
        for field, value in result['undoFoodValues'].items():
            if result['undoFoodFieldPresence'][field]: food[field] = value
            else: food.pop(field, None)
        self.assertEqual(before['food'], food)

    def test_cohort_alias_collision_is_detected_but_not_merged(self):
        row, entry, body = inputs()
        first = names.review_row(row, entry, body, NOW)
        second = copy.deepcopy(first)
        second['id'] = '2'
        found = names.collisions([first, second])
        self.assertTrue(found)
        self.assertTrue(all(r['ids'] == ['1', '2'] for r in found))
        self.assertFalse(names.collisions([first]))

    def test_registry_is_not_wired_to_runtime_and_has_exact118_unique_ids(self):
        root = Path(__file__).resolve().parents[1]
        registry = names.prereq.followup.read(root / 'src/main/resources/catalog/source-name-proposals-r5-f4-v1.json')
        self.assertEqual(118, len(registry['entries']))
        self.assertEqual(118, len({r[0] for r in registry['entries']}))
        self.assertEqual('OFFLINE_PROPOSALS_ONLY_NOT_RUNTIME_APPROVED', registry['status'])
        java = root / 'src/main/java/com/grun/calorietracker/service/support/FoodSourceNameRules.java'
        self.assertNotIn('source-name-proposals-r5-f4-v1.json', java.read_text(encoding='utf-8'))

    def test_entire_checked_in_registry_keeps_source_qualifiers_in_both_languages_and_aliases(self):
        root = Path(__file__).resolve().parents[1]
        registry = names.prereq.followup.read(root / 'src/main/resources/catalog/source-name-proposals-r5-f4-v1.json')
        for values in registry['entries']:
            entry = dict(zip(registry['columns'], values))
            with self.subTest(fdc=entry['fdcId']):
                names.check_qualifiers(entry['sourceDescription'], entry['displayNameEn'], entry['displayNameTr'])
                candidates = names.aliases(entry)
                self.assertEqual(entry['sourceDescription'], candidates[0]['alias'])
                names.check_qualifiers(entry['sourceDescription'], candidates[1]['alias'], candidates[2]['alias'])
                self.assertTrue(all(not a['active'] for a in candidates))

    def test_empty_unknown_prefix_cannot_become_an_unqualified_alias(self):
        for label in ('Quinoa', 'Muz', '', 'Very Good Rice'):
            with self.subTest(label=label), self.assertRaises(ValueError):
                names.rotate_preparation(label, 'EN')


if __name__ == '__main__':
    unittest.main()

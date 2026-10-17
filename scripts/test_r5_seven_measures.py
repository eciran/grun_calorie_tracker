import copy
import importlib.util
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

spec = importlib.util.spec_from_file_location('r5_seven_tests', Path(__file__).with_name('review-r5-seven-measures.py'))
review = importlib.util.module_from_spec(spec)
spec.loader.exec_module(review)
NOW = datetime(2026, 10, 15, 22, 30, tzinfo=timezone.utc)


class SevenSourceMeasureTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.stages, report = review.integrity.batch.load_cycle(Path('outputs/r5-ten-job-integrity-20261015/review-accepted'))
        cls.sources = review.integrity.load_sources(report)

    def test_t01_all_seven_are_dispositioned_without_a_fabricated_resolution(self):
        result = review.review(self.stages, self.sources, NOW)
        self.assertEqual(7, len(result['findings']))
        self.assertEqual(7, result['summary']['unresolvedSourceMeasuresRetained'])
        self.assertFalse(result['summary']['globalR5Done'])
        self.assertTrue(all(not r['mlConversionAllowed'] and not r['applyAllowed'] for r in result['findings']))

    def test_t02_water_alternative_is_the_exact_explicit_ml_pair_not_the_250ml_cup(self):
        result = review.review(self.stages, self.sources, NOW)['waterAlternative']
        self.assertEqual(93847, result['sourcePortionId'])
        self.assertEqual((1, 1, 1), tuple(result['proposedOption'][key] for key in ('quantity', 'gramWeight', 'mlVolume')))
        self.assertEqual(143, result['existingOptionsKept'][0]['id'])
        self.assertIsNone(result['existingOptionsKept'][0]['gram_weight'])
        self.assertFalse(result['existingVolumeOnlyCupAttestsDensity'])
        self.assertFalse(result['appendOrPublishOption'])

    def test_t03_historical_profile_coincidence_does_not_authorize_reassignment(self):
        result = review.review(self.stages, self.sources, NOW)['tofuReview']
        for key in ('sourceIdReassignmentAllowed', 'chooseClosestNutritionAllowed', 'deriveWholeBlockAllowed'):
            self.assertFalse(result[key])
        self.assertIn('NOT_PROVEN_PROVIDER_ERROR', result['inference'])

    def test_changed_or_foreign_source_cannot_borrow_the_water_bridge(self):
        for mutation in ('identity', 'weight', 'measure-id'):
            sources = copy.deepcopy(self.sources)
            body = sources['USDA_FOODDATA:fdc:174158'][0]
            if mutation == 'identity':
                body['fdcId'] = 999
            else:
                portion = next(p for p in body['foodPortions'] if p['id'] == 93847)
                portion['gramWeight' if mutation == 'weight' else 'id'] = 2
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                review.review(self.stages, sources, NOW)

    def test_source_expiry_does_not_change_values_or_approve_the_alternative(self):
        before = review.cycle.digest(self.sources)
        result = review.review(self.stages, self.sources, NOW + timedelta(days=2))
        self.assertTrue(all(not row['sourceFreshness']['freshAtAudit'] for row in result['findings']))
        self.assertFalse(result['waterAlternative']['applyAllowed'])
        self.assertEqual(before, review.cycle.digest(self.sources))


if __name__ == '__main__':
    unittest.main()

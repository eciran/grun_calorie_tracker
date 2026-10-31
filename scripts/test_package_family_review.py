import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('review',Path(__file__).with_name('review-package-family-candidates.py'))
review=importlib.util.module_from_spec(spec); spec.loader.exec_module(review)

class ReviewTests(unittest.TestCase):
    def test_equivalent_mass(self):
        self.assertEqual(review.package('Yogurt 1 kg'),review.package('Yogurt 1000 g'))
    def test_equivalent_volume(self):
        self.assertEqual(review.package('Milk 1,5 lt'),review.package('Milk 1500 ml'))
    def test_mass_is_not_volume(self):
        self.assertNotEqual(review.package('Milk 100 g'),review.package('Milk 100 ml'))
    def test_multipack_is_not_single_pack(self):
        self.assertNotEqual(review.package('Drink 6 x 100 ml'),review.package('Drink 600 ml'))
    def test_percent_retained_in_identity(self):
        f=review.families.identity
        row={'catalog_type':'BRANDED_PRODUCT','brand':'X','display_name':'','brand_id':'1'}
        self.assertNotEqual(f({**row,'name':'Milk 1.5% 100 g'})[0],f({**row,'name':'Milk 3.5% 100 g'})[0])
    def test_gtin_checksum(self):
        self.assertTrue(review.gtin_valid('5449000003102'))
        self.assertFalse(review.gtin_valid('5449000003103'))
        self.assertFalse(review.gtin_valid(''))

if __name__=='__main__': unittest.main()

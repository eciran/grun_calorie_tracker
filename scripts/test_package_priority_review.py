import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('priority',Path(__file__).with_name('close-package-priority-review.py'))
priority=importlib.util.module_from_spec(spec); spec.loader.exec_module(priority)

class PriorityTests(unittest.TestCase):
    def test_zeros_are_not_verification(self):
        self.assertTrue(priority.all_zero_profile(dict(calories='0',protein='0.0',carbs='0',fat='0')))
    def test_missing_is_not_zero(self):
        self.assertFalse(priority.all_zero_profile(dict(calories='',protein='0',carbs='0',fat='0')))
    def test_nonzero_profile(self):
        self.assertFalse(priority.all_zero_profile(dict(calories='317',protein='22',carbs='0.9',fat='25')))

if __name__=='__main__': unittest.main()

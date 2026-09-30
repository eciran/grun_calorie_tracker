import importlib.util,unittest
from pathlib import Path
P=Path(__file__).with_name('build-category-conditional-refinement-apply.py'); S=importlib.util.spec_from_file_location('apply',P); M=importlib.util.module_from_spec(S); S.loader.exec_module(M)
class ApplyTest(unittest.TestCase):
 def setUp(self):
  self.base={'rules':[{'sourceTag':'en:milks','categorySlug':'milk','primaryPriority':1,'decision':'REVIEW_REQUIRED'}]}; self.ref={'rules':[{'id':'milk','sourceReviewCategory':'milk','targetCategory':'milk','requiredAnyTags':['en:whole-milks'],'excludedAnyTags':[]}]}
 def test_rehearsal(self):
  sql=M.build(self.base,self.ref,3,False); self.assertTrue(sql.rstrip().endswith('ROLLBACK;')); self.assertIn('expected 3',sql)
 def test_commit(self):
  sql=M.build(self.base,self.ref,3,True); self.assertTrue(sql.rstrip().endswith('COMMIT;')); self.assertIn("'APPLY' mode",sql)
 def test_positive_gate(self):
  with self.assertRaisesRegex(ValueError,'positive'): M.build(self.base,self.ref,0,False)
if __name__=='__main__': unittest.main()

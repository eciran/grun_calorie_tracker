import importlib.util, unittest
from pathlib import Path
P=Path(__file__).with_name("build-category-conditional-refinement-report.py")
S=importlib.util.spec_from_file_location("conditional",P); M=importlib.util.module_from_spec(S); S.loader.exec_module(M)
class ConditionalReportTest(unittest.TestCase):
    def test_read_only_and_exclusions(self):
        base={"rules":[{"sourceTag":"en:milks","categorySlug":"milk","primaryPriority":1,"decision":"REVIEW_REQUIRED"}]}
        refinements={"rules":[{"id":"milk","sourceReviewCategory":"milk","targetCategory":"milk","requiredAnyTags":["en:whole-milks"],"excludedAnyTags":["en:fake"]}]}
        sql=M.build(base,refinements)
        self.assertIn("BEGIN TRANSACTION READ ONLY",sql); self.assertIn("has_excluded",sql)
        self.assertIn("assigned.primary_category",sql); self.assertIn("sample_rank<=25",sql)
        self.assertNotIn("INSERT INTO food_",sql)
    def test_requires_required_tags(self):
        with self.assertRaisesRegex(ValueError,"Required tags"):
            M.build({"rules":[{"sourceTag":"en:x","categorySlug":"x","primaryPriority":1}]},{"rules":[{"id":"x","sourceReviewCategory":"x","targetCategory":"x","requiredAnyTags":[]}]})
if __name__=="__main__": unittest.main()

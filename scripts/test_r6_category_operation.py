import copy
import importlib.util
import json
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

spec = importlib.util.spec_from_file_location("prepare", Path(__file__).with_name("prepare-r6-category-rehearsal.py"))
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)


class CategoryOperationTests(unittest.TestCase):
    def plan(self):
        rows = [{"id":str(i),"targetCategory":{"id":1},"expectedBefore":{"food":{"id":i,"name":"O'Brien"},
                 "source_tags":[],"evidence":[]}} for i in range(1,579)]
        return {"rows":rows}

    def test_rehearsal_sql_has_database_session_expiry_and_count_gates(self):
        sql = prepare.apply_sql(self.plan(),{},"r6-rehearsal-012345abcdef",(datetime.now(timezone.utc)+timedelta(hours=1)).isoformat())
        for required in ("current_database()<>'r6_rehearsal'", "grun.rehearsal.owner", "package expired", "FOR UPDATE",
                         "FOR SHARE OF e", "FOR SHARE OF s", "Existing category/repeated apply", "<>578", "ISOLATION LEVEL READ COMMITTED"):
            self.assertIn(required,sql)
        self.assertNotIn("UPDATE food_items",sql)
        self.assertNotIn("ON CONFLICT",sql)
        self.assertIn("O''Brien",sql)

    def test_partial_and_duplicate_plan_rejected(self):
        plan = self.plan()
        plan["rows"].pop()
        with self.assertRaises(ValueError):
            prepare.apply_sql(plan,{},"r6-rehearsal-012345abcdef","2026-10-12T16:00:00+00:00")
        plan = self.plan()
        plan["rows"][-1]["id"] = "1"
        with self.assertRaises(ValueError):
            prepare.apply_sql(plan,{},"r6-rehearsal-012345abcdef","2026-10-12T16:00:00+00:00")

    def test_bad_owner_marker_rejected(self):
        with self.assertRaises(ValueError):
            prepare.prefix("production-owner';--","2026-10-12T16:00:00+00:00")

    def receipts(self):
        return [{"food_item_id":i,"category_id":1,"created_by":"r6-rehearsal-012345abcdef",
                 "updated_by":"r6-rehearsal-012345abcdef"} for i in range(1,579)]

    def test_undo_requires_complete_owned_exact_receipt(self):
        for mutate in (lambda r:r.pop(),lambda r:r[0].update(created_by="ADMIN"),lambda r:r[-1].update(food_item_id=1)):
            receipts = self.receipts()
            mutate(receipts)
            with self.assertRaises(ValueError):
                prepare.undo_sql(self.plan(),receipts,"r6-rehearsal-012345abcdef","2026-10-12T16:00:00+00:00")

    def test_undo_never_deletes_food_nutrients_aliases_or_servings(self):
        sql = prepare.undo_sql(self.plan(),self.receipts(),"r6-rehearsal-012345abcdef","2026-10-12T16:00:00+00:00")
        self.assertIn("to_jsonb(a)=r.body",sql)
        self.assertIn("later admin edit or ownership drift",sql)
        for forbidden in ("DELETE FROM food_items", "UPDATE food_items", "DELETE FROM food_item_search_aliases", "DELETE FROM food_item_serving_options"):
            self.assertNotIn(forbidden,sql)

    def audit_fixture(self, identifier="181008"):
        registry = json.loads((prepare.ROOT / "data/catalog/everyday-category-review-r6-v1.json").read_text(encoding="utf-8"))
        decision = next(d for d in registry["usdaNames"] if d["id"] == identifier)
        row = {"id":identifier,"food":{"source_key":decision["sourceKey"],"name":decision["currentName"]},
               "name_review":{"aliases":[],"options":[],"option_localizations":[],
                              "localizations":[{"id":1,"language":"EN","active":True},{"id":2,"language":"TR","active":True}]}}
        source = {"fdcId":int(decision["sourceKey"].rsplit(":",1)[-1]),"description":decision["sourceDescription"]}
        return row,decision,source

    def test_unproven_breast_alias_proposed_inactive_not_deleted(self):
        row,decision,source = self.audit_fixture()
        row["name_review"]["aliases"] = [{"id":2530,"alias":"Fırında tavuk göğsü","normalized_alias":"firinda tavuk gogsu","active":True,"language":"TR"},
                                           {"id":1,"alias":"light meat","normalized_alias":"light meat","active":True,"language":"EN"}]
        result = prepare.audit_name(row,decision,source)
        self.assertEqual("DEACTIVATE_UNPROVEN_BREAST_ALIAS",result["aliasActions"][0]["action"])
        self.assertEqual([1],result["preserveAliasIds"])
        self.assertFalse(result["liveApplyAllowed"])
        self.assertTrue(result["portionDataPreserved"])

    def test_legitimate_egg_cooking_alternative_gets_source_qualifier(self):
        row,decision,source = self.audit_fixture("181466")
        row["name_review"]["aliases"] = [{"id":1,"alias":"çırpılmış yumurta beyazı","normalized_alias":"cirpilmis yumurta beyazi","active":True,"language":"TR"}]
        result = prepare.audit_name(row,decision,source)
        change = result["aliasActions"][0]
        self.assertEqual("REPLACE_WITH_SOURCE_QUALIFIED_ALIAS",change["action"])
        self.assertIn("Pişirme spreyiyle",change["replaceWith"]["alias"])
        self.assertNotIn("ş",change["replaceWith"]["normalized_alias"])
        self.assertFalse(result["portionSourceAccuracyCertified"])

    def test_inactive_and_noncontradictory_aliases_preserved(self):
        row,decision,source = self.audit_fixture()
        row["name_review"]["aliases"] = [{"id":1,"alias":"breast","normalized_alias":"breast","active":False,"language":"EN"},
                                           {"id":2,"alias":"chicken","normalized_alias":"chicken","active":True,"language":"EN"}]
        result = prepare.audit_name(row,decision,source)
        self.assertEqual([],result["aliasActions"])
        self.assertEqual([1,2],result["preserveAliasIds"])

    def test_missing_locale_or_source_identity_refuses_name_plan(self):
        row,decision,source = self.audit_fixture()
        row["name_review"]["localizations"].pop()
        with self.assertRaises(ValueError):
            prepare.audit_name(row,decision,source)
        row,decision,source = self.audit_fixture()
        source["description"] = "Other source food"
        with self.assertRaises(ValueError):
            prepare.audit_name(row,decision,source)

    def test_existing_inactive_qualified_alias_requires_manual_review(self):
        row,decision,source = self.audit_fixture("181466")
        row["name_review"]["aliases"] = [{"id":1,"alias":"egg white omelet","normalized_alias":"egg white omelet","active":True,"language":"EN"},
                                           {"id":2,"alias":"egg white omelet (cooking spray)","normalized_alias":"egg white omelet (cooking spray)","active":False,"language":"EN"}]
        with self.assertRaises(ValueError):
            prepare.audit_name(row,decision,source)

    def test_new_aws_read_is_select_only_and_scoped(self):
        sql = (Path(__file__).with_name("sql") / "export-r6-category-release-review.sql").read_text()
        for marker in ("READ ONLY", "181008,181466", "food_item_search_aliases", "food_item_localizations", "option_localizations"):
            self.assertIn(marker,sql)
        for forbidden in ("UPDATE ", "INSERT ", "DELETE ", "DROP "):
            self.assertNotIn(forbidden,sql.upper())


if __name__ == "__main__":
    unittest.main()

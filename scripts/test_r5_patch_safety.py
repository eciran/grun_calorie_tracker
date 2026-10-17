import copy
import importlib.util
import unittest
from pathlib import Path


def load(name,file):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).with_name(file))
    m=importlib.util.module_from_spec(spec); spec.loader.exec_module(m); return m


patch=load("patch","build-r5-controlled-patch.py")
check=load("check","verify-r5-applied-receipts.py")


class PatchSafetyTests(unittest.TestCase):
    def row(self):
        return {"id":"1","sourceKey":"barcode:5449000000996","status":"READY_FOR_BACKUP_REHEARSAL","nutritionWriteAllowed":False,
                "before":{"food":{"id":1,"nutrition_reference_unit":"PER_100G","data_source":"OPEN_FOOD_FACTS","calories":50},"options":[],"option_localizations":[],"evidence":[],"references":{}},
                "changes":{"nutrition_reference_unit":"PER_100ML","serving_unit":"MILLILITER","serving_size_grams":None},"options":[],"nutrients":{},"sourceFingerprint":"f"*64}

    def test_only_three_metadata_columns_can_be_changed(self):
        r=self.row(); r["changes"]["calories"]=0
        with self.assertRaisesRegex(ValueError,"Forbidden"):patch.generate([r],"r5-test-safe","2026-10-13T00:00:00Z")
        r=self.row(); r["changes"]["serving_size_grams"]=250
        with self.assertRaisesRegex(ValueError,"native volume"):patch.generate([r],"r5-test-safe","2026-10-13T00:00:00Z")

    def test_held_rows_and_unbounded_operations_are_rejected(self):
        r=self.row(); r["status"]="HOLD"
        with self.assertRaises(ValueError):patch.generate([r],"r5-test-safe","2026-10-13T00:00:00Z")
        for rows,operation in (([],"r5-test-safe"),([self.row()]*51,"r5-test-safe"),([self.row()],"bad';sql")):
            with self.assertRaises(ValueError):patch.generate(rows,operation,"2026-10-13T00:00:00Z")

    def test_quotes_escaped_and_do_delimiter_cannot_enter_sql(self):
        r=self.row(); r["before"]["food"]["name"]="User's Product"
        sql,_=patch.generate([r],"r5-test-safe","2026-10-13T00:00:00Z")
        self.assertIn("User''s Product",sql)
        r["before"]["food"]["name"]="$r5_patch$"
        with self.assertRaisesRegex(ValueError,"delimiter"):patch.generate([r],"r5-test-safe","2026-10-13T00:00:00Z")

    def test_rollback_targets_owned_ids_and_blocks_later_usage(self):
        sql,_=patch.generate([{"id":"1","operation":"r5-test-safe","status":"APPLIED"}],"r5-test-safe","2026-10-13T00:00:00Z",True)
        self.assertIn("insertedOptionIds",sql)
        self.assertIn("subsequently used portion",sql)
        self.assertIn("subsequently logged food",sql)
        self.assertIn("rollback dependency conflict",sql)
        self.assertNotIn("DELETE FROM food_logs",sql)

    def test_postcheck_detects_unapproved_field_or_owned_option_drift(self):
        before={"food":{"id":1,"calories":50,"nutrition_reference_unit":"PER_100G"},"references":{}}
        after={"food":{"id":1,"calories":50,"nutrition_reference_unit":"PER_100ML"},"options":[],"option_localizations":[],"evidence":[]}
        receipt={"id":"1","before":before,"after":after,"insertedOptionIds":[],"insertedEvidenceIds":[]}
        current={"1":{**copy.deepcopy(after),"references":{}}}
        self.assertEqual("PASS",check.verify([receipt],current)["status"])
        current["1"]["food"]["calories"]=51
        with self.assertRaises(ValueError):check.verify([receipt],current)
        with self.assertRaises(ValueError):check.verify([receipt,receipt],current)


if __name__=="__main__":unittest.main()

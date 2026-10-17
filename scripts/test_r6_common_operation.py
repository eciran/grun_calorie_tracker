import copy
import json
import tempfile
import unittest
from datetime import datetime,timedelta,timezone
from pathlib import Path
from importlib.util import module_from_spec,spec_from_file_location

spec=spec_from_file_location('common_operation_tests',Path(__file__).with_name('prepare-r6-common-operation.py'))
p=module_from_spec(spec)
spec.loader.exec_module(p)
n=p.module('common_undo_tests','rehearse-r6-held-names.py')


def ledger():
    registry={r['id']:r for r in p.names.held.read_json(p.names.REGISTRY)['entries']}
    rows=[]
    for identifier,decision in registry.items():
        before={'id':identifier,'food':{'id':int(identifier),'source_key':decision['sourceKey']},'name_review':{'aliases':[]}}
        rows.append({'id':identifier,'component':p.OVERRIDES.get(identifier,'names'),'expectedBefore':before,
                     'beforeSha256':p.fingerprint(before),'foodChanges':{},'aliasActions':[],'localizationActions':[]})
    return rows,registry


def snapshot(now):
    return {'Status':'available','Encrypted':True,'DBInstanceIdentifier':'grun-staging-db','Engine':'postgres','EngineVersion':'18.3',
            'DBSnapshotIdentifier':'test-common-backup','DBSnapshotArn':'arn:aws:rds:eu-west-1:911291530432:snapshot:test-common-backup',
            'SnapshotCreateTime':(now-timedelta(minutes=5)).isoformat()}


class CommonOperationTests(unittest.TestCase):
    def test_ledger_has_one_owner_per_candidate_with_five_explicit_overrides(self):
        rows,registry=ledger()
        original=copy.deepcopy(rows)
        self.assertEqual({},p.validate_ledger(rows,registry))
        self.assertEqual(original,rows)
        self.assertEqual(5,sum(r['component']!='names' for r in rows))

    def test_append_special_decision_instead_of_replacing_is_rejected(self):
        rows,registry=ledger()
        rows.append(copy.deepcopy(rows[0]))
        with self.assertRaises(ValueError): p.validate_ledger(rows,registry)

    def test_base_plan_cannot_overwrite_authoritative_brand_preparation_or_identity(self):
        for identifier in p.OVERRIDES:
            rows,registry=ledger()
            next(r for r in rows if r['id']==identifier)['component']='names'
            with self.assertRaises(ValueError): p.validate_ledger(rows,registry)

    def test_nutrition_portion_unit_source_key_and_id_fields_are_forbidden(self):
        for field in ('calories','gram_weight','ml_volume','nutrition_reference_unit','source_key','id','source_data_type','market_region'):
            rows,registry=ledger()
            rows[0]['foodChanges'][field]='changed'
            with self.assertRaises(ValueError): p.validate_ledger(rows,registry)

    def test_identical_alias_between_two_planned_products_is_a_conflict(self):
        rows,registry=ledger()
        action={'action':'ADD_SOURCE_QUALIFIED_ALIAS','language':'TR','alias':'Test','normalized_alias':'test'}
        rows[0]['aliasActions']=[action]
        rows[1]['aliasActions']=[copy.deepcopy(action)]
        with self.assertRaises(ValueError): p.validate_ledger(rows,registry)

    def test_wrong_alias_owner_or_unsupported_alias_action_is_not_accepted(self):
        rows,registry=ledger()
        rows[0]['aliasActions']=[{'action':'DEACTIVATE_EXACT_MISLEADING_ALIAS','before':{'id':1,'food_item_id':999}}]
        with self.assertRaises(ValueError): p.validate_ledger(rows,registry)
        rows,registry=ledger()
        rows[0]['aliasActions']=[{'action':'REACTIVATE_INACTIVE_ALIAS'}]
        with self.assertRaises(ValueError): p.validate_ledger(rows,registry)

    def test_parent_before_image_checksum_is_required(self):
        rows,registry=ledger()
        rows[0]['expectedBefore']['food']['source_key']='other'
        with self.assertRaises(ValueError): p.validate_ledger(rows,registry)

    def test_backup_must_be_fresh_available_encrypted_and_exact_region_account_database(self):
        now=datetime(2026,10,13,17,0,tzinfo=timezone.utc)
        self.assertEqual('test-common-backup',p.backup_gate(snapshot(now),now)['identifier'])
        for field,value in [('Status','creating'),('Encrypted',False),('DBInstanceIdentifier','another-db'),
                            ('Engine','mysql'),('EngineVersion','17.0'),('DBSnapshotArn','arn:aws:rds:us-east-1:123:snapshot:test-common-backup')]:
            current=snapshot(now)
            current[field]=value
            with self.assertRaises(ValueError): p.backup_gate(current,now)

    def test_old_future_or_naive_backup_timestamp_is_rejected(self):
        now=datetime(2026,10,13,17,0,tzinfo=timezone.utc)
        for stamp in [(now-timedelta(hours=2)).isoformat(),(now+timedelta(minutes=1)).isoformat(),'2026-10-13T16:55:00']:
            current=snapshot(now)
            current['SnapshotCreateTime']=stamp
            with self.assertRaises(ValueError): p.backup_gate(current,now)

    def test_null_original_brand_and_numeric_brand_id_can_be_undone_without_stringifying_null(self):
        before={'id':'181453','food':{'id':181453,'brand':None,'brand_id':None},'assignments':[],'source_tags':[],'evidence':[],
                'name_review':{'aliases':[],'localizations':[],'options':[],'option_localizations':[]}}
        row={'id':'181453','expectedBefore':before,'foodNameChanges':{'brand':'Lifeway','brand_id':123},'localizationActions':[],'aliasActions':[]}
        after=copy.deepcopy(before)
        after['food'].update(row['foodNameChanges'])
        sql=n.undo_rows([row],[after],'r6-rehearsal-123456abcdef','2099-01-01T00:00:00+00:00')
        self.assertIn('brand=NULL',sql)
        self.assertIn('brand_id=NULL',sql)
        self.assertNotIn("brand='None'",sql)
        self.assertEqual(1,sql.count('CREATE TEMP TABLE r6_plan'))

    def test_common_reader_rejects_missing_protected_companion(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'selection.jsonl'
            rows,registry=ledger()
            selected=[{'id':int(r['id']),'requestedAliases':[{'language':'EN','normalized_alias':'test'}]} for r in rows]
            path.write_text(''.join(json.dumps(r)+'\n' for r in selected),encoding='utf-8')
            with self.assertRaises(ValueError): p.names.collector.common_review_sql(path)
            selected.extend({'id':int(i),'requestedAliases':[{'language':'EN','normalized_alias':"o'brien"}]} for i in p.PROTECTED)
            path.write_text(''.join(json.dumps(r)+'\n' for r in selected),encoding='utf-8')
            sql=p.names.collector.common_review_sql(path)
            self.assertIn('REPEATABLE READ READ ONLY',sql)
            self.assertIn("'o''brien'",sql)
            self.assertIn('commonDependencies',sql)
            self.assertNotIn('__SELECTED_IDS__',sql)
            self.assertNotIn('UPDATE food_items',sql)


if __name__=='__main__': unittest.main()

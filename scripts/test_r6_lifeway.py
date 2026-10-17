import copy
import json
import tempfile
import unittest
from pathlib import Path
from importlib.util import module_from_spec, spec_from_file_location

spec=spec_from_file_location('lifeway_tests',Path(__file__).with_name('prepare-r6-lifeway.py'))
p=module_from_spec(spec)
spec.loader.exec_module(p)


def fixture():
    food={'id':181453,'source_key':'USDA_FOODDATA:fdc:170904','name':'Plain Low-Fat Kefir',
          'catalog_type':'GENERIC_INGREDIENT','brand':None,'brand_id':None,
          'canonical_food_key':'GLOBAL:GENERIC_INGREDIENT:PREPARED:plain_low_fat_kefir',
          'preparation_state':'PREPARED','nutrition_reference_unit':'PER_100G','barcode':None,'normalized_barcode':None,
          'data_source':'USDA_FOODDATA','is_custom':False,'publication_status':'PUBLISHED','calories':43}
    locales=[{'id':5308,'food_item_id':181453,'language':'EN','active':True},
             {'id':5309,'food_item_id':181453,'language':'TR','active':True}]
    before={'id':'181453','food':food,'name_review':{'localizations':locales}}
    changes={'name':p.EN,'display_name':p.EN,'short_display_name':p.EN,'brand':'Lifeway','catalog_type':'BRANDED_PRODUCT',
             'canonical_food_key':None,'publication_status':'INTERNAL_REVIEW','verification_status':'NEEDS_REVIEW'}
    row={'id':'181453','expectedBefore':before,'beforeSha256':p.fingerprint(before),'foodChanges':changes,
         'localizationActions':[{'action':'UPDATE_EXACT_ACTIVE_LOCALIZATION','before':r,
            'changes':{'display_name':p.EN if r['language']=='EN' else p.TR,
                       'short_display_name':p.EN if r['language']=='EN' else p.TR}} for r in locales],
         'aliasActions':[{'action':'ADD_SOURCE_QUALIFIED_ALIAS','language':lang,'alias':label,
                          'normalized_alias':p.names.aliases.normalize_alias(label)} for lang,label in [('EN',p.EN),('TR',p.TR)]]}
    meta={'brands':[],'brandAliases':[],'canonicalResolutions':[],
          'sourceKeyClaims':[{'id':181453,'source_key':'USDA_FOODDATA:fdc:170904'}],
          'canonicalClaims':[{'id':181453,'source_key':'USDA_FOODDATA:fdc:170904','name':'Plain Low-Fat Kefir'}],
          'dependencies':[{'constraint':'fk_logs','schema':'public','table':'food_logs','supported':True,'count':'2','fingerprint':'a'*32}]}
    return {'version':'R6_LIFEWAY_BRAND_PREPARATION_V1','schemaVersion':'305','approval':None,'backup':None,
            'liveApplyAllowed':False,'row':row,'brandAndDependenciesBefore':meta}


class LifewayTests(unittest.TestCase):
    def test_review_only_plan_does_not_rewrite_inputs_or_retail_gtin(self):
        plan=fixture()
        original=copy.deepcopy(plan)
        p.validate(plan)
        self.assertEqual(original,plan)
        self.assertEqual('INTERNAL_REVIEW',plan['row']['foodChanges']['publication_status'])

    def test_existing_brand_or_canonical_resolution_cannot_be_overwritten(self):
        for key in ('brands','brandAliases','canonicalResolutions'):
            plan=fixture()
            plan['brandAndDependenciesBefore'][key]=[{'id':1}]
            with self.assertRaises(ValueError): p.validate(plan)

    def test_no_nutrition_unit_serving_market_or_source_key_writes(self):
        for field in ('calories','serving_unit','serving_size_grams','market_region','source_key','barcode'):
            plan=fixture()
            plan['row']['foodChanges'][field]='changed'
            with self.assertRaises(ValueError): p.validate(plan)

    def test_generic_key_cannot_remain_on_branded_reference_and_no_auto_publication(self):
        for field,value in [('canonical_food_key','old:generic'),('publication_status','PUBLISHED'),('verification_status','VERIFIED')]:
            plan=fixture()
            plan['row']['foodChanges'][field]=value
            with self.assertRaises(ValueError): p.validate(plan)

    def test_source_duplicate_or_unsupported_fk_blocks(self):
        plan=fixture()
        plan['brandAndDependenciesBefore']['sourceKeyClaims'].append({'id':1})
        with self.assertRaises(ValueError): p.validate(plan)
        plan=fixture()
        plan['brandAndDependenciesBefore']['dependencies'][0]['supported']=False
        with self.assertRaises(ValueError): p.validate(plan)

    def test_old_brand_barcode_or_basis_change_needs_renewed_review(self):
        for field in ('brand','brand_id','barcode','source_key','nutrition_reference_unit','preparation_state'):
            plan=fixture()
            plan['row']['expectedBefore']['food'][field]='changed'
            plan['row']['beforeSha256']=p.fingerprint(plan['row']['expectedBefore'])
            with self.assertRaises(ValueError): p.validate(plan)

    def test_locale_scope_and_alias_actions_are_exact_and_source_qualified(self):
        plan=fixture()
        plan['row']['aliasActions'][0]['alias']='Plain Kefir'
        with self.assertRaises(ValueError): p.validate(plan)
        plan=fixture()
        plan['row']['localizationActions'][0]['before']['food_item_id']=1
        plan['row']['beforeSha256']=p.fingerprint(plan['row']['expectedBefore'])
        with self.assertRaises(ValueError): p.validate(plan)

    def test_live_owner_approval_or_schema_drift_is_not_preparation(self):
        for key,value in [('approval','yes'),('backup','unverified'),('liveApplyAllowed',True),('schemaVersion','307')]:
            plan=fixture()
            plan[key]=value
            with self.assertRaises(ValueError): p.validate(plan)

    def test_scoped_read_is_read_only_and_quotes_bounded_alias_inputs(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'selection.jsonl'
            path.write_text(json.dumps({'id':181453,'requestedAliases':[{'language':'EN','normalized_alias':"o'brien"}]})+'\n',encoding='utf-8')
            sql=p.names.collector.lifeway_review_sql(path)
            self.assertIn('REPEATABLE READ READ ONLY',sql)
            self.assertIn("'o''brien'",sql)
            self.assertIn('food_brands',sql)
            self.assertTrue(sql.rstrip().endswith('ROLLBACK;'))
            path.write_text(json.dumps({'id':1,'requestedAliases':[]}),encoding='utf-8')
            with self.assertRaises(ValueError): p.names.collector.lifeway_review_sql(path)


if __name__=='__main__': unittest.main()

import copy
import json
import tempfile
import unittest
from pathlib import Path
from importlib.util import module_from_spec, spec_from_file_location

spec = spec_from_file_location('edamame_tests', Path(__file__).with_name('rehearse-r6-edamame.py'))
r = module_from_spec(spec)
spec.loader.exec_module(r)
p = r.prepare


def fixture():
    food = {'id':181467,'source_key':'USDA_FOODDATA:fdc:168411','data_source':'USDA_FOODDATA',
            'catalog_type':'GENERIC_INGREDIENT','name':'Prepared Edamame','display_name':'Prepared Edamame',
            'short_display_name':'Prepared Edamame','preparation_state':'BOILED',
            'canonical_food_key':p.OLD_KEY,'nutrition_reference_unit':'PER_100G','is_custom':False,
            'publication_status':'PUBLISHED','calories':121}
    locales = [{'id':identifier,'food_item_id':181467,'language':lang,'active':True,'display_name':label,
                'short_display_name':label,'updated_at':'before'}
               for identifier,lang,label in [(5336,'EN','Prepared Edamame'),(5337,'TR','Haşlanmış Edamame')]]
    aliases = [{'id':identifier,'food_item_id':181467,'language':lang,'active':True,'normalized_alias':normalized,
                'alias':label} for identifier,lang,normalized,label in [(2865,'TR','edamame','edamame'),
                    (2866,'TR','haslanmis edamame','haşlanmış edamame'),(2867,'EN','edamame cooked','edamame cooked')]]
    option = {'id':3265,'food_item_id':181467,'unit_type':'CUP','quantity':1,'gram_weight':155,'ml_volume':None,
              'label':'1 cup','is_default':True,'user_visible':True,'quality_status':'VERIFIED'}
    before = {'id':'181467','food':food,'assignments':[],'source_tags':[],'evidence':[],
              'name_review':{'localizations':locales,'aliases':aliases,'options':[option],'option_localizations':[]}}
    row = {'id':'181467','expectedBefore':before,'beforeSha256':p.fingerprint(before),'foodChanges':dict(p.CHANGES),
           'localizationActions':[{'action':'UPDATE_EXACT_ACTIVE_LOCALIZATION','before':l,'changes':{
               'display_name':p.EN if l['language']=='EN' else p.TR,'short_display_name':p.EN if l['language']=='EN' else p.TR}}
               for l in locales],
           'aliasActions':[{'action':'DEACTIVATE_EXACT_MISLEADING_ALIAS','before':a} for a in aliases if a['id'] in {2866,2867}]
               + [{'action':'ADD_SOURCE_QUALIFIED_ALIAS','language':lang,'alias':label,'normalized_alias':p.names.aliases.normalize_alias(label)}
                  for lang,label in [('EN',p.EN),('TR',p.TR)]]}
    meta = {'keyClaims':[{'id':181467,'source_key':'USDA_FOODDATA:fdc:168411','canonical_food_key':p.OLD_KEY,
                         'is_custom':False,'publication_status':'PUBLISHED'}],'canonicalResolutions':[],'keyIndexes':[],
            'dependencies':[{'constraint':'fk_logs','schema':'public','table':'food_logs','column':'food_id',
                             'supported':True,'count':'0','fingerprint':'a'*32}]}
    return {'version':'R6_EDAMAME_PREPARATION_V1','liveApplyAllowed':False,'approval':None,'backup':None,
            'schemaVersion':'305','row':row,'dependencyBefore':meta}


def receipt(plan,owner):
    row = copy.deepcopy(plan['row']['expectedBefore'])
    row['food'].update(plan['row']['foodChanges'])
    for locale in row['name_review']['localizations']:
        label = p.EN if locale['language']=='EN' else p.TR
        locale.update(display_name=label,short_display_name=label,updated_at='after')
    for alias in row['name_review']['aliases']:
        if alias['id'] in {2866,2867}: alias['active']=False
    for i,action in enumerate(plan['row']['aliasActions'][-2:]):
        row['name_review']['aliases'].append({'id':3000+i,'food_item_id':181467,'language':action['language'],
            'alias':action['alias'],'normalized_alias':action['normalized_alias'],'active':True,'source':owner,'alias_type':'ADMIN_MANUAL'})
    meta = copy.deepcopy(plan['dependencyBefore'])
    meta['keyClaims'][0]['canonical_food_key']=p.NEW_KEY
    return {'owner':owner,'rows':[row],'dependencies':meta}


class EdamameTests(unittest.TestCase):
    def test_exact_plan_is_review_only_and_preserves_gram_cup(self):
        plan=fixture()
        previous=copy.deepcopy(plan)
        p.validate(plan)
        self.assertEqual(plan,previous)
        self.assertNotIn('ml_volume',plan['row']['foodChanges'])

    def test_cooking_method_or_frozen_qualifier_cannot_be_invented_or_lost(self):
        for field,value in [('preparation_state','COOKED'),('preparation_state','BOILED'),
                            ('display_name','Prepared Edamame'),('canonical_food_key',p.OLD_KEY)]:
            plan=fixture()
            plan['row']['foodChanges'][field]=value
            with self.assertRaises(ValueError): p.validate(plan)

    def test_nutrient_units_source_category_and_portion_writes_rejected(self):
        for field in ('calories','source_key','serving_unit','nutrition_reference_unit','primary_category','source_data_type'):
            plan=fixture()
            plan['row']['foodChanges'][field]='changed'
            with self.assertRaises(ValueError): p.validate(plan)

    def test_source_key_canonical_collision_or_resolution_blocks(self):
        for field in ('keyClaims','canonicalResolutions'):
            plan=fixture()
            plan['dependencyBefore'][field].append({'id':1})
            with self.assertRaises(ValueError): p.validate(plan)

    def test_source_portion_drift_does_not_become_a_density(self):
        for field,value in [('gram_weight',154),('ml_volume',250),('unit_type','MILLILITER'),('label','1 cup boiled edamame')]:
            plan=fixture()
            plan['row']['expectedBefore']['name_review']['options'][0][field]=value
            plan['row']['beforeSha256']=p.fingerprint(plan['row']['expectedBefore'])
            with self.assertRaises(ValueError): p.validate(plan)

    def test_basic_alias_retained_unproven_boiled_and_cooked_deactivated_only(self):
        plan=fixture()
        self.assertEqual({2866,2867},{a['before']['id'] for a in plan['row']['aliasActions'] if 'before' in a})
        plan['row']['aliasActions'][0]['before']=plan['row']['expectedBefore']['name_review']['aliases'][0]
        with self.assertRaises(ValueError): p.validate(plan)

    def test_unknown_fk_and_live_approval_fail_closed(self):
        plan=fixture()
        plan['dependencyBefore']['dependencies'][0]['supported']=False
        with self.assertRaises(ValueError): p.validate(plan)
        for field,value in [('liveApplyAllowed',True),('approval','yes'),('backup','unverified'),('schemaVersion','307')]:
            plan=fixture()
            plan[field]=value
            with self.assertRaises(ValueError): p.validate(plan)

    def test_receipt_guards_every_numeric_portion_alias_and_history_field(self):
        plan=fixture()
        after=receipt(plan,'r6-rehearsal-123456abcdef')
        r.verify(plan,after,after['owner'],plan['dependencyBefore'])
        for field in ('calories','protein','serving_size_grams'):
            changed=copy.deepcopy(after)
            changed['rows'][0]['food'][field]=999
            with self.assertRaises(ValueError): r.verify(plan,changed,after['owner'],plan['dependencyBefore'])
        changed=copy.deepcopy(after)
        changed['dependencies']['dependencies'][0]['fingerprint']='b'*32
        with self.assertRaises(ValueError): r.verify(plan,changed,after['owner'],plan['dependencyBefore'])

    def test_undo_uses_one_before_table_and_receipt_bound_maintenance_lock(self):
        plan=fixture()
        owner='r6-rehearsal-123456abcdef'
        after=receipt(plan,owner)
        sql=r.undo_sql(plan,after,plan['dependencyBefore'],owner,'2099-01-01T00:00:00+00:00')
        self.assertEqual(1,sql.count('CREATE TEMP TABLE r6_plan'))
        self.assertIn('IN SHARE ROW EXCLUSIVE MODE',sql)
        self.assertIn('r6_rehearsal',sql)
        self.assertIn(p.OLD_KEY,sql)
        self.assertNotIn('UPDATE food_logs',sql)

    def test_exact_bounded_scoped_query_is_read_only(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'selection.jsonl'
            path.write_text(json.dumps({'id':181467,'requestedAliases':[{'language':'EN','normalized_alias':"o'brien"}]})+'\n',encoding='utf-8')
            sql=p.names.collector.edamame_review_sql(path)
            self.assertIn('REPEATABLE READ READ ONLY',sql)
            self.assertIn("'o''brien'",sql)
            self.assertIn(p.NEW_KEY,sql)
            self.assertNotIn('UPDATE food_items',sql)
            path.write_text(json.dumps({'id':181453,'requestedAliases':[]})+'\n',encoding='utf-8')
            with self.assertRaises(ValueError): p.names.collector.edamame_review_sql(path)


if __name__=='__main__': unittest.main()

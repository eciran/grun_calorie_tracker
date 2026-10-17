"""Portable exact-scope and write-boundary rejection tests, without AWS calls."""
import copy
import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('r7_operation_test', Path(__file__).with_name('r7-approved-presentation-operation.py'))
operation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(operation)


class PresentationOperationTests(unittest.TestCase):
    def fixtures(self):
        rows=[]
        for identifier in sorted(operation.SCOPE):
            food={'id':int(identifier),'barcode':None,'normalized_barcode':None,'brand':None,'brand_id':None,
                  'canonical_food_key':None,'food_concept_key':None,'composition_variant_key':None}
            generic=identifier in operation.review.SCOPE
            source='USDA_FOODDATA:fdc:'+operation.review.SCOPE[identifier] if generic else 'barcode:8696368000237'
            fields={k:'example' for k in ('name','display_name','short_display_name')} if generic else {}
            if generic:
                fields.update(catalog_type='GENERIC_INGREDIENT',source_data_type='SR Legacy')
            identity={'food_concept_key':'example','composition_variant_key':'fdc_'+operation.review.SCOPE[identifier],
                      'canonical_food_key':'C2:example:'+identifier} if generic else None
            rows.append({'id':identifier,'sourceKey':source,'foodChanges':fields,'identityProposal':identity,
                         'expectedBefore':{'food':food,'assignments':[]},
                         'aliasActions':[{}]* (1 if identifier=='181013' else 4 if identifier in {'181138','181140'} else 0),
                         'localizationActions':[{},{}]})
        plan={'version':'R7_SOURCE_PRESENTATION_REVIEW_V1','rows':rows,'inputHashes':{},
              'nutritionWrites':0,'mlConversions':0,'physicalMerges':0}
        meta={'resolutions':[],'keyClaims':[{'id':int(i)} for i in sorted(operation.SCOPE)],
              'dependencies':[{'table':'example_'+str(i),'schema':'public','column':'food_id','supported':True} for i in range(26)],
              'categories':[{'slug':s,'id':i,'parent_id':p,'active':True} for s,i,p in
                            [('beverages',9,None),('vegetables',14,1),('poultry',15,2)]]}
        return plan,meta

    def test_reviewed_exact_scope_validates_without_mutating_inputs(self):
        plan,meta=self.fixtures()
        before=copy.deepcopy((plan,meta))
        operation.validate(plan,meta)
        self.assertEqual(before,(plan,meta))

    def test_nutrition_reference_source_and_market_fields_forbidden(self):
        for field in ('calories','protein','nutrition_reference_unit','source_key','market_region','preparation_state','brand_id'):
            with self.subTest(field=field):
                plan,meta=self.fixtures()
                next(r for r in plan['rows'] if r['id']=='181013')['foodChanges'][field]='unsafe'
                with self.assertRaises(ValueError): operation.validate(plan,meta)

    def test_external_identity_resolution_category_and_fk_drift_forbidden(self):
        for drift in ('claim','resolution','inactive','wrong_parent','foreign_schema','unsupported','missing_fk'):
            with self.subTest(drift=drift):
                plan,meta=self.fixtures()
                if drift=='claim': meta['keyClaims'].append({'id':999})
                if drift=='resolution': meta['resolutions'].append({'primary_food_item_id':181013})
                if drift=='inactive': meta['categories'][0]['active']=False
                if drift=='wrong_parent': meta['categories'][1]['parent_id']=9
                if drift=='foreign_schema': meta['dependencies'][0]['schema']='other'
                if drift=='unsupported': meta['dependencies'][0]['supported']=False
                if drift=='missing_fk': meta['dependencies'].pop()
                with self.assertRaises(ValueError): operation.validate(plan,meta)

    def test_generic_brand_barcode_or_existing_c2_cannot_be_erased(self):
        for field in ('brand','brand_id','barcode','normalized_barcode','canonical_food_key','food_concept_key','composition_variant_key'):
            with self.subTest(field=field):
                plan,meta=self.fixtures()
                next(r for r in plan['rows'] if r['id']=='181013')['expectedBefore']['food'][field]='existing'
                with self.assertRaises(ValueError): operation.validate(plan,meta)

    def test_duplicate_scope_alias_count_or_off_parent_change_forbidden(self):
        for drift in ('duplicate','aliases','off_parent','writes','ml','merge'):
            with self.subTest(drift=drift):
                plan,meta=self.fixtures()
                if drift=='duplicate': plan['rows'][0]=copy.deepcopy(plan['rows'][1])
                if drift=='aliases': plan['rows'][1]['aliasActions']=[]
                if drift=='off_parent': next(r for r in plan['rows'] if r['id']=='49340')['foodChanges']={'name':'other'}
                if drift=='writes': plan['nutritionWrites']=1
                if drift=='ml': plan['mlConversions']=1
                if drift=='merge': plan['physicalMerges']=1
                with self.assertRaises(ValueError): operation.validate(plan,meta)

    def test_production_guard_uses_common_catalog_writer_fence(self):
        plan,meta=self.fixtures()
        package={'operation':'r7-approved-012345abcdef','planDigest':'a'*64,
                 'expiresAt':'2099-01-01T00:00:00+00:00'}
        rows=[{'id':r['id'],**r['expectedBefore']} for r in plan['rows']]
        sql=operation.guard(package,meta,rows)
        self.assertIn('pg_advisory_xact_lock(17491,310)',sql)
        self.assertNotIn('pg_advisory_xact_lock(190713,',sql)


if __name__=='__main__':
    unittest.main()

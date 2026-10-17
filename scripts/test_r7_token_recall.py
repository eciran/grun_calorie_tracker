import copy
import importlib.util
import io
import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('r7_token_probe',Path(__file__).with_name('measure-r7-token-recall.py'))
probe=importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)


class TokenRecallTests(unittest.TestCase):
    def receipt(self):
        rows=[]
        for identifier in ('49340','181013','181138','181140','181155'):
            rows.append({'id':identifier,'food':{'source_key':'source:'+identifier,
                'catalog_type':'BRANDED_PRODUCT' if identifier=='49340' else 'GENERIC_INGREDIENT',
                'preparation_state':None if identifier=='49340' else 'BOILED'},
                'assignments':[{'category_id':9 if identifier=='49340' else 15 if identifier=='181013' else 14}],
                'name_review':{'localizations':[{'active':True,'language':lang,'display_name':lang+' Reviewed Label'} for lang in ('EN','TR')]}})
        return {'rows':rows}

    def test_all_sixty_cases_have_local_only_category_and_independent_judgment(self):
        receipt=self.receipt()
        before=copy.deepcopy(receipt)
        cases=probe.cases(receipt)
        self.assertEqual(receipt,before)
        self.assertEqual(60,len(cases))
        self.assertEqual(60,len({c['id'] for c in cases}))
        self.assertTrue(all(c['params']['categoryId']>0 and c['params']['page']==0 and c['params']['size']==100 for c in cases))
        self.assertEqual({'TR','UK_IE','EU'},{c['params']['region'] for c in cases})
        self.assertTrue(all(c['params']['region']=='TR' for c in cases if c['expectedId']==49340))

    def test_missing_or_duplicate_review_identity_refuses_generation(self):
        for mode in ('missing','duplicate'):
            receipt=self.receipt()
            if mode=='missing': receipt['rows'].pop()
            else: receipt['rows'][0]=copy.deepcopy(receipt['rows'][1])
            with self.assertRaises(ValueError): probe.cases(receipt)

    def test_recall_is_not_self_approved_from_current_top_result(self):
        case=probe.cases(self.receipt())[0]
        result=probe.evaluate(case,{'content':[{'id':999,'sourceKey':'other','displayName':'Other'}],'totalElements':1})
        self.assertEqual(['SOURCE_ANCHOR_MISSING'],result['errors'])
        self.assertIsNone(result['rank'])

    def test_duplicate_id_and_wrong_source_or_language_are_failures(self):
        case=probe.cases(self.receipt())[0]
        product={'id':case['expectedId'],'sourceKey':'wrong','displayName':case['expectedLabel']}
        result=probe.evaluate(case,{'content':[product,product],'totalElements':2})
        self.assertEqual(['DUPLICATE_ID','SOURCE_OR_LOCALIZED_IDENTITY_DRIFT'],result['errors'])

    def test_anchor_presence_does_not_claim_top_one(self):
        case=probe.cases(self.receipt())[0]
        product={'id':case['expectedId'],'sourceKey':case['expectedSourceKey'],'displayName':case['expectedLabel']}
        result=probe.evaluate(case,{'content':[{'id':999},product],'totalElements':2})
        self.assertEqual([],result['errors'])
        self.assertEqual(2,result['rank'])

    def test_http_latency_is_measured_before_client_pacing(self):
        clock=[0.0]
        judgments=iter(probe.cases(self.receipt()))
        class FakeOpener:
            def open(self,request,timeout):
                case=next(judgments)
                clock[0]+=.025
                return io.StringIO(json.dumps({'content':[{'id':case['expectedId'],
                    'sourceKey':case['expectedSourceKey'],'displayName':case['expectedLabel']}],'totalElements':1}))
        fixture_root=Path(__file__).resolve().parents[1]/'outputs/r7-token-recall-test-fixtures'
        fixture_root.mkdir(parents=True,exist_ok=True)
        with tempfile.TemporaryDirectory(prefix='owned-',dir=fixture_root) as temp:
            folder=Path(temp)
            self.assertTrue(folder.resolve().is_relative_to(fixture_root.resolve()))
            receipt=folder/'receipt.json'
            receipt.write_text(json.dumps(self.receipt()),encoding='utf-8')
            output=folder/'report.json'
            with patch.dict(probe.os.environ,{'GRUN_CATALOG_ACCEPTANCE_TOKEN':'unit-test-only'}), \
                    patch.object(probe,'build_opener',return_value=FakeOpener()), \
                    patch.object(probe.time,'perf_counter',side_effect=lambda:clock[0]), \
                    patch.object(probe.time,'sleep',side_effect=lambda wait:clock.__setitem__(0,clock[0]+wait)), \
                    patch('builtins.print'):
                probe.run(SimpleNamespace(receipt=receipt,output=output,pacing_seconds=.65))
            report=json.loads(output.read_text(encoding='utf-8'))
            self.assertEqual('PASS',report['status'])
            self.assertEqual(60,report['completedCases'])
            self.assertEqual(25.0,report['p95HttpMilliseconds'])
            self.assertFalse(report['timingIncludesClientPacing'])


if __name__=='__main__':
    unittest.main()

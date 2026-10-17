"""Bounded GET-only live recall probe against reviewed R7 product identities.

Every request includes an approved category filter, preventing OFF import on a
zero result. Judgments come from committed source review, never search winners.
"""
import argparse
import hashlib
import json
import math
import os
import time
from datetime import datetime,timezone
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request,build_opener
from importlib.util import module_from_spec,spec_from_file_location

spec=spec_from_file_location('r7_recall_redirect',Path(__file__).with_name('verify-r6-live-search.py'))
helpers=module_from_spec(spec)
spec.loader.exec_module(helpers)


def cases(receipt):
    ids={'49340','181013','181138','181140','181155'}
    if len(receipt['rows'])!=5 or {r['id'] for r in receipt['rows']}!=ids:
        raise ValueError('Exact committed five-record source review required')
    result=[]
    for row in receipt['rows']:
        food=row['food']
        for language in ('EN','TR'):
            label=next(l['display_name'] for l in row['name_review']['localizations'] if l['active'] and l['language']==language)
            queries=[('EXACT',label),('REORDERED', ' '.join(reversed(label.split())))]
            if row['id']=='181013':
                queries.append(('CROSS_FIELDS','meat roasted chicken' if language=='EN' else 'eti tavuk fırınlanmış'))
            elif row['id']=='49340':
                queries.append(('CROSS_FIELDS','strawberry activia shot' if language=='EN' else 'muz activia shot'))
            for region in ('TR','UK_IE','EU'):
                if row['id']=='49340' and region!='TR':
                    continue
                for kind,query in queries:
                    params={'q':query,'categoryId':row['assignments'][0]['category_id'],'catalogType':food['catalog_type'],
                            'language':language,'region':region,'size':100,'page':0}
                    if food['preparation_state']:
                        params['preparationState']=food['preparation_state']
                    result.append({'id':row['id']+'-'+language+'-'+region+'-'+kind,'params':params,
                        'expectedId':int(row['id']),'expectedSourceKey':food['source_key'],
                        'expectedLabel':label,'judgment':'SOURCE_REVIEWED_ANCHOR_PRESENT_AT_100_NOT_TOP1_ORACLE'})
    return result


def evaluate(case,page):
    content=page.get('content')
    if not isinstance(content,list):
        raise ValueError('Complete search page required')
    ids=[p['id'] for p in content]
    anchor=next((p for p in content if p['id']==case['expectedId']),None)
    errors=[]
    if len(ids)!=len(set(ids)):
        errors.append('DUPLICATE_ID')
    if not anchor:
        errors.append('SOURCE_ANCHOR_MISSING')
    elif anchor.get('sourceKey')!=case['expectedSourceKey'] or anchor.get('displayName')!=case['expectedLabel']:
        errors.append('SOURCE_OR_LOCALIZED_IDENTITY_DRIFT')
    return {'id':case['id'],'query':case['params']['q'],'language':case['params']['language'],
        'region':case['params']['region'],'expectedId':case['expectedId'],'rank':ids.index(case['expectedId'])+1 if anchor else None,
        'total':page.get('totalElements'),'errors':errors,'top3':[{k:p.get(k) for k in
            ('id','productName','catalogType','preparationState')} for p in content[:3]]}


def run(args):
    token=os.environ.get('GRUN_CATALOG_ACCEPTANCE_TOKEN')
    if not token or args.output.exists():
        raise ValueError('Process-only token and new report path required')
    receipt=json.loads(args.receipt.read_text(encoding='utf-8'))
    judgments=cases(receipt)
    if len(judgments)>80:
        raise ValueError('Request budget exceeded')
    opener=build_opener(helpers.SameOriginRedirect())
    records=[]
    infra=[]
    try:
        for case in judgments:
            request=Request(helpers.ORIGIN+'/api/v1/products/search?'+urlencode(case['params']),
                headers={'Authorization':'Bearer '+token,'Accept':'application/json'})
            started=time.perf_counter()
            try:
                with opener.open(request,timeout=40) as response:
                    page=json.load(response)
            except HTTPError as error:
                raise ValueError('API HTTP '+str(error.code)+'; no credential or response body logged') from None
            # Capture network-inclusive latency BEFORE client pacing. No server-only
            # or true cold-cache/concurrency claim is made from this sequential run.
            elapsed=round((time.perf_counter()-started)*1000,3)
            result=evaluate(case,page)
            result['httpMilliseconds']=elapsed
            records.append(result)
            time.sleep(args.pacing_seconds)
    except Exception as error:
        infra.append(str(error))
    timings=sorted(r['httpMilliseconds'] for r in records)
    report={'version':'R7_SOURCE_TOKEN_RECALL_V1','status':'PASS' if len(records)==len(judgments) and not infra
            and not any(r['errors'] for r in records) else 'FAIL','time':datetime.now(timezone.utc).isoformat(),
            'expectedCases':len(judgments),'completedCases':len(records),'passedCases':sum(not r['errors'] for r in records),
            'infraErrors':infra,'cases':records,'p95HttpMilliseconds':timings[math.ceil(.95*len(timings))-1] if timings else None,
            'timingIncludesClientPacing':False,'latencyScope':'SEQUENTIAL_HTTP_NETWORK_INCLUSIVE_CACHE_STATE_UNCONTROLLED',
            'receiptSha256':hashlib.sha256(args.receipt.read_bytes()).hexdigest(),
            'producerSha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
            'judgments':judgments,'tokenWritten':False,'catalogMutationEndpointsCalled':False,
            'externalFallbackPreventedByCategoryFilter':True,'userFoodLogsWritten':0,'httpMethods':['GET'],
            'limits':'Five reviewed products across languages/markets; not full catalog Top1/MRR/Recall@3 certification'}
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:report[k] for k in ('status','expectedCases','completedCases','passedCases','infraErrors')}))
    if report['status']!='PASS':
        raise SystemExit(1)


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    for key in ('receipt','output'):
        parser.add_argument('--'+key,type=Path,required=True)
    parser.add_argument('--pacing-seconds',type=float,default=.65)
    args=parser.parse_args()
    if not math.isfinite(args.pacing_seconds) or not .2<=args.pacing_seconds<=5:
        parser.error('Pacing must be between .2 and 5 seconds')
    run(args)

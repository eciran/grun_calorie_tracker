"""GET-only exact five-product R7 acceptance; credentials stay in memory."""
import argparse
import json
import os
import time
from datetime import datetime, timezone
from pathlib import Path
from types import SimpleNamespace
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, build_opener
from importlib.util import module_from_spec, spec_from_file_location

spec = spec_from_file_location('r7_common_acceptance', Path(__file__).with_name('verify-common-live-api.py'))
common = module_from_spec(spec)
spec.loader.exec_module(common)


def run(args):
    token = os.environ.get('GRUN_CATALOG_ACCEPTANCE_TOKEN')
    if not token:
        raise ValueError('Process-memory token required')
    receipt = json.loads(args.receipt.read_text(encoding='utf-8'))
    if {r['id'] for r in receipt['rows']} != {'49340', '181013', '181138', '181140', '181155'} or len(receipt['rows']) != 5:
        raise ValueError('Exact five-product receipt required')
    args.output.mkdir(parents=True, exist_ok=False)
    common.run(SimpleNamespace(receipt=args.receipt, output=args.output/'detail-serving-core-search.json'))
    opener = build_opener(common.helpers.SameOriginRedirect())
    checks, errors, observations = [], [], []
    def check(value, label):
        (checks if value else errors).append(label)
    def get(path, **params):
        request = Request(common.ORIGIN+path+('?' + urlencode(params) if params else ''),
                          headers={'Authorization':'Bearer '+token, 'Accept':'application/json'})
        try:
            with opener.open(request, timeout=40) as response:
                result = json.load(response)
        except HTTPError as e:
            raise ValueError('API HTTP '+str(e.code)+' at '+path) from None
        time.sleep(.65)
        return result
    try:
        for row in receipt['rows']:
            identifier, food = row['id'], row['food']
            category = row['assignments'][0]['category_id']
            for language in ('EN','TR'):
                product = get('/api/v1/products/'+identifier, language=language)
                for field, column in [('catalogType','catalog_type'),('sourceDataType','source_data_type'),
                    ('foodConceptKey','food_concept_key'),('compositionVariantKey','composition_variant_key'),
                    ('canonicalFoodKey','canonical_food_key'),('barcode','barcode'),('brand','brand')]:
                    check(product.get(field)==food.get(column),field+'_'+identifier+'_'+language)
                label = next(l['display_name'] for l in row['name_review']['localizations'] if l['active'] and l['language']==language)
                # Source-qualified forms narrow the search; the category is also
                # checked via the public API, not inferred from a detail response.
                region = 'TR' if identifier=='49340' or language=='TR' else 'UK_IE'
                page = get('/api/v1/products/search',q=label,categoryId=category,
                           catalogType=food['catalog_type'],language=language,region=region,size=100)
                found = next((p for p in page.get('content',[]) if p['id']==int(identifier)),None)
                check(found is not None,'QUALIFIED_CATEGORY_SEARCH_'+identifier+'_'+language)
                if found:
                    check(found['displayName']==label,'LOCALIZED_SEARCH_'+identifier+'_'+language)
                observations.append({'id':int(identifier),'language':language,'categoryId':category,'label':label,
                                     'found':found is not None,'total':page.get('totalElements')})
        for language,query in [('EN','chicken breast'),('TR','tavuk göğsü')]:
            page = get('/api/v1/products/search',q=query,language=language,region='TR' if language=='TR' else 'UK_IE',
                       catalogType='GENERIC_INGREDIENT',size=100)
            check(all(p['id']!=181013 for p in page.get('content',[])),'LIGHT_MEAT_NOT_BREAST_'+language)
        for language,query in [('EN','milk'),('TR','süt')]:
            pages=[get('/api/v1/products/search',q=query,language=language,region='TR' if language=='TR' else 'UK_IE',
                       catalogType='GENERIC_INGREDIENT',page=p,size=10) for p in (0,1,0)]
            ids=[[p['id'] for p in page.get('content',[])] for page in pages]
            check(ids[0]==ids[2],'STABLE_REPEAT_PAGE_'+language)
            check(not set(ids[0])&set(ids[1]),'NO_CROSS_PAGE_DUPLICATE_ID_'+language)
    except Exception as e:
        errors.append(str(e))
    report={'status':'PASS' if not errors else 'FAIL','checksPassed':len(checks),'errors':errors,
            'time':datetime.now(timezone.utc).isoformat(),'observations':observations,'httpMethods':['GET'],
            'tokenWritten':False,'userFoodLogsWritten':0,'scope':'EXACT_FIVE_NOT_WHOLE_CATALOG_CERTIFICATION'}
    (args.output/'identity-category-acceptance.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:report[k] for k in ('status','checksPassed','errors')},ensure_ascii=False))
    if errors:
        raise SystemExit(1)


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    for key in ('receipt','output'):
        parser.add_argument('--'+key,type=Path,required=True)
    run(parser.parse_args())

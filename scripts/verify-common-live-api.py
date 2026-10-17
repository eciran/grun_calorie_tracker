"""GET-only EN/TR detail, serving and search acceptance; token never persisted."""
import argparse
import json
import os
import time
from datetime import datetime,timezone
from decimal import Decimal,ROUND_HALF_UP
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request,build_opener
from importlib.util import module_from_spec,spec_from_file_location

spec=spec_from_file_location('common_api_helpers',Path(__file__).with_name('verify-r6-live-search.py'))
helpers=module_from_spec(spec)
spec.loader.exec_module(helpers)
ORIGIN=helpers.ORIGIN


def normalize(value,field):
    if value is None: return None
    scale=1 if field in {'calories','protein','carbs','fat','fiber','sugar','saturatedFat','transFat','sugarAlcohol'} else 3
    return float(Decimal(str(value)).quantize(Decimal(10)**-scale,rounding=ROUND_HALF_UP))


def run(args):
    token=os.environ.get('GRUN_CATALOG_ACCEPTANCE_TOKEN')
    if not token: raise ValueError('Process-memory token required')
    receipt=json.loads(args.receipt.read_text(encoding='utf-8'))
    rows={r['id']:r for r in receipt['rows']}
    opener=build_opener(helpers.SameOriginRedirect())
    checks,errors,samples,searches=[],[],[],[]
    calls=0
    def check(condition,label): (checks if condition else errors).append(label)
    def get(path,**parameters):
        nonlocal calls
        if calls>=180: raise ValueError('Acceptance request budget exhausted')
        calls+=1
        request=Request(ORIGIN+path+('?' + urlencode(parameters) if parameters else ''),headers={'Authorization':'Bearer '+token,'Accept':'application/json'})
        started=time.monotonic()
        try:
            with opener.open(request,timeout=40) as response: result=json.load(response)
        except HTTPError as error:
            if error.code==404: return None,404,round((time.monotonic()-started)*1000)
            raise ValueError('API HTTP '+str(error.code)+' at '+path) from None
        time.sleep(.65)
        return result,200,round((time.monotonic()-started)*1000)
    try:
        for identifier,row in rows.items():
            food=row['food']
            if food['publication_status']=='INTERNAL_REVIEW':
                _,status,_=get('/api/v1/products/'+identifier,language='EN')
                check(status==404,'HIDDEN_SOURCE_REFERENCE_'+identifier)
                context,status,_=get('/api/v1/admin/products/'+identifier+'/quality-workbench')
                check(status==200 and context['product']['id']==int(identifier) and context['product']['brand']=='Lifeway'
                      and context['product']['catalogType']=='BRANDED_PRODUCT','ADMIN_LIFEWAY_REFERENCE')
                continue
            for language in ('EN','TR'):
                product,status,_=get('/api/v1/products/'+identifier,language=language)
                check(status==200 and product.get('id')==int(identifier),'DETAIL_'+identifier+'_'+language)
                if product is None: continue
                locale=next(l for l in row['name_review']['localizations'] if l['active'] and l['language']==language)
                check(product['productName']==locale['short_display_name'] and product['displayName']==locale['display_name'],
                      'DISPLAY_'+identifier+'_'+language)
                check(product['canonicalName']==food['name'] and product['sourceKey']==food['source_key'],
                      'SOURCE_IDENTITY_'+identifier+'_'+language)
                check(product['preparationState']==food['preparation_state'] and product['nutritionReferenceUnit']==food['nutrition_reference_unit'],
                      'STATE_BASIS_'+identifier+'_'+language)
                for field,column in helpers.NUTRIENTS.items():
                    check(product.get(field)==normalize(food.get(column),field),'NUTRIENT_'+identifier+'_'+language+'_'+field)
                options,status,_=get('/api/v1/products/'+identifier+'/serving-options',language=language)
                expected={o['id']:o for o in row['name_review']['options'] if o['user_visible'] and o['quality_status']=='VERIFIED'}
                check(status==200 and {o['id'] for o in options}==set(expected),'SERVING_IDS_'+identifier+'_'+language)
                check({o['id'] for o in product['servingOptions']}==set(expected),'DETAIL_SERVING_IDS_'+identifier+'_'+language)
                for option in options:
                    old=expected[option['id']]
                    label=next((l['label'] for l in row['name_review']['option_localizations'] if l['active'] and l['language']==language and l['serving_option_id']==option['id']),old['label'])
                    check(option['label']==label and option['gramWeight']==old['gram_weight'] and option['mlVolume']==old['ml_volume']
                          and option['unitType']==old['unit_type'] and option['quantity']==old['quantity'] and option['defaultOption']==old['is_default'],
                          'SERVING_VALUES_'+str(option['id'])+'_'+language)
                    if old['ml_volume'] is None:
                        check('MILLILITER' not in option['allowedInputUnits'],'NO_INVENTED_ML_'+str(option['id'])+'_'+language)
                if identifier in {'49682','180987','180988','180991','180995','180998','181150','181467'}:
                    samples.append({'id':int(identifier),'language':language,'name':product['productName'],
                                    'preparation':product['preparationState'],'options':[{k:o[k] for k in ('id','label','gramWeight','mlVolume','unitType')} for o in options]})
        for language,region,queries in [('EN','UK_IE',['banana','broccoli','milk','chicken breast','rice']),
                                        ('TR','TR',['muz','brokoli','süt','tavuk göğsü','pirinç'])]:
            for query in queries:
                page,status,elapsed=get('/api/v1/products/search',q=query,language=language,region=region,page=0,size=10)
                items=page.get('content') or []
                check(status==200 and bool(items),'SEARCH_NONEMPTY_'+language+'_'+query)
                check(len({p['id'] for p in items})==len(items),'SEARCH_NO_DUPLICATE_ID_'+language+'_'+query)
                check(all(p['id']!=181453 for p in items),'SEARCH_HIDDEN_LIFEWAY_'+language+'_'+query)
                if query in {'rice','pirinç'} and items:
                    name=items[0]['productName'].lower()
                    check(not any(word in name for word in ('cream','pudding','krem','puding')),'PLAIN_RICE_NOT_DERIVED_'+language)
                searches.append({'q':query,'language':language,'region':region,'ms':elapsed,'total':page.get('totalElements'),
                                 'top5':[{k:p.get(k) for k in ('id','productName','catalogType','preparationState')} for p in items[:5]]})
    except Exception as error:
        errors.append(str(error))
    report={'status':'PASS' if not errors else 'FAIL','time':datetime.now(timezone.utc).isoformat(),'requests':calls,
            'checksPassed':len(checks),'errors':errors,'searches':searches,'samples':samples,'tokenWritten':False,
            'httpMethods':['GET'],'receiptSha256':helpers.sha(args.receipt),'producerSha256':helpers.sha(Path(__file__)),
            'ranking':'CURRENT_TOP5_OBSERVED_NOT_A_FULL_RELEVANCE_CERTIFICATE','userFoodLogsWritten':0}
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:report[k] for k in ('status','requests','checksPassed','errors')},ensure_ascii=False))
    if errors: raise SystemExit(1)


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    for key in ('receipt','output'): parser.add_argument('--'+key,type=Path,required=True)
    run(parser.parse_args())

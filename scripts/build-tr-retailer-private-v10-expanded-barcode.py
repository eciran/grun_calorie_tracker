import argparse,hashlib,importlib.util,json,sys
from collections import defaultdict
from pathlib import Path

def main():
    p=argparse.ArgumentParser(); p.add_argument('--v9-rich',type=Path,required=True); p.add_argument('--matches',type=Path,required=True); p.add_argument('--catalog',type=Path,required=True); p.add_argument('--excluded',type=Path,required=True); p.add_argument('--output-dir',type=Path,required=True); a=p.parse_args()
    catdoc=json.loads(a.catalog.read_text(encoding='utf-8-sig')); catalog={r['catalogId']:r for r in catdoc.get('products',catdoc)}
    exdoc=json.loads(a.excluded.read_text(encoding='utf-8-sig')); excluded={r['catalogId'] for r in exdoc.get('products',exdoc)}
    rows=json.loads(a.matches.read_text(encoding='utf-8-sig')); groups=defaultdict(list)
    for r in rows:
        if r.get('matchStatus')=='EXACT_SINGLE_GTIN': groups[r['candidateGtins'][0]].append(r)
    items=[]; review=[]
    for gtin,rs in groups.items():
        if len(rs)!=1: review.append({'gtin':gtin,'reason':'MULTIPLE_CATALOG_TARGETS','count':len(rs)}); continue
        r=rs[0]; c=catalog.get(r['catalogId'])
        if not c: review.append({'gtin':gtin,'catalogId':r['catalogId'],'reason':'CATALOG_ROW_MISSING'}); continue
        if r['catalogId'] in excluded: review.append({'gtin':gtin,'catalogId':r['catalogId'],'reason':'MANUAL_OR_POLICY_EXCLUDED'}); continue
        if c.get('isMultipack') or not c.get('name') or not c.get('brand'): review.append({'gtin':gtin,'catalogId':r['catalogId'],'reason':'MULTIPACK_OR_IDENTITY_POLICY'}); continue
        n=c.get('nutrition') or {}; nutrition={'calories':n.get('energyKcal'),'protein':n.get('protein'),'fat':n.get('fat'),'carbs':n.get('carbohydrate'),'fiber':n.get('fiber') or n.get('Lif (g)'),'sugar':n.get('glucose'),'salt':n.get('salt')}
        ev=r.get('evidence') or []; excerpt=json.dumps({'catalogId':r['catalogId'],'gtin':gtin,'matchMethod':r.get('matchMethod'),'evidence':ev},ensure_ascii=False,sort_keys=True,separators=(',',':'))
        items.append({'catalogId':r['catalogId'],'gtin':gtin,'name':c['name'],'brand':c['brand'],'package':r.get('packageKey'),'matchMethod':r.get('matchMethod'),'nutritionBasis':c.get('nutritionBasis'),'nutrition':nutrition,'sources':[{'url':x.get('sourceUrl'),'title':x.get('sourceName'),'sourceFile':x.get('sourceFile'),'evidenceExcerpt':excerpt,'contentChecksumSha256':hashlib.sha256(excerpt.encode()).hexdigest().upper()} for x in ev]})
    a.output_dir.mkdir(parents=True,exist_ok=True); combined=a.output_dir/'combined-candidates.json'; combined.write_text(json.dumps({'status':'PASS','classification':'PRIVATE_TEST_ONLY_EXPANDED_EXACT_BARCODE','candidates':items},ensure_ascii=False,indent=2)+'\n',encoding='utf-8'); (a.output_dir/'prefilter-review.json').write_text(json.dumps(review,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    path=Path(__file__).with_name('build-tr-retailer-private-v5-web.py'); spec=importlib.util.spec_from_file_location('builder',path); mod=importlib.util.module_from_spec(spec); spec.loader.exec_module(mod); old=sys.argv
    try: sys.argv=[str(path),'--v4-rich',str(a.v9_rich),'--candidates',str(combined),'--output-dir',str(a.output_dir)]; rc=mod.main()
    finally: sys.argv=old
    (a.output_dir/'tr-retailer-test-rich-v5-import.csv').replace(a.output_dir/'tr-retailer-test-rich-v10-import.csv'); return rc
if __name__=='__main__': raise SystemExit(main())

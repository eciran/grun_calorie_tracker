#!/usr/bin/env python3
"""Build PRIVATE_TEST_ONLY v7 from v6, ETI candidates and exact-GTIN bulk evidence."""
import argparse, hashlib, importlib.util, json, sys
from pathlib import Path

def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--v6-rich',type=Path,required=True); ap.add_argument('--eti-candidates',type=Path,required=True); ap.add_argument('--bulk-candidates',type=Path,required=True); ap.add_argument('--output-dir',type=Path,required=True); a=ap.parse_args()
    eti=json.loads(a.eti_candidates.read_text(encoding='utf-8-sig'))
    bulk=json.loads(a.bulk_candidates.read_text(encoding='utf-8-sig'))
    candidates=list(eti.get('candidates',[])); seen={str(x.get('gtin')) for x in candidates}
    for r in bulk:
        if str(r.get('barcode')) in seen: continue
        seen.add(str(r.get('barcode')))
        excerpt=str(r.get('evidenceExcerpt') or '')
        candidates.append({
            'catalogId':f"GTIN:{r['barcode']}", 'gtin':str(r['barcode']),
            'name':r.get('catalogName') or r.get('name'), 'brand':r.get('catalogBrand') or r.get('brand'),
            'package':r.get('packageKey'), 'matchMethod':'EXACT_GTIN_RETAILER_PAGE_PER_100_NUTRITION',
            'nutritionBasis':'100 g/ml', 'nutrition':r.get('nutrition'),
            'sources':[{'url':r.get('sourceUrl'),'title':r.get('title'),'evidenceExcerpt':excerpt,
                        'contentChecksumSha256':hashlib.sha256(excerpt.encode('utf-8')).hexdigest().upper(),
                        'rawContentSha256':r.get('rawSha256'),'fetchedAt':r.get('fetchedAt')}]
        })
    a.output_dir.mkdir(parents=True,exist_ok=True)
    combined=a.output_dir/'combined-candidates.json'
    combined.write_text(json.dumps({'status':'PASS','classification':'PRIVATE_TEST_ONLY_BULK_CANDIDATES','candidates':candidates},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    path=Path(__file__).with_name('build-tr-retailer-private-v5-web.py'); spec=importlib.util.spec_from_file_location('builder',path); mod=importlib.util.module_from_spec(spec); spec.loader.exec_module(mod)
    old=sys.argv
    try:
        sys.argv=[str(path),'--v4-rich',str(a.v6_rich),'--candidates',str(combined),'--output-dir',str(a.output_dir)]
        rc=mod.main()
    finally: sys.argv=old
    (a.output_dir/'tr-retailer-test-rich-v5-import.csv').replace(a.output_dir/'tr-retailer-test-rich-v7-import.csv')
    return rc
if __name__=='__main__': raise SystemExit(main())

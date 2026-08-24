#!/usr/bin/env python3
"""Build PRIVATE_TEST_ONLY v9 from v8 and both exact Cepte Sok candidate slices."""
import argparse, hashlib, importlib.util, json, sys
from pathlib import Path

def main():
    p=argparse.ArgumentParser(); p.add_argument('--v8-rich',type=Path,required=True); p.add_argument('--candidate-files',type=Path,nargs='+',required=True); p.add_argument('--output-dir',type=Path,required=True); a=p.parse_args()
    items=[]; seen=set()
    for path in a.candidate_files:
        for r in json.loads(path.read_text(encoding='utf-8-sig')):
            gtin=str(r['catalogId']).split(':')[-1]
            if gtin in seen: continue
            seen.add(gtin); excerpt=json.dumps({'title':r.get('title'),'nutrition':r.get('nutrition'),'basis':r.get('nutritionBasis'),'rawSha256':r.get('rawSha256')},ensure_ascii=False,sort_keys=True,separators=(',',':'))
            items.append({'catalogId':r['catalogId'],'gtin':gtin,'name':r.get('name'),'brand':r.get('brand'),'package':r.get('urlSlug'),'matchMethod':r.get('matchMethod'),'nutritionBasis':r.get('nutritionBasis'),'nutrition':r.get('nutrition'),'sources':[{'url':r.get('sourceUrl'),'title':r.get('title'),'evidenceExcerpt':excerpt,'contentChecksumSha256':hashlib.sha256(excerpt.encode('utf8')).hexdigest().upper(),'rawContentSha256':r.get('rawSha256'),'fetchedAt':r.get('fetchedAt')} ]})
    a.output_dir.mkdir(parents=True,exist_ok=True); combined=a.output_dir/'combined-candidates.json'; combined.write_text(json.dumps({'status':'PASS','classification':'PRIVATE_TEST_ONLY_CEPTESOK','candidates':items},ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    path=Path(__file__).with_name('build-tr-retailer-private-v5-web.py'); spec=importlib.util.spec_from_file_location('builder',path); mod=importlib.util.module_from_spec(spec); spec.loader.exec_module(mod); old=sys.argv
    try: sys.argv=[str(path),'--v4-rich',str(a.v8_rich),'--candidates',str(combined),'--output-dir',str(a.output_dir)]; rc=mod.main()
    finally: sys.argv=old
    (a.output_dir/'tr-retailer-test-rich-v5-import.csv').replace(a.output_dir/'tr-retailer-test-rich-v9-import.csv'); return rc
if __name__=='__main__': raise SystemExit(main())

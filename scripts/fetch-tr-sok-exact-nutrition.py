#!/usr/bin/env python3
"""Bounded Cepte Sok exact-match page fetch and structured per-100 nutrition parser."""
import argparse, concurrent.futures, datetime, hashlib, html, json, re, time
from pathlib import Path
from urllib.request import Request,urlopen

CODES={'calories':'energy-kcal','fat':'fat-g','carbs':'carbohydrate-g','protein':'protein-g','sugar':'sugars-g','fiber':'fiber-g'}
def fetch(row,timeout,retries):
    error=None
    for n in range(retries+1):
        try:
            req=Request(row['sourceUrl'],headers={'User-Agent':'GRUNCatalogResearch/1.0 (+bounded public-page validation)'})
            with urlopen(req,timeout=timeout) as res: raw=res.read(); status=res.status
            text=html.unescape(raw.decode('utf8','replace')).replace(r'\"','"').replace(r'\u003c','<').replace(r'\u003e','>')
            values={}
            for key,code in CODES.items():
                m=re.search(r'"code":"'+re.escape(code)+r'".{0,300}?"value":"([0-9]+(?:[.,][0-9]+)?)"',text,re.I|re.S)
                if m: values[key]=float(m.group(1).replace(',','.'))
            core={k:values.get(k) for k in ('calories','protein','fat','carbs')}
            ok=all(v is not None for v in core.values()) and core['calories']<=1000 and all(0<=core[k]<=100 for k in ('protein','fat','carbs')) and sum(core[k] for k in ('protein','fat','carbs'))<=110 and 'nutritionFactsDescription":"100 g/ml' in text
            title=re.search(r'<title>(.*?)</title>',text,re.I|re.S)
            return {**row,'httpStatus':status,'bytes':len(raw),'rawSha256':hashlib.sha256(raw).hexdigest().upper(),'title':title.group(1).strip() if title else None,'nutrition':values if ok else None,'nutritionBasis':'100 g/ml' if ok else None,'error':None}
        except Exception as e: error=f'{type(e).__name__}: {e}'; time.sleep(min(2**n,4))
    return {**row,'httpStatus':None,'bytes':0,'rawSha256':None,'title':None,'nutrition':None,'nutritionBasis':None,'error':error}
def main():
    p=argparse.ArgumentParser(); p.add_argument('--matches',type=Path,required=True); p.add_argument('--output-dir',type=Path,required=True); p.add_argument('--workers',type=int,default=4); p.add_argument('--timeout',type=int,default=20); p.add_argument('--retries',type=int,default=1); a=p.parse_args()
    rows=json.loads(a.matches.read_text(encoding='utf-8-sig')); fetched=datetime.datetime.now(datetime.timezone.utc).isoformat()
    with concurrent.futures.ThreadPoolExecutor(max_workers=a.workers) as pool: results=list(pool.map(lambda r:fetch(r,a.timeout,a.retries),rows))
    candidates=[{**r,'fetchedAt':fetched} for r in results if r['nutrition']]
    a.output_dir.mkdir(parents=True,exist_ok=True)
    (a.output_dir/'pages.json').write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n',encoding='utf8'); (a.output_dir/'candidates.json').write_text(json.dumps(candidates,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    report={'status':'PASS','source':'CEPTESOK','scannedPages':len(results),'fetchedPages':sum(r['httpStatus']==200 for r in results),'fetchErrors':sum(bool(r['error']) for r in results),'exactJoins':len(results),'validGtins':len({r['catalogId'] for r in results}),'nutritionComplete':len(candidates),'parseRejects':sum(r['httpStatus']==200 and not r['nutrition'] for r in results),'netNewCandidates':len({r['catalogId'] for r in candidates})}
    (a.output_dir/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8'); print(json.dumps(report))
if __name__=='__main__': main()

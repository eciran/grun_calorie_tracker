"""Bounded public-source verification; local evidence only, no catalog writes."""
import concurrent.futures
import csv
import datetime
import hashlib
from html.parser import HTMLParser
import importlib.util
import json
from pathlib import Path
import time
import urllib.request
from urllib.parse import urlsplit
from collections import Counter

ROOT=Path('outputs/package-family-audit-20261029')
spec=importlib.util.spec_from_file_location('review',Path(__file__).with_name('review-package-family-candidates.py'))
review=importlib.util.module_from_spec(spec); spec.loader.exec_module(review)

class Page(HTMLParser):
    def __init__(self):
        super().__init__(); self.capture=False; self.parts=[]; self.blocks=[]; self.headings=[]; self.h1=False
    def handle_starttag(self,tag,attrs):
        attrs=dict(attrs)
        if tag=='script' and attrs.get('type')=='application/ld+json': self.capture=True; self.parts=[]
        if tag=='h1': self.h1=True
    def handle_endtag(self,tag):
        if tag=='script' and self.capture: self.blocks.append(''.join(self.parts)); self.capture=False
        if tag=='h1': self.h1=False
    def handle_data(self,data):
        if self.capture: self.parts.append(data)
        if self.h1: self.headings.append(data)

def products(value):
    found=[]
    if isinstance(value,list):
        for child in value: found+=products(child)
    if isinstance(value,dict):
        types=value.get('@type',[]); types=[types] if isinstance(types,str) else types
        if 'Product' in types: found.append(value)
        for key,child in value.items():
            if isinstance(child,(list,dict)): found+=products(child)
    return found

class SameHostRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,req,fp,code,msg,headers,newurl):
        if urlsplit(newurl).hostname!=urlsplit(req.full_url).hostname or urlsplit(newurl).scheme!='https':
            raise ValueError('Cross-host or non-HTTPS redirect not followed')
        return super().redirect_request(req,fp,code,msg,headers,newurl)

def fetch(url):
    target=ROOT/'live-source-evidence'/f'{hashlib.sha256(url.encode()).hexdigest()}.json'
    if target.exists(): return json.loads(target.read_text(encoding='utf-8'))
    result={'url':url,'retrievedAt':datetime.datetime.now(datetime.timezone.utc).isoformat()}
    try:
        time.sleep(.8)
        req=urllib.request.Request(url,headers={'User-Agent':'GRun-Catalog-Research/0.1','Accept':'text/html'})
        with urllib.request.build_opener(SameHostRedirect).open(req,timeout=12) as response:
            data=response.read(2*1024*1024+1)
            if len(data)>2*1024*1024: raise ValueError('Response exceeds 2 MiB limit')
            encoding=response.headers.get_content_charset() or 'utf-8'
            html=data.decode(encoding,errors='replace')
            result.update(httpStatus=response.status,sha256=hashlib.sha256(data).hexdigest())
        page=Page(); page.feed(html)
        entries=[]
        for block in page.blocks:
            try: entries+=products(json.loads(block))
            except ValueError: pass
        result.update(status='FETCHED',headings=page.headings,products=[{k:p.get(k) for k in ('name','gtin','gtin8','gtin12','gtin13','gtin14','sku','brand','weight','size')} for p in entries])
    except Exception as error: result.update(status='UNAVAILABLE',error=str(error))
    target.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    return result

def main():
    groups=[g for g in json.loads((ROOT/'source-review.json').read_text(encoding='utf-8')) if g['decision'] in ('SOURCE_PACKAGE_CONFLICT','ARCHIVED_PACKAGE_CORROBORATED_CURRENT_REVIEW_REQUIRED')]
    urls=sorted({s['url'] for g in groups for row in g['products'] for s in row['matchingArchivedSources']+row['conflictingArchivedSources'] if s.get('url','').startswith('https://')})
    (ROOT/'live-source-evidence').mkdir(exist_ok=True)
    results={}
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        for index,result in enumerate(pool.map(fetch,urls),1):
            results[result['url']]=result
            if index%20==0: print(f'Checked {index}/{len(urls)}',flush=True)
    decisions=[]
    for group in groups:
        rows=[]
        for row in group['products']:
            matches=[]; conflicts=[]
            for source in row['matchingArchivedSources']+row['conflictingArchivedSources']:
                current=results.get(source.get('url'),{})
                for product in current.get('products',[]):
                    codes={str(product.get(key) or '') for key in ('gtin','gtin8','gtin12','gtin13','gtin14')}
                    if row['barcode'] not in codes: continue
                    expected=review.package(row['name']); observed=review.package(product.get('name'))
                    if expected and expected==observed: matches.append({'url':source['url'],'name':product.get('name'),'barcode':row['barcode']})
                    elif observed: conflicts.append({'url':source['url'],'name':product.get('name'),'package':observed})
            status='LIVE_PACKAGE_CONFLICT' if conflicts else 'LIVE_GTIN_AND_PACKAGE_CORROBORATED' if matches else 'LIVE_STRUCTURED_EVIDENCE_INSUFFICIENT'
            rows.append({'id':row['id'],'name':row['name'],'status':status,'matches':matches,'conflicts':conflicts})
        decisions.append({'group_id':group['group_id'],'previousDecision':group['decision'],'safeToApply':False,'products':rows})
    summary={'groups':len(groups),'urls':len(urls),'fetchStatuses':dict(Counter(r['status'] for r in results.values())),'productStatuses':dict(Counter(r['status'] for g in decisions for r in g['products'])),'safeToApply':0,'note':'GTIN/package corroboration is not formula or full identity approval. No product changed.'}
    (ROOT/'live-review.json').write_text(json.dumps(decisions,ensure_ascii=False,indent=2),encoding='utf-8')
    (ROOT/'live-review-summary.json').write_text(json.dumps(summary,indent=2),encoding='utf-8')
    with (ROOT/'live-review.csv').open('w',encoding='utf-8-sig',newline='') as handle:
        writer=csv.DictWriter(handle,fieldnames=['group_id','previous_decision','group_package_supported','id','name','status','matched_urls','safe_to_apply']); writer.writeheader()
        for group in decisions:
            supported=all(p['status']=='LIVE_GTIN_AND_PACKAGE_CORROBORATED' for p in group['products'])
            for row in group['products']:
                writer.writerow({'group_id':group['group_id'],'previous_decision':group['previousDecision'],'group_package_supported':supported,'id':row['id'],'name':row['name'],'status':row['status'],'matched_urls':';'.join(m['url'] for m in row['matches']),'safe_to_apply':False})
    print(json.dumps(summary),flush=True)

if __name__=='__main__': main()

#!/usr/bin/env python3
"""Exact normalized-name/package intersection of Cepte Sok sitemap and TR nutrition queue."""
import argparse, hashlib, json, re, unicodedata
from pathlib import Path
from urllib.parse import urlparse
import xml.etree.ElementTree as ET

def clean(s):
    s=str(s or '')
    try:
        if any(x in s for x in ('Ã','Ä','Å')): s=s.encode('latin1').decode('utf8')
    except (UnicodeError,UnicodeEncodeError): pass
    s=s.replace('ı','i').replace('İ','I').replace('ş','s').replace('Ş','S').replace('ğ','g').replace('Ğ','G').replace('ç','c').replace('Ç','C').replace('ö','o').replace('Ö','O').replace('ü','u').replace('Ü','U')
    s=unicodedata.normalize('NFKD',s).encode('ascii','ignore').decode().lower()
    return re.sub(r'[^a-z0-9]+','-',s).strip('-')

def main():
    p=argparse.ArgumentParser(); p.add_argument('--sitemap',type=Path,required=True); p.add_argument('--queue',type=Path,required=True); p.add_argument('--output-dir',type=Path,required=True); p.add_argument('--source',default='CEPTESOK'); a=p.parse_args()
    queue=json.loads(a.queue.read_text(encoding='utf-8-sig'))
    by_slug={}
    for r in queue:
        if r.get('status')=='NO_EXACT_GTIN_NUTRITION': by_slug.setdefault(clean(r.get('name')),[]).append(r)
    root=ET.parse(a.sitemap).getroot(); urls=[x.text.strip() for x in root.findall('.//{*}loc') if x.text]
    matches=[]
    for url in urls:
        slug=urlparse(url).path.rstrip('/').split('/')[-1]; slug=re.sub(r'-p-\d+$','',slug)
        rows=by_slug.get(clean(slug),[])
        if len(rows)==1: matches.append({'sourceUrl':url,'urlSlug':slug,**rows[0],'matchMethod':'EXACT_NORMALIZED_FULL_NAME_PACKAGE_URL_SLUG'})
    a.output_dir.mkdir(parents=True,exist_ok=True)
    (a.output_dir/'exact-matches.json').write_text(json.dumps(matches,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    report={'status':'PASS','source':a.source,'sitemapUrls':len(urls),'queueRows':len(queue),'exactSingleMatches':len(matches),'uniqueGtins':len({r['catalogId'].split(':',1)[1] for r in matches if str(r.get('catalogId','')).startswith('GTIN:') and r['catalogId'].split(':',1)[1].isdigit()}),'sitemapSha256':hashlib.sha256(a.sitemap.read_bytes()).hexdigest().upper()}
    (a.output_dir/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8'); print(json.dumps(report))
if __name__=='__main__': main()

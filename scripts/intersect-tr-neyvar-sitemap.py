import argparse,json,re,unicodedata,xml.etree.ElementTree as ET
from pathlib import Path
from urllib.parse import urlparse

def norm(s):
    s=unicodedata.normalize('NFKD',str(s)).encode('ascii','ignore').decode().lower()
    return re.sub(r'[^a-z0-9]+',' ',s).strip()
def gtin_ok(s):
    return s.isdigit() and len(s) in (8,12,13,14) and sum((3 if i%2==0 else 1)*int(x) for i,x in enumerate(s[-2::-1]))%10 == (10-int(s[-1]))%10
def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--queue',required=True); ap.add_argument('--sitemap',required=True); ap.add_argument('--out',required=True); a=ap.parse_args()
    queue=json.loads(Path(a.queue).read_text(encoding='utf-8-sig'))
    urls=[e.text.strip() for e in ET.parse(a.sitemap).getroot().iter() if e.tag.endswith('loc') and e.text and '/urun/' in e.text]
    byslug={}
    for u in urls: byslug.setdefault(norm(urlparse(u).path.rsplit('/',1)[-1]),[]).append(u)
    exact=[]; collisions=[]; rejects=0
    for r in queue:
        if r.get('status')!='NO_EXACT_GTIN_NUTRITION': continue
        gtin=r.get('catalogId','').split(':')[-1]; key=norm(r.get('name',''))
        if not gtin_ok(gtin): rejects+=1; continue
        hits=byslug.get(key,[])
        if len(hits)==1: exact.append({'sourceFamily':'neyvar','sourceProductId':urlparse(hits[0]).path.rsplit('/',1)[-1],'name':r.get('name'),'brand':r.get('brand'),'barcode':gtin,'sourceUrl':hits[0],'sourceFile':a.sitemap,'catalogName':r.get('name'),'catalogBrand':r.get('brand'),'matchMethod':'EXACT_NORMALIZED_FULL_NAME_PACKAGE_SLUG'})
        elif len(hits)>1: collisions.append({'gtin':gtin,'name':r.get('name'),'hits':hits})
    out=Path(a.out); out.mkdir(parents=True,exist_ok=True)
    (out/'source-index.json').write_text(json.dumps({'products':exact},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (out/'review.json').write_text(json.dumps(collisions,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    report={'sitemapUrls':len(urls),'queueRows':len(queue),'exactJoins':len(exact),'validGtins':len({x['barcode'] for x in exact}),'collisions':len(collisions),'invalidGtinRejects':rejects}
    (out/'report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8'); print(json.dumps(report))
if __name__=='__main__': main()

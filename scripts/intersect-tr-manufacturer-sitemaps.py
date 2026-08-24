import argparse,json,re,unicodedata,xml.etree.ElementTree as ET
from pathlib import Path
from urllib.parse import urlparse

def norm(s):
    s=unicodedata.normalize('NFKD',str(s)).encode('ascii','ignore').decode().lower()
    return re.sub(r'[^a-z0-9]+',' ',s).strip()
def gtin_ok(s):
    return s.isdigit() and len(s) in (8,12,13,14) and sum((3 if i%2==0 else 1)*int(x) for i,x in enumerate(s[-2::-1]))%10 == (10-int(s[-1]))%10
def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--queue',required=True); ap.add_argument('--output',required=True); ap.add_argument('--sources',nargs='+',required=True); a=ap.parse_args()
    rows=json.loads(Path(a.queue).read_text(encoding='utf-8-sig')); urls=[]
    for spec in a.sources:
        fam,file=spec.split('=',1); root=ET.parse(file).getroot()
        for e in root.iter():
            if e.tag.endswith('loc') and e.text: urls.append((fam,e.text.strip()))
    targets=[]; collisions=[]
    for r in rows:
        if r.get('status')!='NO_EXACT_GTIN_NUTRITION': continue
        brand=norm(r.get('brand','')); name=norm(r.get('name','')); gtin=r.get('catalogId','').split(':')[-1]
        fam = 'torku' if 'torku' in brand else ('sutas' if 'sutas' in brand else None)
        if not fam or not gtin_ok(gtin): continue
        hits=[]
        for sf,u in urls:
            if sf!=fam: continue
            slug=norm(urlparse(u).path.rsplit('/',1)[-1])
            if slug and (slug==name or (len(name)>12 and len(slug)>12 and (slug.endswith(name) or name.endswith(slug)))): hits.append(u)
        if len(hits)==1: targets.append({'sourceFamily':fam,'sourceProductId':urlparse(hits[0]).path.rsplit('/',1)[-1],'name':r.get('name'),'brand':r.get('brand'),'barcode':gtin,'sourceUrl':hits[0],'sourceFile':'official-sitemap','catalogName':r.get('name'),'catalogBrand':r.get('brand'),'matchMethod':'EXACT_NORMALIZED_NAME_PACKAGE_SLUG'})
        elif len(hits)>1: collisions.append({'gtin':gtin,'hits':hits})
    out=Path(a.output); out.mkdir(parents=True,exist_ok=True)
    (out/'source-index.json').write_text(json.dumps({'products':targets},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    report={'sitemapUrls':len(urls),'queueRows':len(rows),'exactTargets':len(targets),'collisions':len(collisions),'validGtins':len({x['barcode'] for x in targets})}
    (out/'report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8'); (out/'review.json').write_text(json.dumps(collisions,indent=2)+'\n',encoding='utf-8'); print(json.dumps(report))
if __name__=='__main__': main()

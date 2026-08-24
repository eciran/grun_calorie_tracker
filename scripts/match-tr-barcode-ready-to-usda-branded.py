import argparse,csv,hashlib,json,zipfile
from pathlib import Path

NUTRIENTS={1008:'calories',1003:'protein',1004:'fat',1005:'carbs'}
def gtins(s):
    d=''.join(c for c in str(s) if c.isdigit()); out={d}
    if len(d)==12: out.add('0'+d)
    if len(d)==14 and d.startswith('0'): out.add(d[1:])
    return {x for x in out if len(x) in (8,12,13,14)}
def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--queue',required=True); ap.add_argument('--zip',required=True); ap.add_argument('--out',required=True); a=ap.parse_args()
    q=json.loads(Path(a.queue).read_text(encoding='utf-8-sig')); wanted={r['catalogId'].split(':')[-1]:r for r in q if r.get('status')=='NO_EXACT_GTIN_NUTRITION'}
    out=Path(a.out); out.mkdir(parents=True,exist_ok=True); source_sha=hashlib.sha256(Path(a.zip).read_bytes()).hexdigest().upper(); matches={}; z=zipfile.ZipFile(a.zip)
    bname=next(n for n in z.namelist() if n.endswith('/branded_food.csv'))
    with z.open(bname) as raw:
        rows=csv.DictReader((line.decode('utf-8-sig') for line in raw))
        scanned=0
        for r in rows:
            scanned+=1
            hit=next((x for x in gtins(r['gtin_upc']) if x in wanted),None)
            if hit: matches.setdefault(r['fdc_id'],{'gtin':hit,'catalog':wanted[hit],'brandOwner':r['brand_owner'],'brandName':r['brand_name'],'description':r['short_description'],'marketCountry':r['market_country'],'modifiedDate':r['modified_date'],'nutrients':{}})
    ids=set(matches); fname=next(n for n in z.namelist() if n.endswith('/food_nutrient.csv'))
    with z.open(fname) as raw:
        for r in csv.DictReader((line.decode('utf-8-sig') for line in raw)):
            if r['fdc_id'] in ids and int(r['nutrient_id']) in NUTRIENTS:
                try: matches[r['fdc_id']]['nutrients'][NUTRIENTS[int(r['nutrient_id'])]]=float(r['amount'])
                except ValueError: pass
    complete=[]; review=[]
    bygtin={}
    for fid,m in matches.items(): bygtin.setdefault(m['gtin'],[]).append((fid,m))
    for g,group in bygtin.items():
        good=[(fid,m) for fid,m in group if set(m['nutrients'])==set(NUTRIENTS.values()) and 0<=m['nutrients']['calories']<=1000 and all(0<=m['nutrients'][k]<=100 for k in ('protein','fat','carbs'))]
        if len(good)==1:
            fid,m=good[0]; complete.append({**m,'fdcId':fid,'sourceUrl':f'https://fdc.nal.usda.gov/fdc-app.html#/food-details/{fid}/nutrients','sourceFile':a.zip,'sourceFileSha256':source_sha,'matchMethod':'EXACT_GTIN','nutritionBasis':'PER_100_FDC'})
        else: review.append({'gtin':g,'exactFdcRows':len(group),'completeRows':len(good),'reason':'DUPLICATE_OR_INCOMPLETE'})
    report={'brandedRowsScanned':scanned,'queueGtins':len(wanted),'exactGtinGroups':len(bygtin),'exactFdcRows':len(matches),'nutritionCompleteUnique':len(complete),'collisionsOrIncomplete':len(review),'netNewCandidates':len(complete)}
    for n,o in [('candidates.json',complete),('review.json',review),('report.json',report)]: (out/n).write_text(json.dumps(o,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(report))
if __name__=='__main__': main()

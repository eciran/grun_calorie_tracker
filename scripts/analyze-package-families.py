"""Conservative candidate-only grouping; never mutates catalog records."""
import csv
import json
import re
import sys
import unicodedata
from collections import defaultdict, Counter
from pathlib import Path

PACK = re.compile(r'(?<!\w)(?:(\d+)\s*[xX]\s*)?(\d+(?:[.,]\d+)?)\s*(kg|mg|gr|g|ml|cl|lt|l)(?!\w)', re.I)

def fold(value):
    return ''.join(c for c in unicodedata.normalize('NFKD', value.casefold().replace('ı','i')) if not unicodedata.combining(c))

def identity(row):
    name = row['display_name'] or row['name']
    packs = [m.group(0) for m in PACK.finditer(name)]
    key = re.sub(r'[^a-z0-9]+',' ',fold(PACK.sub(' ',name))).strip()
    brand = row.get('brand_id') or fold(row.get('brand',''))
    return (row['catalog_type'], brand, key), packs

def analyze(root):
    with (root/'catalog.csv').open(encoding='utf-8-sig', newline='') as handle:
        rows=list(csv.DictReader(handle))
    groups=defaultdict(list)
    missing_brand=0
    for row in rows:
        key,packs=identity(row)
        if key[0]=='BRANDED_PRODUCT' and not key[1]:
            missing_brand+=1
            continue
        row['package_text']= ' | '.join(packs)
        if key[2]: groups[key].append(row)
    candidates=[]
    for key,items in groups.items():
        if len(items)<2: continue
        complete=all(all(r.get(f) not in ('',None) for f in ('calories','protein','carbs','fat','nutrition_reference_unit')) for r in items)
        profiles={tuple(r.get(f) for f in ('nutrition_reference_unit','calories','protein','carbs','fat','fiber','sugar')) for r in items}
        context={tuple(r.get(f) for f in ('market_region','preparation_state')) for r in items}
        packs={r['package_text'] for r in items if r['package_text']}
        barcodes={r['barcode'] for r in items if r['barcode']}
        if len(context)>1: decision='REVIEW_MARKET_OR_PREPARATION'
        elif not complete: decision='REVIEW_INCOMPLETE_NUTRITION'
        elif len(profiles)>1: decision='REVIEW_NUTRITION_DIFFERENCE'
        elif len(packs)>1: decision='PACKAGE_FAMILY_CANDIDATE'
        elif len(barcodes)==1 and all(r['barcode'] for r in items): decision='SAME_BARCODE_DUPLICATE_CANDIDATE'
        else: decision='REVIEW_IDENTITY_OR_PACKAGE_EVIDENCE'
        group_id=f'PF-{len(candidates)+1:05d}'
        candidates.append({'group_id':group_id,'decision':decision,'identity':key,'products':items})
    (root/'candidates.json').write_text(json.dumps(candidates,ensure_ascii=False,indent=2),encoding='utf-8')
    fields=['group_id','decision','id','name','display_name','brand','barcode','package_text','market_region','preparation_state','nutrition_reference_unit','calories','protein','carbs','fat','publication_status','source_key']
    with (root/'candidates.csv').open('w',encoding='utf-8-sig',newline='') as handle:
        writer=csv.DictWriter(handle,fieldnames=fields,extrasaction='ignore'); writer.writeheader()
        for group in candidates:
            for row in group['products']: writer.writerow({**row,'group_id':group['group_id'],'decision':group['decision']})
    summary={'catalog_rows':len(rows),'candidate_groups':len(candidates),'candidate_rows':sum(len(g['products']) for g in candidates),'groups_by_decision':dict(Counter(g['decision'] for g in candidates)),'missing_brand_excluded':missing_brand,'limitations':['Candidates only, no automatic merge approval.','Pack amount parsed from name is NOT verified package evidence.','Exact normalized identity only; fuzzy variants remain outside this report.','No g/ml or per-serving profile conversions assumed.']}
    for filename, decisions in [('package-variants.csv',{'PACKAGE_FAMILY_CANDIDATE'}),('review-required.csv',set(summary['groups_by_decision'])-{'PACKAGE_FAMILY_CANDIDATE'})]:
        with (root/filename).open('w',encoding='utf-8-sig',newline='') as handle:
            writer=csv.DictWriter(handle,fieldnames=fields,extrasaction='ignore'); writer.writeheader()
            for group in candidates:
                if group['decision'] in decisions:
                    for row in group['products']: writer.writerow({**row,'group_id':group['group_id'],'decision':group['decision']})
    duplicates=[]
    for identity_field in ('barcode','source_key'):
        index=defaultdict(list)
        for row in rows:
            value=(row.get(identity_field) or '').strip()
            if value: index[value].append(row)
        for value,items in index.items():
            if len(items)<2: continue
            signatures={tuple(r.get(f) for f in ('brand_id','market_region','preparation_state','nutrition_reference_unit','calories','protein','carbs','fat','fiber','sugar')) for r in items}
            complete=all(all(r.get(f) not in ('',None) for f in ('calories','protein','carbs','fat','nutrition_reference_unit')) for r in items)
            decision='DUPLICATE_IDENTITY_CANDIDATE' if len(signatures)==1 and complete else 'REVIEW_DUPLICATE_IDENTITY_CONFLICT'
            duplicates.append({'group_id':f'DUP-{len(duplicates)+1:05d}','decision':decision,'identity_field':identity_field,'identity_value':value,'products':items})
    (root/'duplicates.json').write_text(json.dumps(duplicates,ensure_ascii=False,indent=2),encoding='utf-8')
    with (root/'duplicates.csv').open('w',encoding='utf-8-sig',newline='') as handle:
        writer=csv.DictWriter(handle,fieldnames=fields+['identity_field','identity_value'],extrasaction='ignore'); writer.writeheader()
        for group in duplicates:
            for row in group['products']: writer.writerow({**row,**{k:v for k,v in group.items() if k!='products'}})
    summary['duplicate_identity_groups']=len(duplicates)
    summary['duplicate_groups_by_decision']=dict(Counter(g['decision'] for g in duplicates))
    summary['catalog_type_counts']=dict(Counter(r['catalog_type'] for r in rows))
    summary['publication_counts']=dict(Counter(r['publication_status'] for r in rows))
    summary['snapshot_proof']=json.loads((root/'snapshot-proof.json').read_text(encoding='utf-8'))
    (root/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(summary,ensure_ascii=True))

if __name__=='__main__': analyze(Path(sys.argv[1]))

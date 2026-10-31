"""Read-only source-evidence triage for package families. No DB writes."""
import csv
import hashlib
import importlib.util
import json
from collections import Counter, defaultdict
from decimal import Decimal
from pathlib import Path

ROOT=Path('outputs/package-family-audit-20261029')
spec=importlib.util.spec_from_file_location('families',Path(__file__).with_name('analyze-package-families.py'))
families=importlib.util.module_from_spec(spec); spec.loader.exec_module(families)

def package(name):
    result=[]
    for match in families.PACK.finditer(name or ''):
        count=Decimal(match[1] or '1'); value=Decimal(match[2].replace(',','.'))
        unit=match[3].lower()
        factors={'kg':('G',1000),'mg':('G',Decimal('.001')),'gr':('G',1),'g':('G',1),'ml':('ML',1),'cl':('ML',10),'lt':('ML',1000),'l':('ML',1000)}
        basis,factor=factors[unit]
        result.append((format(count.normalize(),'f'),format((value*factor).normalize(),'f'),basis))
    return result

def gtin_valid(code):
    if not code.isdigit() or len(code) not in (8,12,13,14): return False
    return (sum(int(c)*(3 if i%2==0 else 1) for i,c in enumerate(reversed(code[:-1])))+int(code[-1]))%10==0

def main():
    evidence=defaultdict(list); source_files=[]
    for path in sorted(Path('outputs/TR_Products/sources').glob('*/products.json')):
        payload=json.loads(path.read_text(encoding='utf-8-sig'))
        source_files.append({'path':str(path),'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
        for row in payload.get('products',[]):
            codes=set(row.get('barcodes') or [])|{row.get('barcode'),row.get('primaryBarcode')}
            for code in codes:
                if code: evidence[str(code)].append({**row,'evidenceFile':str(path)})
    groups=json.loads((ROOT/'candidates.json').read_text(encoding='utf-8'))
    output=[]
    for group in groups:
        if group['decision'] not in ('PACKAGE_FAMILY_CANDIDATE','REVIEW_IDENTITY_OR_PACKAGE_EVIDENCE'): continue
        reviews=[]
        for row in group['products']:
            expected=package(row['display_name'] or row['name'])
            hits=evidence.get(row['barcode'],[])
            matching=[r for r in hits if package(r.get('name'))==expected and expected and r.get('sourceUrl') and r.get('fetchedAt')]
            conflicting=[r for r in hits if package(r.get('name')) and package(r.get('name'))!=expected]
            reasons=[]
            if not gtin_valid(row['barcode']): reasons.append('MISSING_OR_INVALID_GTIN')
            if not expected: reasons.append('PACKAGE_AMOUNT_NOT_IN_NAME')
            if len(expected)>1: reasons.append('MULTIPLE_PACKAGE_AMOUNTS')
            if any(p[2]=='ML' for p in expected) and row['nutrition_reference_unit']!='PER_100ML': reasons.append('VOLUME_PACKAGE_WITH_NON_ML_REFERENCE')
            if row['nutrition_reference_unit']=='PER_SERVING': reasons.append('PER_SERVING_COMPARISON_REQUIRES_WEIGHT')
            if conflicting: reasons.append('SOURCE_PACKAGE_CONFLICT')
            if not matching: reasons.append('NO_MATCHING_ARCHIVED_PACKAGE_EVIDENCE')
            # Archived text supports quantity, not current nutrition or full identity.
            reasons.append('CURRENT_SOURCE_AND_FULL_IDENTITY_REVIEW_REQUIRED')
            reviews.append({'id':row['id'],'name':row['name'],'brand':row['brand'],'barcode':row['barcode'],'source_key':row['source_key'],'package':expected,'reference':row['nutrition_reference_unit'],'reasons':reasons,'matchingArchivedSources':[{'url':r['sourceUrl'],'fetchedAt':r['fetchedAt'],'name':r.get('name'),'file':r['evidenceFile']} for r in matching],'conflictingArchivedSources':[{'url':r.get('sourceUrl'),'name':r.get('name'),'fetchedAt':r.get('fetchedAt')} for r in conflicting]})
        normalized={tuple(tuple(p) for p in r['package']) for r in reviews if r['package']}
        if group['decision']=='PACKAGE_FAMILY_CANDIDATE':
            if len(normalized)<2: decision='SAME_QUANTITY_FORMAT_VARIANT'
            elif any('SOURCE_PACKAGE_CONFLICT' in r['reasons'] for r in reviews): decision='SOURCE_PACKAGE_CONFLICT'
            elif any('VOLUME_PACKAGE_WITH_NON_ML_REFERENCE' in r['reasons'] for r in reviews): decision='REVIEW_LIQUID_REFERENCE'
            elif all(r['matchingArchivedSources'] for r in reviews): decision='ARCHIVED_PACKAGE_CORROBORATED_CURRENT_REVIEW_REQUIRED'
            else: decision='PACKAGE_SOURCE_EVIDENCE_REQUIRED'
        else:
            codes={r['barcode'] for r in reviews if r['barcode']}
            if len(normalized)>1: decision='PACKAGE_VARIANT_WITH_MISSING_NAME_EVIDENCE'
            elif len(codes)>1: decision='DISTINCT_GTIN_IDENTITY_REVIEW'
            elif any(not r['barcode'] for r in reviews): decision='MISSING_GTIN_IDENTITY_REVIEW'
            else: decision='SOURCE_IDENTITY_REVIEW'
        output.append({'group_id':group['group_id'],'original_decision':group['decision'],'decision':decision,'safeToApply':False,'products':reviews})
    (ROOT/'source-review.json').write_text(json.dumps(output,ensure_ascii=False,indent=2),encoding='utf-8')
    for file,original in [('package-source-review.csv','PACKAGE_FAMILY_CANDIDATE'),('identity-triage.csv','REVIEW_IDENTITY_OR_PACKAGE_EVIDENCE')]:
        with (ROOT/file).open('w',encoding='utf-8-sig',newline='') as handle:
            writer=csv.DictWriter(handle,fieldnames=['group_id','decision','id','name','brand','barcode','source_key','package','reference','reasons','matching_source_count']); writer.writeheader()
            for group in output:
                if group['original_decision']!=original: continue
                for row in group['products']:
                    writer.writerow({**{k:row[k] for k in ('id','name','brand','barcode','source_key','reference')},'group_id':group['group_id'],'decision':group['decision'],'package':json.dumps(row['package']),'reasons':';'.join(row['reasons']),'matching_source_count':len(row['matchingArchivedSources'])})
    summary={'reviewedGroups':len(output),'packageGroups':dict(Counter(g['decision'] for g in output if g['original_decision']=='PACKAGE_FAMILY_CANDIDATE')),'identityGroups':dict(Counter(g['decision'] for g in output if g['original_decision']=='REVIEW_IDENTITY_OR_PACKAGE_EVIDENCE')),'rowsWithArchivedPackageSupport':sum(bool(r['matchingArchivedSources']) for g in output for r in g['products']),'safeToApply':0,'sources':source_files,'limitations':['Archived sources are not live validation.','Package text match does not prove product formula identity.','All candidates remain non-applicable until current source and full identity review.']}
    (ROOT/'source-review-summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({k:v for k,v in summary.items() if k!='sources'}))

if __name__=='__main__': main()

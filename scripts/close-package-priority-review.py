"""Finalize evidence decisions without modifying the AWS catalog or prior reports."""
import csv
import json
from pathlib import Path
from collections import Counter

ROOT=Path('outputs/package-family-audit-20261029')

def all_zero_profile(row):
    fields=('calories','protein','carbs','fat')
    return all(row.get(f) not in ('',None) for f in fields) and all(float(row[f])==0 for f in fields)

def main():
    groups=json.loads((ROOT/'candidates.json').read_text(encoding='utf-8'))
    selected=[g for g in groups if g['decision']=='PACKAGE_FAMILY_CANDIDATE']
    decisions={
      'PF-09456':{
        'decision':'BLOCKED_ALL_ZERO_NUTRITION',
        'identity':'Package GTINs corroborated; pure soluble coffee family supported, exact package formula not established.',
        'nutrition':'All four DB profiles are zero. Equal zeros are not verified nutrition. Dry powder must not inherit prepared-beverage values.',
        'sources':['https://www.nescafe.com/tr/kahveler/gold','https://www.migros.com.tr/hemen/nescafe-gold-ekonomik-paket-50-g-p-314e49'],
        'next':'Obtain package-specific dry-product nutrition evidence; do not substitute 3-in-1 or prepared coffee.'},
      'PF-09482':{
        'decision':'REVIEW_EXACT_VARIANT_AND_175G_NUTRITION',
        'identity':'GTIN and quantity corroborated, but current matching-source names omit double-roasted distinction.',
        'nutrition':'450 g retailer profile matches DB core/fiber/sugar. Exact-variant 175 g nutrition remains unverified.',
        'sources':['https://www.ulker.com.tr/tr/markalar/potibor','https://www.macrocenter.com.tr/ulker-potibor-biskuvi-cifte-kavrulmus-450-g-p-6afae9'],
        'next':'Confirm both labels identify double-roasted variant and compare 175 g formula/nutrition.'},
      'PF-09633':{
        'decision':'PRESENTATION_FAMILY_SUPPORTED_REVIEW_READY',
        'identity':'Manufacturer catalog lists both 400 g and 600 g kashar products. Live exact-GTIN/quantity evidence exists for both.',
        'nutrition':'Retailer declarations for both packages agree with DB on per-100 g calories 317, fat 25, carbohydrate 0.9, protein 22. Ingredient descriptions align. Sugar is absent from both DB rows although sources declare 0.9 g.',
        'sources':['https://eker.com/urunler','https://www.ozdilekteyim.com/market/eker-kasar-peyniri-400-g','https://www.carrefoursa.com/eker-kasar-peyniri-600-gr-p-30255418'],
        'next':'Eligible for human approval of presentation-only family linkage. Preserve both GTIN records and nutrition; do not merge or fill missing micronutrients.'}
    }
    for group in selected:
        row=decisions.get(group['group_id'])
        if row:
            row.update(group_id=group['group_id'],productIds=[p['id'] for p in group['products']],safeToApply=False,reviewScope='Presentation family only, never physical product merge')
    (ROOT/'priority-identity-nutrition-decisions.json').write_text(json.dumps(list(decisions.values()),ensure_ascii=False,indent=2),encoding='utf-8')
    queue=[]
    for group in selected:
        flags=[]
        if any(all_zero_profile(p) for p in group['products']): flags.append('ALL_ZERO_PROFILE_NOT_VERIFIED')
        if any(p['nutrition_reference_unit']=='PER_SERVING' for p in group['products']): flags.append('SERVING_BASIS_REVIEW')
        if any(p.get('sugar') in ('',None) or p.get('fiber') in ('',None) for p in group['products']): flags.append('PARTIAL_EXTENDED_NUTRITION')
        decision=decisions.get(group['group_id'],{}).get('decision','SOURCE_IDENTITY_NUTRITION_EVIDENCE_REQUIRED')
        queue.append({'group_id':group['group_id'],'decision':decision,'product_ids':';'.join(p['id'] for p in group['products']),'names':' | '.join(p['name'] for p in group['products']),'flags':';'.join(flags),'safe_to_apply':False})
    with (ROOT/'remaining-package-evidence-queue.csv').open('w',encoding='utf-8-sig',newline='') as handle:
        writer=csv.DictWriter(handle,fieldnames=list(queue[0])); writer.writeheader(); writer.writerows(queue)
    summary={'packageGroups':len(queue),'priorityReviews':3,'presentationFamilyReviewReady':1,'automaticApplicationApproved':0,'groupsWithAllZeroProfiles':sum('ALL_ZERO_PROFILE_NOT_VERIFIED' in r['flags'] for r in queue),'decisionCounts':dict(Counter(r['decision'] for r in queue)),'allMicronutrientsVerified':False,'catalogWrites':0,'evidenceDate':'2026-10-29'}
    (ROOT/'priority-review-summary.json').write_text(json.dumps(summary,indent=2),encoding='utf-8')
    print(json.dumps(summary))

if __name__=='__main__': main()

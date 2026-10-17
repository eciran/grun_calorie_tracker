"""Operation-scoped R5 review. No live writer or numeric/volume conversion.

Typed source alternatives are evidence, not proof of a legacy scalar's unit.
Historical portion IDs/weights/labels remain immutable; reviewed replacements
are appended and old selection/default flags alone are retired after approval.
"""
import argparse
import copy
import hashlib
import json
import math
import re
from collections import Counter
from datetime import datetime,timezone
from pathlib import Path
from importlib.util import module_from_spec,spec_from_file_location


def module(name,file):
    spec=spec_from_file_location(name,Path(__file__).with_name(file))
    value=module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


common=module('r5_common_prepare','prepare-r6-common-operation.py')
sha,fingerprint=common.sha,common.fingerprint
REGISTRY=common.ROOT/'data/catalog/r5-common-portion-decisions-v1.json'
EXPECTED_OPTIONS={128,3634,2301,2303,2307,3228,2316,3230,2322,3235,3256,3258,3265}


def number(value):
    return isinstance(value,(int,float)) and not isinstance(value,bool) and math.isfinite(value) and value>=0


def vitamin_a(food,body):
    if food.get('vitamin_a') is not None and not number(food['vitamin_a']): raise ValueError('Invalid legacy vitamin A')
    candidates=[]
    for nid,name,unit in [(1104,'VITAMIN_A_IU','IU'),(1106,'VITAMIN_A_RAE','ug')]:
        entries=[r for r in body.get('foodNutrients',[]) if r.get('nutrient',{}).get('id')==nid]
        if len(entries)>1: raise ValueError('Ambiguous source vitamin A record')
        if entries:
            n=entries[0]
            actual=str(n['nutrient'].get('unitName','')).replace('µ','u').replace('μ','u')
            if actual.lower()!=unit.lower() or not number(n.get('amount')): raise ValueError('Invalid typed source vitamin A')
            candidates.append({'nutrientId':nid,'semantic':name,'unit':unit,'amount':n['amount'],
                               'sameNumberWithinRounding':food.get('vitamin_a') is not None and abs(food['vitamin_a']-n['amount'])<=.051})
    matches=[c for c in candidates if c['sameNumberWithinRounding']]
    return {'current':food.get('vitamin_a'),'sourceCandidates':candidates,
            'state':'UNKNOWN_RETAINED' if food.get('vitamin_a') is None else 'SOURCE_NUMBER_MATCH_UNIT_UNATTESTED' if matches else 'UNRESOLVED_SOURCE_VALUE_RETAINED',
            'legacyUnitAttested':False,'conversionAllowed':False,'numericWriteAllowed':False,
            'reason':'Legacy evidence stores per-100g basis but no nutrient unit/form. Matching RAE/IU numbers are reported separately, never an automatic canonical unit assignment.'}


def source_portion(body,decision):
    matches=[p for p in body.get('foodPortions',[]) if p.get('amount')==1 and p.get('gramWeight')==decision['sourceGrams']
             and p.get('modifier')==decision['sourceModifier']]
    if len(matches)!=1: raise ValueError('Exactly one reviewed source measure required')
    return matches[0]


def validate(plan):
    if (plan.get('version')!='R5_COMMON_READINESS_PREPARATION_V1' or plan.get('liveApplyAllowed') is not False
            or plan.get('approval') is not None or plan.get('backup') is not None or plan.get('numericWritesAllowed') is not False
            or plan.get('mlConversionAllowed') is not False or plan.get('globalR5Completed') is not False
            or plan.get('legacyMicronutrientUnitsCertified') is not False
            or plan.get('scope')!='R5_DEPENDENCIES_OF_EXACT_R6_COMMON_OPERATION_ONLY'
            or len(plan['currentRows'])!=37 or len({r['id'] for r in plan['currentRows']})!=37
            or {r['id'] for r in plan['currentRows']}!={r['id'] for r in common.names.held.read_json(common.names.REGISTRY)['entries']}|set(common.PROTECTED)
            or len(plan['replacements'])!=13
            or {r['before']['id'] for r in plan['replacements']}!=EXPECTED_OPTIONS):
        raise ValueError('Exact unapproved 13-option, no nutrient/ml writes plan required')
    common.validate_schema(plan.get('schemaVersion'),plan['currentRows'])
    registry={r['optionId']:r for r in common.names.held.read_json(REGISTRY)['entries']}
    parent={r['id']:r for r in plan['currentRows']}
    for row in plan['replacements']:
        before,d=row['before'],registry[row['before']['id']]
        current=parent[d['foodId']]
        if (row['foodId']!=d['foodId'] or before not in current['name_review']['options'] or before['food_item_id']!=int(d['foodId'])
                or before['gram_weight']!=d['oldGrams'] or before.get('ml_volume') is not None or before['quantity']!=1
                or before['user_visible'] is not True or before['quality_status']!=('NEEDS_REVIEW' if before['id']==128 else 'VERIFIED')
                or row['retireChanges']!={'user_visible':False,'is_default':False}
                or row['newOption']!={'label':d['en'],'unit_type':d['unit'],'quantity':1,'gram_weight':d['sourceGrams'],
                    'ml_volume':None,'is_default':before['is_default'],'source':'ADMIN','quality_status':'VERIFIED','user_visible':True}
                or row['labels']!={'EN':d['en'],'TR':d['tr']}
                or row['sourcePortion']['gramWeight']!=d['sourceGrams'] or row['sourcePortion']['amount']!=1
                or row['sourcePortion'].get('modifier')!=d['sourceModifier']
                or row['sourcePortionSha256']!=fingerprint(row['sourcePortion'])
                or row['sourceKey']!=current['food']['source_key'] or row['beforeSha256']!=fingerprint(current)):
            raise ValueError('Portion/source/ownership/retirement allowlist drift')
        if len(current['name_review']['options'])+1>20: raise ValueError('Option limit cannot be bypassed')
    for ref in plan['optionDependenciesBefore']['optionDependencies']:
        if (ref['supported'] is not True or ref['schema']!='public' or not re.fullmatch('[a-z][a-z0-9_]*',ref['table'])
                or not re.fullmatch('[a-z][a-z0-9_]*',ref['column']) or not str(ref['count']).isdigit()
                or not re.fullmatch('[a-f0-9]{32}',ref['fingerprint'])): raise ValueError('Unsupported option dependency')


def build(args):
    now=datetime.now(timezone.utc)
    common_plan=common.names.held.read_json(args.common_plan)
    common.validate_plan(common_plan)
    summary=common.names.held.read_json(args.common_plan.parent/'summary.json')
    if summary['planSha256']!=sha(args.common_plan): raise ValueError('Common package changed')
    proof=common.names.held.read_json(args.current/'snapshot-proof.json')
    selection=args.selection/'selected-ids.jsonl'
    if (proof.get('scope')!='r5-common' or proof.get('transactionReadOnly') is not True or proof.get('exitCode')!=0
            or proof.get('origin')!='AWS_READ_ONLY_SNAPSHOT'
            or proof.get('rows')!=37 or len(proof['metrics'])!=6 or proof.get('schemaVersion')!=common_plan['schemaVersion']
            or proof.get('sha256')!=sha(args.current/'catalog.csv') or proof.get('decisionsSha256')!=sha(selection)
            or proof.get('sqlSha256')!=hashlib.sha256(common.names.collector.common_option_review_sql(selection).encode()).hexdigest()
            or not common.names.held.everyday.fresh(proof['time'],now)):
        raise ValueError('Fresh full source/option-FK snapshot required')
    if common_plan['inputHashes']['snapshot']!=proof['sha256'] or common_plan['snapshotTime']!=proof['time']:
        raise ValueError('Common and R5 must use the SAME consistent current snapshot')
    bodies,evidence=common.names.held.source_evidence(args.sources,args.selection/'source-queue.csv',now)
    registry=common.names.held.read_json(REGISTRY)
    if registry['version']!='R5_COMMON_PORTION_DECISIONS_V1' or registry['liveApplyAllowed'] is not False:
        raise ValueError('Reviewed portion registry required')
    rows=common_plan['currentRows']
    by_id={r['id']:r for r in rows}
    nutrition=[]
    for row in rows:
        food=row['food']
        body=bodies[food['source_key'].split(':')[-1]]
        core=common.names.held.gate.source.usda_core(body)
        if (food['nutrition_reference_unit']!='PER_100G' or food.get('nutrition_basis')!='SOURCE_REPORTED'
                or any(not number(food.get(k)) or not number(core.get(k)) or abs(food[k]-core[k])>(.51 if k=='calories' else .051)
                       for k in ('calories','protein','fat','carbs'))):
            raise ValueError('Invalid/missing/different core or basis blocks semantic/portion release')
        comparisons=common.names.held.gate.comparison(food,common.names.held.gate.all_usda_fields(body),'USDA_FOODDATA')
        numeric_holds=[k for k,v in comparisons.items() if v['state'] in {'INVALID_CURRENT','UNMEASURED_CURRENT_FIELD'}]
        if numeric_holds: raise ValueError('Invalid/unmeasured current values cannot be retained as an approved release')
        optional={k:v for k,v in comparisons.items() if k not in {'calories','protein','fat','carbs'}}
        for k,v in optional.items():
            if v['current'] is not None and v['current']<0: raise ValueError('Negative optional nutrient blocks release')
            if v['state'] in {'DIFFERS','SOURCE_VALUE_REQUIRED'} and k!='vitamin_a' and row['id']!='179893':
                raise ValueError('Unexpected mutable candidate nutrient mismatch needs review')
        nutrition.append({'id':row['id'],'sourceKey':food['source_key'],'sourceBodySha256':fingerprint(body),
            'coreState':'EXACT_SOURCE_MATCH_WITH_EXPORT_ROUNDING','optionalComparisons':optional,'vitaminA':vitamin_a(food,body),
            'disposition':'PROTECTED_COMPANION_RETAIN_OPEN_R5_FINDINGS' if row['id']=='179893' else 'RETAIN_NUMBERS_NO_NEW_UNIT_CLAIMS',
            'numericWritesAllowed':False,'unknownValuesRemainUnknown':True})
    replacements=[]
    for d in registry['entries']:
        row=by_id[d['foodId']]
        old=next(o for o in row['name_review']['options'] if o['id']==d['optionId'])
        body=bodies[row['food']['source_key'].split(':')[-1]]
        portion=source_portion(body,d)
        replacements.append({'foodId':d['foodId'],'sourceKey':row['food']['source_key'],'before':old,'beforeSha256':fingerprint(row),
            'retireChanges':{'user_visible':False,'is_default':False},
            'newOption':{'label':d['en'],'unit_type':d['unit'],'quantity':1,'gram_weight':d['sourceGrams'],'ml_volume':None,
                         'is_default':old['is_default'],'source':'ADMIN','quality_status':'VERIFIED','user_visible':True},
            'labels':{'EN':d['en'],'TR':d['tr']},'sourcePortion':portion,'sourcePortionSha256':fingerprint(portion),
            'sourceBodySha256':fingerprint(body),'reason':d['reason'],'oldHistoryFieldsRemainImmutable':True})
    all_options=[o for row in rows for o in row['name_review']['options']]
    plan={'version':'R5_COMMON_READINESS_PREPARATION_V1','schemaVersion':common_plan['schemaVersion'],'scope':'R5_DEPENDENCIES_OF_EXACT_R6_COMMON_OPERATION_ONLY',
          'liveApplyAllowed':False,'approval':None,'backup':None,'numericWritesAllowed':False,'mlConversionAllowed':False,
          'commonPlanSha256':sha(args.common_plan),'currentRows':rows,'replacements':replacements,'nutritionReviews':nutrition,
          'optionDependenciesBefore':proof['metrics'][5],'snapshotTime':proof['time'],'sourceFetchedAt':evidence['fetchedAt'],
          'legacyMicronutrientUnitsCertified':False,'globalR5Completed':False,
          'operationDisposition':'CORE_VALID_ALL_CURRENT_NUTRIENTS_RETAINED_WITH_TYPED_SOURCE_AND_EXPLICIT_UNKNOWN_UNIT_FINDINGS',
          'globalFollowups':['EXPLICIT_LEGACY_MICRONUTRIENT_UNIT_FORM_METADATA','PROTECTED_FOUNDATION_FETA_MINERAL_SUGAR_REVIEW',
                             'SOURCE_MEASURED_LIQUID_ML_BRIDGES','REST_OF_R5_262_HELD_QUEUE'],
          'primaryReferences':['https://dsid.od.nih.gov/unit-conversions','https://fdc.nal.usda.gov/Foundation_Foods_Documentation/'],
          'inputHashes':{'producer':sha(Path(__file__)),'registry':sha(REGISTRY),'commonPlan':sha(args.common_plan),
                        'snapshotProof':sha(args.current/'snapshot-proof.json'),'sourceEvidence':sha(args.sources/'evidence.json'),
                        'optionSql':sha(common.ROOT/'scripts/sql/export-r5-common-option-links.sql')}}
    validate(plan)
    args.output.mkdir(parents=True,exist_ok=False)
    target=args.output/'r5-common-plan.json'
    target.write_text(json.dumps(plan,ensure_ascii=False,indent=2,allow_nan=False)+'\n',encoding='utf-8')
    results={'status':'R5_COMMON_OPERATION_DISPOSITIONS_AND_REPLACEMENTS_PREPARED_NOT_APPLIED','profilesReviewed':37,'coreFieldsMatched':148,
             'vitaminASourceMatchStates':dict(Counter(r['vitaminA']['state'] for r in nutrition)),
             'legacyNutrientUnitsCertified':False,'currentOptionsReviewed':len(all_options),'oldOptionsRetiredNominated':13,
             'newSourceOptionsNominated':13,'newLocalizedLabels':26,
             'sourceShapeWeightRepairs':sum(r['before']['gram_weight']!=r['newOption']['gram_weight'] or r['before']['unit_type']!=r['newOption']['unit_type'] for r in replacements),
             'sourceCupLabelClarifications':sum(r['before']['gram_weight']==r['newOption']['gram_weight'] and r['before']['unit_type']==r['newOption']['unit_type'] for r in replacements),
             'oldHistoricalWeightsAndLabelsUnchanged':True,'numericFoodWrites':0,'mlConversions':0,'awsDataWrites':0,
             'globalR5Completed':False,'planSha256':sha(target)}
    (args.output/'summary.json').write_text(json.dumps(results,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(results,indent=2))


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    for key in ('common-plan','current','selection','sources','output'): parser.add_argument('--'+key,type=Path,required=True)
    build(parser.parse_args())

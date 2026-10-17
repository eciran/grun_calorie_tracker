"""Rebase five reviewed R6 packages into one exact, review-only ledger.

No AWS writer and no approval inferred from a source snapshot or rehearsal.
"""
import argparse
import copy
import csv
import hashlib
import json
import re
from collections import Counter
from datetime import datetime, timedelta, timezone
from pathlib import Path
from importlib.util import module_from_spec, spec_from_file_location


def module(name,file):
    spec=spec_from_file_location(name,Path(__file__).with_name(file))
    value=module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


names=module('common_names','prepare-r6-held-names.py')
identity=module('common_identity','prepare-r6-identity-repairs.py')
feta=module('common_feta','review-r6-feta-aliases.py')
lifeway=module('common_lifeway','prepare-r6-lifeway.py')
edamame=module('common_edamame','prepare-r6-edamame.py')
sha,fingerprint=names.held.everyday.sha,names.held.everyday.fingerprint
ROOT=Path(__file__).resolve().parents[1]
OVERRIDES={'49682':'identities','181002':'identities','180991':'feta','181453':'lifeway','181467':'edamame'}
PROTECTED={'179890':'USDA_FOODDATA:fdc:171413','179893':'USDA_FOODDATA:fdc:2259796'}
VERSIONS={'names':'R6_HELD_NAME_APPLICATION_PREPARATION_V1','identities':'R6_TWO_IDENTITY_REPAIR_PREPARATION_V1',
          'feta':'R6_FETA_ALIAS_PREPARATION_V1','lifeway':'R6_LIFEWAY_BRAND_PREPARATION_V1','edamame':'R6_EDAMAME_PREPARATION_V1'}
ADDITIVE_IDENTITY_FIELDS=('food_concept_key','composition_variant_key','source_data_type')


def validate_schema(version,rows):
    if version not in {'305','307'}: raise ValueError('Only reviewed schema 305/307 supported')
    if version=='307' and any(k not in row['food'] or row['food'][k] is not None
                             for row in rows for k in ADDITIVE_IDENTITY_FIELDS):
        raise ValueError('V307 requires fresh explicit null identity fields; assigned concepts need renewed review')


def packages(path):
    inputs=names.held.read_json(path)
    if set(inputs)!=set(VERSIONS): raise ValueError('Exact five component packages required')
    result,hashes={},{}
    for key,file in inputs.items():
        source=(ROOT/file).resolve()
        if not source.is_relative_to(ROOT/'outputs'): raise ValueError('Private review package outside workspace outputs')
        plan=names.held.read_json(source)
        summary=names.held.read_json(source.parent/'summary.json')
        if (plan.get('version')!=VERSIONS[key] or plan.get('liveApplyAllowed') is not False
                or plan.get('approval') is not None or plan.get('backup') is not None or summary['planSha256']!=sha(source)):
            raise ValueError('Component checksum/version/approval drift: '+key)
        result[key],hashes[key]=plan,{'path':str(source.relative_to(ROOT)),'sha256':sha(source)}
    registry=names.held.read_json(names.REGISTRY)
    if (len(registry['entries'])!=35 or len(result['names']['rows'])!=35
            or {r['id'] for r in result['names']['rows']}!={r['id'] for r in registry['entries']}):
        raise ValueError('Exact historical name scope required')
    identity.validate_plan(result['identities'])
    feta.validate(result['feta'])
    lifeway.validate(result['lifeway'])
    edamame.validate(result['edamame'])
    return result,hashes,{r['id']:r for r in registry['entries']}


def select(args):
    plans,hashes,registry=packages(args.components)
    args.output.mkdir(parents=True,exist_ok=False)
    selected=[]
    for identifier in sorted(set(registry)|set(PROTECTED),key=int):
        requests={(lang,names.aliases.normalize_alias(registry[identifier][key])) for lang,key in [('EN','en'),('TR','tr')]} if identifier in registry else set()
        if identifier=='181453': requests.update((lang,names.aliases.normalize_alias(label)) for lang,label in [('EN',lifeway.EN),('TR',lifeway.TR)])
        if identifier=='181467': requests.update((lang,names.aliases.normalize_alias(label)) for lang,label in [('EN',edamame.EN),('TR',edamame.TR)])
        if identifier in PROTECTED:
            requests.add(('EN','olive oil' if identifier=='179890' else 'feta cheese'))
        selected.append({'id':int(identifier),'requestedAliases':[{'language':l,'normalized_alias':a} for l,a in sorted(requests)]})
    selection=args.output/'selected-ids.jsonl'
    selection.write_text(''.join(json.dumps(r,ensure_ascii=False)+'\n' for r in selected),encoding='utf-8')
    queue=args.output/'source-queue.csv'
    with queue.open('w',encoding='utf-8',newline='') as stream:
        writer=csv.DictWriter(stream,fieldnames=['id','source_key','data_source'])
        writer.writeheader()
        writer.writerows({'id':i,'source_key':registry[i]['sourceKey'] if i in registry else PROTECTED[i],
                         'data_source':'USDA_FOODDATA'} for i in sorted(set(registry)|set(PROTECTED),key=int))
    proof={'version':'R6_COMMON_SELECTION_V1','componentHashes':hashes,'registrySha256':sha(names.REGISTRY),
           'componentsSha256':sha(args.components),'decisionsSha256':sha(selection),'queueSha256':sha(queue),
           'products':37,'candidateProducts':35,'protectedProducts':2,'liveApplyAllowed':False}
    (args.output/'selection-proof.json').write_text(json.dumps(proof,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'selected':37,'candidates':35,'protected':2,'historicalPackages':5,'awsDataWrites':0}))


def refresh(row,current):
    result=copy.deepcopy(row)
    result['expectedBefore']=current
    result['beforeSha256']=fingerprint(current)
    for key,children in [('localizationActions','localizations'),('aliasActions','aliases')]:
        captured={r['id']:r for r in current['name_review'][children]}
        for action in result[key]:
            if 'before' in action:
                old=action['before']
                if old['id'] not in captured or captured[old['id']]!=old:
                    raise ValueError('Captured action changed; renewed review required')
                action['before']=captured[old['id']]
    return result


def normalize(row,component):
    return {'id':row['id'],'component':component,'expectedBefore':row['expectedBefore'],'beforeSha256':row['beforeSha256'],
            'foodChanges':row.get('foodChanges',row.get('foodNameChanges',{})),
            'localizationActions':row['localizationActions'],'aliasActions':row['aliasActions'],
            'independentDataPrerequisites':row.get('independentDataPrerequisites',[])}


def validate_ledger(rows,registry):
    if len(rows)!=35 or {r['id'] for r in rows}!=set(registry): raise ValueError('Exactly one ledger row per candidate required')
    claims={}
    for row in rows:
        identifier=row['id']
        if row['component']!=OVERRIDES.get(identifier,'names'): raise ValueError('Special decision must replace, not append to, base plan')
        food=row['expectedBefore']['food']
        if str(food['id'])!=identifier or food['source_key']!=registry[identifier]['sourceKey'] or row['beforeSha256']!=fingerprint(row['expectedBefore']):
            raise ValueError('Ledger source/record checksum drift')
        allowed={'name','display_name','short_display_name'}
        if row['component']=='identities': allowed.add('canonical_food_key')
        if row['component']=='edamame': allowed.update({'canonical_food_key','preparation_state'})
        if row['component']=='lifeway': allowed.update({'brand','catalog_type','canonical_food_key','publication_status','verification_status'})
        if row['component']=='feta': allowed=set()
        if not set(row['foodChanges'])<=allowed: raise ValueError('Forbidden common field write')
        for action in row['aliasActions']:
            if action['action']=='ADD_SOURCE_QUALIFIED_ALIAS':
                key=(action['language'],action['normalized_alias'])
                if key in claims and claims[key]!=identifier: raise ValueError('Two planned products claim the same active alias')
                claims[key]=identifier
            elif action['action']!='DEACTIVATE_EXACT_MISLEADING_ALIAS': raise ValueError('Unsupported alias action')
            if 'before' in action and action['before'] not in row['expectedBefore']['name_review']['aliases']:
                raise ValueError('Alias outside captured owned scope')
    return claims


def backup_gate(snapshot,now):
    created=datetime.fromisoformat(snapshot['SnapshotCreateTime'].replace('Z','+00:00'))
    if (snapshot.get('Status')!='available' or snapshot.get('Encrypted') is not True
            or snapshot.get('DBInstanceIdentifier')!='grun-staging-db' or snapshot.get('Engine')!='postgres'
            or snapshot.get('EngineVersion')!='18.3' or created.tzinfo is None
            or snapshot.get('DBSnapshotArn')!='arn:aws:rds:eu-west-1:911291530432:snapshot:'+snapshot.get('DBSnapshotIdentifier','')
            or created>now or now-created>timedelta(hours=1)):
        raise ValueError('Fresh available encrypted exact-database PG18.3 backup required')
    return {'identifier':snapshot['DBSnapshotIdentifier'],'arn':snapshot['DBSnapshotArn'],'createdAt':snapshot['SnapshotCreateTime']}


def validate_plan(plan):
    registry={r['id']:r for r in names.held.read_json(names.REGISTRY)['entries']}
    if (plan.get('version')!='R6_COMMON_OPERATION_PREPARATION_V1' or plan.get('scope')!='EXACT_R6_35_NAMES_PLUS_TWO_PROTECTED_COMPANIONS'
            or plan.get('liveApplyAllowed') is not False or plan.get('approval') is not None or plan.get('backup') is not None
            or plan.get('numericWritesAllowed') is not False
            or plan.get('portionWritesAllowed') is not False or plan.get('mergeAllowed') is not False
            or set(plan['protectedIds'])!=set(PROTECTED) or len(plan['currentRows'])!=37
            or {r['id'] for r in plan['currentRows']}!=set(registry)|set(PROTECTED)):
        raise ValueError('Exact unapproved common review scope required')
    validate_schema(plan.get('schemaVersion'),plan['currentRows'])
    validate_ledger(plan['rows'],registry)
    current={r['id']:r for r in plan['currentRows']}
    for row in plan['rows']:
        if row['expectedBefore']!=current[row['id']]: raise ValueError('One consistent before-image per product required')
        if row['component']=='names':
            expected={k:registry[row['id']]['en'] for k in ('name','display_name','short_display_name')
                      if current[row['id']]['food'].get(k)!=registry[row['id']]['en']}
            if row['foodChanges']!=expected: raise ValueError('Base name decision changed')
        else:
            cp=plan['components'][row['component']]
            if row['component'] in {'identities','feta'}: original=next(r for r in cp['rows'] if r['id']==row['id'])
            else: original=cp['row']
            expected=normalize(original,row['component'])
            if any(row[k]!=expected[k] for k in ('expectedBefore','foodChanges','localizationActions','aliasActions')):
                raise ValueError('Authoritative component decision differs from common ledger')
    identity.validate_plan(plan['components']['identities'])
    feta.validate(plan['components']['feta'])
    lifeway.validate(plan['components']['lifeway'])
    edamame.validate(plan['components']['edamame'])


def build(args):
    now=datetime.now(timezone.utc)
    plans,hashes,registry=packages(args.components)
    selection=args.selection/'selected-ids.jsonl'
    selection_proof=names.held.read_json(args.selection/'selection-proof.json')
    if (selection_proof['componentHashes']!=hashes or selection_proof['registrySha256']!=sha(names.REGISTRY)
            or selection_proof['componentsSha256']!=sha(args.components) or selection_proof['decisionsSha256']!=sha(selection)
            or selection_proof['queueSha256']!=sha(args.selection/'source-queue.csv')): raise ValueError('Selection changed')
    proof=names.held.read_json(args.current/'snapshot-proof.json')
    catalog=args.current/'catalog.csv'
    option_scope=proof.get('scope')=='r5-common'
    expected_sql=names.collector.common_option_review_sql(selection) if option_scope else names.collector.common_review_sql(selection)
    if (proof.get('scope') not in {'r6-common','r5-common'} or proof.get('origin')!='AWS_READ_ONLY_SNAPSHOT' or proof.get('transactionReadOnly') is not True
            or proof.get('exitCode')!=0 or proof.get('rows')!=37 or proof.get('schemaVersion') not in {'305','307'}
            or proof.get('sha256')!=sha(catalog) or proof.get('decisionsSha256')!=sha(selection)
            or proof.get('sqlSha256')!=hashlib.sha256(expected_sql.encode()).hexdigest()
            or not names.held.everyday.fresh(proof['time'],now) or len(proof['metrics'])!=(6 if option_scope else 5)):
        raise ValueError('Fresh exact consistent common snapshot required')
    with catalog.open(encoding='utf-8-sig',newline='') as stream:
        current={r['id']:{k:v if k=='id' else json.loads(v) for k,v in r.items()} for r in csv.DictReader(stream)}
    if set(current)!=set(registry)|set(PROTECTED): raise ValueError('Current scope drift')
    validate_schema(proof['schemaVersion'],list(current.values()))
    bodies,evidence=names.held.source_evidence(args.sources,args.selection/'source-queue.csv',now)
    old={r['id']:r for r in plans['names']['rows']}
    reviews={}
    source_changes=[]
    for identifier,decision in registry.items():
        body=bodies[decision['sourceKey'].split(':')[-1]]
        if body['description']!=decision['sourceDescription'] or body['dataType']!='SR Legacy':
            raise ValueError('Reviewed source description/subtype changed')
        if fingerprint(body)!=old[identifier]['sourceBodySha256']: source_changes.append(identifier)
        reviews[identifier]=names.audit(current[identifier],decision,body,
            {'sourceBodySha256':fingerprint(body),'holds':old[identifier]['independentDataPrerequisites']},proof['metrics'][0]['requestedAliasConflicts'])
    if source_changes: raise ValueError('Historical full source bodies changed: '+','.join(source_changes))
    components={}
    ip=copy.deepcopy(plans['identities'])
    ip['dependencyBefore']=proof['metrics'][1]
    ip['rows']=[refresh(r,current[r['id']]) for r in ip['rows']]
    identity.validate_plan(ip)
    components['identities']=ip
    fp=copy.deepcopy(plans['feta'])
    fp['rows']=[refresh(r,current[r['id']]) for r in fp['rows']]
    fp['aliasClaimsBefore']=[r for r in proof['metrics'][0]['requestedAliasConflicts'] if r['language']=='EN' and r['normalized_alias']=='feta cheese']
    feta.validate(fp)
    components['feta']=fp
    lp=copy.deepcopy(plans['lifeway'])
    lp['row']=refresh(lp['row'],current['181453'])
    lp['brandAndDependenciesBefore']=proof['metrics'][2]
    lifeway.validate(lp)
    components['lifeway']=lp
    ep=copy.deepcopy(plans['edamame'])
    ep['row']=refresh(ep['row'],current['181467'])
    ep['dependencyBefore']=proof['metrics'][3]
    edamame.validate(ep)
    components['edamame']=ep
    for identifier,source in PROTECTED.items():
        if current[identifier]['food']['source_key']!=source: raise ValueError('Protected companion identity changed')
    rows=[]
    for identifier in sorted(registry,key=int):
        component=OVERRIDES.get(identifier,'names')
        if component=='names':
            candidate=reviews[identifier]
            if candidate['nameApplicationHolds']: raise ValueError('Unresolved base semantic/alias hold: '+identifier)
        elif component in {'identities','feta'}:
            candidate=next(r for r in components[component]['rows'] if r['id']==identifier)
        else: candidate=components[component]['row']
        row=normalize(candidate,component)
        row['independentDataPrerequisites']=reviews[identifier]['independentDataPrerequisites']
        rows.append(row)
    claims=validate_ledger(rows,registry)
    for conflict in proof['metrics'][0]['requestedAliasConflicts']:
        key=(conflict['language'],conflict['normalized_alias'])
        if key in claims and str(conflict['food_item_id'])!=claims[key]: raise ValueError('Current external alias claim blocks ledger')
    meta=proof['metrics'][4]
    for ref in meta['commonDependencies']:
        if (ref['supported'] is not True or ref['schema']!='public' or not re.fullmatch('[a-z][a-z0-9_]*',ref['table'])
                or not re.fullmatch('[a-z][a-z0-9_]*',ref['column']) or not str(ref['count']).isdigit()
                or not re.fullmatch('[a-f0-9]{32}',ref['fingerprint'])): raise ValueError('Unsupported aggregate FK path')
    for identifier,before in current.items():
        source=before['food']['source_key']
        own=[c for c in meta['commonKeyClaims'] if c['source_key'] in {source,source.replace('USDA_FOODDATA:','USDA:')}]
        if len(own)!=1 or str(own[0]['id'])!=identifier: raise ValueError('Cross-namespace duplicate source identity')
    comparisons={i:names.held.gate.comparison(r['food'],names.held.gate.all_usda_fields(bodies[r['food']['source_key'].split(':')[-1]]),'USDA_FOODDATA')
                 for i,r in current.items()}
    runtime_files=['src/main/resources/catalog/source-name-decisions-r4-v1.json','src/main/resources/catalog/source-name-decisions-r6-v1.json',
                   'src/main/resources/catalog/usda-named-brand-scopes-r6-v1.json','src/main/java/com/grun/calorietracker/service/support/FoodSourceNameRules.java',
                   'src/main/java/com/grun/calorietracker/service/support/UsdaNamedBrandRules.java','src/main/java/com/grun/calorietracker/service/impl/FoodProductImportServiceImpl.java']
    runtime_files+=['src/main/java/com/grun/calorietracker/service/impl/FoodServingOptionServiceImpl.java',
                   'src/main/java/com/grun/calorietracker/service/impl/FoodLogsServiceImpl.java',
                   'src/main/java/com/grun/calorietracker/service/support/FoodPortionCalculator.java',
                   'src/main/java/com/grun/calorietracker/mapper/FoodServingOptionMapper.java',
                   'src/main/java/com/grun/calorietracker/mapper/FoodItemMapper.java',
                   'src/main/java/com/grun/calorietracker/dto/FoodServingOptionDto.java',
                   'src/main/java/com/grun/calorietracker/dto/FoodProductDto.java',
                   'src/main/java/com/grun/calorietracker/entity/FoodItemEntity.java',
                   'src/main/java/com/grun/calorietracker/entity/FoodItemServingOptionEntity.java',
                   'src/main/java/com/grun/calorietracker/entity/FoodItemServingOptionLocalizationEntity.java',
                   'src/main/resources/db/migration/V306__add_resumable_product_catalog_audit.sql',
                   'src/main/resources/db/migration/V307__add_reviewed_food_concept_identity.sql']
    plan={'version':'R6_COMMON_OPERATION_PREPARATION_V1','scope':'EXACT_R6_35_NAMES_PLUS_TWO_PROTECTED_COMPANIONS',
          'schemaVersion':proof['schemaVersion'],'liveApplyAllowed':False,'approval':None,'backup':None,'rows':rows,
          'currentRows':[current[i] for i in sorted(current,key=int)],'protectedIds':sorted(PROTECTED,key=int),
          'overlapResolution':[{'id':i,'replacedComponent':'names','authoritativeComponent':c} for i,c in sorted(OVERRIDES.items())],
          'components':components,'componentHashes':hashes,'snapshotTime':proof['time'],'sourceFetchedAt':evidence['fetchedAt'],
          'dependencyBefore':meta,'aliasClaimsBefore':proof['metrics'][0]['requestedAliasConflicts'],'nutrientComparisons':comparisons,
          'numericWritesAllowed':False,'portionWritesAllowed':False,'mergeAllowed':False,'historicalSourceBodiesChanged':[],
          'runtimeStatus':'LOCAL_NOT_DEPLOYED','requiredReleaseGates':['EXACT_COMMON_OWNER_APPROVAL','FRESH_ENCRYPTED_BACKUP_WITHIN_ONE_HOUR',
          'FRESH_FULL_SOURCE_SNAPSHOT_AND_RULE_HASHES','R5_NUMERIC_PORTION_DISPOSITIONS','HISTORY_SEMANTIC_APPROVAL_NO_SNAPSHOT_REWRITE',
          'COMPATIBLE_DEPLOYED_RUNTIME_SCHEMA','BOUNDED_MAINTENANCE_LOCK_APPROVAL','COMMON_RECEIPT_BOUND_UNDO',
          'AFTER_COMMIT_CACHE_REVISION_AUTHENTICATED_EN_TR_PORTION_ACCEPTANCE'],
          'notIncluded':['R3_173_CLASSIFICATION_RELEASE','R4_16_SOURCE_NAME_STATE_RELEASE','R5_NUTRITION_ML_SERVING_QUEUES',
                        'FOUNDATION_FETA_NAME_KEY_FOLLOWUP','DUPLICATE_SURVIVOR_APPLICATION'],
          'inputHashes':{'producer':sha(Path(__file__)),'snapshot':sha(catalog),'snapshotProof':sha(args.current/'snapshot-proof.json'),
                        'sources':sha(args.sources/'evidence.json'),'registry':sha(names.REGISTRY),
                        'sql':sha(ROOT/'scripts/sql/export-r6-common-dependencies.sql'),
                        'tooling':{file:sha(ROOT/file) for file in ['scripts/collect-r3-source-snapshot.py','scripts/rehearse-r6-held-names.py',
                            'scripts/rehearse-r6-common-operation.py','scripts/prepare-r6-identity-repairs.py','scripts/review-r6-feta-aliases.py',
                            'scripts/prepare-r6-lifeway.py','scripts/prepare-r6-edamame.py']},
                        'runtime':{file:sha(ROOT/file) for file in runtime_files}}}
    validate_plan(plan)
    args.output.mkdir(parents=True,exist_ok=False)
    target=args.output/'common-plan.json'
    target.write_text(json.dumps(plan,ensure_ascii=False,indent=2,allow_nan=False)+'\n',encoding='utf-8')
    summary={'status':'CONFLICT_FREE_COMMON_PREPARATION_NOT_APPROVED','currentProducts':37,'candidateProducts':35,'protectedProducts':2,
             'mutatingCandidates':sum(bool(r['foodChanges'] or r['aliasActions'] or r['localizationActions']) for r in rows),
             'resolvedComponentOverlaps':5,'foodFieldUpdates':sum(len(r['foodChanges']) for r in rows),
             'localeActions':sum(len(r['localizationActions']) for r in rows),
             'aliasActions':dict(Counter(a['action'] for r in rows for a in r['aliasActions'])),
             'fkPaths':len(meta['commonDependencies']),'numericWrites':0,'portionWrites':0,'awsDataWrites':0,
             'numericComparisonStates':dict(Counter(v['state'] for c in comparisons.values() for v in c.values())),
             'planSha256':sha(target),'freshEncryptedBackupTaken':False,'liveApplyAllowed':False}
    (args.output/'summary.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
    preview=[]
    for row in rows:
        food=row['expectedBefore']['food']
        locales={l['language']:l['display_name'] for l in row['expectedBefore']['name_review']['localizations'] if l['active']}
        for action in row['localizationActions']:
            if 'before' in action: locales[action['before']['language']]=action['changes'].get('display_name',action['before']['display_name'])
            else: locales[action['language']]=action['display_name']
        preview.append({'id':row['id'],'component':row['component'],'oldName':food['name'],
                        'newName':row['foodChanges'].get('name',food['name']),'localizedNames':locales,
                        'oldPreparation':food['preparation_state'],'newPreparation':row['foodChanges'].get('preparation_state',food['preparation_state']),
                        'oldCanonicalKey':food['canonical_food_key'],'newCanonicalKey':row['foodChanges'].get('canonical_food_key',food['canonical_food_key']),
                        'publicationAfterApproval':row['foodChanges'].get('publication_status',food['publication_status']),
                        'portionChanges':False,'numericChanges':False,'independentPrerequisites':row['independentDataPrerequisites']})
    (args.output/'application-preview.json').write_text(json.dumps(preview,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(summary,indent=2))


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--select',action='store_true')
    parser.add_argument('--components',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    for key in ('selection','current','sources'): parser.add_argument('--'+key,type=Path)
    args=parser.parse_args()
    select(args) if args.select else build(args)

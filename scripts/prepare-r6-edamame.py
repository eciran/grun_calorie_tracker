"""Exact source-bound Edamame preparation nomination. No AWS writer."""
import argparse
import csv
import hashlib
import json
import re
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from importlib.util import module_from_spec, spec_from_file_location


def module(name, file):
    spec = spec_from_file_location(name, Path(__file__).with_name(file))
    value = module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


names = module('edamame_names', 'prepare-r6-held-names.py')
sha, fingerprint = names.held.everyday.sha, names.held.everyday.fingerprint
DESCRIPTION = 'Edamame, frozen, prepared'
EN, TR = 'Prepared Edamame (From Frozen)', 'Dondurulmuştan Hazırlanmış Edamame'
OLD_KEY = 'GLOBAL:GENERIC_INGREDIENT:BOILED:edamame'
NEW_KEY = 'GLOBAL:GENERIC_INGREDIENT:PREPARED:edamame_from_frozen'
REGISTRY = Path(__file__).resolve().parents[1] / 'src/main/resources/catalog/source-name-decisions-r6-v1.json'
CHANGES = {'name': EN, 'display_name': EN, 'short_display_name': EN,
           'preparation_state': 'PREPARED', 'canonical_food_key': NEW_KEY}


def validate(plan):
    if (plan.get('version') != 'R6_EDAMAME_PREPARATION_V1' or plan.get('liveApplyAllowed') is not False
            or plan.get('approval') is not None or plan.get('backup') is not None or plan.get('schemaVersion') != '305'):
        raise ValueError('Exact review-only Edamame plan required')
    row, meta = plan['row'], plan['dependencyBefore']
    before = row['expectedBefore']
    food = before['food']
    if (row['id'] != '181467' or before['id'] != row['id'] or str(food['id']) != row['id']
            or food['source_key'] != 'USDA_FOODDATA:fdc:168411' or food['data_source'] != 'USDA_FOODDATA'
            or food['catalog_type'] != 'GENERIC_INGREDIENT' or food['is_custom'] or food['publication_status'] != 'PUBLISHED'
            or food['preparation_state'] != 'BOILED' or food['canonical_food_key'] != OLD_KEY
            or food['name'] != 'Prepared Edamame' or food['nutrition_reference_unit'] != 'PER_100G'
            or food.get('food_concept_key') is not None or food.get('composition_variant_key') is not None
            or food.get('source_data_type') not in {None, 'SR Legacy'}
            or food.get('brand') is not None or food.get('brand_id') is not None
            or row['beforeSha256'] != fingerprint(before) or row['foodChanges'] != CHANGES):
        raise ValueError('Unapproved source/state/key/numeric mutation or identity drift')
    if meta['canonicalResolutions'] or meta['keyClaims'] != [{'id': 181467, 'source_key': 'USDA_FOODDATA:fdc:168411',
            'canonical_food_key': OLD_KEY, 'is_custom': False, 'publication_status': 'PUBLISHED'}]:
        raise ValueError('Source/key collision or existing resolution requires renewed review')
    refs = meta['dependencies']
    if not refs or len({r['constraint'] for r in refs}) != len(refs):
        raise ValueError('Complete unique FK inventory required')
    for ref in refs:
        if (ref['supported'] is not True or ref['schema'] != 'public'
                or not re.fullmatch('[a-z][a-z0-9_]*', ref['table'])
                or not re.fullmatch('[a-z][a-z0-9_]*', ref['column'])
                or not str(ref['count']).isdigit() or not re.fullmatch('[a-f0-9]{32}', ref['fingerprint'])):
            raise ValueError('Unsupported dependency cannot be ignored')
    locales = [l for l in before['name_review']['localizations'] if l['active']]
    if {(l['id'], l['language']) for l in locales} != {(5336, 'EN'), (5337, 'TR')} or len(locales) != 2:
        raise ValueError('Exact captured bilingual locale pair required')
    if row['localizationActions'] != [{'action': 'UPDATE_EXACT_ACTIVE_LOCALIZATION', 'before': l,
            'changes': {'display_name': EN if l['language']=='EN' else TR,
                        'short_display_name': EN if l['language']=='EN' else TR}} for l in locales]:
        raise ValueError('Unapproved localization action')
    aliases = before['name_review']['aliases']
    if {a['id'] for a in aliases} != {2865, 2866, 2867} or any(not a['active'] for a in aliases):
        raise ValueError('Exact current alias inventory required')
    expected_aliases = {2865: ('TR','edamame'), 2866: ('TR','haslanmis edamame'), 2867: ('EN','edamame cooked')}
    if any((a['language'],a['normalized_alias']) != expected_aliases[a['id']] or a['food_item_id'] != 181467 for a in aliases):
        raise ValueError('Alias semantic ownership drift')
    actions = [{'action': 'DEACTIVATE_EXACT_MISLEADING_ALIAS', 'before': a} for a in aliases if a['id'] in {2866,2867}]
    actions += [{'action': 'ADD_SOURCE_QUALIFIED_ALIAS', 'language': lang, 'alias': label,
                 'normalized_alias': names.aliases.normalize_alias(label)} for lang,label in [('EN',EN),('TR',TR)]]
    if row['aliasActions'] != actions:
        raise ValueError('Unapproved alias addition/deactivation or loss of basic edamame alias')
    options = before['name_review']['options']
    if (len(options) != 1 or options[0]['id'] != 3265 or options[0]['food_item_id'] != 181467
            or options[0]['unit_type'] != 'CUP' or options[0]['quantity'] != 1 or options[0]['gram_weight'] != 155
            or options[0]['ml_volume'] is not None or options[0]['label'] != '1 cup'
            or not options[0]['is_default'] or not options[0]['user_visible'] or options[0]['quality_status'] != 'VERIFIED'):
        raise ValueError('Captured source-compatible portion drift; never infer cup ml')
    if any(re.search(r'\b(boiled|steamed|cooked|haslanmis|pismis)\b', names.aliases.normalize_alias(l['label']))
           for l in before['name_review']['option_localizations']):
        raise ValueError('Misleading portion label requires separate label review')


def build(args):
    now = datetime.now(timezone.utc)
    catalog, selection = args.current/'catalog.csv', args.selection/'selected-ids.jsonl'
    snapshot = names.held.read_json(args.current/'snapshot-proof.json')
    if (snapshot.get('scope') != 'r6-edamame' or snapshot.get('origin') != 'AWS_READ_ONLY_SNAPSHOT'
            or snapshot.get('transactionReadOnly') is not True or snapshot.get('exitCode') != 0
            or snapshot.get('rows') != 1 or snapshot.get('schemaVersion') != '305'
            or snapshot.get('sha256') != sha(catalog) or snapshot.get('decisionsSha256') != sha(selection)
            or snapshot.get('sqlSha256') != hashlib.sha256(names.collector.edamame_review_sql(selection).encode()).hexdigest()
            or not names.held.everyday.fresh(snapshot['time'], now) or len(snapshot['metrics']) != 2
            or snapshot['metrics'][0]['requestedAliasConflicts']):
        raise ValueError('Fresh exact source/key/alias/FK snapshot proof required')
    with catalog.open(encoding='utf-8-sig', newline='') as stream:
        rows = list(csv.DictReader(stream))
    if len(rows) != 1 or rows[0]['id'] != '181467':
        raise ValueError('Exactly one captured Edamame row required')
    before = {k: v if k=='id' else json.loads(v) for k,v in rows[0].items()}
    bodies, evidence = names.held.source_evidence(args.sources, args.selection/'source-queue.csv', now)
    body = bodies['168411']
    if body['description'] != DESCRIPTION or body['dataType'] != 'SR Legacy':
        raise ValueError('Fresh prepared-from-frozen source evidence required')
    if len(body['foodPortions']) != 1 or any(body['foodPortions'][0].get(k) != v
            for k,v in {'amount':1,'gramWeight':155,'modifier':'cup'}.items()):
        raise ValueError('Source portion needs renewed review')
    registry = names.held.read_json(REGISTRY)
    decision = next(r for r in registry['entries'] if r['sourceKey']=='USDA_FOODDATA:fdc:168411')
    if (registry['version'] != 'catalog-naming-r6-v1' or decision['sourceDescription'] != DESCRIPTION
            or decision['targetState'] != 'PREPARED' or decision['displayNameEn'] != EN or decision['displayNameTr'] != TR
            or decision['servingReviewRequired'] is not False):
        raise ValueError('Exact matching runtime rule required')
    child = before['name_review']
    row = {'id':'181467','expectedBefore':before,'beforeSha256':fingerprint(before),'foodChanges':dict(CHANGES),
           'localizationActions':[{'action':'UPDATE_EXACT_ACTIVE_LOCALIZATION','before':l,
               'changes':{'display_name':EN if l['language']=='EN' else TR,'short_display_name':EN if l['language']=='EN' else TR}}
               for l in child['localizations'] if l['active']],
           'aliasActions':[{'action':'DEACTIVATE_EXACT_MISLEADING_ALIAS','before':a} for a in child['aliases'] if a['id'] in {2866,2867}]
               + [{'action':'ADD_SOURCE_QUALIFIED_ALIAS','language':lang,'alias':label,'normalized_alias':names.aliases.normalize_alias(label)}
                  for lang,label in [('EN',EN),('TR',TR)]]}
    nutrients = names.held.gate.comparison(before['food'],names.held.gate.all_usda_fields(body),'USDA_FOODDATA')
    plan = {'version':'R6_EDAMAME_PREPARATION_V1','schemaVersion':'305','liveApplyAllowed':False,'approval':None,'backup':None,
            'row':row,'dependencyBefore':snapshot['metrics'][1],'snapshotTime':snapshot['time'],
            'sourceFetchedAt':evidence['fetchedAt'],'sourceBodySha256':fingerprint(body),'sourceDescription':DESCRIPTION,
            'sourceDataType':'SR Legacy','sourcePublicationDate':body.get('publicationDate'),
            'sourceUrl':'https://fdc.nal.usda.gov/food-details/168411/nutrients',
            'sourcePreparationDecision':'PREPARED_EXPLICIT_NO_COOKING_METHOD_PROVEN',
            'sourcePortion':body['foodPortions'][0],'sourcePortionValidated':True,'nutrients':nutrients,
            'forbiddenWrites':['numeric_values','unit_basis','portions_and_labels','source_keys','source_evidence','categories',
                               'markets','history','other_products','merges','deletes'],
            'requiredReleaseGates':['EXPLICIT_OWNER_APPROVAL_CURRENT_ENCRYPTED_BACKUP','FRESH_EXACT_BEFORE_IMAGES_SOURCE_AND_FK_PROOFS',
                  'BOUNDED_GLOBAL_KEY_ALIAS_AND_DEPENDENCY_LOCKS','RECEIPT_BOUND_UNDO','COMPATIBLE_IMPORT_RUNTIME_DEPLOYMENT',
                  'R5_NUMERIC_REVIEW_DISPOSITION','CACHE_REVISION_AUTHENTICATED_EN_TR_PREPARATION_ACCEPTANCE'],
            'inputHashes':{'catalog':sha(catalog),'snapshotProof':sha(args.current/'snapshot-proof.json'),
                           'sourceEvidence':sha(args.sources/'evidence.json'),'runtimeRegistry':sha(REGISTRY),'producer':sha(Path(__file__))}}
    validate(plan)
    args.output.mkdir(parents=True,exist_ok=False)
    path = args.output/'edamame-plan.json'
    path.write_text(json.dumps(plan,ensure_ascii=False,indent=2,allow_nan=False)+'\n',encoding='utf-8')
    summary = {'status':'PREPARATION_METHOD_RESOLVED_PREPARED_NOT_APPLIED','products':1,'stateBefore':'BOILED','stateAfter':'PREPARED',
               'names':{'EN':EN,'TR':TR},'localeUpdates':2,'newAliases':2,'deactivatedUnprovenAliases':2,
               'sourceValidatedCupGrams':155,'cupMlConversion':False,'nutritionWrites':0,'portionWrites':0,'awsDataWrites':0,
               'inboundForeignKeysMeasured':len(plan['dependencyBefore']['dependencies']),
               'numericComparisonStates':dict(Counter(v['state'] for v in nutrients.values())), 'planSha256':sha(path)}
    (args.output/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(summary,ensure_ascii=False,indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    for key in ('current','selection','sources','output'): parser.add_argument('--'+key,type=Path,required=True)
    build(parser.parse_args())

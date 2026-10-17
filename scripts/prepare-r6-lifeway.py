"""Exact Lifeway reference-brand preparation; no AWS writer.

USDA's SR Legacy profile is not a current retail GTIN or a measured ml density.
"""
import argparse
import csv
import json
import hashlib
from datetime import datetime, timezone
from pathlib import Path
from importlib.util import module_from_spec, spec_from_file_location


def module(name, filename):
    spec = spec_from_file_location(name, Path(__file__).with_name(filename))
    value = module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


names = module('lifeway_names', 'prepare-r6-held-names.py')
sha, fingerprint = names.held.everyday.sha, names.held.everyday.fingerprint
EN, TR = 'Lifeway Plain Low-Fat Kefir', 'Lifeway Sade Az Yağlı Kefir'
DESCRIPTION = 'Kefir, lowfat, plain, LIFEWAY'


def validate(plan):
    if (plan.get('version') != 'R6_LIFEWAY_BRAND_PREPARATION_V1' or plan.get('liveApplyAllowed') is not False
            or plan.get('approval') is not None or plan.get('backup') is not None or plan.get('schemaVersion') != '305'):
        raise ValueError('Review-only Lifeway plan required')
    row = plan['row']
    food = row['expectedBefore']['food']
    if (row['id'] != '181453' or str(food['id']) != row['id'] or food['source_key'] != 'USDA_FOODDATA:fdc:170904'
            or food['catalog_type'] != 'GENERIC_INGREDIENT' or food['brand'] is not None or food['brand_id'] is not None
            or food['name'] != 'Plain Low-Fat Kefir' or food['canonical_food_key'] != 'GLOBAL:GENERIC_INGREDIENT:PREPARED:plain_low_fat_kefir'
            or food['preparation_state'] != 'PREPARED' or food['nutrition_reference_unit'] != 'PER_100G'
            or food['barcode'] is not None or food['normalized_barcode'] is not None
            or food['data_source'] != 'USDA_FOODDATA' or food['is_custom'] or food['publication_status'] != 'PUBLISHED'
            or row['beforeSha256'] != fingerprint(row['expectedBefore'])
            or row['foodChanges'] != {'name': EN, 'display_name': EN, 'short_display_name': EN, 'brand': 'Lifeway',
                                     'catalog_type': 'BRANDED_PRODUCT', 'canonical_food_key': None,
                                     'publication_status': 'INTERNAL_REVIEW', 'verification_status': 'NEEDS_REVIEW'}):
        raise ValueError('Source/old identity drift or unapproved field change')
    meta = plan['brandAndDependenciesBefore']
    if meta['brands'] or meta['brandAliases'] or meta['canonicalResolutions']:
        raise ValueError('Existing brand/alias/resolution needs renewed review, never overwrite')
    if meta['sourceKeyClaims'] != [{'id': 181453, 'source_key': 'USDA_FOODDATA:fdc:170904'}] or meta['canonicalClaims'] != [
            {'id': 181453, 'source_key': 'USDA_FOODDATA:fdc:170904', 'name': 'Plain Low-Fat Kefir'}]:
        raise ValueError('Source/canonical claims changed')
    import re
    if not meta['dependencies'] or len({r['constraint'] for r in meta['dependencies']}) != len(meta['dependencies']):
        raise ValueError('Complete unique FK inventory required')
    for ref in meta['dependencies']:
        if (ref['supported'] is not True or ref['schema'] != 'public' or not re.fullmatch('[a-z][a-z0-9_]*', ref['table'])
                or not str(ref['count']).isdigit() or not re.fullmatch('[a-f0-9]{32}', ref['fingerprint'])):
            raise ValueError('Unsupported dependency cannot be skipped')
    if len(row['localizationActions']) != 2 or row['aliasActions'] != [
            {'action': 'ADD_SOURCE_QUALIFIED_ALIAS', 'language': language, 'alias': label,
             'normalized_alias': names.aliases.normalize_alias(label)} for language, label in [('EN', EN), ('TR', TR)]]:
        raise ValueError('Exact two qualified aliases/localizations required')
    for action in row['localizationActions']:
        locale = action['before']
        label = EN if locale['language'] == 'EN' else TR
        if (action['action'] != 'UPDATE_EXACT_ACTIVE_LOCALIZATION' or locale not in row['expectedBefore']['name_review']['localizations']
                or not locale['active'] or (locale['id'], locale['language']) not in {(5308, 'EN'), (5309, 'TR')}
                or locale['food_item_id'] != 181453 or action['changes'] != {'display_name': label, 'short_display_name': label}):
            raise ValueError('Captured owned locale pair required')


def build(args):
    now = datetime.now(timezone.utc)
    path = args.current / 'catalog.csv'
    snapshot = names.held.read_json(args.current / 'snapshot-proof.json')
    selection = args.selection / 'selected-ids.jsonl'
    if (snapshot.get('scope') != 'r6-lifeway' or snapshot.get('origin') != 'AWS_READ_ONLY_SNAPSHOT'
            or snapshot.get('transactionReadOnly') is not True or snapshot.get('exitCode') != 0
            or snapshot.get('rows') != 1 or snapshot.get('schemaVersion') != '305' or snapshot.get('sha256') != sha(path)
            or snapshot.get('decisionsSha256') != sha(selection)
            or snapshot.get('sqlSha256') != hashlib.sha256(names.collector.lifeway_review_sql(selection).encode()).hexdigest()
            or not names.held.everyday.fresh(snapshot['time'], now) or len(snapshot['metrics']) != 2
            or snapshot['metrics'][0]['requestedAliasConflicts']):
        raise ValueError('Fresh source/brand/name query and alias proof required')
    with path.open(encoding='utf-8-sig', newline='') as stream:
        records = list(csv.DictReader(stream))
    if len(records) != 1 or records[0]['id'] != '181453':
        raise ValueError('Exact one product required')
    before = {k: v if k == 'id' else json.loads(v) for k, v in records[0].items()}
    bodies, evidence = names.held.source_evidence(args.sources, args.selection / 'source-queue.csv', now)
    body = bodies['170904']
    if body['description'] != DESCRIPTION or body['dataType'] != 'SR Legacy':
        raise ValueError('Exact named-manufacturer source required')
    runtime_registry = Path(__file__).resolve().parents[1]/'src/main/resources/catalog/usda-named-brand-scopes-r6-v1.json'
    scopes = names.held.read_json(runtime_registry)
    rule = next(r for r in scopes['entries'] if r['sourceKey']=='USDA_FOODDATA:fdc:170904')
    if (scopes['version']!='usda-named-brand-scopes-r6-v1' or rule['sourceDescription']!=DESCRIPTION
            or rule['sourceDataType']!='SR Legacy' or rule['canonicalBrand']!='Lifeway'):
        raise ValueError('Runtime source/brand classification rule drift')
    row = {'id': '181453', 'expectedBefore': before, 'beforeSha256': fingerprint(before),
           'foodChanges': {'name': EN, 'display_name': EN, 'short_display_name': EN, 'brand': 'Lifeway',
                           'catalog_type': 'BRANDED_PRODUCT', 'canonical_food_key': None,
                           'publication_status': 'INTERNAL_REVIEW', 'verification_status': 'NEEDS_REVIEW'},
           'localizationActions': [{'action': 'UPDATE_EXACT_ACTIVE_LOCALIZATION', 'before': locale,
                'changes': {'display_name': EN if locale['language']=='EN' else TR,
                            'short_display_name': EN if locale['language']=='EN' else TR}}
                for locale in before['name_review']['localizations'] if locale['active']],
           'aliasActions': [{'action': 'ADD_SOURCE_QUALIFIED_ALIAS', 'language': language, 'alias': label,
                            'normalized_alias': names.aliases.normalize_alias(label)} for language, label in [('EN', EN), ('TR', TR)]]}
    plan = {'version': 'R6_LIFEWAY_BRAND_PREPARATION_V1', 'liveApplyAllowed': False, 'approval': None, 'backup': None,
            'schemaVersion': '305', 'row': row, 'brandAndDependenciesBefore': snapshot['metrics'][1],
            'snapshotTime': snapshot['time'], 'sourceFetchedAt': evidence['fetchedAt'], 'sourceBodySha256': fingerprint(body),
            'sourceDescription': DESCRIPTION, 'sourceDataTypePreserved': 'SR Legacy', 'sourcePublicationDate': body.get('publicationDate'),
            'brandNomination': {'canonical_name': 'Lifeway', 'normalized_key': 'lifeway', 'status': 'ACTIVE', 'verified': False},
            'manufacturerReference': {'url': 'https://lifewaykefir.com/products/plain-lowfat-kefir/',
                'reviewedDate': now.date().isoformat(), 'scope': 'Brand/family identity only; no historical SKU, GTIN or density equivalence'},
            'nutrients': names.held.gate.comparison(before['food'], names.held.gate.all_usda_fields(body), 'USDA_FOODDATA'),
            'forbiddenWrites': ['nutrition', 'units', 'preparation', 'servings', 'history', 'source_keys', 'barcodes',
                'categories', 'markets', 'other_products', 'merges', 'deletes'],
            'requiredReleaseGates': ['EXPLICIT_OWNER_APPROVAL_AND_CURRENT_ENCRYPTED_BACKUP', 'FRESH_FULL_BEFORE_IMAGES_AND_SOURCE',
                'NEW_BRAND_ABSENCE_AND_DEPENDENCY_LOCKS', 'RECEIPT_BOUND_UNDO', 'COMPATIBLE_NAMED_BRAND_IMPORT_RUNTIME_DEPLOYMENT',
                'HISTORY_SNAPSHOTS_PRESERVED', 'CACHE_REVISION_AND_EN_TR_API_ACCEPTANCE'],
            'retailSkuEquivalenceApproved': False, 'mlConversionApproved': False,
            'inputHashes': {'snapshot': sha(path), 'snapshotProof': sha(args.current/'snapshot-proof.json'),
                'sourceEvidence': sha(args.sources/'evidence.json'), 'runtimeRegistry': sha(runtime_registry), 'producer': sha(Path(__file__))}}
    validate(plan)
    args.output.mkdir(parents=True, exist_ok=False)
    output = args.output/'lifeway-plan.json'
    output.write_text(json.dumps(plan, ensure_ascii=False, indent=2, allow_nan=False)+'\n', encoding='utf-8')
    summary = {'status': 'SOURCE_BRAND_CLASSIFICATION_PREPARED_NOT_APPLIED', 'products': 1, 'newCanonicalBrandsNominated': 1,
               'otherExistingLifewayProductsPreserved': len(plan['brandAndDependenciesBefore']['brandProductLinks'])-1,
               'inboundForeignKeysMeasured': len(plan['brandAndDependenciesBefore']['dependencies']),
               'foodLogsPreserved': next(int(r['count']) for r in plan['brandAndDependenciesBefore']['dependencies'] if r['table']=='food_logs'),
               'names': {'EN': EN, 'TR': TR}, 'sourceDataType': 'SR Legacy', 'catalogTypeNomination': 'BRANDED_PRODUCT',
               'numericWrites': 0, 'mlConversion': False, 'barcodeInvented': False, 'awsDataWrites': 0,
               'planSha256': sha(output)}
    (args.output/'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2)+'\n',encoding='utf-8')
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    for key in ('current','selection','sources','output'):
        parser.add_argument('--'+key,type=Path,required=True)
    build(parser.parse_args())

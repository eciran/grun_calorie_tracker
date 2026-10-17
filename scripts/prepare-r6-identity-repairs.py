"""Prepare exact source-bound identity repairs. No database writer or AWS client.

Canonical keys are legacy schema305 nominations, not C2 migration approvals.
Historical recipe/log snapshots must stay unchanged and need separate review.
"""
import argparse
import copy
import csv
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
from importlib.util import module_from_spec, spec_from_file_location


def module(name, file):
    spec = spec_from_file_location(name, Path(__file__).with_name(file))
    result = module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


names = module('identity_names', 'prepare-r6-held-names.py')
sha, fingerprint = names.held.everyday.sha, names.held.everyday.fingerprint
ROOT = Path(__file__).resolve().parents[1]
PREFIX = 'GLOBAL:GENERIC_INGREDIENT:'
IDENTITIES = {
    '49682': {'fdcId': 171443, 'sourceDescription': 'Mayonnaise, reduced fat, with olive oil',
              'state': 'PREPARED', 'old': PREFIX + 'PREPARED:olive_oil',
              'new': PREFIX + 'PREPARED:reduced_fat_mayonnaise_with_olive_oil'},
    '181002': {'fdcId': 171491, 'sourceDescription': 'Turkey, all classes, light meat, cooked, roasted',
               'state': 'ROASTED', 'old': PREFIX + 'ROASTED:turkey_breast_all_classes',
               'new': PREFIX + 'ROASTED:turkey_light_meat_all_classes'},
}
ALLOWED = {'name', 'display_name', 'short_display_name', 'canonical_food_key'}


def validate_dependencies(metrics):
    required = {'identityDependencies', 'identityKeyClaims', 'identityKeyIndexes', 'canonicalResolutions', 'historyLinks'}
    if not required <= metrics.keys():
        raise ValueError('Complete inbound FK, canonical-resolution and history evidence required')
    refs = metrics['identityDependencies']
    if not refs or len({(r['schema'], r['constraint']) for r in refs}) != len(refs):
        raise ValueError('Missing/duplicate inbound reference inventory')
    import re
    for r in refs:
        if (r['supported'] is not True or r['schema'] != 'public' or not re.fullmatch('[a-z][a-z0-9_]*', r['table'])
                or not re.fullmatch('[a-z][a-z0-9_]*', r['column']) or not str(r['count']).isdigit()
                or not re.fullmatch('[a-f0-9]{32}', r['fingerprint'])):
            raise ValueError('Unsupported or incomplete inbound reference proof')
    if {r['id'] for r in metrics['historyLinks']} != {49682, 181002} or len(metrics['historyLinks']) != 2:
        raise ValueError('Exact per-product history counts required')
    for table, field in (('food_logs', 'logs'), ('recipe_ingredients', 'recipes')):
        measured = [r for r in refs if r['table'] == table]
        if len(measured) != 1 or int(measured[0]['count']) != sum(r[field] for r in metrics['historyLinks']):
            raise ValueError('Aggregate/per-product history mismatch')
    targets = {v['new'] for v in IDENTITIES.values()}
    if any(r['canonical_food_key'] in targets for r in metrics['identityKeyClaims']):
        raise ValueError('Target key already claimed: no automatic merge')
    for identifier, expected in IDENTITIES.items():
        claims = [r for r in metrics['identityKeyClaims'] if r['source_key'] == 'USDA_FOODDATA:fdc:' + str(expected['fdcId'])]
        if len(claims) != 1 or str(claims[0]['id']) != identifier or claims[0]['canonical_food_key'] != expected['old']:
            raise ValueError('Source key duplicated or current key drifted')
    # The oil winner is a separate valid source profile and must not be moved.
    resolutions = metrics['canonicalResolutions']
    if resolutions != [{'canonical_food_key': IDENTITIES['49682']['old'], 'primary_food_item_id': 179890}]:
        raise ValueError('Canonical resolution needs renewed explicit review')


def validate_plan(plan):
    if (plan.get('version') != 'R6_TWO_IDENTITY_REPAIR_PREPARATION_V1' or plan.get('liveApplyAllowed') is not False
            or plan.get('approval') is not None or plan.get('backup') is not None or plan.get('schemaVersion') != '305'
            or len(plan['rows']) != 2 or {r['id'] for r in plan['rows']} != set(IDENTITIES)):
        raise ValueError('Exact two review-only identity repairs required')
    validate_dependencies(plan['dependencyBefore'])
    registered = {r['id']: r for r in names.held.read_json(names.REGISTRY)['entries']}
    for row in plan['rows']:
        expected = IDENTITIES[row['id']]
        food = row['expectedBefore']['food']
        if (row['sourceKey'] != 'USDA_FOODDATA:fdc:' + str(expected['fdcId']) or food['source_key'] != row['sourceKey']
                or str(food['id']) != row['id'] or row['sourceDescription'] != expected['sourceDescription']
                or food['canonical_food_key'] != expected['old'] or food['preparation_state'] != expected['state']
                or food['catalog_type'] != 'GENERIC_INGREDIENT' or food['data_source'] != 'USDA_FOODDATA'
                or food['is_custom'] or food['publication_status'] != 'PUBLISHED'
                or row['beforeSha256'] != fingerprint(row['expectedBefore'])
                or not set(row['foodChanges']) <= ALLOWED
                or row['foodChanges'].get('canonical_food_key') != expected['new']
                or row['proposedNames'] != {'EN': registered[row['id']]['en'], 'TR': registered[row['id']]['tr']}):
            raise ValueError('Unapproved identity/profile/source change')
        if any(row['foodChanges'].get(k) != row['proposedNames']['EN'] for k in ALLOWED - {'canonical_food_key'}):
            raise ValueError('Exact source-qualified name fields required')
        if any(a['action'] != 'UPDATE_EXACT_ACTIVE_LOCALIZATION' for a in row['localizationActions']):
            raise ValueError('Only captured active EN/TR localization updates allowed')
        if len(row['localizationActions']) != 2 or {a['before']['language'] for a in row['localizationActions']} != {'EN', 'TR'}:
            raise ValueError('Exact active localization pair required')
        for a in row['localizationActions']:
            if (a['before'] not in row['expectedBefore']['name_review']['localizations'] or not a['before']['active']
                    or a['before']['food_item_id'] != int(row['id'])
                    or a['changes'] != {k: row['proposedNames'][a['before']['language']] for k in ('display_name', 'short_display_name')}):
                raise ValueError('Localization content or ownership drift')
        deactivated = []
        for a in row['aliasActions']:
            if a['action'] == 'DEACTIVATE_EXACT_MISLEADING_ALIAS':
                if a['before'] not in row['expectedBefore']['name_review']['aliases'] or not a['before']['active']:
                    raise ValueError('Captured active alias required')
                deactivated.append(a['before']['id'])
            elif a['action'] == 'ADD_SOURCE_QUALIFIED_ALIAS':
                if (a['language'] not in {'EN', 'TR'} or a['alias'] != row['proposedNames'][a['language']]
                        or a['normalized_alias'] != names.aliases.normalize_alias(a['alias'])):
                    raise ValueError('Exact source-qualified alias required')
            else:
                raise ValueError('Unknown alias action')
        if sorted(deactivated) != ([401, 402] if row['id'] == '49682' else [2524]):
            raise ValueError('Only three reviewed misleading aliases may be deactivated')
        additions = [a for a in row['aliasActions'] if a['action'].startswith('ADD')]
        if len(additions) != 2 or {a['language'] for a in additions} != {'EN', 'TR'}:
            raise ValueError('Exact two alias additions required')


def build(args):
    now = datetime.now(timezone.utc)
    selected = [json.loads(line) for line in args.selection.read_text(encoding='utf-8').splitlines()]
    registered = {r['id']: r for r in names.held.read_json(names.REGISTRY)['entries']}
    if len(selected) != 2 or {r['id'] for r in selected} != {49682, 181002}:
        raise ValueError('Exact identity/alias selection required')
    for selection in selected:
        decision = registered[str(selection['id'])]
        expected = [{'language': lang, 'normalized_alias': names.aliases.normalize_alias(decision[key])}
                    for lang, key in (('EN', 'en'), ('TR', 'tr'))]
        if selection['requestedAliases'] != expected:
            raise ValueError('Alias collision lookup must cover the proposed exact EN/TR labels')
    proof = names.held.read_json(args.current / 'snapshot-proof.json')
    path = args.current / 'catalog.csv'
    if (proof.get('scope') != 'r6-identities' or proof.get('origin') != 'AWS_READ_ONLY_SNAPSHOT'
            or proof.get('transactionReadOnly') is not True or proof.get('exitCode') != 0 or proof.get('rows') != 2
            or proof.get('schemaVersion') != '305' or proof.get('sha256') != sha(path)
            or proof.get('decisionsSha256') != sha(args.selection)
            or proof.get('sqlSha256') != hashlib.sha256(names.collector.identity_review_sql(args.selection).encode()).hexdigest()
            or not names.held.everyday.fresh(proof['time'], now) or len(proof['metrics']) != 2):
        raise ValueError('Fresh complete query-bound identity snapshot required')
    with path.open(encoding='utf-8-sig', newline='') as stream:
        rows = [{k: v if k == 'id' else json.loads(v) for k, v in r.items()} for r in csv.DictReader(stream)]
    if len(rows) != 2 or {r['id'] for r in rows} != set(IDENTITIES):
        raise ValueError('Two complete identities required')
    validate_dependencies(proof['metrics'][1])
    previous = names.held.read_json(args.names_plan)
    previous_summary = names.held.read_json(args.names_plan.parent / 'summary.json')
    if previous_summary['planSha256'] != sha(args.names_plan):
        raise ValueError('Original name preparation changed')
    bodies, evidence = names.held.source_evidence(args.sources, args.source_queue, now)
    decisions = {r['id']: r for r in names.held.read_json(names.REGISTRY)['entries']}
    proposals = []
    for row in rows:
        identifier = row['id']
        expected, decision = IDENTITIES[identifier], decisions[identifier]
        body = bodies[str(expected['fdcId'])]
        old = next(r for r in previous['rows'] if r['id'] == identifier)
        if (old['expectedBefore'] != row or body['description'] != expected['sourceDescription']
                or fingerprint(body) != old['sourceBodySha256'] or body['dataType'] != 'SR Legacy'
                or decision['en'] != old['proposedNames']['EN'] or decision['tr'] != old['proposedNames']['TR']):
            raise ValueError('Source body/name decision/before-image drift')
        if proof['metrics'][0]['requestedAliasConflicts']:
            raise ValueError('New EN/TR alias collision requires review')
        proposal = copy.deepcopy(old)
        proposal['foodChanges'] = {**old['foodNameChanges'], 'canonical_food_key': expected['new']}
        proposal['canonicalIdentityReview'].update(canonicalKeyWriteAllowed=False, nominatedKey=expected['new'],
                numericProductIdPreserved=True, sourceKeyPreserved=True, oldOilWinnerPreserved=True)
        proposal['status'] = 'IDENTITY_REPAIR_PREPARED_NOT_APPROVED'
        proposal['nameApplicationHolds'] = ['OWNER_APPROVAL_BACKUP_FRESHNESS_AND_HISTORY_RELEASE_REQUIRED']
        proposals.append(proposal)
    plan = {'version': 'R6_TWO_IDENTITY_REPAIR_PREPARATION_V1', 'liveApplyAllowed': False,
            'approval': None, 'backup': None, 'schemaVersion': '305', 'snapshotTime': proof['time'],
            'sourceFetchedAt': evidence['fetchedAt'], 'dependencyBefore': proof['metrics'][1], 'rows': proposals,
            'inputHashes': {'snapshot': sha(path), 'snapshotProof': sha(args.current / 'snapshot-proof.json'),
                'selection': sha(args.selection), 'sources': sha(args.sources / 'evidence.json'),
                'namesPlan': sha(args.names_plan), 'registry': sha(names.REGISTRY), 'producer': sha(Path(__file__))},
            'requiredReleaseGates': ['EXPLICIT_TWO_RECORD_OWNER_APPROVAL', 'CURRENT_ENCRYPTED_RDS_BACKUP',
                'FRESH_SOURCE_AND_BEFORE_IMAGES', 'HISTORY_RECIPE_SEMANTIC_REVIEW_NO_SNAPSHOT_REWRITE',
                'CURRENT_FK_AND_CANONICAL_CLAIMS_WITH_EXACT_LOCKED_COUNTS', 'BOUNDED_MAINTENANCE_WRITE_LOCK_APPROVAL',
                'RECEIPT_BOUND_UNDO', 'CACHE_REVISION_AFTER_COMMIT', 'LIVE_EN_TR_SEARCH_ACCEPTANCE'],
            'forbiddenWrites': ['numeric_ids', 'source_keys', 'nutrition', 'units', 'preparation_state', 'servings',
                'markets', 'categories', 'source_evidence', 'canonical_resolutions', 'history_snapshots', 'merges', 'product_deletes']}
    validate_plan(plan)
    args.output.mkdir(parents=True, exist_ok=False)
    output = args.output / 'identity-repair-plan.json'
    output.write_text(json.dumps(plan, ensure_ascii=False, allow_nan=False, indent=2) + '\n', encoding='utf-8')
    summary = {'status': 'PREPARATION_COMPLETE_NOT_APPROVED', 'products': 2,
            'canonicalKeyChanges': 2, 'foodNameChanges': 2, 'localeUpdates': 4,
            'aliasAdditions': 4, 'misleadingAliasDeactivations': 3,
            'inboundForeignKeysMeasured': len(plan['dependencyBefore']['identityDependencies']),
            'targetKeyCollisions': 0, 'sourceKeyDuplicates': 0, 'oilWinnerPreserved': 179890,
            'historyLinks': plan['dependencyBefore']['historyLinks'],
            'currentEncryptedBackupTaken': False, 'awsDatabaseWrites': 0, 'liveApplyAllowed': False,
            'planSha256': sha(output), 'snapshotTime': proof['time'], 'sourceFetchedAt': evidence['fetchedAt']}
    (args.output / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(summary, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    for key in ('current', 'selection', 'names-plan', 'sources', 'source-queue', 'output'):
        parser.add_argument('--' + key, type=Path, required=True)
    build(parser.parse_args())

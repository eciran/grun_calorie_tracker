"""Source-backed Feta alias disposition and isolated apply/undo rehearsal.

Two distinct FDC profiles stay separate. No AWS writer or production SQL.
Only the SR Legacy record's Turkish locale and new Turkish alias are nominated.
"""
import argparse
import copy
import csv
import hashlib
import json
import subprocess
import time
import uuid
from collections import Counter
from datetime import datetime, timedelta, timezone
from pathlib import Path
from importlib.util import module_from_spec, spec_from_file_location


def module(name, filename):
    spec = spec_from_file_location(name, Path(__file__).with_name(filename))
    value = module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


names = module('feta_names', 'prepare-r6-held-names.py')
proof = module('feta_receipts', 'rehearse-r6-held-names.py')
sha, fingerprint = names.held.everyday.sha, names.held.everyday.fingerprint
text, literal = proof.text, proof.sql_json
SOURCES = {'180991': ('173420', 'Cheese, feta', 'SR Legacy'),
           '179893': ('2259796', 'Cheese, feta, whole milk, crumbled', 'Foundation')}


def validate(plan):
    if (plan.get('version') != 'R6_FETA_ALIAS_PREPARATION_V1' or plan.get('liveApplyAllowed') is not False
            or plan.get('schemaVersion') != '305' or plan.get('approval') is not None or plan.get('backup') is not None
            or len(plan['rows']) != 2 or {r['id'] for r in plan['rows']} != set(SOURCES)):
        raise ValueError('Exact two-record, review-only Feta plan required')
    for row in plan['rows']:
        identifier, description, subtype = SOURCES[row['id']]
        before = row['expectedBefore']
        food = before['food']
        if (before['id'] != row['id'] or str(food['id']) != row['id'] or food['source_key'] != 'USDA_FOODDATA:fdc:' + identifier
                or row['sourceDescription'] != description or row['sourceType'] != subtype
                or food['data_source'] != 'USDA_FOODDATA' or food['catalog_type'] != 'GENERIC_INGREDIENT'
                or food['is_custom'] or food['publication_status'] != 'PUBLISHED'
                or food['nutrition_reference_unit'] != 'PER_100G' or row['beforeSha256'] != fingerprint(before)
                or food['name'] != ('Feta Cheese' if row['id'] == '180991' else description)
                or food['preparation_state'] != ('UNSPECIFIED' if row['id'] == '180991' else 'PREPARED')
                or row['foodNameChanges']):
            raise ValueError('Source/identity/publication drift or forbidden food write')
        if row['id'] == '179893':
            if row['aliasActions'] or row['localizationActions']:
                raise ValueError('Foundation variant is protected, never merged or modified')
            continue
        if len(row['localizationActions']) != 1 or len(row['aliasActions']) != 1:
            raise ValueError('One exact Turkish localization and alias required')
        action = row['localizationActions'][0]
        locale = action['before']
        if (action['action'] != 'UPDATE_EXACT_ACTIVE_LOCALIZATION' or locale['id'] != 4385
                or locale['food_item_id'] != 180991 or locale['language'] != 'TR' or not locale['active']
                or locale not in before['name_review']['localizations']
                or action['changes'] != {'display_name': 'Feta Peyniri', 'short_display_name': 'Feta Peyniri'}):
            raise ValueError('Exact captured Turkish localization required')
        if row['aliasActions'][0] != {'action': 'ADD_SOURCE_QUALIFIED_ALIAS', 'language': 'TR',
                                     'alias': 'Feta Peyniri', 'normalized_alias': 'feta peyniri'}:
            raise ValueError('No English alias overwrite/reactivation/deactivation allowed')
        if names.already_searchable_exact_label(food, before['name_review'], 'EN', 'Feta Cheese') is not True:
            raise ValueError('English direct-search proof no longer valid')
    foundation = next(r for r in plan['rows'] if r['id'] == '179893')['expectedBefore']
    expected_external = [a for a in foundation['name_review']['aliases'] if a['id'] == 807]
    if (len(expected_external) != 1 or expected_external[0]['food_item_id'] != 179893
            or not expected_external[0]['active'] or expected_external[0]['language'] != 'EN'
            or expected_external[0]['normalized_alias'] != 'feta cheese'
            or plan['aliasClaimsBefore'] != expected_external):
        raise ValueError('Exact existing external alias inventory required')


def build(args):
    now = datetime.now(timezone.utc)
    selection = args.selection / 'selected-ids.jsonl'
    selected = [json.loads(line) for line in selection.read_text(encoding='utf-8').splitlines()]
    if len(selected) != 2 or {r['id'] for r in selected} != {180991, 179893} or any(r['requestedAliases'] != [
            {'language': 'EN', 'normalized_alias': 'feta cheese'}, {'language': 'TR', 'normalized_alias': 'feta peyniri'}] for r in selected):
        raise ValueError('Exact bounded Feta alias query required')
    snapshot = names.held.read_json(args.current / 'snapshot-proof.json')
    catalog = args.current / 'catalog.csv'
    if (snapshot.get('scope') != 'r6-names' or snapshot.get('origin') != 'AWS_READ_ONLY_SNAPSHOT'
            or snapshot.get('transactionReadOnly') is not True or snapshot.get('exitCode') != 0
            or snapshot.get('schemaVersion') != '305' or snapshot.get('rows') != 2
            or snapshot.get('sha256') != sha(catalog) or snapshot.get('decisionsSha256') != sha(selection)
            or snapshot.get('sqlSha256') != hashlib.sha256(names.collector.name_review_sql(selection).encode()).hexdigest()
            or not names.held.everyday.fresh(snapshot['time'], now) or len(snapshot['metrics']) != 1):
        raise ValueError('Fresh complete Feta before-images/query proof required')
    with catalog.open(encoding='utf-8-sig', newline='') as stream:
        rows = [{k: v if k == 'id' else json.loads(v) for k, v in r.items()} for r in csv.DictReader(stream)]
    if len(rows) != 2 or {r['id'] for r in rows} != set(SOURCES):
        raise ValueError('Feta cohort drift')
    bodies, evidence = names.held.source_evidence(args.sources, args.selection / 'source-queue.csv', now)
    for row in rows:
        identifier, description, subtype = SOURCES[row['id']]
        if bodies[identifier]['description'] != description or bodies[identifier]['dataType'] != subtype:
            raise ValueError('Source identity/variant changed')
    registry = names.held.read_json(names.REGISTRY)
    decision = next(r for r in registry['entries'] if r['id'] == '180991')
    old_plan = names.held.read_json(args.previous_plan)
    old_summary = names.held.read_json(args.previous_plan.parent / 'summary.json')
    if old_summary['planSha256'] != sha(args.previous_plan):
        raise ValueError('Historical held disposition changed')
    previous = next(r for r in old_plan['rows'] if r['id'] == '180991')
    plain = next(r for r in rows if r['id'] == '180991')
    body = bodies['173420']
    review = names.audit(plain, decision, body, {'sourceBodySha256': fingerprint(body),
                         'holds': previous['independentDataPrerequisites']}, snapshot['metrics'][0]['requestedAliasConflicts'])
    if review['nameApplicationHolds'] or len(review['redundantAliasesOmitted']) != 1:
        raise ValueError('Alias ambiguity is not safely resolved')
    review['sourceType'] = 'SR Legacy'
    protected = next(r for r in rows if r['id'] == '179893')
    companion = {'id': '179893', 'expectedBefore': protected, 'beforeSha256': fingerprint(protected),
                 'sourceDescription': SOURCES['179893'][1], 'sourceType': 'Foundation',
                 'sourceBodySha256': fingerprint(bodies['2259796']), 'foodNameChanges': {},
                 'aliasActions': [], 'localizationActions': [], 'status': 'DISTINCT_SOURCE_VARIANT_PRESERVED'}
    comparisons = {r['id']: names.held.gate.comparison(r['food'], names.held.gate.all_usda_fields(bodies[SOURCES[r['id']][0]]),
                   'USDA_FOODDATA') for r in rows}
    differences = {field: {r['id']: r['food'].get(field) for r in rows}
                   for field in names.held.gate.FIELDS if plain['food'].get(field) != protected['food'].get(field)}
    plan = {'version': 'R6_FETA_ALIAS_PREPARATION_V1', 'liveApplyAllowed': False, 'approval': None, 'backup': None,
            'schemaVersion': '305', 'snapshotTime': snapshot['time'], 'sourceFetchedAt': evidence['fetchedAt'],
            'rows': [companion, review], 'aliasClaimsBefore': snapshot['metrics'][0]['requestedAliasConflicts'],
            'nutritionComparisons': comparisons, 'currentProfileDifferences': differences,
            'disposition': 'KEEP_DISTINCT_SOURCE_VARIANTS_SKIP_REDUNDANT_EN_ALIAS',
            'mergeAllowed': False, 'deleteAllowed': False,
            'variantPresentationFollowup': {'id': 179893, 'sourceDescription': SOURCES['179893'][1],
                'sourceBodySha256': fingerprint(bodies['2259796']), 'enNomination': 'Feta Cheese (Whole Milk, Crumbled)',
                'trNomination': 'Tam Yağlı Sütten Ufalanmış Feta Peyniri',
                'status': 'R4_IDENTITY_AND_PREPARATION_REVIEW_BEFORE_NAME_CHANGE', 'applyAllowed': False,
                'reason': 'Existing Prepared Feta Cheese display omits source composition/form; do not silently rekey it.'},
            'requiredReleaseGates': ['EXPLICIT_OWNER_APPROVAL', 'FRESH_ENCRYPTED_BACKUP_AND_CURRENT_BEFORE_IMAGES',
                'GLOBAL_ALIAS_LOOKUP_UNDER_BOUNDED_WRITE_LOCK', 'RECEIPT_BOUND_UNDO',
                'AFTER_COMMIT_CACHE_REVISION', 'AUTHENTICATED_LIVE_EN_TR_SEARCH'],
            'forbiddenWrites': ['food_fields', 'canonical_keys', 'source_keys', 'nutrition', 'preparation', 'units',
                'portions', 'categories', 'markets', 'source_evidence', 'history', 'external_aliases', 'merges', 'deletes'],
            'inputHashes': {'snapshot': sha(catalog), 'proof': sha(args.current / 'snapshot-proof.json'),
                'sourceEvidence': sha(args.sources / 'evidence.json'), 'previousPlan': sha(args.previous_plan),
                'registry': sha(names.REGISTRY), 'nameRules': sha(Path(__file__).with_name('prepare-r6-held-names.py')),
                'receiptRules': sha(Path(__file__).with_name('rehearse-r6-held-names.py')), 'producer': sha(Path(__file__))}}
    validate(plan)
    args.output.mkdir(parents=True, exist_ok=False)
    path = args.output / 'feta-alias-plan.json'
    path.write_text(json.dumps(plan, ensure_ascii=False, indent=2, allow_nan=False) + '\n', encoding='utf-8')
    summary = {'status': 'ALIAS_AMBIGUITY_PREPARATION_RESOLVED_NOT_APPLIED', 'sourceProfiles': 2, 'changedProductsNominated': 1,
            'existingExternalAlias807Preserved': True, 'redundantEnglishAliasNotAdded': True,
            'turkishLocaleUpdates': 1, 'newTurkishAliases': 1, 'foodFieldUpdates': 0,
            'sourceKeyUpdates': 0, 'canonicalKeyUpdates': 0, 'merges': 0, 'deletes': 0,
            'numericFieldsComparedPerProfile': len(names.held.gate.FIELDS),
            'numericComparisonStates': dict(Counter(v['state'] for r in comparisons.values() for v in r.values())),
            'differingCurrentProfileFields': sorted(differences), 'liveApplyAllowed': False, 'awsDataWrites': 0,
            'snapshotTime': snapshot['time'], 'sourceFetchedAt': evidence['fetchedAt'], 'planSha256': sha(path)}
    (args.output / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(summary, indent=2))


def claim_expression():
    return "coalesce((SELECT jsonb_agg(to_jsonb(a) ORDER BY a.food_item_id,a.id) FROM food_item_search_aliases a WHERE a.active AND (a.language,a.normalized_alias) IN (('EN','feta cheese'),('TR','feta peyniri'))),'[]'::jsonb)"


def prefix_guard(sql, owner, expires, claims):
    prefix = proof.fixture.prepare.prefix(owner, expires)
    if not sql.startswith(prefix):
        raise ValueError('Owned isolated prefix required')
    guard = "LOCK TABLE food_item_search_aliases IN SHARE ROW EXCLUSIVE MODE;\n"
    guard += "DO $$ BEGIN IF (SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1) IS DISTINCT FROM '305' THEN RAISE EXCEPTION 'Schema drift'; END IF; END $$;\n"
    guard += f"DO $$ BEGIN IF {claim_expression()} IS DISTINCT FROM {literal(claims)} THEN RAISE EXCEPTION 'Global Feta alias claims changed'; END IF; END $$;\n"
    return prefix + guard + sql[len(prefix):]


def apply_sql(plan, owner, expires):
    validate(plan)
    return prefix_guard(proof.apply_rows(plan['rows'], owner, expires), owner, expires, plan['aliasClaimsBefore'])


def verify(plan, receipts, owner):
    validate(plan)
    proof.verify_rows(plan['rows'], receipts, owner)


def undo_sql(plan, receipts, owner, expires):
    verify(plan, receipts, owner)
    added = [a for r in receipts for a in r['name_review']['aliases']
             if a['id'] not in {v['id'] for v in next(p['expectedBefore']['name_review']['aliases'] for p in plan['rows'] if p['id'] == r['id'])}]
    claims = sorted(plan['aliasClaimsBefore'] + added, key=lambda a: (a['food_item_id'], a['id']))
    return prefix_guard(proof.undo_rows(plan['rows'], receipts, owner, expires), owner, expires, claims)


def rehearse(args):
    plan = names.held.read_json(args.plan)
    summary = names.held.read_json(args.plan.parent / 'summary.json')
    if summary['planSha256'] != sha(args.plan) or any(plan['inputHashes'][key] != sha(Path(__file__).with_name(file))
            for key, file in [('producer', Path(__file__).name), ('nameRules', 'prepare-r6-held-names.py'),
                              ('receiptRules', 'rehearse-r6-held-names.py')]):
        raise ValueError('Feta plan/rules changed')
    validate(plan)
    rows = [r['expectedBefore'] for r in plan['rows']]
    projected = copy.deepcopy(rows)
    for row in projected:
        row['assignments'] = []
    categories = {c['slug']: c for c in names.held.read_json(args.categories)['metrics'][0]['categories']}
    sql = proof.fixture.fixture_sql({'currentRows': projected, 'categories': categories})
    assignments = [{k: v for k, v in a.items() if k not in {'slug', 'active'}} for row in rows for a in row['assignments']]
    sql += f'INSERT INTO food_item_categories SELECT * FROM jsonb_populate_recordset(NULL::food_item_categories,{literal(assignments)});\n'
    for table in ('food_item_search_aliases', 'food_item_localizations'):
        sql += f"CREATE SEQUENCE {table}_proof_id; SELECT setval('{table}_proof_id',(SELECT max(id)+1 FROM {table}),false); ALTER TABLE {table} ALTER COLUMN id SET DEFAULT nextval('{table}_proof_id');\n"
    sql += "CREATE TABLE flyway_schema_history(version text,success boolean,installed_rank integer); INSERT INTO flyway_schema_history VALUES('305',true,1);\n"
    owner = 'r6-rehearsal-' + uuid.uuid4().hex[:12]
    expires = (datetime.now(timezone.utc) + timedelta(hours=1)).isoformat()
    args.output.mkdir(parents=True, exist_ok=False)
    container, checks = None, []

    def docker(*command, stdin=None, fail=False):
        result = subprocess.run(['docker', *command], input=stdin.encode('utf-8') if stdin is not None else None,
                                capture_output=True, timeout=120)
        if (not fail and result.returncode) or (fail and not result.returncode):
            raise ValueError('Owned Feta rehearsal failed: ' + result.stderr.decode('utf-8', errors='replace')[-1400:])
        return result.stdout.decode('utf-8')

    def execute(sql, fail=False, database='r6_rehearsal', owned=True):
        marker = f"SELECT set_config('grun.rehearsal.owner',{text(owner)},false);\n" if owned else ''
        return docker('exec', '-i', container, 'psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-U', 'postgres', '-d', database, stdin=marker + sql, fail=fail)

    def state():
        return next(json.loads(line) for line in execute(proof.fixture.state_sql()).splitlines() if line.startswith('{'))

    def reject(sql, label, **kwargs):
        before = state()
        execute(sql, fail=True, **kwargs)
        if state() != before:
            raise ValueError('Rejected Feta patch mutated tables')
        checks.append(label)

    try:
        container = docker('run', '-d', '--label', 'grun.test.owner=' + owner, '--tmpfs', '/var/lib/postgresql',
            '-e', 'POSTGRES_HOST_AUTH_METHOD=trust', '-e', 'POSTGRES_DB=r6_rehearsal', proof.fixture.IMAGE).strip()
        for _ in range(40):
            if subprocess.run(['docker', 'exec', container, 'pg_isready', '-U', 'postgres'], capture_output=True, timeout=10).returncode == 0:
                break
            time.sleep(.5)
        else:
            raise ValueError('Owned PostgreSQL startup timed out')
        execute(sql)
        if execute('SHOW server_version_num;').splitlines()[-1] != '180003':
            raise ValueError('PG18.3 required')
        before = state()
        apply = apply_sql(plan, owner, expires)
        reject(apply, 'wrong_session_rejected', owned=False)
        reject(apply, 'wrong_database_rejected', database='postgres')
        reject(apply_sql(plan, owner, (datetime.now(timezone.utc) - timedelta(minutes=1)).isoformat()), 'expired_package_rejected')
        execute("INSERT INTO food_item_search_aliases(food_item_id,alias,normalized_alias,language,alias_type,source,active) VALUES(9000000,'Feta Peyniri','feta peyniri','TR','ADMIN_MANUAL','collision',true);")
        reject(apply, 'new_third_product_alias_collision_rejected')
        execute("DELETE FROM food_item_search_aliases WHERE source='collision';")
        output = execute(apply)
        receipts = next(json.loads(line)['rows'] for line in output.splitlines() if line.startswith('{') and json.loads(line).get('kind') == 'receipts')
        verify(plan, receipts, owner)
        after = state()
        if any(after[t] != before[t] for t in proof.fixture.TABLES if t not in {'food_item_search_aliases', 'food_item_localizations'}):
            raise ValueError('Source/food/portion/category modified')
        checks.append('one_tr_locale_one_tr_alias_no_food_profile_writes')
        protected = next(r['expectedBefore'] for r in plan['rows'] if r['id'] == '179893')
        if next(r for r in receipts if r['id'] == '179893') != protected:
            raise ValueError('Distinct Foundation variant/external alias modified')
        checks.append('foundation_profile_alias807_and_category_preserved')
        reject(apply, 'repeat_apply_rejected_atomically')
        undo = undo_sql(plan, receipts, owner, expires)
        execute('UPDATE food_items SET protein=protein+1 WHERE id=179893;')
        reject(undo, 'later_counterpart_nutrition_blocks_undo')
        execute('UPDATE food_items SET protein=protein-1 WHERE id=179893;')
        execute("UPDATE food_item_search_aliases SET alias='Modified external alias' WHERE id=807;")
        reject(undo, 'external_alias_drift_blocks_undo')
        execute("UPDATE food_item_search_aliases SET alias='feta cheese' WHERE id=807;")
        execute(undo)
        if state() != before:
            raise ValueError('Feta undo did not restore every table exactly')
        checks.append('receipt_bound_undo_restores_exact_tables')
        result = {'status': 'PASS', 'postgresVersion': '18.3', 'checks': checks, 'changedProducts': 1,
                  'protectedCounterpart': 179893, 'planSha256': summary['planSha256'], 'awsDataWrites': 0,
                  'liveApplyAllowed': False, 'fullRdsRestoreOrConcurrencyCertification': False}
        for filename, value in [('proof.json', result), ('receipts.json', receipts)]:
            (args.output / filename).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        (args.output / 'rehearsal-only-apply.sql').write_text(apply, encoding='utf-8')
        (args.output / 'rehearsal-only-undo.sql').write_text(undo, encoding='utf-8')
        print(json.dumps(result, indent=2))
    finally:
        if container:
            if docker('inspect', '--format', '{{index .Config.Labels "grun.test.owner"}}', container).strip() != owner:
                raise ValueError('Owned fixture cleanup refused')
            docker('rm', '-f', container)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--rehearse', action='store_true')
    parser.add_argument('--output', type=Path, required=True)
    for key in ('selection', 'current', 'sources', 'previous-plan', 'plan', 'categories'):
        parser.add_argument('--' + key, type=Path)
    args = parser.parse_args()
    rehearse(args) if args.rehearse else build(args)

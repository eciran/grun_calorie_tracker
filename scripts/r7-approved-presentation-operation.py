"""Exact five-product metadata operation, guarded apply/undo, owned PG proof.

No nutrition, source, serving or user-history writes. Local rehearsal is a
snapshot-backed catalogue projection, not a full RDS restoration certificate.
"""
import argparse
import base64
import copy
import gzip
import json
import re
import subprocess
import time
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

from importlib.util import module_from_spec, spec_from_file_location


def module(name, file):
    spec = spec_from_file_location(name, Path(__file__).with_name(file))
    value = module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


review = module('r7_review', 'prepare-r7-source-presentation.py')
ops = module('r7_ops', 'r6-approved-catalog-operation.py')
fixture = module('r7_fixture', 'rehearse-r6-category-operation.py')
typed = module('r7_typed', 'rehearse-r5-common-readiness.py')
deploy = module('r7_deploy', 'deploy-common-product-release.py')
ROOT = Path(__file__).resolve().parents[1]
SCOPE = {'49340', *review.SCOPE}
sha, literal, text, write, aws = review.sha, ops.literal, ops.text, ops.write, ops.aws
ALLOWED = {'name', 'display_name', 'short_display_name', 'catalog_type', 'source_data_type',
           'food_concept_key', 'composition_variant_key', 'canonical_food_key'}
SQL_PATH = ROOT / 'scripts/sql/export-r7-presentation-dependencies.sql'


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def validate(plan, meta):
    if (plan['version'] != 'R7_SOURCE_PRESENTATION_REVIEW_V1' or len(plan['rows']) != 5
            or {r['id'] for r in plan['rows']} != SCOPE or plan['nutritionWrites'] != 0
            or plan['mlConversions'] != 0 or plan['physicalMerges'] != 0):
        raise ValueError('Exact five-product preserved-nutrition scope required')
    for file, digest in plan['inputHashes'].items():
        if sha(Path(file)) != digest:
            raise ValueError('Bound source/reviewer/snapshot changed')
    if (meta['resolutions'] or len(meta['keyClaims']) != 5
            or {str(r['id']) for r in meta['keyClaims']} != SCOPE):
        raise ValueError('Existing resolution or external identity claimant')
    if (len(meta['dependencies']) != 26 or any(r['supported'] is not True or r['schema'] != 'public'
            or not re.fullmatch('[a-z][a-z0-9_]*', r['table'])
            or not re.fullmatch('[a-z][a-z0-9_]*', r['column']) for r in meta['dependencies'])):
        raise ValueError('Reviewed complete, safe FK inventory required')
    taxonomy = {r['slug']: r for r in meta['categories']}
    if {s: (taxonomy[s]['id'], taxonomy[s]['parent_id'], taxonomy[s]['active'])
            for s in ('beverages', 'vegetables', 'poultry')} != {
                'beverages': (9, None, True), 'vegetables': (14, 1, True), 'poultry': (15, 2, True)}:
        raise ValueError('Approved active category references changed')
    for r in plan['rows']:
        before = r['expectedBefore']
        if before['assignments'] or before['food']['id'] != int(r['id']):
            raise ValueError('Existing category or owner drift')
        changes = {**r['foodChanges'], **(r['identityProposal'] or {})}
        if not set(changes) <= ALLOWED or set(changes) & set(review.gate.FIELDS):
            raise ValueError('Unapproved parent field')
        if r['id'] in review.SCOPE:
            food = before['food']
            if (r['sourceKey'] != 'USDA_FOODDATA:fdc:' + review.SCOPE[r['id']]
                    or changes['catalog_type'] != 'GENERIC_INGREDIENT'
                    or changes['source_data_type'] != 'SR Legacy'
                    or changes['composition_variant_key'] != 'fdc_' + review.SCOPE[r['id']]
                    or any(food.get(k) is not None for k in ('barcode', 'normalized_barcode', 'brand', 'brand_id',
                        'food_concept_key', 'composition_variant_key', 'canonical_food_key'))):
                raise ValueError('Reviewed generic identity drift')
        elif changes or r['sourceKey'] != 'barcode:8696368000237':
            raise ValueError('OFF parent must remain intact')
    if (sum(len(r['foodChanges']) for r in plan['rows']) != 20
            or sum(len(r['identityProposal'] or {}) for r in plan['rows']) != 12
            or sum(len(r['aliasActions']) for r in plan['rows']) != 9
            or sum(len(r['localizationActions']) for r in plan['rows']) != 10):
        raise ValueError('Exact reviewed operation counts changed')


def dependency_function():
    return 'CREATE OR REPLACE FUNCTION pg_temp.r7_dependencies() RETURNS jsonb LANGUAGE sql AS $refs$\n' + SQL_PATH.read_text(encoding='utf-8') + '\n$refs$;\n'


def guard(package, meta, rows):
    owner, digest = package['operation'], package['planDigest']
    if not re.fullmatch('r7-approved-[a-f0-9]{12}', owner) or not re.fullmatch('[a-f0-9]{64}', digest):
        raise ValueError('Exact owner/digest required')
    tables = sorted({'food_items', 'food_categories', 'food_canonical_resolutions',
                     'food_item_serving_option_localizations'} | {r['table'] for r in meta['dependencies']})
    if any(not re.fullmatch('[a-z][a-z0-9_]*', t) for t in tables):
        raise ValueError('Unsafe lock namespace')
    return f"""BEGIN ISOLATION LEVEL READ COMMITTED;
SET LOCAL lock_timeout='3s'; SET LOCAL statement_timeout='90s';
DO $$ BEGIN
 IF current_database()<>'grun_calorie_db'
 OR current_setting('grun.r7.operation',true) IS DISTINCT FROM {text(owner)}
 OR current_setting('grun.r7.plan',true) IS DISTINCT FROM {text(digest)}
 THEN RAISE EXCEPTION 'Exact approved database/session/plan required'; END IF;
 IF now()>{text(package['expiresAt'])}::timestamptz THEN RAISE EXCEPTION 'Package expired'; END IF;
 IF (SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1) IS DISTINCT FROM '307'
 THEN RAISE EXCEPTION 'Schema drift'; END IF;
END $$;
SELECT pg_advisory_xact_lock(17491,310);
LOCK TABLE {','.join('public.'+t for t in tables)} IN SHARE ROW EXCLUSIVE MODE;
""" + dependency_function() + f"""DO $$ BEGIN
 IF pg_temp.r7_dependencies()->'values' IS DISTINCT FROM {literal(meta)}
 THEN RAISE EXCEPTION 'FK, identity, category or index namespace drift'; END IF;
END $$;
""" + ops.name_before(rows)


def apply_sql(plan, meta, package):
    validate(plan, meta)
    sql = guard(package, meta, [r['expectedBefore'] for r in plan['rows']])
    taxonomy = {c['slug']: c for c in meta['categories']}
    for r in plan['rows']:
        identifier = int(r['id'])
        fields = {**r['foodChanges'], **(r['identityProposal'] or {})}
        if fields:
            sql += f"UPDATE food_items SET {','.join(k+'='+text(v) for k,v in fields.items())} WHERE id={identifier};\n"
        for action in r['localizationActions']:
            if action['action'] == 'UPDATE_EXACT_ACTIVE_LOCALIZATION':
                fields = ','.join(k+'='+text(v) for k, v in action['changes'].items())
                sql += f"UPDATE food_item_localizations SET {fields},updated_at=current_timestamp WHERE id={int(action['before']['id'])};\n"
            elif action['action'] == 'ADD_MISSING_LOCALIZATION' and identifier == 49340 and action['language'] == 'EN':
                sql += f"INSERT INTO food_item_localizations(food_item_id,language,display_name,short_display_name,source,active,created_at,updated_at) VALUES({identifier},'EN',{text(action['display_name'])},{text(action['short_display_name'])},{text(package['operation'])},true,current_timestamp,current_timestamp);\n"
            else:
                raise ValueError('Unreviewed localization action')
        for action in r['aliasActions']:
            if action['action'] != 'DEACTIVATE_EXACT_MISLEADING_ALIAS':
                raise ValueError('Unreviewed alias action')
            sql += f"UPDATE food_item_search_aliases SET active=false WHERE id={int(action['before']['id'])};\n"
        category = taxonomy[r['categoryProposal']['targetSlug']]['id']
        sql += f"INSERT INTO food_item_categories(food_item_id,category_id,primary_category,assignment_source,confidence_score,reviewed,created_by,updated_by) VALUES({identifier},{category},true,'ADMIN',100,true,{text(package['operation'])},{text(package['operation'])});\n"
    sql += f"SELECT jsonb_build_object('kind','r7_receipt','owner',{text(package['operation'])},'planDigest',{text(package['planDigest'])},'rows',(SELECT jsonb_agg({ops.name_state()} ORDER BY p.id) FROM r6_plan p),'dependencies',pg_temp.r7_dependencies()->'values');\nCOMMIT;\n"
    return sql


def verify(plan, meta, package, receipt):
    if receipt.get('owner') != package['operation'] or receipt.get('planDigest') != package['planDigest']:
        raise ValueError('Wrong receipt owner/plan')
    actual = {r['id']: r for r in receipt['rows']}
    if len(receipt['rows']) != 5 or set(actual) != SCOPE:
        raise ValueError('Exact five receipts required')
    taxonomy = {c['slug']: c for c in meta['categories']}
    for r in plan['rows']:
        before, after = r['expectedBefore'], actual[r['id']]
        if after['food'] != {**before['food'], **r['foodChanges'], **(r['identityProposal'] or {})}:
            raise ValueError('Unapproved parent/nutrition/source/portion change')
        for key in ('source_tags', 'evidence'):
            if after[key] != before[key]:
                raise ValueError('Source evidence changed')
        old, child = before['name_review'], after['name_review']
        for key in ('options', 'option_localizations'):
            if child[key] != old[key]:
                raise ValueError('Serving/history changed')
        retire = {a['before']['id'] for a in r['aliasActions']}
        if child['aliases'] != [{**a, **({'active': False} if a['id'] in retire else {})} for a in old['aliases']]:
            raise ValueError('Unapproved alias change')
        changes = {a['before']['id']: a['changes'] for a in r['localizationActions'] if 'before' in a}
        locales = {l['id']: l for l in child['localizations']}
        for previous in old['localizations']:
            current = locales.get(previous['id'])
            delta = changes.get(previous['id'], {})
            if current is None or current != {**previous, **delta, **({'updated_at': current['updated_at']} if delta else {})}:
                raise ValueError('Old localization drift')
        additions = [l for l in child['localizations'] if l['id'] not in {p['id'] for p in old['localizations']}]
        expected = [a for a in r['localizationActions'] if a['action'] == 'ADD_MISSING_LOCALIZATION']
        if len(additions) != len(expected):
            raise ValueError('Added locale count drift')
        for l, a in zip(additions, expected):
            if (l['food_item_id'] != int(r['id']) or l['language'] != a['language'] or not l['active']
                    or l['source'] != package['operation'] or l['display_name'] != a['display_name']
                    or l['short_display_name'] != a['short_display_name']):
                raise ValueError('Added locale ownership drift')
        assignment = after['assignments']
        category = taxonomy[r['categoryProposal']['targetSlug']]
        if (len(assignment) != 1 or assignment[0]['category_id'] != category['id']
                or assignment[0]['food_item_id'] != int(r['id']) or assignment[0]['slug'] != category['slug']
                or not assignment[0]['active'] or not assignment[0]['primary_category']
                or not assignment[0]['reviewed'] or assignment[0]['confidence_score'] != 100
                or assignment[0]['assignment_source'] != 'ADMIN'
                or assignment[0]['created_by'] != package['operation'] or assignment[0]['updated_by'] != package['operation']):
            raise ValueError('Category ownership/content drift')
    after = receipt['dependencies']
    if any(after[k] != meta[k] for k in ('categories', 'resolutions', 'indexes')):
        raise ValueError('Taxonomy, resolutions or indexes changed')
    changes = {int(r['id']): r['identityProposal'] or {} for r in plan['rows']}
    if after['keyClaims'] != [{**c, **changes[c['id']]} for c in meta['keyClaims']]:
        raise ValueError('Identity namespace drift')
    previous = {r['constraint']: r for r in meta['dependencies']}
    current = {r['constraint']: r for r in after['dependencies']}
    if set(previous) != set(current):
        raise ValueError('FK inventory changed')
    for key, ref in previous.items():
        updated = current[key]
        if ref['table'] in {'food_item_categories', 'food_item_localizations', 'food_item_search_aliases'}:
            added = 5 if ref['table'] == 'food_item_categories' else 1 if ref['table'] == 'food_item_localizations' else 0
            if (int(updated['count']) != int(ref['count']) + added
                    or any(updated[k] != ref[k] for k in ref if k not in {'count', 'fingerprint'})):
                raise ValueError('Edited dependency count/structure drift')
        elif updated != ref:
            raise ValueError('Protected dependency/history changed')


def undo_sql(plan, meta, package, receipt):
    verify(plan, meta, package, receipt)
    sql = guard(package, receipt['dependencies'], receipt['rows'])
    actual = {r['id']: r for r in receipt['rows']}
    for r in plan['rows']:
        before, after = r['expectedBefore'], actual[r['id']]
        fields = {**r['foodChanges'], **(r['identityProposal'] or {})}
        if fields:
            sql += f"UPDATE food_items SET {','.join(k+'='+(text(before['food'][k]) if before['food'][k] is not None else 'NULL') for k in fields)} WHERE id={int(r['id'])};\n"
        for action in r['localizationActions']:
            if 'before' in action:
                old = action['before']
                sql += f"UPDATE food_item_localizations SET display_name={text(old['display_name'])},short_display_name={text(old['short_display_name'])},updated_at={text(old['updated_at'])}::timestamp WHERE id={int(old['id'])};\n"
            else:
                added = next(l for l in after['name_review']['localizations'] if l['id'] not in {p['id'] for p in before['name_review']['localizations']})
                sql += f"DELETE FROM food_item_localizations WHERE id={int(added['id'])} AND source={text(package['operation'])};\n"
        for action in r['aliasActions']:
            sql += f"UPDATE food_item_search_aliases SET active=true WHERE id={int(action['before']['id'])};\n"
        sql += f"DELETE FROM food_item_categories WHERE food_item_id={int(r['id'])} AND created_by={text(package['operation'])};\n"
        sql += f"DO $$ BEGIN IF {ops.name_state(str(int(r['id'])))} IS DISTINCT FROM {literal(before)} THEN RAISE EXCEPTION 'Exact rollback failed'; END IF; END $$;\n"
    sql += f"DO $$ BEGIN IF pg_temp.r7_dependencies()->'values' IS DISTINCT FROM {literal(meta)} THEN RAISE EXCEPTION 'Dependency rollback failed'; END IF; END $$;\nCOMMIT;\n"
    return sql


def package(plan_path, proof_path, output):
    plan, proof = read(plan_path), read(proof_path)
    meta = proof['metrics'][-1]
    validate(plan, meta)
    now = datetime.now(timezone.utc)
    if not 0 <= (now - datetime.fromisoformat(proof['time'])).total_seconds() <= 3600:
        raise ValueError('One-hour fresh AWS exact snapshot required')
    result = {'version': 'R7_EXACT_PRESENTATION_OPERATION_V1', 'operation': 'r7-approved-' + uuid.uuid4().hex[:12],
              'planDigest': sha(plan_path), 'expiresAt': (now + timedelta(minutes=90)).isoformat(),
              'approval': 'USER_EXPLICIT_IDENTITY_CATEGORY_UNDO_BACKUP_AWS_APPLY_20261013',
              'producerSha256': sha(Path(__file__)), 'dependencySqlSha256': sha(SQL_PATH),
              'planPath': str(plan_path.resolve()), 'proofPath': str(proof_path.resolve()),
              'nutritionWrites': 0, 'servingWrites': 0, 'physicalMerges': 0}
    if output.exists():
        raise ValueError('New operation directory required')
    output.mkdir(parents=True)
    sql = apply_sql(plan, meta, result)
    (output / 'apply.sql').write_text(sql, encoding='utf-8')
    result['applySha256'] = sha(output / 'apply.sql')
    write(output / 'manifest.json', result)
    return result, plan, meta


def load(folder):
    p = read(folder / 'manifest.json')
    if (p['producerSha256'] != sha(Path(__file__)) or p['dependencySqlSha256'] != sha(SQL_PATH)
            or sha(Path(p['planPath'])) != p['planDigest'] or sha(folder / 'apply.sql') != p['applySha256']):
        raise ValueError('Bound producer/SQL/plan changed')
    plan, meta = read(Path(p['planPath'])), read(Path(p['proofPath']))['metrics'][-1]
    if (folder / 'apply.sql').read_text(encoding='utf-8') != apply_sql(plan, meta, p):
        raise ValueError('Rendered SQL differs')
    return p, plan, meta


def rehearse(args):
    p, plan, meta = load(args.package)
    if (args.package / 'rehearsal/proof.json').exists():
        raise ValueError('Preserve previous rehearsal')
    owner = p['operation']
    container = None
    output = args.package / 'rehearsal'
    output.mkdir()
    checks = []
    def docker(*command, stdin=None, fail=False):
        r = subprocess.run(['docker', *command], input=stdin.encode('utf-8') if stdin is not None else None,
                           capture_output=True, timeout=120)
        if (not fail and r.returncode) or (fail and not r.returncode):
            raise ValueError('Owned rehearsal failed: ' + r.stderr.decode('utf-8', errors='replace')[-1400:])
        return r.stdout.decode('utf-8')
    def execute(sql, fail=False, marked=True, database='grun_calorie_db'):
        marker = f"SELECT set_config('grun.r7.operation',{text(owner)},false);SELECT set_config('grun.r7.plan',{text(p['planDigest'])},false);\n" if marked else ''
        return docker('exec', '-i', container, 'psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-U', 'postgres', '-d', database, stdin=marker+sql, fail=fail)
    def frame(sql, **kwargs):
        return next(json.loads(l) for l in execute(sql, **kwargs).splitlines() if l.startswith('{'))
    def state(database='grun_calorie_db'):
        tables = sorted(set(fixture.TABLES) | {r['table'] for r in meta['dependencies']} | {'flyway_schema_history'})
        return frame('SELECT jsonb_build_object(' + ','.join(text(t)+f",(SELECT md5(coalesce(jsonb_agg(to_jsonb(x) ORDER BY to_jsonb(x)::text),'[]'::jsonb)::text) FROM {t} x)" for t in tables) + ');', database=database)
    try:
        container = docker('run', '-d', '--label', 'grun.test.owner='+owner, '--tmpfs', '/var/lib/postgresql',
                           '-e', 'POSTGRES_HOST_AUTH_METHOD=trust', '-e', 'POSTGRES_DB=grun_calorie_db', fixture.IMAGE).strip()
        for _ in range(60):
            r = subprocess.run(['docker', 'exec', container, 'pg_isready', '-U', 'postgres'], capture_output=True)
            if r.returncode == 0:
                break
            time.sleep(.5)
        else:
            raise ValueError('Owned PostgreSQL not ready')
        if execute("SELECT current_setting('server_version_num');").splitlines()[-1] != '180003':
            raise ValueError('PG18.3 parity required')
        rows = copy.deepcopy([r['expectedBefore'] for r in plan['rows']])
        for row in rows:
            for key in ('food_concept_key', 'composition_variant_key', 'source_data_type'):
                row['food'].pop(key)
        sql = fixture.fixture_sql({'currentRows': rows, 'categories': {c['slug']: c for c in meta['categories']}})
        sql += typed.typed_child_constraints()
        sql += (ROOT/'src/main/resources/db/migration/V307__add_reviewed_food_concept_identity.sql').read_text(encoding='utf-8')
        sql += "CREATE INDEX idx_food_items_canonical_food_key ON food_items(canonical_food_key) WHERE canonical_food_key IS NOT NULL;\n"
        sql += "CREATE SEQUENCE r7_locale_ids START 10000000;ALTER TABLE food_item_localizations ALTER COLUMN id SET DEFAULT nextval('r7_locale_ids');\n"
        sql += "CREATE TABLE flyway_schema_history(installed_rank int,version text,success boolean);INSERT INTO flyway_schema_history VALUES(307,'307',true);\n"
        existing = set(fixture.TABLES)
        for table in sorted({r['table'] for r in meta['dependencies']} - existing):
            cols = {r['column'] for r in meta['dependencies'] if r['table'] == table}
            if table == 'food_canonical_resolutions':
                cols.add('canonical_food_key')
            sql += f"CREATE TABLE {table}(id bigint PRIMARY KEY," + ','.join(c+(' text' if c == 'canonical_food_key' else ' bigint REFERENCES food_items(id)') for c in sorted(cols)) + ');\n'
            counts = [int(r['count']) for r in meta['dependencies'] if r['table'] == table]
            if any(counts):
                if table not in {'food_item_market_regions', 'food_product_quality_issues'} or len(cols) != 1:
                    raise ValueError('Unexpected populated private projection dependency')
                column = next(iter(cols))
                sql += f"INSERT INTO {table} SELECT n,49340 FROM generate_series(1,{max(counts)}) n;\n"
        execute(sql)
        # Full private history is not downloaded. Local FK fingerprints are measured
        # independently; the identical production renderer binds the real AWS proofs.
        local_meta = frame(SQL_PATH.read_text(encoding='utf-8'))['values']
        validate(plan, local_meta)
        local = copy.deepcopy(p)
        local_sql = apply_sql(plan, local_meta, local)
        before = state()
        docker('exec', container, 'pg_dump', '-U', 'postgres', '-d', 'grun_calorie_db', '-Fc', '--file=/tmp/r7-before.dump')
        docker('cp', container+':/tmp/r7-before.dump', str((output/'before-cohort.dump').resolve()))
        checks.append('OWNED_CATALOG_PROJECTION_BACKUP')
        def reject(sql, **kwargs):
            old = state()
            execute(sql, fail=True, **kwargs)
            if state() != old:
                raise ValueError('Rejected transaction changed state')
        reject(local_sql, marked=False)
        checks.append('WRONG_SESSION_ATOMIC_REFUSAL')
        expired = copy.deepcopy(local)
        expired['expiresAt'] = '2000-01-01T00:00:00+00:00'
        reject(apply_sql(plan, local_meta, expired))
        checks.append('EXPIRED_PACKAGE_ATOMIC_REFUSAL')
        execute("UPDATE flyway_schema_history SET version='306';")
        reject(local_sql)
        execute("UPDATE flyway_schema_history SET version='307';")
        checks.append('SCHEMA_DRIFT_REFUSAL')
        receipt = frame(local_sql)
        verify(plan, local_meta, local, receipt)
        checks.append('EXACT_FIVE_APPLY_NUTRIENTS_SOURCES_PORTIONS_HISTORY_PRESERVED')
        reject(local_sql)
        checks.append('REPEATED_APPLY_REFUSED')
        undo = undo_sql(plan, local_meta, local, receipt)
        execute("INSERT INTO food_logs(id,food_id) VALUES(9000001,181013);")
        reject(undo)
        execute('DELETE FROM food_logs WHERE id=9000001;')
        checks.append('NEW_HISTORY_CONSUMER_BLOCKS_UNDO')
        execute("UPDATE food_items SET calories=calories+1 WHERE id=181138;")
        reject(undo)
        execute('UPDATE food_items SET calories='+str(next(r for r in plan['rows'] if r['id']=='181138')['expectedBefore']['food']['calories'])+' WHERE id=181138;')
        checks.append('AFTER_NUTRIENT_DRIFT_BLOCKS_UNDO')
        execute(undo)
        if state() != before:
            raise ValueError('Exact all-table rollback failed')
        checks.append('EXACT_ALL_TABLE_ROLLBACK_RESTORED')
        reject(undo)
        checks.append('REPEATED_UNDO_REFUSED')
        key = next(r for r in plan['rows'] if r['id']=='181013')['identityProposal']['canonical_food_key']
        execute(f"UPDATE food_items SET canonical_food_key={text(key)} WHERE id=9000000;")
        reject(local_sql)
        execute('UPDATE food_items SET canonical_food_key=NULL WHERE id=9000000;')
        checks.append('EXTERNAL_C2_CLAIM_BLOCKS_APPLY')
        execute("UPDATE food_item_search_aliases SET alias=alias||' drift' WHERE id=2535;")
        reject(local_sql)
        original = next(a['before']['alias'] for r in plan['rows'] for a in r['aliasActions'] if a['before']['id']==2535)
        execute(f"UPDATE food_item_search_aliases SET alias={text(original)} WHERE id=2535;")
        checks.append('ALIAS_DRIFT_BLOCKS_APPLY')
        reject(local_sql.removesuffix('COMMIT;\n')+"SELECT 1/0;COMMIT;\n")
        checks.append('LATE_FAILURE_ROLLS_BACK_ALL_WRITES')
        # Real table locks, including non-unique identity namespace, serialize races.
        lock = subprocess.Popen(['docker','exec','-i',container,'psql','-X','-qAt','-U','postgres','-d','grun_calorie_db'],
                                stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
        lock.stdin.write(b'BEGIN;LOCK TABLE food_items IN SHARE ROW EXCLUSIVE MODE;SELECT pg_sleep(5);ROLLBACK;\n')
        lock.stdin.close()
        time.sleep(.8)
        reject(local_sql)
        lock.wait(timeout=15)
        checks.append('CONCURRENT_NAMESPACE_LOCK_REFUSED_WITHOUT_PARTIAL_WRITE')
        if state() != before:
            raise ValueError('Rehearsal baseline changed')
        docker('exec',container,'createdb','-U','postgres','r7_restore')
        docker('exec',container,'pg_restore','-U','postgres','-d','r7_restore','--exit-on-error','/tmp/r7-before.dump')
        reject(local_sql, database='r7_restore')
        checks.append('WRONG_DATABASE_REFUSED')
        write(output/'local-receipt.json',receipt)
        (output/'local-undo.sql').write_text(undo,encoding='utf-8')
        restored = state(database='r7_restore')
        if restored != before:
            raise ValueError('Owned logical restore differs')
        checks.append('OWNED_CATALOG_LOGICAL_RESTORE_HASHES_MATCH')
        proof = {'status':'PASS','manifestSha256':sha(args.package/'manifest.json'),'applySha256':sha(args.package/'apply.sql'),
                 'checks':checks,'postgresVersion':'18.3','catalogProjectionOnly':True,'fullRdsRestoreTest':False,
                 'localMetaSha256':review.fingerprint(local_meta),'completedAt':datetime.now(timezone.utc).isoformat()}
        write(output/'proof.json',proof)
        print(json.dumps({'status':'PASS','checks':len(checks),'products':5,'nutritionWrites':0,'servingWrites':0}))
    finally:
        if container:
            labels = json.loads(docker('inspect',container))[0]['Config']['Labels']
            if labels.get('grun.test.owner') != owner:
                raise ValueError('Cleanup owner mismatch')
            docker('rm','-f',container)
            write(output/'cleanup.json',{'ownedContainer':container,'removed':True})


def execute_approved(args):
    folder = args.package
    p, plan, meta = load(folder)
    proof = read(folder/'rehearsal/proof.json')
    if (not args.execute_approved or proof['status'] != 'PASS' or proof['manifestSha256'] != sha(folder/'manifest.json')
            or proof['applySha256'] != sha(folder/'apply.sql') or datetime.now(timezone.utc)>datetime.fromisoformat(p['expiresAt'])):
        raise ValueError('Approved, rehearsed, unexpired exact operation required')
    output = folder/'applied'
    if output.exists():
        raise ValueError('Dispatch already attempted; read-only reconciliation, never blind retry')
    backup = aws('rds','describe-db-snapshots','--db-snapshot-identifier',args.backup)['DBSnapshots'][0]
    deploy.ops.pair.prepare.common.backup_gate(backup,datetime.now(timezone.utc))
    db = aws('rds','describe-db-instances','--db-instance-identifier','grun-staging-db')['DBInstances'][0]
    if db['DBInstanceStatus']!='available' or db['EngineVersion']!='18.3' or not db['StorageEncrypted'] or db['DBName']!='grun_calorie_db':
        raise ValueError('Exact healthy encrypted PG18.3 DB required')
    service = aws('ecs','describe-services','--cluster','grun-staging-cluster','--services','grun-staging-api-service')['services'][0]
    taskdef = aws('ecs','describe-task-definition','--task-definition',service['taskDefinition'])['taskDefinition']
    if (taskdef['containerDefinitions'][0]['image'] != args.image or service['runningCount'] != 1
            or service['desiredCount']!=1 or service['pendingCount'] or len(service['deployments'])!=1
            or service['deployments'][0].get('rolloutState')!='COMPLETED'):
        raise ValueError('Expected single healthy deployed image required')
    definition = read(args.template)
    if len(definition['containerDefinitions'])!=1:
        raise ValueError('Single owned worker required')
    c = definition['containerDefinitions'][0]
    env = {e['name']:e['value'] for e in c['environment']}
    if (env.get('PGHOST')!=db['Endpoint']['Address'] or env.get('PGDATABASE')!=db['DBName'] or 'PGPASSWORD' in env
            or not any(s['name']=='PGPASSWORD' for s in c['secrets']) or not c['image'].startswith('postgres@sha256:')):
        raise ValueError('Exact endpoint/secret-ref/pinned worker required')
    definition['family']='grun-staging-r7-presentation-approved'
    c['name']='r7-approved'
    c['logConfiguration']['options'].update({'awslogs-stream-prefix':'r7-approved','mode':'blocking'})
    c['environment']=[e for e in c['environment'] if e['name']!='PGOPTIONS']+[
        {'name':'PGOPTIONS','value':'-c default_transaction_read_only=off -c statement_timeout=90000 -c lock_timeout=3000'}]
    marker=f"SELECT set_config('grun.r7.operation',{text(p['operation'])},false);SELECT set_config('grun.r7.plan',{text(p['planDigest'])},false);\n"
    encoded=base64.b64encode(gzip.compress((marker+(folder/'apply.sql').read_text(encoding='utf-8')).encode('utf-8'))).decode()
    c['entryPoint']=['sh','-c']
    c['command']=["""for chunk in "$@"; do printf '%s' "$chunk"; done | base64 -d | gzip -d > /tmp/r7.sql || exit 1
psql -X -qAt -v ON_ERROR_STOP=1 -f /tmp/r7.sql -o /tmp/r7.jsonl || exit 1
gzip -c /tmp/r7.jsonl | base64 -w 60000 > /tmp/chunks || exit 1
printf '{"kind":"transport","chunks":%s,"sha256":"%s"}\\n' "$(wc -l < /tmp/chunks)" "$(sha256sum /tmp/r7.jsonl | cut -d ' ' -f 1)"
awk '{print "{\\"kind\\":\\"chunk\\",\\"sequence\\":" NR ",\\"data\\":\\"" $0 "\\"}"}' /tmp/chunks
sleep 15
""",'r7-payload',*[encoded[i:i+60000] for i in range(0,len(encoded),60000)]]
    if len(json.dumps(definition).encode())>65000:
        raise ValueError('Exact task payload too large; use reviewed private transport, no truncated dispatch')
    output.mkdir()
    write(output/'backup-gate.json',backup)
    write(output/'owned-task-definition.json',definition)
    arn=task=None
    stopped=False
    try:
        arn=aws('ecs','register-task-definition','--cli-input-json','file://'+str((output/'owned-task-definition.json').resolve()))['taskDefinition']['taskDefinitionArn']
        started=aws('ecs','run-task','--cluster','grun-staging-cluster','--task-definition',arn,'--launch-type','FARGATE','--network-configuration',json.dumps(service['networkConfiguration']))
        if started.get('failures') or len(started.get('tasks',[]))!=1:
            raise ValueError('Owned worker start failed')
        task=started['tasks'][0]['taskArn']
        write(output/'task-status.json',{'status':'STARTED_COMMIT_UNKNOWN','taskArn':task,'definitionArn':arn})
        deadline=time.monotonic()+360
        while time.monotonic()<deadline:
            state=aws('ecs','describe-tasks','--cluster','grun-staging-cluster','--tasks',task)['tasks'][0]
            if state['lastStatus']=='STOPPED':
                stopped=True
                break
            time.sleep(8)
        if not stopped or any(c.get('exitCode')!=0 for c in state['containers']):
            raise ValueError('Worker failed/commit uncertain; read-only reconciliation required')
        time.sleep(20)
        messages,token=[],None
        for _ in range(100):
            command=['logs','get-log-events','--log-group-name',c['logConfiguration']['options']['awslogs-group'],
                     '--log-stream-name','r7-approved/r7-approved/'+task.rsplit('/',1)[1],'--start-from-head']
            if token:
                command+=['--next-token',token]
            page=aws(*command)
            messages.extend(e['message'] for e in page['events'])
            if page['nextForwardToken']==token:
                break
            token=page['nextForwardToken']
        frames=ops.transport.decode_transport([json.loads(m) for m in messages if m.strip().startswith('{')])
        receipts=[f for f in frames if f.get('kind')=='r7_receipt']
        if len(receipts)!=1:
            raise ValueError('Missing exact commit receipt; never retry')
        receipt=receipts[0]
        write(output/'receipt.json',receipt)
        verify(plan,meta,p,receipt)
        (output/'exact-undo.sql').write_text(undo_sql(plan,meta,p,receipt),encoding='utf-8')
        write(output/'summary.json',{'status':'APPLIED_RECEIPT_COMPLETE','products':5,'backup':args.backup,
            'receiptSha256':sha(output/'receipt.json'),'undoSha256':sha(output/'exact-undo.sql'),
            'nutritionWrites':0,'servingWrites':0,'sourceWrites':0,'physicalMerges':0})
        print(json.dumps({'status':'APPLIED_RECEIPT_COMPLETE','products':5,'nutritionWrites':0,'servingWrites':0}))
    finally:
        if task and not stopped:
            aws('ecs','stop-task','--cluster','grun-staging-cluster','--task',task,'--reason','Owned R7 timeout; reconcile before retry')
        if arn:
            aws('ecs','deregister-task-definition','--task-definition',arn)
        write(output/'cleanup.json',{'taskArn':task,'definitionArn':arn,'stopped':stopped,'deregistered':bool(arn)})


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    sub=parser.add_subparsers(dest='command',required=True)
    build=sub.add_parser('build')
    for key in ('plan','proof','output'):
        build.add_argument('--'+key,type=Path,required=True)
    rehearsal=sub.add_parser('rehearse')
    rehearsal.add_argument('--package',type=Path,required=True)
    run=sub.add_parser('execute')
    for key in ('package','template'):
        run.add_argument('--'+key,type=Path,required=True)
    for key in ('image','backup'):
        run.add_argument('--'+key,required=True)
    run.add_argument('--execute-approved',action='store_true')
    args=parser.parse_args()
    if args.command=='build':
        p,_,_=package(args.plan,args.proof,args.output)
        print(json.dumps({'status':'PREPARED','products':5,'operation':p['operation']}))
    elif args.command=='rehearse':
        rehearse(args)
    else:
        execute_approved(args)

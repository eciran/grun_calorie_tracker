"""Rehearse two exact identity repairs in owned disposable PostgreSQL ONLY.

No AWS writer. Synthetic reference rows demonstrate preservation/guards without
exporting user data. This is not an RDS restore or historical semantic approval.
"""
import argparse
import json
import subprocess
import time
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path
from importlib.util import module_from_spec, spec_from_file_location


def module(name, file):
    spec = spec_from_file_location(name, Path(__file__).with_name(file))
    result = module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


prepare = module('identity_prepare', 'prepare-r6-identity-repairs.py')
names = module('identity_name_receipts', 'rehearse-r6-held-names.py')
fixture, operation = names.fixture, names.operation
text, literal = operation.text, operation.literal


def dependency_function():
    sql = Path(__file__).with_name('sql').joinpath('export-r6-identity-dependencies.sql').read_text(encoding='utf-8')
    return 'CREATE OR REPLACE FUNCTION pg_temp.r6_identity_dependencies() RETURNS jsonb LANGUAGE sql AS $r6_dependencies$\n' + sql + '\n$r6_dependencies$;\n'


def preflight(plan, expected_rows, dependency_before, owner, expires):
    prepare.validate_plan(plan)
    import re
    for ref in dependency_before['identityDependencies']:
        if ref['schema'] != 'public' or not re.fullmatch('[a-z][a-z0-9_]*', ref['table']):
            raise ValueError('Unsafe dependency lock target')
    # Keys have a non-unique index, so a row lock cannot prevent a concurrent
    # target-key insertion. Bounded maintenance locks are explicit, not hidden.
    tables = sorted({'food_items'} | {r['table'] for r in dependency_before['identityDependencies']})
    sql = fixture.prepare.prefix(owner, expires)
    sql += "DO $$ BEGIN IF (SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1) IS DISTINCT FROM '305' THEN RAISE EXCEPTION 'Schema drift'; END IF; END $$;\n"
    sql += 'LOCK TABLE ' + ','.join('public.' + t for t in tables) + ' IN SHARE ROW EXCLUSIVE MODE;\n'
    sql += dependency_function()
    sql += f"DO $$ BEGIN IF pg_temp.r6_identity_dependencies()->'values' IS DISTINCT FROM {literal(dependency_before)} THEN RAISE EXCEPTION 'Dependency/key/history drift'; END IF; END $$;\n"
    return sql + operation.name_before(expected_rows)


def apply_sql(plan, dependency_before, owner, expires):
    sql = preflight(plan, [r['expectedBefore'] for r in plan['rows']], dependency_before, owner, expires)
    for row in plan['rows']:
        identifier = int(row['id'])
        sql += 'UPDATE food_items SET ' + ','.join(k + '=' + text(v) for k, v in sorted(row['foodChanges'].items())) + f' WHERE id={identifier};\n'
        for action in row['localizationActions']:
            sql += 'UPDATE food_item_localizations SET ' + ','.join(k + '=' + text(v) for k, v in action['changes'].items())
            sql += f",updated_at=current_timestamp::text WHERE id={int(action['before']['id'])} AND food_item_id={identifier};\n"
        for action in row['aliasActions']:
            if action['action'].startswith('DEACTIVATE'):
                sql += f"UPDATE food_item_search_aliases SET active=false WHERE id={int(action['before']['id'])} AND food_item_id={identifier};\n"
            else:
                sql += f"DO $$ BEGIN IF EXISTS(SELECT 1 FROM food_item_search_aliases WHERE active AND food_item_id<>{identifier} AND language={text(action['language'])} AND normalized_alias={text(action['normalized_alias'])}) THEN RAISE EXCEPTION 'Alias collision'; END IF; END $$;\n"
                sql += f"INSERT INTO food_item_search_aliases(food_item_id,alias,normalized_alias,language,alias_type,source,active,created_at) VALUES({identifier},{text(action['alias'])},{text(action['normalized_alias'])},{text(action['language'])},'ADMIN_MANUAL',{text(owner)},true,current_timestamp::text);\n"
    sql += f"SELECT jsonb_build_object('kind','identity_receipts','owner',{text(owner)},'dependencyBeforeSha256',{text(prepare.fingerprint(dependency_before))},'rows',(SELECT jsonb_agg({operation.name_state()} ORDER BY p.id) FROM r6_plan p),'dependencies',pg_temp.r6_identity_dependencies()->'values');\nCOMMIT;\n"
    return sql


def verify(plan, receipt, owner, dependency_before):
    prepare.validate_plan(plan)
    if (receipt.get('owner') != owner or receipt.get('kind') != 'identity_receipts'
            or receipt.get('dependencyBeforeSha256') != prepare.fingerprint(dependency_before)):
        raise ValueError('Exact owned identity receipts required')
    names.verify_rows([{**r, 'foodNameChanges': r['foodChanges']} for r in plan['rows']], receipt['rows'], owner)
    if receipt['dependencies']['canonicalResolutions'] != plan['dependencyBefore']['canonicalResolutions']:
        raise ValueError('Oil canonical resolution changed')
    claims = {str(r['id']): r for r in receipt['dependencies']['identityKeyClaims']}
    for identifier, expected in prepare.IDENTITIES.items():
        if claims[identifier]['canonical_food_key'] != expected['new']:
            raise ValueError('Wrong resulting key')
    before = {str(r['id']): r for r in plan['dependencyBefore']['identityKeyClaims']}
    if set(claims) != set(before) or claims['179890'] != before['179890']:
        raise ValueError('Unrelated oil winner/key claims changed')
    if dependency_before is not None:
        current = receipt['dependencies']
        if current['identityKeyIndexes'] != dependency_before['identityKeyIndexes']:
            raise ValueError('Canonical index changed')
        key = lambda r: (r['schema'], r['table'], r['constraint'])
        old_refs = {key(r): r for r in dependency_before['identityDependencies']}
        new_refs = {key(r): r for r in current['identityDependencies']}
        if set(new_refs) != set(old_refs):
            raise ValueError('FK inventory changed')
        for identifier, previous in old_refs.items():
            actual = new_refs[identifier]
            if previous['table'] not in {'food_item_localizations', 'food_item_search_aliases'}:
                if actual != previous:
                    raise ValueError('Protected reference/history fingerprint changed')
            elif {k: v for k, v in actual.items() if k not in {'count', 'fingerprint'}} != {k: v for k, v in previous.items() if k not in {'count', 'fingerprint'}}:
                raise ValueError('Alias/localization reference shape changed')
            elif int(actual['count']) != int(previous['count']) + (4 if previous['table'] == 'food_item_search_aliases' else 0):
                raise ValueError('Alias/localization exact count mismatch')


def undo_sql(plan, receipt, dependency_before, owner, expires):
    verify(plan, receipt, owner, dependency_before)
    sql = preflight(plan, receipt['rows'], receipt['dependencies'], owner, expires)
    after = {r['id']: r for r in receipt['rows']}
    for row in plan['rows']:
        identifier, before = int(row['id']), row['expectedBefore']
        sql += 'UPDATE food_items SET ' + ','.join(k + '=' + text(before['food'][k]) for k in sorted(row['foodChanges'])) + f' WHERE id={identifier};\n'
        for action in row['localizationActions']:
            changes = {k: action['before'][k] for k in action['changes']}
            changes['updated_at'] = action['before']['updated_at']
            sql += 'UPDATE food_item_localizations SET ' + ','.join(k + '=' + text(v) for k, v in changes.items()) + f" WHERE id={int(action['before']['id'])};\n"
        old_ids = {a['id'] for a in before['name_review']['aliases']}
        for a in after[row['id']]['name_review']['aliases']:
            if a['id'] not in old_ids:
                sql += f"DELETE FROM food_item_search_aliases WHERE id={int(a['id'])} AND source={text(owner)} AND to_jsonb(food_item_search_aliases)={literal(a)};\n"
        for action in row['aliasActions']:
            if action['action'].startswith('DEACTIVATE'):
                sql += f"UPDATE food_item_search_aliases SET active=true WHERE id={int(action['before']['id'])};\n"
        sql += f"DO $$ BEGIN IF {operation.name_state(str(identifier))} IS DISTINCT FROM {literal(before)} THEN RAISE EXCEPTION 'Exact identity undo failed'; END IF; END $$;\n"
    return sql + 'COMMIT;\n'


def fixture_sql(plan, categories):
    rows = [r['expectedBefore'] for r in plan['rows']]
    sql = fixture.fixture_sql({'currentRows': rows, 'categories': {c['slug']: c for c in categories}})
    sql += '\nUPDATE food_items SET canonical_food_key=NULL WHERE id=9000000;\n'
    winner = dict(rows[0]['food'], id=179890, name='Olive Oil', display_name='Olive Oil', short_display_name='Olive Oil', source_key='USDA_FOODDATA:fdc:171413')
    sql += f'INSERT INTO food_items SELECT * FROM jsonb_populate_record(NULL::food_items,{literal(winner)});\n'
    # Produce all observed FK paths without retrieving private source rows.
    existing = set(fixture.TABLES)
    grouped = {}
    for ref in plan['dependencyBefore']['identityDependencies']:
        grouped.setdefault(ref['table'], []).append(ref)
    for table, refs in grouped.items():
        if table not in existing:
            fields = ['id bigint PRIMARY KEY']
            fields += [r['column'] + ' bigint REFERENCES food_items(id)' for r in refs]
            if table == 'food_canonical_resolutions':
                fields += ['canonical_food_key text UNIQUE NOT NULL']
            elif table == 'food_logs':
                fields += ['display_name text', 'snapshot_calories double precision']
            elif table == 'recipe_ingredients':
                fields += ['snapshot_food_name text', 'snapshot_calories double precision']
            else:
                fields += ['proof_marker text']
            sql += f'CREATE TABLE {table}(' + ','.join(fields) + ');\n'
            for ref in refs:
                count = int(ref['count'])
                if table in {'food_logs', 'recipe_ingredients'}:
                    sql += f"INSERT INTO {table}(id,{ref['column']}," + ('display_name' if table == 'food_logs' else 'snapshot_food_name') + ",snapshot_calories) VALUES(1,49682,'Historical snapshot preserved',361);\n"
                elif count:
                    sql += f"INSERT INTO {table}(id,{ref['column']},proof_marker) SELECT n,CASE WHEN n%2=0 THEN 49682 ELSE 181002 END,'synthetic reference' FROM generate_series(1,{count}) n;\n"
    sql += "INSERT INTO food_canonical_resolutions(id,primary_food_item_id,canonical_food_key) VALUES(1,179890,'GLOBAL:GENERIC_INGREDIENT:PREPARED:olive_oil');\n"
    sql += "CREATE INDEX idx_food_items_canonical_food_key ON food_items(canonical_food_key) WHERE canonical_food_key IS NOT NULL;\n"
    for table in ('food_item_search_aliases', 'food_item_localizations'):
        sql += f"CREATE SEQUENCE {table}_proof_id; SELECT setval('{table}_proof_id',coalesce((SELECT max(id)+1 FROM {table}),1),false); ALTER TABLE {table} ALTER COLUMN id SET DEFAULT nextval('{table}_proof_id');\n"
    return sql + "CREATE TABLE flyway_schema_history(version text,success boolean,installed_rank integer); INSERT INTO flyway_schema_history VALUES('305',true,1);\n"


def run(args):
    plan = prepare.names.held.read_json(args.plan)
    summary = prepare.names.held.read_json(args.plan.parent / 'summary.json')
    if summary['planSha256'] != prepare.sha(args.plan) or plan['inputHashes']['producer'] != prepare.sha(Path(__file__).with_name('prepare-r6-identity-repairs.py')):
        raise ValueError('Identity package/producer changed')
    prepare.validate_plan(plan)
    categories = prepare.names.held.read_json(args.categories)['metrics'][0]['categories']
    owner = 'r6-rehearsal-' + uuid.uuid4().hex[:12]
    expires = (datetime.now(timezone.utc) + timedelta(hours=1)).isoformat()
    args.output.mkdir(parents=True, exist_ok=False)
    container, checks = None, []

    def docker(*command, stdin=None, fail=False):
        result = subprocess.run(['docker', *command], input=stdin.encode('utf-8') if stdin is not None else None,
                                capture_output=True, timeout=120)
        if (not fail and result.returncode) or (fail and not result.returncode):
            raise ValueError('Owned identity rehearsal failed: ' + result.stderr.decode('utf-8', errors='replace')[-1600:])
        return result.stdout.decode('utf-8')

    def execute(sql, fail=False, database='r6_rehearsal', owned=True):
        marker = f"SELECT set_config('grun.rehearsal.owner',{text(owner)},false);\n" if owned else ''
        return docker('exec', '-i', container, 'psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-U', 'postgres', '-d', database, stdin=marker + sql, fail=fail)

    def json_result(sql):
        return next(json.loads(line) for line in execute(sql).splitlines() if line.startswith('{'))

    def dependencies():
        return json_result(dependency_function() + "SELECT pg_temp.r6_identity_dependencies()->'values';")

    def all_state():
        tables = sorted(set(fixture.TABLES) | {r['table'] for r in plan['dependencyBefore']['identityDependencies']})
        return json_result('SELECT jsonb_build_object(' + ','.join(text(t) + f",(SELECT md5(coalesce(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text),'[]'::jsonb)::text) FROM {t} r)" for t in tables) + ');')

    def reject(sql, name, **options):
        before = all_state()
        execute(sql, fail=True, **options)
        if all_state() != before:
            raise ValueError('Rejected operation mutated tables: ' + name)
        checks.append(name)

    try:
        container = docker('run', '-d', '--label', 'grun.test.owner=' + owner, '--tmpfs', '/var/lib/postgresql',
            '-e', 'POSTGRES_HOST_AUTH_METHOD=trust', '-e', 'POSTGRES_DB=r6_rehearsal', fixture.IMAGE).strip()
        for _ in range(40):
            ready = subprocess.run(['docker', 'exec', container, 'pg_isready', '-U', 'postgres'], capture_output=True, timeout=10)
            if ready.returncode == 0:
                break
            time.sleep(.5)
        else:
            raise ValueError('Owned PostgreSQL startup timeout')
        if execute('SHOW server_version_num;').splitlines()[-1] != '180003':
            raise ValueError('PG18.3 proof required')
        execute(fixture_sql(plan, categories))
        before, deps = all_state(), dependencies()
        apply = apply_sql(plan, deps, owner, expires)
        reject(apply, 'wrong_session_rejected', owned=False)
        reject(apply, 'production_database_rejected', database='postgres')
        reject(apply_sql(plan, deps, owner, (datetime.now(timezone.utc) - timedelta(minutes=1)).isoformat()), 'expired_package_rejected')
        execute("INSERT INTO food_items SELECT (jsonb_populate_record(NULL::food_items,to_jsonb(f)||jsonb_build_object('id',9000001,'source_key','fixture:collision','canonical_food_key','GLOBAL:GENERIC_INGREDIENT:PREPARED:reduced_fat_mayonnaise_with_olive_oil'))).* FROM food_items f WHERE id=9000000;")
        reject(apply, 'nonunique_index_target_collision_blocks_apply')
        execute('DELETE FROM food_items WHERE id=9000001;')
        execute("UPDATE recipe_ingredients SET snapshot_food_name='Concurrent historical edit' WHERE id=1;")
        reject(apply, 'historical_reference_drift_blocks_apply')
        execute("UPDATE recipe_ingredients SET snapshot_food_name='Historical snapshot preserved' WHERE id=1;")
        alias = plan['rows'][0]['aliasActions'][-1]
        execute(f"INSERT INTO food_item_search_aliases(food_item_id,alias,normalized_alias,language,alias_type,source,active) VALUES(9000000,{text(alias['alias'])},{text(alias['normalized_alias'])},{text(alias['language'])},'ADMIN_MANUAL','collision',true);")
        reject(apply, 'new_external_alias_collision_blocks_apply')
        execute("DELETE FROM food_item_search_aliases WHERE source='collision';")
        receipt = json_result(apply)
        verify(plan, receipt, owner, deps)
        checks.append('exact_two_keys_names_four_locales_four_aliases_three_deactivations')
        after = all_state()
        if any(before[t] != after[t] for t in before if t not in {'food_items', 'food_item_localizations', 'food_item_search_aliases'}):
            raise ValueError('History/nutrition/portion/source reference state changed')
        checks.append('all_reference_and_protected_tables_preserved')
        reject(apply, 'repeat_apply_rejected_atomically')
        undo = undo_sql(plan, receipt, deps, owner, expires)
        execute('UPDATE food_items SET calories=calories+1 WHERE id=49682;')
        reject(undo, 'later_nutrition_change_blocks_undo')
        execute('UPDATE food_items SET calories=calories-1 WHERE id=49682;')
        execute('UPDATE food_logs SET snapshot_calories=snapshot_calories+1 WHERE id=1;')
        reject(undo, 'later_history_change_blocks_undo')
        execute('UPDATE food_logs SET snapshot_calories=snapshot_calories-1 WHERE id=1;')
        execute(undo)
        if all_state() != before or dependencies() != deps:
            raise ValueError('Exact identity undo failed to restore every table')
        checks.append('receipt_bound_undo_restores_exact_tables_and_keys')
        # Two actual PostgreSQL sessions: a maintenance lock must prevent both
        # new canonical claims and changes to historical child rows.
        tables = sorted({'food_items'} | {r['table'] for r in deps['identityDependencies']})
        holder_sql = "SET application_name='r6_identity_lock_probe'; BEGIN; LOCK TABLE " + ','.join(tables) + " IN SHARE ROW EXCLUSIVE MODE; SELECT pg_sleep(5); ROLLBACK;"
        holder = subprocess.Popen(['docker', 'exec', '-i', container, 'psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1',
                                   '-U', 'postgres', '-d', 'r6_rehearsal'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        try:
            holder.stdin.write(holder_sql.encode('utf-8'))
            holder.stdin.close()
            for _ in range(20):
                locked = execute("SELECT EXISTS(SELECT 1 FROM pg_locks l JOIN pg_stat_activity a ON a.pid=l.pid WHERE a.application_name='r6_identity_lock_probe' AND l.relation='food_items'::regclass AND l.mode='ShareRowExclusiveLock' AND l.granted);").splitlines()[-1]
                if locked == 't':
                    break
                time.sleep(.1)
            else:
                raise ValueError('Concurrent lock holder did not acquire the maintenance lock')
            execute("SET lock_timeout='200ms'; UPDATE food_items SET canonical_food_key='fixture:concurrent-claim' WHERE id=9000000;", fail=True)
            execute("SET lock_timeout='200ms'; UPDATE food_logs SET snapshot_calories=0 WHERE id=1;", fail=True)
            execute("SET lock_timeout='200ms'; UPDATE recipe_ingredients SET snapshot_food_name='Concurrent rename' WHERE id=1;", fail=True)
            checks.append('two_session_maintenance_lock_blocks_catalog_log_and_recipe_writes')
        finally:
            holder.wait(timeout=15)
            error = holder.stderr.read().decode('utf-8', errors='replace')
            holder.stdout.close()
            holder.stderr.close()
            if holder.returncode:
                raise ValueError('Concurrent holder failed: ' + error[-500:])
        if all_state() != before:
            raise ValueError('Concurrent probes changed table contents')
        # A new FK-bearing table cannot pass the captured dependency inventory.
        execute('CREATE TABLE new_product_reference(id bigint PRIMARY KEY,food_id bigint REFERENCES food_items(id));')
        reject(apply, 'new_fk_inventory_blocks_stale_apply')
        execute('DROP TABLE new_product_reference;')
        proof = {'status': 'PASS', 'postgresVersion': '18.3', 'checks': checks, 'changedProducts': 2,
                'planSha256': summary['planSha256'], 'awsWrites': 0, 'liveApplyAllowed': False,
                'fixtureScope': 'Snapshot catalogue plus synthetic anonymous history/FK paths',
                'fullRdsRestoreOrConcurrentBackendCertification': False, 'historySemanticApproval': False}
        for filename, body in (('proof.json', proof), ('receipts.json', receipt), ('fixture-dependency-before.json', deps)):
            (args.output / filename).write_text(json.dumps(body, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        (args.output / 'rehearsal-only-apply.sql').write_text(apply, encoding='utf-8')
        (args.output / 'rehearsal-only-undo.sql').write_text(undo, encoding='utf-8')
        print(json.dumps(proof, indent=2))
    finally:
        if container:
            if docker('inspect', '--format', '{{index .Config.Labels "grun.test.owner"}}', container).strip() != owner:
                raise ValueError('Owned fixture cleanup refused')
            docker('rm', '-f', container)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    for key in ('plan', 'categories', 'output'):
        parser.add_argument('--' + key, type=Path, required=True)
    run(parser.parse_args())

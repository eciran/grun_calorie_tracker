"""Owned PG18.3 apply/undo proof for exact Edamame state. No AWS writer.

Captured catalogue projection plus synthetic private-reference fixtures, not a
full RDS restore, live history approval or production-concurrency certificate.
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
    value = module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


prepare = module('edamame_prepare', 'prepare-r6-edamame.py')
names = module('edamame_receipts', 'rehearse-r6-held-names.py')
text, literal = names.text, names.sql_json


def dependency_function():
    sql = Path(__file__).with_name('sql').joinpath('export-r6-edamame-review.sql').read_text(encoding='utf-8')
    return 'CREATE OR REPLACE FUNCTION pg_temp.r6_edamame_dependencies() RETURNS jsonb LANGUAGE sql AS $edamame$\n' + sql + '\n$edamame$;\n'


def preflight(plan, expected, meta, owner, expires, include_before=True):
    prepare.validate(plan)
    import re
    if any(r['schema']!='public' or not re.fullmatch('[a-z][a-z0-9_]*',r['table']) for r in meta['dependencies']):
        raise ValueError('Unsafe dependency lock target')
    tables = sorted({'food_items','food_item_search_aliases'} | {r['table'] for r in meta['dependencies']})
    sql = names.fixture.prepare.prefix(owner,expires)
    sql += "DO $$ BEGIN IF (SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1) IS DISTINCT FROM '305' THEN RAISE EXCEPTION 'Schema drift'; END IF; END $$;\n"
    # The canonical index is non-unique; lock the whole key/alias write surface.
    sql += 'LOCK TABLE ' + ','.join('public.'+t for t in tables) + ' IN SHARE ROW EXCLUSIVE MODE;\n'
    sql += dependency_function()
    sql += f"DO $$ BEGIN IF pg_temp.r6_edamame_dependencies()->'values' IS DISTINCT FROM {literal(meta)} THEN RAISE EXCEPTION 'Dependency/key drift'; END IF; END $$;\n"
    return sql + (names.operation.name_before(expected) if include_before else '')


def apply_sql(plan, meta, owner, expires):
    row = plan['row']
    sql = preflight(plan,[row['expectedBefore']],meta,owner,expires)
    sql += 'UPDATE food_items SET ' + ','.join(k+'='+text(v) for k,v in sorted(row['foodChanges'].items())) + ' WHERE id=181467;\n'
    for action in row['localizationActions']:
        sql += 'UPDATE food_item_localizations SET ' + ','.join(k+'='+text(v) for k,v in action['changes'].items())
        sql += f",updated_at=current_timestamp::text WHERE id={int(action['before']['id'])} AND food_item_id=181467;\n"
    for action in row['aliasActions']:
        if action['action'].startswith('DEACTIVATE'):
            sql += f"UPDATE food_item_search_aliases SET active=false WHERE id={int(action['before']['id'])} AND food_item_id=181467;\n"
        else:
            sql += f"DO $$ BEGIN IF EXISTS(SELECT 1 FROM food_item_search_aliases WHERE active AND food_item_id<>181467 AND language={text(action['language'])} AND normalized_alias={text(action['normalized_alias'])}) THEN RAISE EXCEPTION 'Alias collision'; END IF; END $$;\n"
            sql += f"INSERT INTO food_item_search_aliases(food_item_id,alias,normalized_alias,language,alias_type,source,active,created_at) VALUES(181467,{text(action['alias'])},{text(action['normalized_alias'])},{text(action['language'])},'ADMIN_MANUAL',{text(owner)},true,current_timestamp::text);\n"
    return sql + f"SELECT jsonb_build_object('owner',{text(owner)},'rows',jsonb_build_array({names.operation.name_state('181467')}),'dependencies',pg_temp.r6_edamame_dependencies()->'values');\nCOMMIT;\n"


def verify(plan,receipt,owner,meta):
    prepare.validate(plan)
    if receipt['owner'] != owner:
        raise ValueError('Owned state/key receipt required')
    names.verify_rows([{**plan['row'],'foodNameChanges':plan['row']['foodChanges']}],receipt['rows'],owner)
    current = receipt['dependencies']
    expected_claims = [{**c,'canonical_food_key':prepare.NEW_KEY} for c in meta['keyClaims']]
    if (current['keyClaims'] != expected_claims or current['keyIndexes'] != meta['keyIndexes']
            or current['canonicalResolutions'] != meta['canonicalResolutions']):
        raise ValueError('Exact key/index/resolution drift')
    previous = {r['constraint']:r for r in meta['dependencies']}
    after = {r['constraint']:r for r in current['dependencies']}
    if set(previous) != set(after):
        raise ValueError('Dependency inventory changed')
    for key,old in previous.items():
        new = after[key]
        if old['table'] not in {'food_item_search_aliases','food_item_localizations'}:
            if new != old: raise ValueError('Protected source/portion/history dependency changed')
        elif ({k:v for k,v in new.items() if k not in {'count','fingerprint'}}
              != {k:v for k,v in old.items() if k not in {'count','fingerprint'}}
              or int(new['count']) != int(old['count']) + (2 if old['table']=='food_item_search_aliases' else 0)):
            raise ValueError('Localized/alias dependency shape/count drift')


def undo_sql(plan,receipt,meta,owner,expires):
    verify(plan,receipt,owner,meta)
    row = {**plan['row'],'foodNameChanges':plan['row']['foodChanges']}
    undo = names.undo_rows([row],receipt['rows'],owner,expires)
    prefix = names.fixture.prepare.prefix(owner,expires)
    if not undo.startswith(prefix): raise ValueError('Wrong rehearsal prefix')
    return preflight(plan,receipt['rows'],receipt['dependencies'],owner,expires,include_before=False) + undo[len(prefix):]


def fixture_sql(plan,categories):
    before = plan['row']['expectedBefore']
    sql = names.fixture.fixture_sql({'currentRows':[before],'categories':categories})
    sql += 'UPDATE food_items SET canonical_food_key=NULL WHERE id=9000000;\n'
    grouped = {}
    for ref in plan['dependencyBefore']['dependencies']:
        grouped.setdefault(ref['table'],[]).append(ref)
    for table,refs in sorted(grouped.items()):
        if table in names.fixture.TABLES: continue
        fields = ['id bigint PRIMARY KEY'] + [r['column']+' bigint REFERENCES food_items(id)' for r in refs]
        if table=='food_canonical_resolutions': fields.append('canonical_food_key text UNIQUE')
        elif table=='food_logs': fields.extend(['display_name text','snapshot_calories double precision'])
        else: fields.append('proof_marker text')
        sql += 'CREATE TABLE '+table+'('+','.join(fields)+');\n'
        for ref in refs:
            count = int(ref['count'])
            if table=='food_logs':
                sql += "INSERT INTO food_logs VALUES(1,181467,'Synthetic historical snapshot',121),(2,181467,'Second synthetic snapshot',121);\n"
            elif count:
                sql += f"INSERT INTO {table}(id,{ref['column']},proof_marker) SELECT n,181467,'synthetic reference' FROM generate_series(1,{count}) n;\n"
    sql += "CREATE INDEX idx_food_items_canonical_food_key ON food_items(canonical_food_key) WHERE canonical_food_key IS NOT NULL;\n"
    for table in ('food_item_search_aliases','food_item_localizations'):
        sql += f"CREATE SEQUENCE {table}_proof_id; SELECT setval('{table}_proof_id',(SELECT max(id)+1 FROM {table}),false); ALTER TABLE {table} ALTER COLUMN id SET DEFAULT nextval('{table}_proof_id');\n"
    return sql + "CREATE TABLE flyway_schema_history(version text,success boolean,installed_rank integer); INSERT INTO flyway_schema_history VALUES('305',true,1);\n"


def run(args):
    plan = prepare.names.held.read_json(args.plan)
    summary = prepare.names.held.read_json(args.plan.parent/'summary.json')
    if (summary['planSha256'] != prepare.sha(args.plan) or plan['inputHashes']['producer'] != prepare.sha(Path(__file__).with_name('prepare-r6-edamame.py'))
            or plan['inputHashes']['runtimeRegistry'] != prepare.sha(prepare.REGISTRY)):
        raise ValueError('Prepared Edamame package/rules changed')
    prepare.validate(plan)
    categories = {c['slug']:c for c in prepare.names.held.read_json(args.categories)['metrics'][0]['categories']}
    owner = 'r6-rehearsal-'+uuid.uuid4().hex[:12]
    expires = (datetime.now(timezone.utc)+timedelta(hours=1)).isoformat()
    args.output.mkdir(parents=True,exist_ok=False)
    container, checks = None, []
    def docker(*command,stdin=None,fail=False):
        result = subprocess.run(['docker',*command],input=stdin.encode('utf-8') if stdin is not None else None,capture_output=True,timeout=120)
        if (not fail and result.returncode) or (fail and not result.returncode):
            raise ValueError('Owned Edamame rehearsal failed: '+result.stderr.decode('utf-8',errors='replace')[-1600:])
        return result.stdout.decode('utf-8')
    def execute(sql,fail=False,database='r6_rehearsal',owned=True):
        marker = f"SELECT set_config('grun.rehearsal.owner',{text(owner)},false);\n" if owned else ''
        return docker('exec','-i',container,'psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','postgres','-d',database,stdin=marker+sql,fail=fail)
    def value(sql):
        return next(json.loads(line) for line in execute(sql).splitlines() if line.startswith('{'))
    def dependencies():
        return value(dependency_function()+"SELECT pg_temp.r6_edamame_dependencies()->'values';")
    def state():
        tables = sorted(set(names.fixture.TABLES)|{r['table'] for r in plan['dependencyBefore']['dependencies']})
        return value('SELECT jsonb_build_object('+','.join(text(t)+f",(SELECT md5(coalesce(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text),'[]'::jsonb)::text) FROM {t} r)" for t in tables)+');')
    def reject(sql,label,**kwargs):
        before = state()
        execute(sql,fail=True,**kwargs)
        if state()!=before: raise ValueError('Rejected operation mutated data: '+label)
        checks.append(label)
    try:
        container = docker('run','-d','--label','grun.test.owner='+owner,'--tmpfs','/var/lib/postgresql',
                           '-e','POSTGRES_HOST_AUTH_METHOD=trust','-e','POSTGRES_DB=r6_rehearsal',names.fixture.IMAGE).strip()
        for _ in range(40):
            if subprocess.run(['docker','exec',container,'pg_isready','-U','postgres'],capture_output=True,timeout=10).returncode==0: break
            time.sleep(.5)
        else: raise ValueError('Owned PostgreSQL startup timeout')
        if execute('SHOW server_version_num;').splitlines()[-1]!='180003': raise ValueError('PG18.3 required')
        execute(fixture_sql(plan,categories))
        initial,meta = state(),dependencies()
        apply = apply_sql(plan,meta,owner,expires)
        reject(apply,'wrong_session_rejected',owned=False)
        reject(apply,'wrong_database_rejected',database='postgres')
        reject(apply_sql(plan,meta,owner,(datetime.now(timezone.utc)-timedelta(minutes=1)).isoformat()),'expired_package_rejected')
        execute(f"INSERT INTO food_items SELECT (jsonb_populate_record(NULL::food_items,to_jsonb(f)||jsonb_build_object('id',9000001,'source_key','fixture:collision','canonical_food_key',{text(prepare.NEW_KEY)}))).* FROM food_items f WHERE id=9000000;")
        reject(apply,'nonunique_index_target_key_collision_blocks_apply')
        execute('DELETE FROM food_items WHERE id=9000001;')
        alias = plan['row']['aliasActions'][-1]
        execute(f"INSERT INTO food_item_search_aliases(food_item_id,alias,normalized_alias,language,alias_type,source,active) VALUES(9000000,{text(alias['alias'])},{text(alias['normalized_alias'])},{text(alias['language'])},'ADMIN_MANUAL','collision',true);")
        reject(apply,'external_alias_collision_blocks_apply')
        execute("DELETE FROM food_item_search_aliases WHERE source='collision';")
        execute('UPDATE food_item_serving_options SET gram_weight=156 WHERE id=3265;')
        reject(apply,'source_portion_drift_blocks_apply')
        execute('UPDATE food_item_serving_options SET gram_weight=155 WHERE id=3265;')
        receipt = value(apply)
        verify(plan,receipt,owner,meta)
        checks.append('exact_prepared_state_key_names_two_locales_alias_disposition')
        after = state()
        if any(after[t]!=initial[t] for t in initial if t not in {'food_items','food_item_localizations','food_item_search_aliases'}):
            raise ValueError('Source/portion/history/other product tables changed')
        checks.append('protected_nutrients_portions_labels_source_history_and_categories_preserved')
        reject(apply,'repeat_apply_rejected_atomically')
        undo = undo_sql(plan,receipt,meta,owner,expires)
        execute('UPDATE food_items SET calories=calories+1 WHERE id=181467;')
        reject(undo,'later_nutrition_edit_blocks_undo')
        execute('UPDATE food_items SET calories=calories-1 WHERE id=181467;')
        execute('UPDATE food_logs SET snapshot_calories=snapshot_calories+1 WHERE id=1;')
        reject(undo,'later_history_edit_blocks_undo')
        execute('UPDATE food_logs SET snapshot_calories=snapshot_calories-1 WHERE id=1;')
        execute(undo)
        if state()!=initial or dependencies()!=meta: raise ValueError('Exact all-table state/key/alias undo failed')
        checks.append('receipt_bound_undo_restores_every_table_and_key')
        result = {'status':'PASS','postgresVersion':'18.3','checks':checks,'planSha256':summary['planSha256'],
                  'awsDataWrites':0,'liveApplyAllowed':False,'syntheticHistoryFixture':True,'fullRdsRestoreOrConcurrencyCertification':False}
        for filename,payload in [('proof.json',result),('receipt.json',receipt)]:
            (args.output/filename).write_text(json.dumps(payload,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        (args.output/'rehearsal-only-apply.sql').write_text(apply,encoding='utf-8')
        (args.output/'rehearsal-only-undo.sql').write_text(undo,encoding='utf-8')
        print(json.dumps(result,indent=2))
    finally:
        if container:
            if docker('inspect','--format','{{index .Config.Labels "grun.test.owner"}}',container).strip()!=owner:
                raise ValueError('Foreign container cleanup refused')
            docker('rm','-f',container)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    for key in ('plan','categories','output'): parser.add_argument('--'+key,type=Path,required=True)
    run(parser.parse_args())

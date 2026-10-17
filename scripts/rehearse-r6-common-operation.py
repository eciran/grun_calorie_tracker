"""Atomic common R6 rehearsal in an owned isolated PG18.3 fixture only.

No AWS writer/production SQL. Synthetic history is not real-user approval.
"""
import argparse
import copy
import json
import subprocess
import time
import uuid
from datetime import datetime,timedelta,timezone
from pathlib import Path
from importlib.util import module_from_spec,spec_from_file_location


def module(name,file):
    spec=spec_from_file_location(name,Path(__file__).with_name(file))
    value=module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


prepare=module('common_prepare','prepare-r6-common-operation.py')
names=module('common_receipts','rehearse-r6-held-names.py')
text,literal=names.text,names.sql_json


def dependency_function(plan):
    ids=','.join(str(int(r['id'])) for r in sorted(plan['currentRows'],key=lambda r:int(r['id'])))
    sql=Path(__file__).with_name('sql').joinpath('export-r6-common-dependencies.sql').read_text(encoding='utf-8').replace('__SELECTED_IDS__',ids)
    return 'CREATE OR REPLACE FUNCTION pg_temp.r6_common_dependencies() RETURNS jsonb LANGUAGE sql AS $common$\n'+sql+'\n$common$;\n'


def alias_claims(plan):
    requested=set()
    registry={r['id']:r for r in prepare.names.held.read_json(prepare.names.REGISTRY)['entries']}
    for decision in registry.values():
        requested.update((language,prepare.names.aliases.normalize_alias(decision[key])) for language,key in [('EN','en'),('TR','tr')])
    requested.update((a['language'],a['normalized_alias']) for row in plan['rows'] for a in row['aliasActions'] if a['action'].startswith('ADD'))
    requested.add(('EN','olive oil'))
    return "coalesce((SELECT jsonb_agg(to_jsonb(a) ORDER BY a.food_item_id,a.id) FROM food_item_search_aliases a WHERE a.active AND (a.language,a.normalized_alias) IN ("+','.join('('+text(l)+','+text(a)+')' for l,a in sorted(requested))+")),'[]'::jsonb)"


def preflight(plan,expected,meta,claims,owner,expires,transaction_guard=None):
    prepare.validate_plan(plan)
    import re
    if any(r['schema']!='public' or not re.fullmatch('[a-z][a-z0-9_]*',r['table']) for r in meta['commonDependencies']):
        raise ValueError('Unsafe dependency lock target')
    tables=sorted({'food_items','food_brands','food_brand_aliases','food_item_search_aliases','food_item_serving_option_localizations'}
                  | {r['table'] for r in meta['commonDependencies']})
    sql=transaction_guard if transaction_guard is not None else names.fixture.prepare.prefix(owner,expires)
    sql+=f"DO $$ BEGIN IF (SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1) IS DISTINCT FROM {text(plan['schemaVersion'])} THEN RAISE EXCEPTION 'Schema drift'; END IF; END $$;\n"
    sql+='LOCK TABLE '+','.join('public.'+t for t in tables)+' IN SHARE ROW EXCLUSIVE MODE;\n'
    sql+=dependency_function(plan)
    sql+=f"DO $$ BEGIN IF pg_temp.r6_common_dependencies()->'values' IS DISTINCT FROM {literal(meta)} OR {alias_claims(plan)} IS DISTINCT FROM {literal(claims)} THEN RAISE EXCEPTION 'Source/key/FK/alias namespace drift'; END IF; END $$;\n"
    return sql+names.operation.name_before(expected)


def apply_sql(plan,meta,claims,owner,expires,transaction_guard=None):
    sql=preflight(plan,plan['currentRows'],meta,claims,owner,expires,transaction_guard)
    sql+="DO $$ BEGIN IF EXISTS(SELECT 1 FROM food_brands WHERE lower(canonical_name)='lifeway' OR normalized_key='lifeway') OR EXISTS(SELECT 1 FROM food_brand_aliases WHERE normalized_alias='lifeway') THEN RAISE EXCEPTION 'Lifeway namespace already used'; END IF; END $$;\n"
    sql+=f"INSERT INTO food_brands(canonical_name,normalized_key,status,source,verified,created_by,updated_by) VALUES('Lifeway','lifeway','ACTIVE','R6_REVIEW',false,{text(owner)},{text(owner)});\n"
    sql+=f"INSERT INTO food_brand_aliases(brand_id,alias,normalized_alias,source,created_by) SELECT id,'Lifeway','lifeway','R6_REVIEW',{text(owner)} FROM food_brands WHERE normalized_key='lifeway';\n"
    for row in plan['rows']:
        identifier=int(row['id'])
        changes=[k+'='+('NULL' if v is None else text(v)) for k,v in row['foodChanges'].items()]
        if row['component']=='lifeway': changes.append("brand_id=(SELECT id FROM food_brands WHERE normalized_key='lifeway')")
        if changes: sql+='UPDATE food_items SET '+','.join(changes)+f' WHERE id={identifier};\n'
        for action in row['localizationActions']:
            if action['action']=='UPDATE_EXACT_ACTIVE_LOCALIZATION':
                sql+='UPDATE food_item_localizations SET '+','.join(k+'='+text(v) for k,v in action['changes'].items())
                sql+=f",updated_at=current_timestamp WHERE id={int(action['before']['id'])} AND food_item_id={identifier};\n"
            elif action['action']=='ADD_MISSING_LOCALIZATION':
                sql+=f"INSERT INTO food_item_localizations(food_item_id,language,display_name,short_display_name,source,active,created_at,updated_at) VALUES({identifier},{text(action['language'])},{text(action['display_name'])},{text(action['short_display_name'])},{text(owner)},true,current_timestamp,current_timestamp);\n"
            else: raise ValueError('Unsupported localization action')
        for action in row['aliasActions']:
            if action['action']=='DEACTIVATE_EXACT_MISLEADING_ALIAS':
                sql+=f"UPDATE food_item_search_aliases SET active=false WHERE id={int(action['before']['id'])} AND food_item_id={identifier};\n"
            else:
                sql+=f"DO $$ BEGIN IF EXISTS(SELECT 1 FROM food_item_search_aliases WHERE active AND food_item_id<>{identifier} AND language={text(action['language'])} AND normalized_alias={text(action['normalized_alias'])}) THEN RAISE EXCEPTION 'Alias collision'; END IF; END $$;\n"
                sql+=f"INSERT INTO food_item_search_aliases(food_item_id,alias,normalized_alias,language,alias_type,source,active,created_at) VALUES({identifier},{text(action['alias'])},{text(action['normalized_alias'])},{text(action['language'])},'ADMIN_MANUAL',{text(owner)},true,current_timestamp);\n"
    return sql+f"SELECT jsonb_build_object('owner',{text(owner)},'rows',(SELECT jsonb_agg({names.operation.name_state()} ORDER BY p.id) FROM r6_plan p),'dependencies',pg_temp.r6_common_dependencies()->'values','aliasClaims',{alias_claims(plan)},'brand',(SELECT to_jsonb(b) FROM food_brands b WHERE normalized_key='lifeway'),'brandAlias',(SELECT to_jsonb(a) FROM food_brand_aliases a WHERE normalized_alias='lifeway'));\nCOMMIT;\n"


def receipt_rows(plan,brand_id):
    rows=[]
    for row in plan['rows']:
        changes=dict(row['foodChanges'])
        if row['component']=='lifeway': changes['brand_id']=brand_id
        rows.append({**row,'foodNameChanges':changes})
    for before in plan['currentRows']:
        if before['id'] in plan['protectedIds']:
            rows.append({'id':before['id'],'expectedBefore':before,'foodNameChanges':{},'localizationActions':[],'aliasActions':[]})
    return rows


def verify(plan,receipt,owner,meta):
    prepare.validate_plan(plan)
    brand,alias=receipt['brand'],receipt['brandAlias']
    if (receipt['owner']!=owner or brand['canonical_name']!='Lifeway' or brand['normalized_key']!='lifeway'
            or brand['status']!='ACTIVE' or brand['verified'] or brand['created_by']!=owner or brand['updated_by']!=owner
            or alias['brand_id']!=brand['id'] or alias['created_by']!=owner or alias['normalized_alias']!='lifeway'):
        raise ValueError('Exact owned brand/alias receipt required')
    names.verify_rows(receipt_rows(plan,brand['id']),receipt['rows'],owner)
    changed={int(r['id']):r['foodChanges'] for r in plan['rows']}
    expected=[{**c,**{k:v for k,v in changed.get(c['id'],{}).items() if k in c}} for c in meta['commonKeyClaims']]
    current=receipt['dependencies']
    if (current['commonKeyClaims']!=expected or current['commonCanonicalResolutions']!=meta['commonCanonicalResolutions']
            or current['commonKeyIndexes']!=meta['commonKeyIndexes'] or current['categories']!=meta['categories']):
        raise ValueError('Unapproved shared key/resolution/taxonomy change')
    old={r['constraint']:r for r in meta['commonDependencies']}
    new={r['constraint']:r for r in current['commonDependencies']}
    if set(old)!=set(new): raise ValueError('Dependency inventory changed')
    additions=sum(a['action']=='ADD_SOURCE_QUALIFIED_ALIAS' for r in plan['rows'] for a in r['aliasActions'])
    locale_adds=sum(a['action']=='ADD_MISSING_LOCALIZATION' for r in plan['rows'] for a in r['localizationActions'])
    for key,before in old.items():
        after=new[key]
        if before['table'] not in {'food_item_search_aliases','food_item_localizations'}:
            if after!=before: raise ValueError('Protected numeric/portion/source/history dependency changed')
        elif ({k:v for k,v in before.items() if k not in {'count','fingerprint'}}
              != {k:v for k,v in after.items() if k not in {'count','fingerprint'}}
              or int(after['count'])!=int(before['count'])+(additions if before['table']=='food_item_search_aliases' else locale_adds)):
            raise ValueError('Exact shared alias/localization counts differ')


def undo_sql(plan,receipt,meta,owner,expires,transaction_guard=None):
    verify(plan,receipt,owner,meta)
    sql=preflight(plan,receipt['rows'],receipt['dependencies'],receipt['aliasClaims'],owner,expires,transaction_guard)
    brand,alias=receipt['brand'],receipt['brandAlias']
    sql+=f"DO $$ BEGIN IF (SELECT to_jsonb(b) FROM food_brands b WHERE id={brand['id']}) IS DISTINCT FROM {literal(brand)} OR (SELECT to_jsonb(a) FROM food_brand_aliases a WHERE id={alias['id']}) IS DISTINCT FROM {literal(alias)} OR (SELECT count(*) FROM food_items WHERE brand_id={brand['id']})<>1 OR EXISTS(SELECT 1 FROM food_brands WHERE merged_into_id={brand['id']}) OR (SELECT count(*) FROM food_brand_aliases WHERE brand_id={brand['id']})<>1 THEN RAISE EXCEPTION 'Owned brand changed/used elsewhere'; END IF; END $$;\n"
    rows=receipt_rows(plan,brand['id'])
    underlying=names.undo_rows(rows,receipt['rows'],owner,expires,transaction_guard)
    prefix=(transaction_guard if transaction_guard is not None else names.fixture.prepare.prefix(owner,expires))+names.operation.name_before(receipt['rows'])
    if not underlying.startswith(prefix) or not underlying.endswith('COMMIT;\n'): raise ValueError('Unexpected undo structure')
    # Compose the already guarded typed undo without duplicate temp tables/commit.
    sql+=underlying[len(prefix):-len('COMMIT;\n')]
    sql+=f"DELETE FROM food_brand_aliases WHERE id={alias['id']} AND created_by={text(owner)}; DELETE FROM food_brands WHERE id={brand['id']} AND created_by={text(owner)};\nCOMMIT;\n"
    return sql


def fixture_sql(plan):
    rows=copy.deepcopy(plan['currentRows'])
    if plan['schemaVersion']=='307':
        for row in rows:
            for key in prepare.ADDITIVE_IDENTITY_FIELDS: row['food'].pop(key)
    assignments=[{k:v for k,v in a.items() if k not in {'slug','active'}} for r in rows for a in r['assignments']]
    for row in rows: row['assignments']=[]
    sql=names.fixture.fixture_sql({'currentRows':rows,'categories':{c['slug']:c for c in plan['dependencyBefore']['categories']}})
    if assignments: sql+='\nINSERT INTO food_item_categories SELECT * FROM jsonb_populate_recordset(NULL::food_item_categories,'+literal(assignments)+');\n'
    sql+='UPDATE food_items SET canonical_food_key=NULL WHERE id=9000000;\n'
    captured={int(r['id']) for r in rows}
    for claim in plan['dependencyBefore']['commonKeyClaims']:
        if claim['id'] not in captured:
            # Only captured key-namespace facts are real; the remaining food
            # fields are synthetic and never certify this outside product.
            body={**rows[0]['food'],**claim,'name':'Synthetic external key claimant'}
            sql+='INSERT INTO food_items SELECT * FROM jsonb_populate_record(NULL::food_items,'+literal(body)+');\n'
    ddl=(prepare.ROOT/'src/main/resources/db/migration/V286__catalog_brand_category_foundation.sql').read_text(encoding='utf-8').split('alter table food_items')[0]
    sql+=ddl+'ALTER TABLE food_items ALTER COLUMN brand_id TYPE bigint USING brand_id::bigint; ALTER TABLE food_items ADD FOREIGN KEY(brand_id) REFERENCES food_brands(id);\n'
    grouped={}
    for ref in plan['dependencyBefore']['commonDependencies']: grouped.setdefault(ref['table'],[]).append(ref)
    for table,refs in grouped.items():
        if table in names.fixture.TABLES: continue
        if table=='food_canonical_resolutions':
            payload=plan['dependencyBefore']['commonCanonicalResolutions']
            columns=names.fixture.fixture_columns(payload,{'canonical_food_key':'text PRIMARY KEY','primary_food_item_id':'bigint REFERENCES food_items(id)'})
            sql+='CREATE TABLE '+table+'('+','.join('"'+k+'" '+v for k,v in columns.items())+');\n'
            sql+='INSERT INTO '+table+' SELECT * FROM jsonb_populate_recordset(NULL::'+table+','+literal(payload)+');\n'
            continue
        fields=['id bigint PRIMARY KEY']+[r['column']+' bigint REFERENCES food_items(id)' for r in refs]+['proof_marker text','snapshot_calories double precision']
        sql+='CREATE TABLE '+table+'('+','.join(fields)+');\n'
        offset=0
        for ref in refs:
            count=int(ref['count'])
            if count:
                sql+=f"INSERT INTO {table}(id,{ref['column']},proof_marker,snapshot_calories) SELECT n+{offset},181453,'Synthetic reference snapshot',43 FROM generate_series(1,{count}) n;\n"
            offset+=count
    sql+='CREATE INDEX idx_food_items_canonical_food_key ON food_items(canonical_food_key) WHERE canonical_food_key IS NOT NULL;\n'
    for table in ('food_item_search_aliases','food_item_localizations'):
        sql+=f"CREATE SEQUENCE {table}_proof_id; SELECT setval('{table}_proof_id',(SELECT max(id)+1 FROM {table}),false); ALTER TABLE {table} ALTER COLUMN id SET DEFAULT nextval('{table}_proof_id');\n"
    if plan['schemaVersion']=='307':
        # Real additive DDL on the snapshot projection, not a full RDS restore.
        sql+=(prepare.ROOT/'src/main/resources/db/migration/V307__add_reviewed_food_concept_identity.sql').read_text(encoding='utf-8')
    return sql+f"CREATE TABLE flyway_schema_history(version text,success boolean,installed_rank integer); INSERT INTO flyway_schema_history VALUES({text(plan['schemaVersion'])},true,1);\n"


def run(args):
    plan=prepare.names.held.read_json(args.plan)
    summary=prepare.names.held.read_json(args.plan.parent/'summary.json')
    if (summary['planSha256']!=prepare.sha(args.plan) or plan['inputHashes']['producer']!=prepare.sha(Path(__file__).with_name('prepare-r6-common-operation.py'))
            or plan['inputHashes']['sql']!=prepare.sha(Path(__file__).with_name('sql')/'export-r6-common-dependencies.sql')):
        raise ValueError('Common package or producer changed')
    for file,hash in plan['inputHashes']['runtime'].items():
        if prepare.sha(prepare.ROOT/file)!=hash: raise ValueError('Runtime changed after preparation')
    for file,hash in plan['inputHashes']['tooling'].items():
        if prepare.sha(prepare.ROOT/file)!=hash: raise ValueError('Review/receipt tooling changed after preparation')
    prepare.validate_plan(plan)
    owner='r6-rehearsal-'+uuid.uuid4().hex[:12]
    expires=(datetime.now(timezone.utc)+timedelta(hours=1)).isoformat()
    args.output.mkdir(parents=True,exist_ok=False)
    container,checks=None,[]
    def docker(*command,stdin=None,fail=False):
        result=subprocess.run(['docker',*command],input=stdin.encode('utf-8') if stdin is not None else None,capture_output=True,timeout=120)
        if (not fail and result.returncode) or (fail and not result.returncode):
            raise ValueError('Owned common rehearsal failed: '+result.stderr.decode('utf-8',errors='replace')[-1800:])
        return result.stdout.decode('utf-8')
    def execute(sql,fail=False,database='r6_rehearsal',owned=True):
        marker=f"SELECT set_config('grun.rehearsal.owner',{text(owner)},false);\n" if owned else ''
        return docker('exec','-i',container,'psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','postgres','-d',database,stdin=marker+sql,fail=fail)
    def value(sql): return next(json.loads(line) for line in execute(sql).splitlines() if line.startswith(('{','[')))
    def deps(): return value(dependency_function(plan)+"SELECT pg_temp.r6_common_dependencies()->'values';")
    def claims(): return value('SELECT '+alias_claims(plan)+';')
    def state():
        tables=sorted(set(names.fixture.TABLES)|{r['table'] for r in plan['dependencyBefore']['commonDependencies']}|{'food_brands','food_brand_aliases'})
        return value('SELECT jsonb_build_object('+','.join(text(t)+f",(SELECT md5(coalesce(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text),'[]'::jsonb)::text) FROM {t} r)" for t in tables)+');')
    def reject(sql,label,**kwargs):
        initial=state()
        execute(sql,fail=True,**kwargs)
        if state()!=initial: raise ValueError('Rejected common operation mutated tables')
        checks.append(label)
    try:
        container=docker('run','-d','--label','grun.test.owner='+owner,'--tmpfs','/var/lib/postgresql',
            '-e','POSTGRES_HOST_AUTH_METHOD=trust','-e','POSTGRES_DB=r6_rehearsal',names.fixture.IMAGE).strip()
        for _ in range(40):
            if subprocess.run(['docker','exec',container,'pg_isready','-U','postgres'],capture_output=True,timeout=10).returncode==0: break
            time.sleep(.5)
        else: raise ValueError('Owned PG startup timeout')
        if execute('SHOW server_version_num;').splitlines()[-1]!='180003': raise ValueError('PG18.3 required')
        execute(fixture_sql(plan))
        initial,meta,before_claims=state(),deps(),claims()
        apply=apply_sql(plan,meta,before_claims,owner,expires)
        reject(apply,'wrong_session_rejected',owned=False)
        reject(apply,'wrong_database_rejected',database='postgres')
        reject(apply_sql(plan,meta,before_claims,owner,(datetime.now(timezone.utc)-timedelta(minutes=1)).isoformat()),'expired_package_rejected')
        execute("INSERT INTO food_brands(canonical_name,normalized_key) VALUES('Lifeway','lifeway');")
        reject(apply,'new_brand_collision_blocks_all_components')
        execute("DELETE FROM food_brands WHERE normalized_key='lifeway';")
        execute(f"INSERT INTO food_items SELECT (jsonb_populate_record(NULL::food_items,to_jsonb(f)||jsonb_build_object('id',9000001,'source_key','fixture:collision','canonical_food_key',{text(prepare.edamame.NEW_KEY)}))).* FROM food_items f WHERE id=9000000;")
        reject(apply,'target_key_collision_blocks_entire_batch')
        execute('DELETE FROM food_items WHERE id=9000001;')
        action=next(a for row in plan['rows'] for a in row['aliasActions'] if a['action']=='ADD_SOURCE_QUALIFIED_ALIAS')
        execute(f"INSERT INTO food_item_search_aliases(food_item_id,alias,normalized_alias,language,alias_type,source,active) VALUES(9000000,{text(action['alias'])},{text(action['normalized_alias'])},{text(action['language'])},'ADMIN_MANUAL','collision',true);")
        reject(apply,'external_alias_collision_blocks_entire_batch')
        execute("DELETE FROM food_item_search_aliases WHERE source='collision';")
        execute('UPDATE food_items SET calories=calories+1 WHERE id=179893;')
        reject(apply,'protected_foundation_nutrient_drift_blocks_batch')
        execute('UPDATE food_items SET calories=calories-1 WHERE id=179893;')
        receipt=value(apply)
        verify(plan,receipt,owner,meta)
        checks.append('atomic_34_mutations_one_noop_two_protected_companions')
        after=state()
        if any(after[t]!=initial[t] for t in initial if t not in {'food_items','food_item_search_aliases','food_item_localizations','food_brands','food_brand_aliases'}):
            raise ValueError('Protected source/portion/history/taxonomy changed')
        checks.append('nutrition_portions_source_history_and_oil_resolution_preserved')
        reject(apply,'repeat_apply_rejected_atomically')
        undo=undo_sql(plan,receipt,meta,owner,expires)
        execute(f"UPDATE food_items SET brand_id={receipt['brand']['id']} WHERE id=9000000;")
        reject(undo,'new_brand_used_by_another_product_blocks_undo')
        execute('UPDATE food_items SET brand_id=NULL WHERE id=9000000;')
        execute('UPDATE food_items SET calories=calories+1 WHERE id=181467;')
        reject(undo,'later_nutrition_edit_blocks_undo')
        execute('UPDATE food_items SET calories=calories-1 WHERE id=181467;')
        execute('UPDATE recipe_ingredients SET snapshot_calories=snapshot_calories+1 WHERE id=1;')
        reject(undo,'later_history_edit_blocks_undo')
        execute('UPDATE recipe_ingredients SET snapshot_calories=snapshot_calories-1 WHERE id=1;')
        execute(undo)
        if state()!=initial or deps()!=meta or claims()!=before_claims: raise ValueError('Exact all-component undo failed')
        checks.append('receipt_bound_undo_restores_all_tables_aliases_keys_and_brand')
        execute('CREATE TABLE new_product_reference(id bigint PRIMARY KEY,food_id bigint REFERENCES food_items(id));')
        reject(apply,'new_fk_inventory_blocks_stale_batch')
        execute('DROP TABLE new_product_reference;')
        # Force a late statement error to verify no earlier component commits.
        forced=apply
        marker="SELECT jsonb_build_object('owner'"
        where=forced.index(marker)
        reject(forced[:where]+"DO $$ BEGIN RAISE EXCEPTION 'Synthetic late failure'; END $$;\n"+forced[where:],'late_failure_rolls_back_entire_batch')
        tables=sorted({'food_items','food_brands','food_brand_aliases','food_item_search_aliases'}|{r['table'] for r in meta['commonDependencies']})
        holder_sql="SET application_name='r6_common_lock_probe'; BEGIN; LOCK TABLE "+','.join(tables)+" IN SHARE ROW EXCLUSIVE MODE; SELECT pg_sleep(5); ROLLBACK;"
        holder=subprocess.Popen(['docker','exec','-i',container,'psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','postgres','-d','r6_rehearsal'],
                                stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
        try:
            holder.stdin.write(holder_sql.encode('utf-8'))
            holder.stdin.close()
            for _ in range(20):
                locked=execute("SELECT EXISTS(SELECT 1 FROM pg_locks l JOIN pg_stat_activity a ON a.pid=l.pid WHERE a.application_name='r6_common_lock_probe' AND l.relation='food_items'::regclass AND l.mode='ShareRowExclusiveLock' AND l.granted);").splitlines()[-1]
                if locked=='t': break
                time.sleep(.1)
            else: raise ValueError('Concurrent lock holder failed to acquire lock')
            execute("SET lock_timeout='200ms'; UPDATE food_items SET canonical_food_key='fixture:concurrent' WHERE id=9000000;",fail=True)
            execute("SET lock_timeout='200ms'; UPDATE food_item_search_aliases SET active=false WHERE id=807;",fail=True)
            execute("SET lock_timeout='200ms'; INSERT INTO food_brands(canonical_name,normalized_key) VALUES('Lifeway','lifeway');",fail=True)
            execute("SET lock_timeout='200ms'; UPDATE recipe_ingredients SET snapshot_calories=0 WHERE id=1;",fail=True)
            checks.append('two_session_lock_blocks_key_alias_brand_and_history_writes')
        finally:
            holder.wait(timeout=15)
            error=holder.stderr.read().decode('utf-8',errors='replace')
            holder.stdout.close()
            holder.stderr.close()
            if holder.returncode: raise ValueError('Concurrent holder failed: '+error[-500:])
        if state()!=initial: raise ValueError('Concurrent probe changed tables')
        result={'status':'PASS','postgresVersion':'18.3','checks':checks,'changedProducts':34,'protectedProducts':2,
                'planSha256':summary['planSha256'],'awsDataWrites':0,'liveApplyAllowed':False,
                'syntheticHistoryFixture':True,'fullRdsRestoreOrBackendConcurrencyCertificate':False}
        for file,payload in [('proof.json',result),('receipts.json',receipt),('fixture-before-dependencies.json',meta)]:
            (args.output/file).write_text(json.dumps(payload,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        (args.output/'rehearsal-only-apply.sql').write_text(apply,encoding='utf-8')
        (args.output/'rehearsal-only-undo.sql').write_text(undo,encoding='utf-8')
        print(json.dumps(result,indent=2))
    finally:
        if container:
            if docker('inspect','--format','{{index .Config.Labels "grun.test.owner"}}',container).strip()!=owner: raise ValueError('Foreign cleanup refused')
            docker('rm','-f',container)


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    for key in ('plan','output'): parser.add_argument('--'+key,type=Path,required=True)
    run(parser.parse_args())

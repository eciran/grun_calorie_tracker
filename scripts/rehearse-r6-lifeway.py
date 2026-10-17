"""Owned PG18.3 proof for the one Lifeway brand-reference nomination. No AWS writer."""
import argparse
import copy
import json
import subprocess
import time
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path
from importlib.util import module_from_spec, spec_from_file_location


def module(name, file):
    spec=spec_from_file_location(name,Path(__file__).with_name(file))
    value=module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


prepare=module('lifeway_prepare','prepare-r6-lifeway.py')
names=module('lifeway_receipts','rehearse-r6-held-names.py')
text,literal=names.text,names.sql_json


def brand_guard():
    return "LOCK TABLE food_brands,food_brand_aliases,food_item_search_aliases IN SHARE ROW EXCLUSIVE MODE;\nDO $$ BEGIN IF EXISTS(SELECT 1 FROM food_brands WHERE lower(canonical_name)='lifeway' OR normalized_key='lifeway') OR EXISTS(SELECT 1 FROM food_brand_aliases WHERE normalized_alias='lifeway') THEN RAISE EXCEPTION 'Concurrent existing brand requires review'; END IF; END $$;\n"


def apply_sql(plan,owner,expires):
    prepare.validate(plan)
    row=plan['row']
    sql=names.fixture.prepare.prefix(owner,expires)+brand_guard()+names.operation.name_before([row['expectedBefore']])
    sql+="DO $$ BEGIN IF (SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1) IS DISTINCT FROM '305' THEN RAISE EXCEPTION 'Schema drift'; END IF; END $$;\n"
    sql+=f"INSERT INTO food_brands(canonical_name,normalized_key,status,source,verified,created_by,updated_by) VALUES('Lifeway','lifeway','ACTIVE','R6_REVIEW',false,{text(owner)},{text(owner)});\n"
    sql+=f"INSERT INTO food_brand_aliases(brand_id,alias,normalized_alias,source,created_by) SELECT id,'Lifeway','lifeway','R6_REVIEW',{text(owner)} FROM food_brands WHERE normalized_key='lifeway';\n"
    sql+='UPDATE food_items SET '+','.join(k+'='+('NULL' if v is None else text(v)) for k,v in row['foodChanges'].items())+",brand_id=(SELECT id FROM food_brands WHERE normalized_key='lifeway') WHERE id=181453;\n"
    for action in row['localizationActions']:
        label=prepare.EN if action['before']['language']=='EN' else prepare.TR
        sql+=f"UPDATE food_item_localizations SET display_name={text(label)},short_display_name={text(label)},updated_at=current_timestamp::text WHERE id={int(action['before']['id'])};\n"
    for action in row['aliasActions']:
        sql+=f"DO $$ BEGIN IF EXISTS(SELECT 1 FROM food_item_search_aliases WHERE active AND food_item_id<>181453 AND language={text(action['language'])} AND normalized_alias={text(action['normalized_alias'])}) THEN RAISE EXCEPTION 'New qualified alias collision'; END IF; END $$;\n"
        sql+=f"INSERT INTO food_item_search_aliases(food_item_id,alias,normalized_alias,language,alias_type,source,active,created_at) VALUES(181453,{text(action['alias'])},{text(action['normalized_alias'])},{text(action['language'])},'ADMIN_MANUAL',{text(owner)},true,current_timestamp::text);\n"
    sql+=f"SELECT jsonb_build_object('owner',{text(owner)},'rows',jsonb_build_array({names.operation.name_state('181453')}),'brand',(SELECT to_jsonb(b) FROM food_brands b WHERE normalized_key='lifeway'),'brandAlias',(SELECT to_jsonb(a) FROM food_brand_aliases a WHERE normalized_alias='lifeway'));\nCOMMIT;\n"
    return sql


def verify(plan,receipt,owner):
    prepare.validate(plan)
    brand=receipt['brand']
    if (receipt['owner']!=owner or brand['canonical_name']!='Lifeway' or brand['normalized_key']!='lifeway'
            or brand['status']!='ACTIVE' or brand['verified'] or brand['created_by']!=owner or brand['updated_by']!=owner
            or receipt['brandAlias']['brand_id']!=brand['id'] or receipt['brandAlias']['created_by']!=owner
            or receipt['brandAlias']['normalized_alias']!='lifeway'):
        raise ValueError('Exact owned new brand/alias receipt required')
    row={**plan['row'],'foodNameChanges':{**plan['row']['foodChanges'],'brand_id':brand['id']}}
    names.verify_rows([row],receipt['rows'],owner)


def undo_sql(plan,receipt,owner,expires):
    verify(plan,receipt,owner)
    row=plan['row']
    brand=receipt['brand']
    sql=names.fixture.prepare.prefix(owner,expires)+"LOCK TABLE food_brands,food_brand_aliases,food_item_search_aliases IN SHARE ROW EXCLUSIVE MODE;\n"+names.operation.name_before(receipt['rows'])
    sql+=f"DO $$ BEGIN IF (SELECT to_jsonb(b) FROM food_brands b WHERE id={brand['id']}) IS DISTINCT FROM {literal(brand)} OR (SELECT to_jsonb(a) FROM food_brand_aliases a WHERE id={receipt['brandAlias']['id']}) IS DISTINCT FROM {literal(receipt['brandAlias'])} OR (SELECT count(*) FROM food_items WHERE brand_id={brand['id']})<>1 OR EXISTS(SELECT 1 FROM food_brands WHERE merged_into_id={brand['id']}) OR (SELECT count(*) FROM food_brand_aliases WHERE brand_id={brand['id']})<>1 THEN RAISE EXCEPTION 'New brand used/changed elsewhere; do not delete'; END IF; END $$;\n"
    original=row['expectedBefore']['food']
    fields={k:original[k] for k in row['foodChanges']}
    fields['brand_id']=original['brand_id']
    sql+='UPDATE food_items SET '+','.join(k+'='+('NULL' if v is None else text(v)) for k,v in fields.items())+' WHERE id=181453;\n'
    for action in row['localizationActions']:
        before=action['before']
        sql+='UPDATE food_item_localizations SET '+','.join(k+'='+text(before[k]) for k in ('display_name','short_display_name','updated_at'))+f" WHERE id={before['id']};\n"
    previous={a['id'] for a in row['expectedBefore']['name_review']['aliases']}
    for alias in receipt['rows'][0]['name_review']['aliases']:
        if alias['id'] not in previous:
            sql+=f"DELETE FROM food_item_search_aliases WHERE id={alias['id']} AND source={text(owner)};\n"
    sql+=f"DELETE FROM food_brand_aliases WHERE id={receipt['brandAlias']['id']} AND created_by={text(owner)}; DELETE FROM food_brands WHERE id={brand['id']} AND created_by={text(owner)};\nCOMMIT;\n"
    return sql


def run(args):
    plan=prepare.names.held.read_json(args.plan)
    summary=prepare.names.held.read_json(args.plan.parent/'summary.json')
    if summary['planSha256']!=prepare.sha(args.plan) or plan['inputHashes']['producer']!=prepare.sha(Path(__file__).with_name('prepare-r6-lifeway.py')):
        raise ValueError('Prepared Lifeway package changed')
    prepare.validate(plan)
    owner='r6-rehearsal-'+uuid.uuid4().hex[:12]
    expires=(datetime.now(timezone.utc)+timedelta(hours=1)).isoformat()
    before=plan['row']['expectedBefore']
    categories={c['slug']:c for c in prepare.names.held.read_json(args.categories)['metrics'][0]['categories']}
    sql=names.fixture.fixture_sql({'currentRows':[before],'categories':categories})
    ddl=Path('src/main/resources/db/migration/V286__catalog_brand_category_foundation.sql').read_text(encoding='utf-8').split('alter table food_items')[0]
    sql+=ddl+"ALTER TABLE food_items ALTER COLUMN brand_id TYPE bigint USING brand_id::bigint; ALTER TABLE food_items ADD FOREIGN KEY(brand_id) REFERENCES food_brands(id);\n"
    sql+="CREATE TABLE food_logs(id bigint PRIMARY KEY,food_id bigint REFERENCES food_items(id),snapshot_food_name text,snapshot_calories double precision); INSERT INTO food_logs VALUES(1,181453,'Historical snapshot one',43),(2,181453,'Historical snapshot two',43);\n"
    for table in ('food_item_search_aliases','food_item_localizations'):
        sql+=f"CREATE SEQUENCE {table}_proof_id; SELECT setval('{table}_proof_id',(SELECT max(id)+1 FROM {table}),false); ALTER TABLE {table} ALTER COLUMN id SET DEFAULT nextval('{table}_proof_id');\n"
    sql+="CREATE TABLE flyway_schema_history(version text,success boolean,installed_rank integer); INSERT INTO flyway_schema_history VALUES('305',true,1);\n"
    args.output.mkdir(parents=True,exist_ok=False)
    container,checks=None,[]
    def docker(*command,stdin=None,fail=False):
        result=subprocess.run(['docker',*command],input=stdin.encode('utf-8') if stdin is not None else None,capture_output=True,timeout=120)
        if (not fail and result.returncode) or (fail and not result.returncode):
            raise ValueError('Owned Lifeway rehearsal failed: '+result.stderr.decode('utf-8',errors='replace')[-1300:])
        return result.stdout.decode('utf-8')
    def execute(sql,fail=False,database='r6_rehearsal',owned=True):
        marker=f"SELECT set_config('grun.rehearsal.owner',{text(owner)},false);\n" if owned else ''
        return docker('exec','-i',container,'psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','postgres','-d',database,stdin=marker+sql,fail=fail)
    def state():
        tables=names.fixture.TABLES+['food_brands','food_brand_aliases','food_logs']
        query='SELECT jsonb_build_object('+','.join(text(t)+f",(SELECT md5(coalesce(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text),'[]'::jsonb)::text) FROM {t} r)" for t in tables)+');'
        return next(json.loads(line) for line in execute(query).splitlines() if line.startswith('{'))
    def reject(sql,label,**kwargs):
        initial=state()
        execute(sql,fail=True,**kwargs)
        if state()!=initial: raise ValueError('Rejected patch changed data')
        checks.append(label)
    try:
        container=docker('run','-d','--label','grun.test.owner='+owner,'--tmpfs','/var/lib/postgresql','-e','POSTGRES_HOST_AUTH_METHOD=trust','-e','POSTGRES_DB=r6_rehearsal',names.fixture.IMAGE).strip()
        for _ in range(40):
            if subprocess.run(['docker','exec',container,'pg_isready','-U','postgres'],capture_output=True,timeout=10).returncode==0: break
            time.sleep(.5)
        execute(sql)
        if execute('SHOW server_version_num;').splitlines()[-1]!='180003': raise ValueError('PG18.3 required')
        initial=state()
        apply=apply_sql(plan,owner,expires)
        reject(apply,'wrong_session_rejected',owned=False)
        reject(apply,'wrong_database_rejected',database='postgres')
        reject(apply_sql(plan,owner,(datetime.now(timezone.utc)-timedelta(minutes=1)).isoformat()),'expired_package_rejected')
        execute("INSERT INTO food_brands(canonical_name,normalized_key) VALUES('Lifeway','lifeway');")
        reject(apply,'concurrent_brand_creation_blocks_apply')
        execute("DELETE FROM food_brands WHERE normalized_key='lifeway';")
        output=execute(apply)
        receipt=next(json.loads(line) for line in output.splitlines() if line.startswith('{') and 'brandAlias' in json.loads(line))
        verify(plan,receipt,owner)
        after=state()
        if any(after[t]!=initial[t] for t in names.fixture.TABLES if t not in {'food_items','food_item_localizations','food_item_search_aliases'}) or after['food_logs']!=initial['food_logs']:
            raise ValueError('Protected source/portion/history state changed')
        checks.append('exact_brand_identity_names_two_aliases_and_two_locales_prepared')
        checks.append('source_nutrition_portions_and_two_history_snapshots_preserved')
        reject(apply,'repeat_apply_rejected')
        undo=undo_sql(plan,receipt,owner,expires)
        execute(f"UPDATE food_items SET brand_id={receipt['brand']['id']} WHERE id=9000000;")
        reject(undo,'other_product_using_new_brand_blocks_undo')
        execute('UPDATE food_items SET brand_id=NULL WHERE id=9000000;')
        execute('UPDATE food_items SET calories=calories+1 WHERE id=181453;')
        reject(undo,'later_nutrition_change_blocks_undo')
        execute('UPDATE food_items SET calories=calories-1 WHERE id=181453;')
        execute(undo)
        if state()!=initial: raise ValueError('Exact brand/key/locale/alias/history undo failed')
        checks.append('owned_undo_restores_all_tables_exactly')
        result={'status':'PASS','postgresVersion':'18.3','checks':checks,'planSha256':summary['planSha256'],
                'awsDataWrites':0,'liveApplyAllowed':False,'syntheticHistoryFixture':True,'fullRdsRestoreOrConcurrencyCertification':False}
        for file,value in [('proof.json',result),('receipt.json',receipt)]:
            (args.output/file).write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        (args.output/'rehearsal-only-apply.sql').write_text(apply,encoding='utf-8')
        (args.output/'rehearsal-only-undo.sql').write_text(undo,encoding='utf-8')
        print(json.dumps(result,indent=2))
    finally:
        if container:
            if docker('inspect','--format','{{index .Config.Labels "grun.test.owner"}}',container).strip()!=owner: raise ValueError('Owned fixture cleanup refused')
            docker('rm','-f',container)


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    for key in ('plan','categories','output'): parser.add_argument('--'+key,type=Path,required=True)
    run(parser.parse_args())

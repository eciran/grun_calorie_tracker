"""Atomic R6 + additive R5 portions in owned PG18.3 only. No AWS writer."""
import argparse
import copy
import json
import re
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


prepare=module('r5_common_review','review-r5-common-readiness.py')
common=module('r5_common_rehearsal','rehearse-r6-common-operation.py')
text,literal=common.text,common.literal


def option_function(plan):
    sql=Path(__file__).with_name('sql').joinpath('export-r5-common-option-links.sql').read_text(encoding='utf-8')
    sql=sql.replace('__SELECTED_IDS__',','.join(str(int(r['id'])) for r in plan['currentRows']))
    return 'CREATE OR REPLACE FUNCTION pg_temp.r5_option_dependencies() RETURNS jsonb LANGUAGE sql AS $options$\n'+sql+'\n$options$;\n'


def option_guard(plan,meta):
    prepare.validate(plan)
    if any(r.get('supported') is not True or r.get('schema')!='public'
           or not re.fullmatch('[a-z][a-z0-9_]*',r.get('table',''))
           or not re.fullmatch('[a-z][a-z0-9_]*',r.get('column','')) for r in meta['optionDependencies']):
        raise ValueError('Unsafe option dependency lock target')
    tables=sorted({'food_item_serving_options','food_item_serving_option_localizations'}|{r['table'] for r in meta['optionDependencies']})
    return 'LOCK TABLE '+','.join('public.'+t for t in tables)+' IN SHARE ROW EXCLUSIVE MODE;\n'+option_function(plan)+f"DO $$ BEGIN IF pg_temp.r5_option_dependencies()->'values' IS DISTINCT FROM {literal(meta)} THEN RAISE EXCEPTION 'Option consumer/FK drift'; END IF; END $$;\n"


def option_apply(plan,owner):
    prepare.validate(plan)
    sql='CREATE TEMP TABLE r5_new_options(old_id bigint PRIMARY KEY,body jsonb) ON COMMIT DROP;\n'
    for row in plan['replacements']:
        old_id=row['before']['id']
        sql+=f"UPDATE food_item_serving_options SET user_visible=false,is_default=false WHERE id={old_id} AND to_jsonb(food_item_serving_options)={literal(row['before'])};\n"
        values={'food_item_id':int(row['foodId']),**row['newOption']}
        sql+='WITH added AS (INSERT INTO food_item_serving_options SELECT * FROM jsonb_populate_record(NULL::food_item_serving_options,'+literal(values)+" || jsonb_build_object('id',nextval(pg_get_serial_sequence('food_item_serving_options','id')))) RETURNING *) INSERT INTO r5_new_options SELECT "+str(old_id)+',to_jsonb(added) FROM added;\n'
        for language,label in row['labels'].items():
            sql+=f"INSERT INTO food_item_serving_option_localizations(serving_option_id,language,label,source,active,created_at,updated_at) SELECT (body->>'id')::bigint,{text(language)},{text(label)},{text(owner)},true,current_timestamp,current_timestamp FROM r5_new_options WHERE old_id={old_id};\n"
    return sql


def apply_sql(cp,plan,meta,claims,option_meta,owner,expires,transaction_guard=None):
    prepare.validate(plan)
    if plan['currentRows']!=cp['currentRows'] or plan['schemaVersion']!=cp['schemaVersion']:
        raise ValueError('Combined operation requires identical schema and before-images')
    sql=common.apply_sql(cp,meta,claims,owner,expires,transaction_guard)
    prefix=transaction_guard if transaction_guard is not None else common.names.fixture.prepare.prefix(owner,expires)
    if not sql.startswith(prefix) or not sql.endswith('\nCOMMIT;\n'): raise ValueError('Unexpected common transaction')
    start=sql.index("SELECT jsonb_build_object('owner'")
    receipt_select=sql[start:-len('\nCOMMIT;\n')]
    if not receipt_select.endswith(';'): raise ValueError('Expected exact common receipt statement')
    result=prefix+option_guard(plan,option_meta)+sql[len(prefix):start]
    result+='CREATE TEMP TABLE r5_common_receipt(body jsonb) ON COMMIT DROP; INSERT INTO r5_common_receipt '+receipt_select+'\n'
    result+=option_apply(plan,owner)
    result+=f"SELECT jsonb_build_object('owner',{text(owner)},'commonReceipt',(SELECT body FROM r5_common_receipt),'rows',(SELECT jsonb_agg({common.names.operation.name_state()} ORDER BY p.id) FROM r6_plan p),'dependencies',pg_temp.r6_common_dependencies()->'values','optionDependencies',pg_temp.r5_option_dependencies()->'values','newOptions',(SELECT jsonb_agg(jsonb_build_object('oldId',old_id,'option',body) ORDER BY old_id) FROM r5_new_options));\nCOMMIT;\n"
    return result


def verify(cp,plan,receipt,owner,meta,option_meta):
    prepare.validate(plan)
    if receipt['owner']!=owner: raise ValueError('Owned combined receipt required')
    common.verify(cp,receipt['commonReceipt'],owner,meta)
    base={r['id']:r for r in receipt['commonReceipt']['rows']}
    actual={r['id']:r for r in receipt['rows']}
    replacements={r['before']['id']:r for r in plan['replacements']}
    new={r['oldId']:r['option'] for r in receipt['newOptions']}
    if set(new)!=set(replacements) or len(new)!=13 or len({o['id'] for o in new.values()})!=13 or set(actual)!=set(base):
        raise ValueError('Exact 13 additions/37 parent receipts required')
    for identifier,previous in base.items():
        current=actual[identifier]
        if any(current[k]!=previous[k] for k in previous if k!='name_review'): raise ValueError('R5 changed parent/nutrition/source/category/history')
        child,old=current['name_review'],previous['name_review']
        if any(child[k]!=old[k] for k in ('aliases','localizations')): raise ValueError('R5 changed R6 name/alias decision')
        old_options={o['id']:o for o in old['options']}
        current_options={o['id']:o for o in child['options']}
        for oid,option in old_options.items():
            expected={**option,**({'user_visible':False,'is_default':False} if oid in replacements else {})}
            if current_options.get(oid)!=expected: raise ValueError('Historical option numeric/label/approval drift')
        requested=[r for r in plan['replacements'] if r['foodId']==identifier]
        if set(current_options)!=set(old_options)|{new[r['before']['id']]['id'] for r in requested}: raise ValueError('Unexpected option membership')
        for row in requested:
            option=new[row['before']['id']]
            expected={'id':option['id'],'food_item_id':int(identifier),**row['newOption']}
            if option!=expected or current_options.get(option['id'])!=option or option['id'] in old_options:
                raise ValueError('New source option differs from exact reviewed values')
        old_labels={l['id']:l for l in old['option_localizations']}
        labels={l['id']:l for l in child['option_localizations']}
        if any(labels.get(k)!=v for k,v in old_labels.items()): raise ValueError('Historical labels changed')
        inserted=[l for l in child['option_localizations'] if l['id'] not in old_labels]
        expected={(new[r['before']['id']]['id'],language,label) for r in requested for language,label in r['labels'].items()}
        if (len(inserted)!=2*len(requested) or {(l['serving_option_id'],l['language'],l['label']) for l in inserted}!=expected
                or any(l['source']!=owner or l['active'] is not True for l in inserted)):
            raise ValueError('Unapproved labels/locale ownership')
        if sum(bool(o['is_default']) for o in current_options.values())!=sum(bool(o['is_default']) for o in old_options.values()):
            raise ValueError('Default selection count changed')
    before=receipt['commonReceipt']['dependencies']
    current=receipt['dependencies']
    if any(current[k]!=before[k] for k in before if k!='commonDependencies'): raise ValueError('Namespace/taxonomy drift')
    old_refs={r['constraint']:r for r in before['commonDependencies']}
    new_refs={r['constraint']:r for r in current['commonDependencies']}
    if set(old_refs)!=set(new_refs): raise ValueError('Product FK inventory changed')
    for key,ref in old_refs.items():
        if ref['table']=='food_item_serving_options':
            if int(new_refs[key]['count'])!=int(ref['count'])+13: raise ValueError('Serving count mismatch')
        elif new_refs[key]!=ref: raise ValueError('Protected dependency contents changed')
    refs={r['constraint']:r for r in option_meta['optionDependencies']}
    after={r['constraint']:r for r in receipt['optionDependencies']['optionDependencies']}
    if set(refs)!=set(after): raise ValueError('Option consumer FK inventory changed')
    for key,ref in refs.items():
        if ref['table']=='food_item_serving_option_localizations':
            if int(after[key]['count'])!=int(ref['count'])+26: raise ValueError('Label count mismatch')
        elif after[key]!=ref: raise ValueError('Historical option consumers changed')
    old_links={r['id']:r for r in option_meta['optionLinks']}
    links={r['id']:r for r in receipt['optionDependencies']['optionLinks']}
    if (any(links.get(k)!=v for k,v in old_links.items()) or set(links)!=set(old_links)|{o['id'] for o in new.values()}
            or any(links[o['id']]['logs']!=0 for o in new.values())): raise ValueError('New/old historical link drift')


def undo_sql(cp,plan,receipt,meta,option_meta,owner,expires,transaction_guard=None):
    verify(cp,plan,receipt,owner,meta,option_meta)
    sql=common.preflight(cp,receipt['rows'],receipt['dependencies'],receipt['commonReceipt']['aliasClaims'],owner,expires,transaction_guard)
    sql+=option_guard(plan,receipt['optionDependencies'])
    current={r['id']:r for r in receipt['rows']}
    new={r['oldId']:r['option'] for r in receipt['newOptions']}
    for row in plan['replacements']:
        option=new[row['before']['id']]
        locales=[l for l in current[row['foodId']]['name_review']['option_localizations'] if l['serving_option_id']==option['id']]
        for label in locales:
            sql+=f"DELETE FROM food_item_serving_option_localizations WHERE id={label['id']} AND source={text(owner)} AND to_jsonb(food_item_serving_option_localizations)={literal(label)};\n"
        sql+=f"DELETE FROM food_item_serving_options WHERE id={option['id']} AND to_jsonb(food_item_serving_options)={literal(option)};\n"
        old=row['before']
        sql+=f"UPDATE food_item_serving_options SET user_visible={str(old['user_visible']).lower()},is_default={str(old['is_default']).lower()} WHERE id={old['id']};\n"
    for row in receipt['commonReceipt']['rows']:
        sql+=f"DO $$ BEGIN IF {common.names.operation.name_state(row['id'])} IS DISTINCT FROM {literal(row)} THEN RAISE EXCEPTION 'Exact portion undo failed'; END IF; END $$;\n"
    sql+='DROP TABLE pg_temp.r6_plan;\n'
    underlying=common.undo_sql(cp,receipt['commonReceipt'],meta,owner,expires,transaction_guard)
    prefix=transaction_guard if transaction_guard is not None else common.names.fixture.prepare.prefix(owner,expires)
    if not underlying.startswith(prefix): raise ValueError('Unexpected underlying undo transaction')
    return sql+underlying[len(prefix):]


def run(args):
    cp=prepare.common.names.held.read_json(args.common_plan)
    plan=prepare.common.names.held.read_json(args.plan)
    summary=prepare.common.names.held.read_json(args.plan.parent/'summary.json')
    if (plan['commonPlanSha256']!=prepare.sha(args.common_plan) or summary['planSha256']!=prepare.sha(args.plan)
            or plan['inputHashes']['producer']!=prepare.sha(Path(__file__).with_name('review-r5-common-readiness.py'))
            or plan['inputHashes']['registry']!=prepare.sha(prepare.REGISTRY)
            or plan['inputHashes']['optionSql']!=prepare.sha(Path(__file__).with_name('sql')/'export-r5-common-option-links.sql')
            or cp['inputHashes']['producer']!=prepare.sha(Path(__file__).with_name('prepare-r6-common-operation.py'))
            or cp['inputHashes']['sql']!=prepare.sha(Path(__file__).with_name('sql')/'export-r6-common-dependencies.sql')
            or plan['currentRows']!=cp['currentRows'] or plan['schemaVersion']!=cp['schemaVersion']):
        raise ValueError('Combined review package changed')
    for group in ('runtime','tooling'):
        for file,hash in cp['inputHashes'][group].items():
            if prepare.sha(prepare.common.ROOT/file)!=hash: raise ValueError('Combined release dependency changed')
    prepare.validate(plan)
    prepare.common.validate_plan(cp)
    actual_schema=cp['schemaVersion']
    if args.project_schema_307:
        if actual_schema!='305': raise ValueError('Projection requires an actual 305 input')
        cp,plan=project_307(cp,plan)
    approved=None
    if args.approved_package:
        production=module('common_production_guard_proof','approved-common-catalog-operation.py')
        approved,approved_cp,approved_plan=production.validate_pair(args.approved_package)
        if approved_cp!=cp or approved_plan!=plan or args.project_schema_307:
            raise ValueError('Exact actual-schema approved pair required for production guard proof')
    owner=approved['operation'] if approved else 'r6-rehearsal-'+uuid.uuid4().hex[:12]
    expires=approved['expiresAt'] if approved else (datetime.now(timezone.utc)+timedelta(hours=1)).isoformat()
    database_name='grun_calorie_db' if approved else 'r6_rehearsal'
    transaction_guard=production.guard(approved) if approved else None
    args.output.mkdir(parents=True,exist_ok=False)
    container,checks=None,[]
    def docker(*command,stdin=None,fail=False):
        result=subprocess.run(['docker',*command],input=stdin.encode('utf-8') if stdin is not None else None,capture_output=True,timeout=120)
        if (not fail and result.returncode) or (fail and not result.returncode):
            raise ValueError('Owned combined R5 rehearsal failed: '+result.stderr.decode('utf-8',errors='replace')[-1500:])
        return result.stdout.decode('utf-8')
    def execute(sql,fail=False,database=None,owned=True):
        marker=(f"SELECT set_config('grun.common.operation',{text(owner)},false); SELECT set_config('grun.common.pair',{text(approved['pairDigest'])},false);\n"
                if approved else f"SELECT set_config('grun.rehearsal.owner',{text(owner)},false);\n") if owned else ''
        return docker('exec','-i',container,'psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','postgres','-d',database or database_name,stdin=marker+sql,fail=fail)
    def value(sql): return next(json.loads(line) for line in execute(sql).splitlines() if line.startswith(('{','[')))
    def meta(): return value(common.dependency_function(cp)+"SELECT pg_temp.r6_common_dependencies()->'values';")
    def ometa(): return value(option_function(plan)+"SELECT pg_temp.r5_option_dependencies()->'values';")
    def state():
        tables=sorted(set(common.names.fixture.TABLES)|{r['table'] for r in cp['dependencyBefore']['commonDependencies']}|{'food_brands','food_brand_aliases'})
        return value('SELECT jsonb_build_object('+','.join(text(t)+f",(SELECT md5(coalesce(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text),'[]'::jsonb)::text) FROM {t} r)" for t in tables)+');')
    def reject(sql,label,**kwargs):
        before=state()
        execute(sql,fail=True,**kwargs)
        if state()!=before: raise ValueError('Rejected transaction changed tables')
        checks.append(label)
    try:
        container=docker('run','-d','--label','grun.test.owner='+owner,'--tmpfs','/var/lib/postgresql','-e','POSTGRES_HOST_AUTH_METHOD=trust',
                         '-e','POSTGRES_DB='+database_name,common.names.fixture.IMAGE).strip()
        for _ in range(40):
            if subprocess.run(['docker','exec',container,'pg_isready','-U','postgres'],capture_output=True,timeout=10).returncode==0: break
            time.sleep(.5)
        else: raise ValueError('Owned PG startup timeout')
        if execute('SHOW server_version_num;').splitlines()[-1]!='180003': raise ValueError('PG18.3 required')
        execute(common.fixture_sql(cp))
        execute(typed_child_constraints())
        for ref in plan['optionDependenciesBefore']['optionDependencies']:
            if ref['table']=='food_item_serving_option_localizations': continue
            execute(f"ALTER TABLE {ref['table']} ADD COLUMN {ref['column']} bigint; ALTER TABLE {ref['table']} ADD CONSTRAINT {ref['constraint']} FOREIGN KEY({ref['column']}) REFERENCES food_item_serving_options(id);")
        index=1
        for link in plan['optionDependenciesBefore']['optionLinks']:
            for _ in range(link['logs']):
                execute(f"UPDATE food_logs SET serving_option_id={link['id']},food_id={link['foodId']} WHERE id={index};")
                index+=1
        for table in ('food_item_serving_options','food_item_serving_option_localizations'):
            execute(f"CREATE SEQUENCE {table}_proof_id; SELECT setval('{table}_proof_id',(SELECT max(id)+1 FROM {table}),false); ALTER TABLE {table} ALTER COLUMN id SET DEFAULT nextval('{table}_proof_id'); ALTER SEQUENCE {table}_proof_id OWNED BY {table}.id;")
        initial,m,om,claims=state(),meta(),ometa(),value('SELECT '+common.alias_claims(cp)+';')
        apply=apply_sql(cp,plan,m,claims,om,owner,expires,transaction_guard)
        execute("UPDATE flyway_schema_history SET version='308';")
        reject(apply,'unexpected_schema_blocks_entire_release')
        execute(f"UPDATE flyway_schema_history SET version={text(cp['schemaVersion'])};")
        reject(apply,'wrong_session_rejected',owned=False)
        reject(apply,'wrong_database_rejected',database='postgres')
        old_expiry=(datetime.now(timezone.utc)-timedelta(minutes=1)).isoformat()
        old_guard=production.guard({**approved,'expiresAt':old_expiry}) if approved else None
        reject(apply_sql(cp,plan,m,claims,om,owner,old_expiry,old_guard),'expired_package_rejected')
        execute('UPDATE food_item_serving_options SET gram_weight=gram_weight+1 WHERE id=2301;')
        reject(apply,'later_old_portion_edit_blocks_entire_release')
        execute('UPDATE food_item_serving_options SET gram_weight=gram_weight-1 WHERE id=2301;')
        receipt=value(apply)
        verify(cp,plan,receipt,owner,m,om)
        checks.extend(['atomic_R6_and_13_additive_R5_replacements','all_old_ids_weights_labels_and_seven_option_log_links_preserved',
                       'all_parent_nutrients_and_protected_companions_preserved','exact_26_localized_labels_and_default_counts'])
        reject(apply,'repeat_apply_rejected')
        undo=undo_sql(cp,plan,receipt,m,om,owner,expires,transaction_guard)
        new_id=receipt['newOptions'][0]['option']['id']
        execute(f'UPDATE food_logs SET serving_option_id={new_id} WHERE id=14;')
        reject(undo,'new_option_used_by_history_blocks_undo')
        execute('UPDATE food_logs SET serving_option_id=NULL WHERE id=14;')
        execute('UPDATE food_items SET calories=calories+1 WHERE id=181467;')
        reject(undo,'later_parent_nutrient_change_blocks_undo')
        execute('UPDATE food_items SET calories=calories-1 WHERE id=181467;')
        execute(undo)
        if state()!=initial or meta()!=m or ometa()!=om: raise ValueError('Exact full combined undo failed')
        checks.append('receipt_bound_combined_undo_restores_every_table')
        final=apply.rindex('SELECT jsonb_build_object')
        reject(apply[:final]+"DO $$ BEGIN RAISE EXCEPTION 'Late R5 failure'; END $$;\n"+apply[final:],'late_R5_failure_rolls_back_R6_and_all_portions')
        tables=sorted({'food_items','food_brands','food_brand_aliases','food_item_search_aliases',
                       'food_item_serving_options','food_item_serving_option_localizations'}
                      | {ref['table'] for ref in m['commonDependencies']}
                      | {ref['table'] for ref in om['optionDependencies']})
        holder=subprocess.Popen(['docker','exec','-i',container,'psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','postgres','-d',database_name],
                                stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
        try:
            holder.stdin.write(("SET application_name='r5_common_lock_probe'; BEGIN; LOCK TABLE "+','.join(tables)+
                                " IN SHARE ROW EXCLUSIVE MODE; SELECT pg_sleep(5); ROLLBACK;").encode('utf-8'))
            holder.stdin.close()
            for _ in range(30):
                locked=execute("SELECT EXISTS(SELECT 1 FROM pg_locks l JOIN pg_stat_activity a ON a.pid=l.pid WHERE a.application_name='r5_common_lock_probe' AND l.relation='food_item_serving_options'::regclass AND l.mode='ShareRowExclusiveLock' AND l.granted);").splitlines()[-1]
                if locked=='t': break
                time.sleep(.1)
            else: raise ValueError('Option maintenance lock probe failed to acquire lock')
            for sql in ["UPDATE food_item_serving_options SET gram_weight=gram_weight+1 WHERE id=2301;",
                        "UPDATE food_logs SET serving_option_id=NULL WHERE id=1;",
                        "UPDATE recipe_ingredients SET serving_option_id=2301 WHERE id=1;",
                        "UPDATE meal_template_items SET serving_option_id=2301 WHERE id=1;",
                        "INSERT INTO food_brands(canonical_name,normalized_key) VALUES('Lifeway','lifeway');"]:
                execute("SET lock_timeout='200ms'; "+sql,fail=True)
            checks.append('two_sessions_block_option_history_recipe_template_brand_writes')
        finally:
            holder.wait(timeout=15)
            error=holder.stderr.read().decode('utf-8',errors='replace')
            holder.stdout.close()
            holder.stderr.close()
            if holder.returncode: raise ValueError('Concurrent lock holder failed: '+error[-500:])
        if state()!=initial: raise ValueError('Concurrent option probe changed tables')
        result={'status':'PASS','postgresVersion':'18.3','checks':checks,'commonPlanSha256':prepare.sha(args.common_plan),
                'r5PlanSha256':prepare.sha(args.plan),'oldPortionsPreserved':28,'newPortions':13,'newLabels':26,
                'foodNutrientWrites':0,'mlConversions':0,'awsDataWrites':0,'liveApplyAllowed':False,
                'syntheticHistoryFixture':True,'fullRdsRestoreOrBackendConcurrencyCertificate':False,
                'actualSnapshotSchema':actual_schema,'rehearsedSchema':cp['schemaVersion'],
                'additive307ProjectionOnly':args.project_schema_307,
                'servingAndLocalizationProductionTypesAndConstraints':True,
                'exactApprovedProductionGuard':approved is not None,
                'approvedManifestSha256':prepare.sha(args.approved_package/'manifest.json') if approved else None,
                'exactProductionSqlMatches':apply_sql(cp,plan,cp['dependencyBefore'],cp['aliasClaimsBefore'],plan['optionDependenciesBefore'],owner,expires,transaction_guard)==(args.approved_package/'approved-apply.sql').read_text(encoding='utf-8') if approved else None,
                'localPrivateReferenceFingerprintsAreAnonymousProjection':True,
                'actualPrivateReferenceFingerprintsExecutedLocally':False,
                'rehearsalCodeSha256':prepare.sha(Path(__file__))}
        for filename,payload in [('proof.json',result),('receipts.json',receipt),('fixture-option-dependencies.json',om)]:
            (args.output/filename).write_text(json.dumps(payload,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        (args.output/'rehearsal-only-apply.sql').write_text(apply,encoding='utf-8')
        (args.output/'rehearsal-only-undo.sql').write_text(undo,encoding='utf-8')
        print(json.dumps(result,indent=2))
    finally:
        if container:
            if docker('inspect','--format','{{index .Config.Labels "grun.test.owner"}}',container).strip()!=owner: raise ValueError('Foreign cleanup refused')
            docker('rm','-f',container)


def typed_child_constraints():
    """Use actual child DDL restrictions while leaving private history synthetic."""
    sql=''
    for field in ('quantity','gram_weight','ml_volume'):
        sql+=f'ALTER TABLE food_item_serving_options ALTER COLUMN {field} TYPE double precision USING {field}::double precision;\n'
    for table,fields in [('food_item_localizations',('created_at','updated_at')),
                         ('food_item_search_aliases',('created_at',)),
                         ('food_item_serving_option_localizations',('created_at','updated_at'))]:
        for field in fields:
            sql+=f'ALTER TABLE {table} ALTER COLUMN {field} TYPE timestamp USING {field}::timestamp;\n'
    for table,fields in [('food_item_serving_options',{'label':120,'unit_type':40,'source':40,'quality_status':40}),
                         ('food_item_serving_option_localizations',{'language':10,'label':120,'source':60})]:
        for field,length in fields.items():
            sql+=f'ALTER TABLE {table} ALTER COLUMN {field} TYPE varchar({length});\n'
    for field in ('food_item_id','label','unit_type','quantity','is_default','source','quality_status','user_visible'):
        sql+=f'ALTER TABLE food_item_serving_options ALTER COLUMN {field} SET NOT NULL;\n'
    sql+='ALTER TABLE food_item_serving_options ADD CONSTRAINT chk_food_item_serving_options_quantity_positive CHECK(quantity>0), ADD CONSTRAINT chk_food_item_serving_options_weight_or_volume CHECK ((gram_weight IS NOT NULL AND gram_weight>0) OR (ml_volume IS NOT NULL AND ml_volume>0));\n'
    for field in ('serving_option_id','language','label','active','created_at','updated_at'):
        sql+=f'ALTER TABLE food_item_serving_option_localizations ALTER COLUMN {field} SET NOT NULL;\n'
    for table,keys in [('food_item_serving_option_localizations','serving_option_id,language'),
                       ('food_item_localizations','food_item_id,language'),
                       ('food_item_search_aliases','food_item_id,normalized_alias,language')]:
        sql+=f'CREATE UNIQUE INDEX r5_proof_{table}_unique ON {table}({keys});\n'
    return sql


def project_307(cp,plan):
    """Test-only additive DDL projection; never retag an AWS snapshot as 307."""
    cp,plan=copy.deepcopy(cp),copy.deepcopy(plan)
    cp['schemaVersion']=plan['schemaVersion']='307'
    for row in cp['currentRows']:
        for key in prepare.common.ADDITIVE_IDENTITY_FIELDS: row['food'][key]=None
    current={r['id']:r for r in cp['currentRows']}
    for row in cp['rows']:
        row['expectedBefore']=current[row['id']]
        row['beforeSha256']=prepare.fingerprint(row['expectedBefore'])
    for component in cp['components'].values():
        for row in component.get('rows',[component.get('row')]):
            if row is not None:
                row['expectedBefore']=current[row['id']]
                row['beforeSha256']=prepare.fingerprint(row['expectedBefore'])
    plan['currentRows']=cp['currentRows']
    for row in plan['replacements']: row['beforeSha256']=prepare.fingerprint(current[row['foodId']])
    prepare.common.validate_plan(cp)
    prepare.validate(plan)
    return cp,plan


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    for key in ('common-plan','plan','output'): parser.add_argument('--'+key,type=Path,required=True)
    parser.add_argument('--project-schema-307',action='store_true',help='Owned additive DDL projection, not an AWS schema assertion')
    parser.add_argument('--approved-package',type=Path,help='Rehearse exact approved guard/SQL in an owned unexposed local DB only')
    run(parser.parse_args())

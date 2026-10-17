"""Exact approved R5/R6 staging operation; no broad reclassification or retry."""
import argparse
import base64
import copy
import gzip
import hashlib
import json
import os
import re
import ssl
import subprocess
import time
import uuid
from datetime import datetime,timedelta,timezone
from pathlib import Path
from urllib.request import urlopen
from importlib.util import module_from_spec,spec_from_file_location


def module(name,file):
    spec=spec_from_file_location(name,Path(__file__).with_name(file))
    value=module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


pair=module('common_approved_pair','rehearse-r5-common-readiness.py')
transfer=module('common_approved_transfer','run-r6-s3-category-batch.py')
aws=transfer.aws
sha=pair.prepare.sha
read=pair.prepare.common.names.held.read_json
text=pair.text
ROOT=pair.prepare.common.ROOT


def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2,allow_nan=False)+'\n',encoding='utf-8')


def decode_receipts(records):
    headers=[r for r in records if r.get('kind')=='transport']
    chunks=sorted((r for r in records if r.get('kind')=='chunk'),key=lambda r:r['sequence'])
    if len(headers)!=1 or len(chunks)!=headers[0]['chunks'] or [r['sequence'] for r in chunks]!=list(range(1,len(chunks)+1)):
        raise ValueError('Incomplete/duplicate receipt transport')
    body=gzip.decompress(base64.b64decode(''.join(r['data'] for r in chunks),validate=True))
    if len(body)>8*1024*1024 or hashlib.sha256(body).hexdigest()!=headers[0]['sha256']:
        raise ValueError('Receipt body size/checksum mismatch')
    frames=[json.loads(line) for line in body.decode('utf-8').splitlines() if line.strip().startswith('{')]
    if len(frames)!=1 or not {'owner','rows','commonReceipt','dependencies','optionDependencies','newOptions'}<=set(frames[0]):
        raise ValueError('Exactly one complete combined receipt required')
    return frames


def guard(manifest):
    if (manifest.get('version')!='APPROVED_COMMON_R5_R6_OPERATION_V1'
            or manifest.get('approvedScope')!='EXACT_35_COMBINED_CANDIDATES_AND_2_PROTECTED_COMPANIONS'
            or not re.fullmatch(r'common-approved-[a-f0-9]{12}',manifest.get('operation',''))
            or not re.fullmatch('[a-f0-9]{64}',manifest.get('pairDigest',''))
            or manifest.get('schemaVersion') not in {'305','307'}):
        raise ValueError('Exact approved operation and bound pair required')
    expiry=datetime.fromisoformat(manifest['expiresAt'])
    if expiry.tzinfo is None: raise ValueError('Timezone-aware operation expiry required')
    return f"""-- EXPLICIT APPROVED EXACT STAGING OPERATION. Not a rehearsal override.
BEGIN ISOLATION LEVEL READ COMMITTED;
SET LOCAL statement_timeout='90s'; SET LOCAL lock_timeout='3s';
DO $$ BEGIN
 IF current_database()<>'grun_calorie_db'
 OR current_setting('grun.common.operation',true) IS DISTINCT FROM {text(manifest['operation'])}
 OR current_setting('grun.common.pair',true) IS DISTINCT FROM {text(manifest['pairDigest'])}
 THEN RAISE EXCEPTION 'Approved database/session/pair required'; END IF;
 IF now()>{text(manifest['expiresAt'])}::timestamptz THEN RAISE EXCEPTION 'Approved operation expired'; END IF;
END $$;
SELECT pg_advisory_xact_lock(17491,310);
"""


def validate_pair(folder):
    manifest=read(folder/'manifest.json')
    guard(manifest)
    cp,rp=read(folder/'common-plan.json'),read(folder/'r5-common-plan.json')
    pair.prepare.common.validate_plan(cp)
    pair.prepare.validate(rp)
    if (sha(folder/'common-plan.json')!=manifest['commonPlanSha256']
            or sha(folder/'r5-common-plan.json')!=manifest['r5PlanSha256']
            or rp['commonPlanSha256']!=manifest['commonPlanSha256']
            or rp['currentRows']!=cp['currentRows'] or cp['schemaVersion']!=manifest['schemaVersion']
            or rp['schemaVersion']!=manifest['schemaVersion']): raise ValueError('Approved pair changed')
    for group in ('runtime','tooling'):
        for file,value in cp['inputHashes'][group].items():
            if sha(ROOT/file)!=value: raise ValueError('Bound runtime/tool changed: '+file)
    if (manifest['producerSha256']!=sha(Path(__file__))
            or manifest['pairRendererSha256']!=sha(Path(__file__).with_name('rehearse-r5-common-readiness.py'))):
        raise ValueError('Production renderer changed')
    return manifest,cp,rp


def prepare(args):
    if not args.approve_exact_common or args.output.exists(): raise ValueError('Explicit exact-scope approval and new output required')
    now=datetime.now(timezone.utc)
    cp,rp=read(args.common_plan),read(args.r5_plan)
    pair.prepare.common.validate_plan(cp)
    pair.prepare.validate(rp)
    proof=read(args.proof)
    if (proof['status']!='PASS' or len(proof['checks'])<15 or proof.get('additive307ProjectionOnly')
            or proof['commonPlanSha256']!=sha(args.common_plan) or proof['r5PlanSha256']!=sha(args.r5_plan)
            or proof['rehearsalCodeSha256']!=sha(Path(__file__).with_name('rehearse-r5-common-readiness.py'))):
        raise ValueError('Fresh actual-schema accepted compound proof required')
    for stamp in (cp['snapshotTime'],cp['sourceFetchedAt']):
        if not 0<=(now-datetime.fromisoformat(stamp)).total_seconds()<=3600: raise ValueError('Execution review is older than one hour')
    backup=aws('rds','describe-db-snapshots','--db-snapshot-identifier',args.backup)['DBSnapshots'][0]
    pair.prepare.common.backup_gate(backup,now)
    service=aws('ecs','describe-services','--cluster','grun-staging-cluster','--services','grun-staging-api-service')['services'][0]
    td=aws('ecs','describe-task-definition','--task-definition',service['taskDefinition'])['taskDefinition']
    if service['runningCount']!=1 or service['desiredCount']!=1 or td['containerDefinitions'][0]['image']!=args.image:
        raise ValueError('Exact healthy deployed image required')
    operation='common-approved-'+uuid.uuid4().hex[:12]
    common_digest,r5_digest=sha(args.common_plan),sha(args.r5_plan)
    digest=hashlib.sha256((common_digest+r5_digest).encode()).hexdigest()
    manifest={'version':'APPROVED_COMMON_R5_R6_OPERATION_V1','operation':operation,'pairDigest':digest,
              'approvedScope':'EXACT_35_COMBINED_CANDIDATES_AND_2_PROTECTED_COMPANIONS',
              'approval':'Direct owner request 2026-10-13: controlled deploy, fresh encrypted backup, apply common package and verify search/portions',
              'approvedNutrientDisposition':'RETAIN_ALL_CURRENT_VALUES_NO_UNIT_CONVERSIONS',
              'schemaVersion':cp['schemaVersion'],'createdAt':now.isoformat(),'expiresAt':(now+timedelta(minutes=45)).isoformat(),
              'commonPlanSha256':common_digest,'r5PlanSha256':r5_digest,'image':args.image,'deployedTask':service['taskDefinition'],
              'backup':backup,'producerSha256':sha(Path(__file__)),
              'pairRendererSha256':sha(Path(__file__).with_name('rehearse-r5-common-readiness.py')),
              'rehearsalProofSha256':sha(args.proof),'mutatingCandidates':35,'parentNutrientWrites':0,'mlConversions':0}
    sql=pair.apply_sql(cp,rp,cp['dependencyBefore'],cp['aliasClaimsBefore'],rp['optionDependenciesBefore'],operation,manifest['expiresAt'],guard(manifest))
    args.output.mkdir(parents=True)
    write(args.output/'common-plan.json',cp)
    write(args.output/'r5-common-plan.json',rp)
    write(args.output/'accepted-compound-proof.json',proof)
    (args.output/'approved-apply.sql').write_text(sql,encoding='utf-8')
    manifest['sqlSha256']=sha(args.output/'approved-apply.sql')
    write(args.output/'manifest.json',manifest)
    validate_pair(args.output)
    print(json.dumps({'status':'APPROVED_EXACT_PACKAGE_PREPARED','products':35,'backup':args.backup,'operation':operation}))


def execute(args):
    if not args.execute_approved or args.output.exists(): raise ValueError('Never-started explicitly approved dispatcher required')
    manifest,cp,rp=validate_pair(args.package)
    proof=read(args.package/'production-guard-proof.json')
    if (proof.get('status')!='PASS' or proof.get('exactApprovedProductionGuard') is not True
            or proof.get('exactProductionSqlMatches') is not True or len(proof.get('checks',[]))<15
            or proof.get('approvedManifestSha256')!=sha(args.package/'manifest.json')
            or proof.get('rehearsalCodeSha256')!=manifest['pairRendererSha256']):
        raise ValueError('Exact production SQL/guard proof required before dispatch')
    if datetime.now(timezone.utc)>datetime.fromisoformat(manifest['expiresAt']): raise ValueError('Approved package expired')
    expected=pair.apply_sql(cp,rp,cp['dependencyBefore'],cp['aliasClaimsBefore'],rp['optionDependenciesBefore'],manifest['operation'],manifest['expiresAt'],guard(manifest))
    path=args.package/'approved-apply.sql'
    if sha(path)!=manifest['sqlSha256'] or path.read_text(encoding='utf-8')!=expected: raise ValueError('Exact production SQL drift')
    account=aws('sts','get-caller-identity')['Account']
    if account!='911291530432': raise ValueError('Wrong AWS account')
    db=aws('rds','describe-db-instances','--db-instance-identifier','grun-staging-db')['DBInstances'][0]
    backup=aws('rds','describe-db-snapshots','--db-snapshot-identifier',manifest['backup']['DBSnapshotIdentifier'])['DBSnapshots'][0]
    pair.prepare.common.backup_gate(backup,datetime.now(timezone.utc))
    if (db['DBInstanceStatus']!='available' or db['EngineVersion']!='18.3' or not db['StorageEncrypted']
            or db['DBName']!='grun_calorie_db' or backup['DBSnapshotArn']!=manifest['backup']['DBSnapshotArn']): raise ValueError('DB/backup gate failed')
    service=aws('ecs','describe-services','--cluster','grun-staging-cluster','--services','grun-staging-api-service')['services'][0]
    td=aws('ecs','describe-task-definition','--task-definition',service['taskDefinition'])['taskDefinition']
    if td['containerDefinitions'][0]['image']!=manifest['image'] or service['taskDefinition']!=manifest['deployedTask']: raise ValueError('Deployment drift')
    definition=read(args.template)
    if len(definition['containerDefinitions'])!=1: raise ValueError('Single pinned SQL container required')
    container=definition['containerDefinitions'][0]
    env={e['name']:e['value'] for e in container['environment']}
    if (env.get('PGHOST')!=db['Endpoint']['Address'] or env.get('PGDATABASE')!=db['DBName'] or 'PGPASSWORD' in env
            or not any(s['name']=='PGPASSWORD' for s in container['secrets'])
            or not container['image'].startswith('postgres@sha256:')): raise ValueError('Exact pinned secret-bound staging template required')
    args.output.mkdir(parents=True)
    with urlopen('https://www.amazontrust.com/repository/AmazonRootCA1.pem',timeout=30) as response: ca=response.read(10000)
    if hashlib.sha256(ssl.PEM_cert_to_DER_cert(ca.decode('ascii'))).hexdigest()!='8ecde6884f3d87b1125ba31ac3fcb13d7016de7f57cc904fe1cb97c6ae98196e': raise ValueError('Amazon TLS root fingerprint mismatch')
    session=f"SELECT set_config('grun.common.operation',{text(manifest['operation'])},false); SELECT set_config('grun.common.pair',{text(manifest['pairDigest'])},false);\n"
    payload=args.output/'payload.sql.gz'
    payload.write_bytes(gzip.compress((session+expected).encode('utf-8')))
    bucket='grun-common-private-'+uuid.uuid4().hex[:20]
    created=uploaded=stopped=False
    arn=task=None
    try:
        aws('s3api','create-bucket','--bucket',bucket,'--create-bucket-configuration','LocationConstraint=eu-west-1')
        created=True
        aws('s3api','put-public-access-block','--bucket',bucket,'--public-access-block-configuration','BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true')
        aws('s3api','put-bucket-encryption','--bucket',bucket,'--server-side-encryption-configuration',json.dumps({'Rules':[{'ApplyServerSideEncryptionByDefault':{'SSEAlgorithm':'AES256'}}]}))
        aws('s3api','put-bucket-tagging','--bucket',bucket,'--tagging',json.dumps({'TagSet':[{'Key':'grun-operation','Value':manifest['operation']}]}))
        write(args.output/'transfer-status.json',{'bucket':bucket,'status':'OWNED_PRIVATE_TRANSFER_CREATED'})
        stored=aws('s3api','put-object','--bucket',bucket,'--key','approved.sql.gz','--body',str(payload.resolve()),'--server-side-encryption','AES256','--expected-bucket-owner',account)
        uploaded=True
        if stored.get('ServerSideEncryption')!='AES256': raise ValueError('Transfer encryption not confirmed')
        url=subprocess.run(['aws','s3','presign',f's3://{bucket}/approved.sql.gz','--expires-in','900','--profile','grun-staging','--region','eu-west-1'],capture_output=True,text=True,timeout=30)
        if url.returncode: raise ValueError('Signed URL could not be generated')
        download=transfer.download_script(url.stdout.strip(),sha(payload),ca)
        smoke=subprocess.run(['docker','run','--rm','-i','--label','grun.test.owner='+manifest['operation'],'--entrypoint','sh',container['image'],'-s'],input=(download+'echo TRANSPORT_TLS_SHA256_PASS\n').encode(),capture_output=True,timeout=100)
        if smoke.returncode or b'TRANSPORT_TLS_SHA256_PASS' not in smoke.stdout: raise ValueError('Transport smoke failed; SQL not dispatched')
        definition['family']='grun-staging-common-approved'
        container['name']='common-approved'
        container['logConfiguration']['options'].update({'awslogs-stream-prefix':'common-approved','mode':'blocking'})
        container['environment']=[e for e in container['environment'] if e['name']!='PGOPTIONS']+[{'name':'PGOPTIONS','value':'-c default_transaction_read_only=off -c statement_timeout=90000 -c lock_timeout=3000'}]
        container['entryPoint']=['sh','-c']
        container['command']=[download+'''psql -X -qAt -v ON_ERROR_STOP=1 -f /tmp/r6.sql -o /tmp/common.jsonl || exit 1
gzip -c /tmp/common.jsonl | base64 -w 60000 > /tmp/common.chunks || exit 1
printf '{"kind":"transport","chunks":%s,"sha256":"%s"}\\n' "$(wc -l < /tmp/common.chunks)" "$(sha256sum /tmp/common.jsonl | cut -d ' ' -f 1)"
awk '{print "{\\"kind\\":\\"chunk\\",\\"sequence\\":" NR ",\\"data\\":\\"" $0 "\\"}"}' /tmp/common.chunks
sleep 15
''']
        write(args.output/'owned-task-definition.json',definition)
        if (args.output/'owned-task-definition.json').stat().st_size>65536: raise ValueError('ECS task definition too large')
        arn=aws('ecs','register-task-definition','--cli-input-json','file://'+str((args.output/'owned-task-definition.json').resolve()))['taskDefinition']['taskDefinitionArn']
        started=aws('ecs','run-task','--cluster','grun-staging-cluster','--task-definition',arn,'--launch-type','FARGATE','--network-configuration',json.dumps(service['networkConfiguration']))
        if started.get('failures') or len(started.get('tasks',[]))!=1: raise ValueError('Owned worker did not start')
        task=started['tasks'][0]['taskArn']
        write(args.output/'task-status.json',{'taskArn':task,'status':'STARTED_COMMIT_UNKNOWN'})
        deadline=time.monotonic()+360
        while time.monotonic()<deadline:
            state=aws('ecs','describe-tasks','--cluster','grun-staging-cluster','--tasks',task)['tasks'][0]
            if state['lastStatus']=='STOPPED': stopped=True; break
            time.sleep(8)
        if not stopped or any(c.get('exitCode')!=0 for c in state['containers']): raise ValueError('Commit failed/unknown: READ-ONLY recovery, never retry')
        time.sleep(20)
        messages,token=[],None
        for _ in range(100):
            command=['logs','get-log-events','--log-group-name',container['logConfiguration']['options']['awslogs-group'],'--log-stream-name','common-approved/common-approved/'+task.rsplit('/',1)[1],'--start-from-head']
            if token: command+=['--next-token',token]
            page=aws(*command)
            messages.extend(e['message'] for e in page['events'])
            if page['nextForwardToken']==token: break
            token=page['nextForwardToken']
        frames=decode_receipts([json.loads(m) for m in messages if m.strip().startswith('{')])
        receipts=[frame for frame in frames if frame.get('owner')==manifest['operation'] and 'commonReceipt' in frame]
        if len(receipts)!=1: raise ValueError('Incomplete committed receipt: read-only recovery required')
        receipt=receipts[0]
        pair.verify(cp,rp,receipt,manifest['operation'],cp['dependencyBefore'],rp['optionDependenciesBefore'])
        write(args.output/'receipt.json',receipt)
        undo=pair.undo_sql(cp,rp,receipt,cp['dependencyBefore'],rp['optionDependenciesBefore'],manifest['operation'],manifest['expiresAt'],guard(manifest))
        (args.output/'exact-undo.sql').write_text(undo,encoding='utf-8')
        write(args.output/'summary.json',{'status':'APPLIED_RECEIPT_COMPLETE','products':35,'newPortions':13,'newLabels':26,'receiptSha256':sha(args.output/'receipt.json'),'undoSha256':sha(args.output/'exact-undo.sql'),'backup':backup['DBSnapshotIdentifier'],'taskArn':task,'image':manifest['image'],'sqlSha256':manifest['sqlSha256']})
        print(json.dumps({'status':'APPLIED_RECEIPT_COMPLETE','products':35,'newPortions':13,'newLabels':26,'backup':backup['DBSnapshotIdentifier']}))
    finally:
        if task and not stopped: aws('ecs','stop-task','--cluster','grun-staging-cluster','--task',task,'--reason','Owned common timeout: inspect commit before retry')
        if arn: aws('ecs','deregister-task-definition','--task-definition',arn)
        if created:
            tags=aws('s3api','get-bucket-tagging','--bucket',bucket,'--expected-bucket-owner',account).get('TagSet',[])
            if {'Key':'grun-operation','Value':manifest['operation']} not in tags: raise ValueError('Foreign transfer cleanup refused')
            if uploaded: aws('s3api','delete-object','--bucket',bucket,'--key','approved.sql.gz','--expected-bucket-owner',account)
            aws('s3api','delete-bucket','--bucket',bucket,'--expected-bucket-owner',account)
            write(args.output/'transfer-status.json',{'bucket':bucket,'status':'OWNED_PAYLOAD_AND_BUCKET_REMOVED'})


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('mode',choices=('prepare','execute'))
    for key in ('common-plan','r5-plan','proof','package','template','output'): parser.add_argument('--'+key,type=Path)
    for key in ('backup','image'): parser.add_argument('--'+key)
    parser.add_argument('--approve-exact-common',action='store_true')
    parser.add_argument('--execute-approved',action='store_true')
    args=parser.parse_args()
    prepare(args) if args.mode=='prepare' else execute(args)

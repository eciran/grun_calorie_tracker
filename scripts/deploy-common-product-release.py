"""Image-only ECS rollout of a frozen product snapshot; no config/Git edits."""
import argparse
import copy
import hashlib
import json
import re
import subprocess
import time
import zipfile
from datetime import datetime,timezone
from pathlib import Path
from urllib.request import urlopen
from importlib.util import module_from_spec,spec_from_file_location

ROOT=Path(__file__).resolve().parents[1]
spec=spec_from_file_location('common_deploy_ops',Path(__file__).with_name('approved-common-catalog-operation.py'))
ops=module_from_spec(spec)
spec.loader.exec_module(ops)
aws,sha,write=ops.aws,ops.sha,ops.write
OLD_IMAGE='911291530432.dkr.ecr.eu-west-1.amazonaws.com/grun-staging-api@sha256:123872d4faf410f3ae08a4c7d18d16074b985211626b77eba109f348f3a86fbd'


def run(args):
    if not args.execute_approved or args.output.exists(): raise ValueError('Explicit controlled deploy and new record required')
    source=ops.read(args.source_manifest)
    baseline_image=args.baseline_image or OLD_IMAGE
    baseline_revision=args.baseline_revision or '89'
    if source.get('baselineImage',baseline_image)!=baseline_image: raise ValueError('Frozen baseline image mismatch')
    source_root=Path(source['sourcePath'])
    if not source_root.resolve().is_relative_to(ROOT/'outputs'): raise ValueError('Owned source snapshot required')
    for file,digest in source['sealedMainSourceHashes'].items():
        if sha(source_root/file)!=digest: raise ValueError('Sealed source changed: '+file)
    if not re.fullmatch(r'911291530432\.dkr\.ecr\.eu-west-1\.amazonaws\.com/grun-staging-api@sha256:[a-f0-9]{64}',args.image): raise ValueError('Exact digest required')
    old=args.baseline_jar or ROOT/'outputs/r5-release-20261012/runtime-app.jar'
    baseline_sha=args.baseline_jar_sha or 'a8d4bc01e910734862c5de956d69ed8692d6b1ebb98f5e9a75e66ba28893f25f'
    if not old.resolve().is_relative_to(ROOT/'outputs') or sha(old)!=baseline_sha: raise ValueError('Pinned previous JAR changed')
    if not re.fullmatch(r'911291530432\.dkr\.ecr\.eu-west-1\.amazonaws\.com/grun-staging-api@sha256:[a-f0-9]{64}',baseline_image): raise ValueError('Pinned baseline digest required')
    with zipfile.ZipFile(old) as before,zipfile.ZipFile(args.jar) as after:
        def libraries(z): return {n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.startswith('BOOT-INF/lib/') and n.endswith('.jar')}
        if libraries(before)!=libraries(after): raise ValueError('Unapproved dependency change')
        allowed={r['file'].removeprefix('src/main/java/').removesuffix('.java') for r in source['selected'] if r['file'].startswith('src/main/java/')}
        allowed|={p.removeprefix('src/main/java/').removesuffix('.java') for p in source.get('partiallyIncluded',{})}
        classes=0
        for n in before.namelist():
            if not n.startswith('BOOT-INF/classes/') or not n.endswith('.class'): continue
            outer=n.removeprefix('BOOT-INF/classes/').removesuffix('.class').split('$')[0]
            if outer in allowed or any(outer==a+'Impl' for a in allowed if '/mapper/' in a): continue
            if n not in after.namelist() or before.read(n)!=after.read(n): raise ValueError('Unapproved compiled class changed: '+outer)
            classes+=1
        allowed_resources={'BOOT-INF/classes/'+r['file'].removeprefix('src/main/resources/') for r in source['selected'] if r['file'].startswith('src/main/resources/')}
        if not args.strict_resources: allowed_resources.add('BOOT-INF/classes/application.yml')
        for n in before.namelist():
            if n.startswith('BOOT-INF/classes/') and not n.endswith(('.class','/')) and n not in allowed_resources:
                if n not in after.namelist() or before.read(n)!=after.read(n): raise ValueError('Unapproved resource change: '+n)
    backup=aws('rds','describe-db-snapshots','--db-snapshot-identifier',args.backup)['DBSnapshots'][0]
    ops.pair.prepare.common.backup_gate(backup,datetime.now(timezone.utc))
    service=aws('ecs','describe-services','--cluster','grun-staging-cluster','--services','grun-staging-api-service')['services'][0]
    previous=aws('ecs','describe-task-definition','--task-definition',service['taskDefinition'])['taskDefinition']
    if (service['taskDefinition'].rsplit(':',1)[-1]!=baseline_revision or previous['containerDefinitions'][0]['image']!=baseline_image
            or service['runningCount']!=1 or service['desiredCount']!=1): raise ValueError('Previous deployment changed')
    args.output.mkdir(parents=True)
    write(args.output/'previous-task-definition.json',previous)
    write(args.output/'previous-service.json',service)
    keys=('family','taskRoleArn','executionRoleArn','networkMode','containerDefinitions','volumes','placementConstraints',
          'requiresCompatibilities','cpu','memory','runtimePlatform','ephemeralStorage','pidMode','ipcMode','proxyConfiguration')
    definition={key:copy.deepcopy(previous[key]) for key in keys if key in previous}
    definition['containerDefinitions'][0]['image']=args.image
    check=copy.deepcopy(definition)
    check['containerDefinitions'][0]['image']=baseline_image
    if check!={key:previous[key] for key in keys if key in previous}: raise ValueError('Non-image task setting changed')
    path=args.output/'new-task-definition.json'
    write(path,definition)
    arn=aws('ecs','register-task-definition','--cli-input-json','file://'+str(path.resolve()))['taskDefinition']['taskDefinitionArn']
    write(args.output/'deployment-record.json',{'status':'REGISTERED_NOT_UPDATED','previousTask':service['taskDefinition'],'newTask':arn,
         'image':args.image,'jarSha256':sha(args.jar),'sourceManifestSha256':sha(args.source_manifest),'backup':args.backup,
         'nonProductClassesVerifiedIdentical':classes,'environmentSecretsRolesHealthCpuMemoryUnchanged':True})
    latest=aws('ecs','describe-services','--cluster','grun-staging-cluster','--services','grun-staging-api-service')['services'][0]
    if latest['taskDefinition']!=service['taskDefinition']: raise ValueError('Concurrent deployment, refuse update')
    aws('ecs','update-service','--cluster','grun-staging-cluster','--service','grun-staging-api-service','--task-definition',arn)
    deadline=time.monotonic()+600
    while time.monotonic()<deadline:
        state=aws('ecs','describe-services','--cluster','grun-staging-cluster','--services','grun-staging-api-service')['services'][0]
        target=[d for d in state['deployments'] if d['taskDefinition']==arn]
        if target and target[0].get('rolloutState')=='FAILED': raise ValueError('Rollout failed; no catalogue application permitted')
        if state['taskDefinition']==arn and len(state['deployments'])==1 and state['runningCount']==state['desiredCount']==1 and not state['pendingCount'] and target[0].get('rolloutState')=='COMPLETED': break
        time.sleep(10)
    else: raise ValueError('Rollout wait exhausted; inspect before catalog write')
    with urlopen('https://api-staging.gruncalorietracker.com/actuator/health',timeout=30) as response: health=json.load(response)
    if health.get('status')!='UP': raise ValueError('Post-deploy health not UP')
    tasks=aws('ecs','list-tasks','--cluster','grun-staging-cluster','--service-name','grun-staging-api-service')['taskArns']
    actual=aws('ecs','describe-tasks','--cluster','grun-staging-cluster','--tasks',*tasks)['tasks']
    if len(actual)!=1 or actual[0]['taskDefinitionArn']!=arn: raise ValueError('Unexpected running task cohort')
    write(args.output/'final-service.json',state)
    write(args.output/'running-task.json',actual[0])
    record=ops.read(args.output/'deployment-record.json')
    record.update(status='DEPLOYED_HEALTHY',health='UP',runningTasks=1,completedAt=datetime.now(timezone.utc).isoformat())
    write(args.output/'deployment-record.json',record)
    print(json.dumps({'status':'DEPLOYED_HEALTHY','task':arn,'health':'UP','nonProductClassesUnchanged':classes,'environmentUnchanged':True}))


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    for key in ('source-manifest','jar','output'): parser.add_argument('--'+key,type=Path,required=True)
    for key in ('image','backup'): parser.add_argument('--'+key,required=True)
    parser.add_argument('--execute-approved',action='store_true')
    parser.add_argument('--baseline-image')
    parser.add_argument('--baseline-revision')
    parser.add_argument('--baseline-jar',type=Path)
    parser.add_argument('--baseline-jar-sha')
    parser.add_argument('--strict-resources',action='store_true')
    run(parser.parse_args())

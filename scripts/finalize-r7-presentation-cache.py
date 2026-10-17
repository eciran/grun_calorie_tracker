"""Clear simple caches by a same-image restart after exact DB reconciliation."""
import argparse
import json
import time
from datetime import datetime,timezone
from pathlib import Path
from urllib.request import urlopen
from importlib.util import module_from_spec,spec_from_file_location

spec=spec_from_file_location('r7_cache_operation',Path(__file__).with_name('r7-approved-presentation-operation.py'))
operation=module_from_spec(spec)
spec.loader.exec_module(operation)


def run(args):
    proof=operation.read(args.reconciliation)
    deploy=operation.read(args.deployment/'deployment-record.json')
    if not args.execute_approved or proof['status']!='PASS' or proof['products']!=5 or deploy['status']!='DEPLOYED_HEALTHY':
        raise ValueError('Reconciled exact five and approved healthy product release required')
    if args.output.exists():
        raise ValueError('Restart already attempted; inspect its rollout, never repeat blindly')
    service=operation.aws('ecs','describe-services','--cluster','grun-staging-cluster','--services','grun-staging-api-service')['services'][0]
    task=operation.aws('ecs','describe-task-definition','--task-definition',service['taskDefinition'])['taskDefinition']
    if (service['taskDefinition']!=deploy['newTask'] or task['containerDefinitions'][0]['image']!=deploy['image']
            or service['runningCount']!=1 or service['desiredCount']!=1 or len(service['deployments'])!=1):
        raise ValueError('Single exact deployed runtime required')
    operation.write(args.output,{'status':'RESTART_REQUESTING','task':service['taskDefinition'],'image':deploy['image'],
                                'reconciliationSha256':operation.sha(args.reconciliation)})
    result=operation.aws('ecs','update-service','--cluster','grun-staging-cluster','--service','grun-staging-api-service','--force-new-deployment')['service']
    deployment=next(d['id'] for d in result['deployments'] if d['status']=='PRIMARY')
    operation.write(args.output,{'status':'RESTART_REQUESTED','task':service['taskDefinition'],'image':deploy['image'],
                                'deployment':deployment,'reconciliationSha256':operation.sha(args.reconciliation)})
    deadline=time.monotonic()+650
    while time.monotonic()<deadline:
        latest=operation.aws('ecs','describe-services','--cluster','grun-staging-cluster','--services','grun-staging-api-service')['services'][0]
        current=[d for d in latest['deployments'] if d['id']==deployment]
        if current and current[0].get('rolloutState')=='FAILED':
            raise ValueError('Restart failed; no API acceptance gate')
        if (len(latest['deployments'])==1 and latest['taskDefinition']==service['taskDefinition']
                and latest['desiredCount']==latest['runningCount']==1 and latest['pendingCount']==0
                and current and current[0].get('rolloutState')=='COMPLETED'):
            break
        time.sleep(10)
    else:
        raise ValueError('Restart wait exhausted; inspect before repeating')
    with urlopen('https://api-staging.gruncalorietracker.com/actuator/health',timeout=30) as response:
        health=json.load(response)
    if health.get('status')!='UP':
        raise ValueError('Health not UP')
    operation.write(args.output,{'status':'RECONCILED_CACHE_RESTART_HEALTHY','task':service['taskDefinition'],
        'image':deploy['image'],'deployment':deployment,'health':'UP','runningTasks':1,
        'reconciliationSha256':operation.sha(args.reconciliation),'time':datetime.now(timezone.utc).isoformat(),
        'reusableCacheHardeningCompleted':False})
    print(json.dumps({'status':'RECONCILED_CACHE_RESTART_HEALTHY','health':'UP','runningTasks':1}))


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    for key in ('reconciliation','deployment','output'):
        parser.add_argument('--'+key,type=Path,required=True)
    parser.add_argument('--execute-approved',action='store_true')
    run(parser.parse_args())

"""Recover and validate the owned read-only snapshot log stream."""
import json
import subprocess
from pathlib import Path
import importlib.util

root=Path('outputs/package-family-audit-20261029')
task=json.loads((root/'task-status.json').read_text())['taskArn'].rsplit('/',1)[1]
events=[]
token=None
for _ in range(1000):
    command=['aws','logs','get-log-events','--log-group-name','/ecs/grun-staging-api','--log-stream-name',f'r5-readonly/r5-readonly/{task}','--start-from-head','--profile','grun-staging','--region','eu-west-1','--output','json']
    if token: command+=['--next-token',token]
    result=subprocess.run(command,capture_output=True,encoding='utf-8',check=True)
    page=json.loads(result.stdout)
    events.extend(page['events'])
    next_token=page['nextForwardToken']
    if next_token==token: break
    token=next_token
(root/'transport-events.json').write_text(json.dumps(events),encoding='utf-8')
records=[]
invalid=[]
for event in events:
    try: records.append(json.loads(event['message']))
    except ValueError: invalid.append(len(event['message']))
headers=[r for r in records if r.get('kind')=='transport']
chunks=[r for r in records if r.get('kind')=='chunk']
print(json.dumps({'events':len(events),'headers':headers,'chunks':len(chunks),'invalid_lengths':invalid,'missing_sequences':sorted(set(range(1,headers[0]['chunks']+1))-{c['sequence'] for c in chunks}) if headers else []}))
spec=importlib.util.spec_from_file_location('collector',Path(__file__).with_name('collect-r3-source-snapshot.py'))
module=importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
decoded=module.decode_transport(records)
rows,start=module.snapshot_rows(decoded)
import csv,hashlib
with (root/'catalog.csv').open('w',encoding='utf-8',newline='') as handle:
    writer=csv.DictWriter(handle,fieldnames=list(rows[0])); writer.writeheader(); writer.writerows(rows)
(root/'snapshot-proof.json').write_text(json.dumps({'rows':len(rows),'time':start['time'],'transactionReadOnly':True,'sha256':hashlib.sha256((root/'catalog.csv').read_bytes()).hexdigest(),'transportSha256':headers[0]['sha256'],'taskArn':task},indent=2),encoding='utf-8')
print('Snapshot recovered:',len(rows))

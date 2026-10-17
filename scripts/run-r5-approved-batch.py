"""Execute one rehearsed, explicitly approved R5 batch in the staging DB only.

No deploy, credentials logging, schema changes or blind retries. A commit with
an incomplete receipt is UNKNOWN and requires read-only recovery, not reapply.
"""
import argparse
import base64
import gzip
import importlib.util
import json
import os
import subprocess
import time
from pathlib import Path

spec=importlib.util.spec_from_file_location("transport",Path(__file__).with_name("collect-r3-source-snapshot.py"))
transport=importlib.util.module_from_spec(spec)
spec.loader.exec_module(transport)


def run(args):
    def aws(*command):
        result=subprocess.run(["aws",*command,"--profile","grun-staging","--region","eu-west-1","--output","json"],
            capture_output=True,encoding="utf-8",timeout=45,env={**os.environ,"AWS_PAGER":"","PYTHONIOENCODING":"utf-8"})
        if result.returncode:
            raise ValueError("AWS operation failed: "+command[0]+"/"+command[1])
        return json.loads(result.stdout)
    def sha(path):
        import hashlib
        return hashlib.sha256(path.read_bytes()).hexdigest()
    manifest=json.loads((args.package/"manifest.json").read_text())
    rehearsal=json.loads(args.rehearsal.read_text())
    runtime=json.loads(args.runtime.read_text())
    batches=[b for b in manifest["batches"] if b["file"]==args.batch]
    if len(batches)!=1 or not args.execute_approved_batch:
        raise ValueError("Explicit exact batch approval required")
    batch=batches[0]
    sql_path=args.package/args.batch
    if (sha(sql_path)!=batch["sha256"] or rehearsal.get("status")!="PASS"
            or rehearsal.get("patchManifestSha256")!=sha(args.package/"manifest.json") or runtime.get("status")!="PASS"):
        raise ValueError("Current source package/rehearsal/runtime proof required")
    db=aws("rds","describe-db-instances","--db-instance-identifier","grun-staging-db")["DBInstances"][0]
    backup=aws("rds","describe-db-snapshots","--db-snapshot-identifier",manifest["backup"]["Identifier"])["DBSnapshots"][0]
    if db["DBInstanceStatus"]!="available" or backup["Status"]!="available" or backup["DBInstanceIdentifier"]!="grun-staging-db" or not backup["Encrypted"]:
        raise ValueError("Ready exact database and available encrypted backup required")
    service=aws("ecs","describe-services","--cluster","grun-staging-cluster","--services","grun-staging-api-service")["services"][0]
    deployed=aws("ecs","describe-task-definition","--task-definition",service["taskDefinition"])["taskDefinition"]
    if deployed["containerDefinitions"][0]["image"]!=runtime["image"]:
        raise ValueError("Deployed runtime changed since contract check")
    definition=json.loads(args.template.read_text(encoding="utf-8-sig"))
    container=definition["containerDefinitions"][0]
    environment={e["name"]:e["value"] for e in container["environment"]}
    if environment.get("PGHOST")!=db["Endpoint"]["Address"] or environment.get("PGDATABASE")!=db["DBName"]:
        raise ValueError("Only the proven staging database can be patched")
    if not container["image"].startswith("postgres@sha256:") or "PGPASSWORD" in environment or not any(s["name"]=="PGPASSWORD" for s in container["secrets"]):
        raise ValueError("Pinned SQL image and secret reference required")
    definition["family"]="grun-staging-r5-approved-patch"
    container["name"]="r5-approved"
    container["logConfiguration"]["options"].update({"awslogs-stream-prefix":"r5-approved","mode":"blocking"})
    container["environment"]=[e for e in container["environment"] if e["name"]!="PGOPTIONS"]+[{"name":"PGOPTIONS","value":"-c default_transaction_read_only=off -c statement_timeout=60000 -c lock_timeout=3000"}]
    encoded=base64.b64encode(gzip.compress(sql_path.read_bytes())).decode()
    container["entryPoint"]=["sh","-c"]
    container["command"]=[f"""printf '%s' '{encoded}' | base64 -d | gzip -d > /tmp/r5-approved.sql || exit 1
psql -X -q -t -A -v ON_ERROR_STOP=1 -f /tmp/r5-approved.sql -o /tmp/r5-receipts.jsonl || exit 1
gzip -c /tmp/r5-receipts.jsonl > /tmp/r5-receipts.gz || exit 1
base64 -w 60000 /tmp/r5-receipts.gz > /tmp/r5-chunks.txt || exit 1
chunks=$(wc -l < /tmp/r5-chunks.txt)
digest=$(sha256sum /tmp/r5-receipts.jsonl | cut -d ' ' -f 1)
printf '{{"kind":"transport","chunks":%s,"sha256":"%s"}}\\n' "$chunks" "$digest"
awk '{{print "{{\\"kind\\":\\"chunk\\",\\"sequence\\":" NR ",\\"data\\":\\"" $0 "\\"}}"}}' /tmp/r5-chunks.txt
sleep 15
"""]
    args.output.mkdir(parents=True,exist_ok=True)
    payload=args.output/"owned-task-definition.json"
    payload.write_text(json.dumps(definition))
    arn=task=None
    stopped=False
    try:
        arn=aws("ecs","register-task-definition","--cli-input-json","file://"+str(payload.resolve()))["taskDefinition"]["taskDefinitionArn"]
        started=aws("ecs","run-task","--cluster","grun-staging-cluster","--task-definition",arn,"--launch-type","FARGATE","--network-configuration",json.dumps(service["networkConfiguration"]))
        if started.get("failures") or len(started.get("tasks",[]))!=1:
            raise ValueError("Owned patch task could not start")
        task=started["tasks"][0]["taskArn"]
        (args.output/"task-status.json").write_text(json.dumps({"taskArn":task,"operation":batch["operation"],"status":"STARTED_COMMIT_UNKNOWN"}))
        deadline=time.monotonic()+360
        while time.monotonic()<deadline:
            state=aws("ecs","describe-tasks","--cluster","grun-staging-cluster","--tasks",task)["tasks"][0]
            if state["lastStatus"]=="STOPPED":
                stopped=True
                break
            time.sleep(8)
        if not stopped or any(c.get("exitCode")!=0 for c in state["containers"]):
            raise ValueError("Patch task failed/unknown; inspect before any retry")
        time.sleep(20)
        stream="r5-approved/r5-approved/"+task.rsplit("/",1)[1]
        messages,token=[],None
        for _ in range(100):
            command=["logs","get-log-events","--log-group-name",container["logConfiguration"]["options"]["awslogs-group"],"--log-stream-name",stream,"--start-from-head"]
            if token:command += ["--next-token",token]
            page=aws(*command)
            messages.extend(e["message"] for e in page["events"])
            if page["nextForwardToken"]==token:break
            token=page["nextForwardToken"]
        records=transport.decode_transport([json.loads(m) for m in messages if m.startswith('{"kind"')])
        receipts=[r["row"] for r in records if r["kind"]=="row"]
        ends=[r for r in records if r["kind"]=="end"]
        if len(receipts)!=batch["products"] or len({r["id"] for r in receipts})!=len(receipts) or len(ends)!=1 or ends[0]["count"]!=len(receipts):
            raise ValueError("Commit receipt incomplete; never blindly repeat")
        path=args.output/"applied-receipts.json"
        path.write_text(json.dumps({"rows":receipts,"operation":batch["operation"],"taskArn":task},ensure_ascii=False)+"\n",encoding="utf-8")
        summary={"operation":batch["operation"],"status":"APPLIED_RECEIPT_COMPLETE","products":len(receipts),
                 "newPortions":sum(len(r["insertedOptionIds"]) for r in receipts),"newEvidence":sum(len(r["insertedEvidenceIds"]) for r in receipts),
                 "receiptSha256":sha(path),"backupIdentifier":backup["DBSnapshotIdentifier"],"sqlSha256":batch["sha256"],"taskArn":task}
        (args.output/"summary.json").write_text(json.dumps(summary,indent=2)+"\n")
        print(json.dumps(summary,indent=2))
    finally:
        if task and not stopped:
            aws("ecs","stop-task","--cluster","grun-staging-cluster","--task",task,"--reason","Owned R5 patch timeout; review commit before retry")
        if arn:aws("ecs","deregister-task-definition","--task-definition",arn)


if __name__=="__main__":
    parser=argparse.ArgumentParser()
    for name in ("package","rehearsal","runtime","template","output"):
        parser.add_argument("--"+name,type=Path,required=True)
    parser.add_argument("--batch",required=True)
    parser.add_argument("--execute-approved-batch",action="store_true")
    run(parser.parse_args())

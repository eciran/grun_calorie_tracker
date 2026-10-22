"""Build, rehearse and execute the bounded 13-row title-case operation."""
import argparse, base64, csv, gzip, hashlib, json, os, subprocess, time, uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REVIEW = ROOT / "outputs/title-case-audit-20261020/review/review.json"
PREFLIGHT = ROOT / "outputs/title-case-audit-20261020/final-preflight/catalog.csv"
SNAPSHOT_ID = "grun-staging-before-title-case-20261022-180506"
EXPECTED = 13

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def q(value): return "'" + str(value).replace("'", "''") + "'"
def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")

def aws(*args):
    result = subprocess.run(["aws", *args, "--profile", "grun-staging", "--region", "eu-west-1", "--output", "json"],
        capture_output=True, text=True, encoding="utf-8", timeout=90,
        env={**os.environ, "AWS_PAGER":"", "PYTHONUTF8":"1"})
    if result.returncode: raise RuntimeError("AWS command failed: " + "/".join(args[:2]) + "\n" + result.stderr[-800:])
    return json.loads(result.stdout) if result.stdout.strip() else {}

def inputs():
    review = json.loads(REVIEW.read_text(encoding="utf-8-sig"))
    approved = {int(r["id"]): r for r in review["rows"] if r["decision"].startswith("APPROVE_")}
    with PREFLIGHT.open(encoding="utf-8-sig", newline="") as handle:
        current = {int(r["id"]): json.loads(r["food"]) for r in csv.DictReader(handle)}
    if len(approved) != EXPECTED or set(approved) != set(current): raise ValueError("Exact 13-row cohort required")
    rows=[]
    for ident in sorted(approved):
        old=current[ident]; decision=approved[ident]; new=decision["proposedDisplayName"]
        if (old["display_name"] != decision["currentDisplayName"] or old["short_display_name"] != decision["currentDisplayName"]
            or old["source_key"] != "barcode:"+decision["barcode"] or old["data_source"] != "OPEN_FOOD_FACTS"
            or old["publication_status"] != "PUBLISHED" or not new or new == old["display_name"]):
            raise ValueError(f"Preflight drift for {ident}")
        rows.append({"id":ident,"sourceKey":old["source_key"],"barcode":old["barcode"],
                     "oldDisplay":old["display_name"],"oldShort":old["short_display_name"],
                     "newDisplay":new,"newShort":new})
    return rows

def values(rows):
    return ",\n".join(f"({r['id']},{q(r['sourceKey'])},{q(r['barcode'])},{q(r['oldDisplay'])},{q(r['oldShort'])},{q(r['newDisplay'])},{q(r['newShort'])})" for r in rows)

def sql(rows, operation, digest, undo=False):
    source = values(rows)
    old_d,old_s,new_d,new_s = (("new_display","new_short","old_display","old_short") if undo else ("old_display","old_short","new_display","new_short"))
    kind="title_case_undo_receipt" if undo else "title_case_receipt"
    return f"""BEGIN ISOLATION LEVEL SERIALIZABLE;
SET LOCAL lock_timeout='3s'; SET LOCAL statement_timeout='90s';
SELECT pg_advisory_xact_lock(17491, 313);
CREATE TEMP TABLE title_case_plan(id bigint PRIMARY KEY,source_key text,barcode text,old_display text,old_short text,new_display text,new_short text) ON COMMIT DROP;
INSERT INTO title_case_plan VALUES
{source};
DO $$ BEGIN
 IF current_database()<>'grun_calorie_db' OR current_setting('grun.title_case.operation',true) IS DISTINCT FROM {q(operation)}
 OR current_setting('grun.title_case.plan',true) IS DISTINCT FROM {q(digest)} THEN RAISE EXCEPTION 'Exact approved session required'; END IF;
 IF (SELECT count(*) FROM title_case_plan)<>{EXPECTED} THEN RAISE EXCEPTION 'Exact cohort required'; END IF;
 IF (SELECT count(*) FROM food_items f JOIN title_case_plan p USING(id)
     WHERE f.source_key=p.source_key AND f.barcode=p.barcode AND f.data_source='OPEN_FOOD_FACTS' AND f.publication_status='PUBLISHED'
       AND f.display_name=p.{old_d} AND f.short_display_name=p.{old_s})<>{EXPECTED} THEN RAISE EXCEPTION 'Row identity or before-image drift'; END IF;
 IF EXISTS(SELECT 1 FROM food_item_localizations l JOIN title_case_plan p ON p.id=l.food_item_id WHERE l.active) THEN RAISE EXCEPTION 'Active localization override requires separate review'; END IF;
END $$;
LOCK TABLE food_items IN SHARE ROW EXCLUSIVE MODE;
UPDATE food_items f SET display_name=p.{new_d}, short_display_name=p.{new_s} FROM title_case_plan p WHERE f.id=p.id;
SELECT jsonb_build_object('kind',{q(kind)},'owner',{q(operation)},'planDigest',{q(digest)},'count',count(*),'rows',jsonb_agg(jsonb_build_object('id',f.id,'sourceKey',f.source_key,'displayName',f.display_name,'shortDisplayName',f.short_display_name) ORDER BY f.id))
FROM food_items f JOIN title_case_plan p USING(id);
COMMIT;
"""

def build(out):
    rows=inputs(); operation="r7-title-case-"+uuid.uuid4().hex[:12]
    digest=hashlib.sha256(json.dumps(rows,ensure_ascii=False,sort_keys=True).encode()).hexdigest()
    manifest={"version":"R7_TITLE_CASE_V1","operation":operation,"planDigest":digest,"rows":rows,"count":EXPECTED,
      "snapshot":SNAPSHOT_ID,"createdAt":datetime.now(timezone.utc).isoformat(),"expiresAt":(datetime.now(timezone.utc)+timedelta(hours=6)).isoformat(),
      "reviewSha256":sha(REVIEW),"preflightSha256":sha(PREFLIGHT),"writes":["food_items.display_name","food_items.short_display_name"]}
    out.mkdir(parents=True,exist_ok=False); write(out/"manifest.json",manifest)
    (out/"apply.sql").write_text(sql(rows,operation,digest),encoding="utf-8")
    (out/"undo.sql").write_text(sql(rows,operation,digest,True),encoding="utf-8")
    write(out/"hashes.json",{"manifest":sha(out/"manifest.json"),"apply":sha(out/"apply.sql"),"undo":sha(out/"undo.sql")})
    print(json.dumps({"status":"BUILT","operation":operation,"rows":EXPECTED}))

def rehearse(folder):
    manifest=json.loads((folder/"manifest.json").read_text(encoding="utf-8")); rows=manifest["rows"]
    name="grun-title-case-"+uuid.uuid4().hex[:8]
    def docker(*args,input=None,ok=True):
        r=subprocess.run(["docker",*args],input=input.encode() if isinstance(input,str) else input,capture_output=True,timeout=120)
        if ok != (r.returncode==0): raise RuntimeError(r.stderr.decode(errors="replace")[-1200:])
        return r.stdout.decode()
    try:
        docker("run","-d","--name",name,"--label","grun.test.owner="+manifest["operation"],"-e","POSTGRES_HOST_AUTH_METHOD=trust","-e","POSTGRES_DB=grun_calorie_db","postgres:18.3-alpine")
        for _ in range(60):
            if subprocess.run(["docker","exec",name,"pg_isready","-U","postgres"],capture_output=True).returncode==0: break
            time.sleep(.5)
        schema="CREATE TABLE food_items(id bigint primary key,source_key text,barcode text,data_source text,publication_status text,display_name text,short_display_name text);CREATE TABLE food_item_localizations(id bigint,food_item_id bigint,active boolean);"
        seed="".join(f"INSERT INTO food_items VALUES({r['id']},{q(r['sourceKey'])},{q(r['barcode'])},'OPEN_FOOD_FACTS','PUBLISHED',{q(r['oldDisplay'])},{q(r['oldShort'])});" for r in rows)
        docker("exec","-i",name,"psql","-X","-qAt","-v","ON_ERROR_STOP=1","-U","postgres","-d","grun_calorie_db",input=schema+seed)
        marker=f"SELECT set_config('grun.title_case.operation',{q(manifest['operation'])},false);SELECT set_config('grun.title_case.plan',{q(manifest['planDigest'])},false);"
        output=docker("exec","-i",name,"psql","-X","-qAt","-v","ON_ERROR_STOP=1","-U","postgres","-d","grun_calorie_db",input=marker+(folder/"apply.sql").read_text(encoding="utf-8"))
        receipt=next(json.loads(x) for x in output.splitlines() if x.startswith("{"));
        if receipt["count"]!=EXPECTED: raise ValueError("Apply receipt mismatch")
        docker("exec","-i",name,"psql","-X","-qAt","-v","ON_ERROR_STOP=1","-U","postgres","-d","grun_calorie_db",input=marker+(folder/"apply.sql").read_text(encoding="utf-8"),ok=False)
        output=docker("exec","-i",name,"psql","-X","-qAt","-v","ON_ERROR_STOP=1","-U","postgres","-d","grun_calorie_db",input=marker+(folder/"undo.sql").read_text(encoding="utf-8"))
        undo=next(json.loads(x) for x in output.splitlines() if x.startswith("{"));
        if undo["count"]!=EXPECTED: raise ValueError("Undo receipt mismatch")
        write(folder/"rehearsal.json",{"status":"PASS","checks":["EXACT_13_APPLY","REPEAT_REFUSED","EXACT_13_UNDO"],"applySha256":sha(folder/"apply.sql")})
        print(json.dumps({"status":"PASS","checks":3,"rows":EXPECTED}))
    finally:
        subprocess.run(["docker","rm","-f",name],capture_output=True)

def execute(folder,template):
    m=json.loads((folder/"manifest.json").read_text(encoding="utf-8")); proof=json.loads((folder/"rehearsal.json").read_text(encoding="utf-8"))
    if proof["status"]!="PASS" or proof["applySha256"]!=sha(folder/"apply.sql") or datetime.now(timezone.utc)>datetime.fromisoformat(m["expiresAt"]): raise ValueError("Unrehearsed or expired package")
    applied=folder/"applied"
    if applied.exists(): raise ValueError("Dispatch already attempted; never blind retry")
    snap=aws("rds","describe-db-snapshots","--db-snapshot-identifier",m["snapshot"])["DBSnapshots"][0]
    if snap["Status"]!="available" or not snap["Encrypted"]: raise ValueError("Available encrypted snapshot required")
    db=aws("rds","describe-db-instances","--db-instance-identifier","grun-staging-db")["DBInstances"][0]
    service=aws("ecs","describe-services","--cluster","grun-staging-cluster","--services","grun-staging-api-service")["services"][0]
    definition=json.loads(template.read_text(encoding="utf-8-sig")); c=definition["containerDefinitions"][0]; env={x["name"]:x["value"] for x in c["environment"]}
    if db["DBInstanceStatus"]!="available" or not db["StorageEncrypted"] or env.get("PGHOST")!=db["Endpoint"]["Address"] or env.get("PGDATABASE")!="grun_calorie_db" or "PGPASSWORD" in env or not any(x["name"]=="PGPASSWORD" for x in c["secrets"]): raise ValueError("DB/task safety gate failed")
    marker=f"SELECT set_config('grun.title_case.operation',{q(m['operation'])},false);SELECT set_config('grun.title_case.plan',{q(m['planDigest'])},false);\n"
    payload=base64.b64encode(gzip.compress((marker+(folder/"apply.sql").read_text(encoding="utf-8")).encode())).decode()
    definition["family"]="grun-staging-r7-title-case"; c["name"]="r7-title-case"; c["entryPoint"]=["sh","-c"]
    c["environment"]=[x for x in c["environment"] if x["name"]!="PGOPTIONS"]+[{"name":"PGOPTIONS","value":"-c default_transaction_read_only=off -c statement_timeout=90000 -c lock_timeout=3000"}]
    c["logConfiguration"]["options"].update({"awslogs-stream-prefix":"r7-title-case","mode":"blocking"})
    c["command"]=[f"printf '%s' '{payload}' | base64 -d | gzip -d | psql -X -qAt -v ON_ERROR_STOP=1; sleep 15"]
    applied.mkdir(); write(applied/"backup.json",snap); write(applied/"task-definition.json",definition)
    arn=task=None; stopped=False
    try:
        arn=aws("ecs","register-task-definition","--cli-input-json","file://"+str((applied/"task-definition.json").resolve()))["taskDefinition"]["taskDefinitionArn"]
        start=aws("ecs","run-task","--cluster","grun-staging-cluster","--task-definition",arn,"--launch-type","FARGATE","--network-configuration",json.dumps(service["networkConfiguration"]))
        task=start["tasks"][0]["taskArn"]; write(applied/"task-status.json",{"status":"STARTED_COMMIT_UNKNOWN","taskArn":task})
        for _ in range(50):
            state=aws("ecs","describe-tasks","--cluster","grun-staging-cluster","--tasks",task)["tasks"][0]
            if state["lastStatus"]=="STOPPED": stopped=True; break
            time.sleep(8)
        if not stopped or any(x.get("exitCode")!=0 for x in state["containers"]): raise ValueError("Worker failed or commit unknown")
        time.sleep(15); stream="r7-title-case/r7-title-case/"+task.rsplit("/",1)[1]
        page=aws("logs","get-log-events","--log-group-name",c["logConfiguration"]["options"]["awslogs-group"],"--log-stream-name",stream,"--start-from-head")
        receipts=[json.loads(x["message"]) for x in page["events"] if x["message"].startswith("{")]
        receipt=next(x for x in receipts if x.get("kind")=="title_case_receipt")
        if receipt["count"]!=EXPECTED or receipt["owner"]!=m["operation"]: raise ValueError("Incomplete receipt")
        write(applied/"receipt.json",receipt); (applied/"exact-undo.sql").write_text((folder/"undo.sql").read_text(encoding="utf-8"),encoding="utf-8")
        write(applied/"summary.json",{"status":"APPLIED_RECEIPT_COMPLETE","products":EXPECTED,"snapshot":m["snapshot"],"receiptSha256":sha(applied/"receipt.json"),"undoSha256":sha(applied/"exact-undo.sql")})
        print(json.dumps({"status":"APPLIED_RECEIPT_COMPLETE","products":EXPECTED,"snapshot":m["snapshot"]}))
    finally:
        if task and not stopped: aws("ecs","stop-task","--cluster","grun-staging-cluster","--task",task,"--reason","Owned title-case timeout; reconcile before retry")
        if arn: aws("ecs","deregister-task-definition","--task-definition",arn)

if __name__=="__main__":
    p=argparse.ArgumentParser(); sub=p.add_subparsers(dest="cmd",required=True)
    b=sub.add_parser("build"); b.add_argument("--output",type=Path,required=True)
    r=sub.add_parser("rehearse"); r.add_argument("--package",type=Path,required=True)
    e=sub.add_parser("execute"); e.add_argument("--package",type=Path,required=True); e.add_argument("--template",type=Path,required=True); e.add_argument("--execute-approved",action="store_true")
    a=p.parse_args()
    if a.cmd=="build": build(a.output)
    elif a.cmd=="rehearse": rehearse(a.package)
    elif not a.execute_approved: raise ValueError("Explicit approval required")
    else: execute(a.package,a.template)

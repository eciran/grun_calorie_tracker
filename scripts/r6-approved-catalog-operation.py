"""Exact, reversible R6 names/categories operation; no deploy or broad backfill."""
import argparse
import base64
import gzip
import importlib.util
import json
import os
import re
import shutil
import subprocess
import time
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


prepare = module("prepare", "prepare-r6-category-rehearsal.py")
transport = module("transport", "collect-r3-source-snapshot.py")
sha = prepare.r6.everyday.sha
literal, text = prepare.literal, prepare.text
TABLES = {
    "aliases": ("food_item_search_aliases", "food_item_id", "id"),
    "localizations": ("food_item_localizations", "food_item_id", "id"),
    "options": ("food_item_serving_options", "food_item_id", "id"),
}


def aws(*command):
    result = subprocess.run(["aws", *command, "--profile", "grun-staging", "--region", "eu-west-1", "--output", "json"],
                            capture_output=True, encoding="utf-8", timeout=45,
                            env={**os.environ, "AWS_PAGER": "", "PYTHONUTF8": "1"})
    if result.returncode:
        raise ValueError("AWS operation failed: " + "/".join(command[:2]))
    return json.loads(result.stdout)


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def guard(package):
    owner = package["operation"]
    if not re.fullmatch(r"r6-approved-[a-f0-9]{12}", owner):
        raise ValueError("Exact operation marker required")
    return f"""BEGIN ISOLATION LEVEL READ COMMITTED;
SET LOCAL statement_timeout='90s'; SET LOCAL lock_timeout='3s';
DO $$ BEGIN
 IF current_database()<>'grun_calorie_db' OR current_setting('grun.r6.operation',true) IS DISTINCT FROM {text(owner)}
 THEN RAISE EXCEPTION 'Approved exact database/session required'; END IF;
 IF now()>{text(package['expiresAt'])}::timestamptz THEN RAISE EXCEPTION 'Approved package expired'; END IF;
 IF (SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1) IS DISTINCT FROM '305'
 THEN RAISE EXCEPTION 'Schema drift'; END IF;
END $$;
SELECT pg_advisory_xact_lock(17491,310);
"""


def name_state(identifier="p.id"):
    parts = [f"'id',({identifier})::text", f"'food',(SELECT to_jsonb(f) FROM food_items f WHERE f.id={identifier})",
             f"'assignments',coalesce((SELECT jsonb_agg(to_jsonb(a)||jsonb_build_object('slug',c.slug,'active',c.active) ORDER BY a.category_id) FROM food_item_categories a JOIN food_categories c ON c.id=a.category_id WHERE a.food_item_id={identifier}),'[]'::jsonb)",
             f"'source_tags',coalesce((SELECT jsonb_agg(s.category_tag ORDER BY s.category_tag) FROM food_item_source_categories s WHERE s.food_item_id={identifier}),'[]'::jsonb)",
             f"'evidence',coalesce((SELECT jsonb_agg(to_jsonb(e) ORDER BY e.id) FROM food_product_source_evidence e WHERE e.food_item_id={identifier}),'[]'::jsonb)"]
    children = [f"'{key}',coalesce((SELECT jsonb_agg(to_jsonb(c) ORDER BY c.id) FROM {table} c WHERE c.food_item_id={identifier}),'[]'::jsonb)"
                for key, (table, _, _) in TABLES.items()]
    children.append(f"'option_localizations',coalesce((SELECT jsonb_agg(to_jsonb(l) ORDER BY l.id) FROM food_item_serving_option_localizations l JOIN food_item_serving_options o ON o.id=l.serving_option_id WHERE o.food_item_id={identifier}),'[]'::jsonb)")
    return "jsonb_build_object(" + ",".join(parts) + ",'name_review',jsonb_build_object(" + ",".join(children) + "))"


def name_before(rows):
    plan = {"rows": [{"id": r["id"], "expectedBefore": r} for r in rows]}
    sql = prepare.before_guard(plan)
    # Parent FOR UPDATE blocks FK inserts. Lock serving parents as well to
    # prevent new serving-label children racing the exact before-image check.
    for table in ("food_item_search_aliases", "food_item_localizations", "food_item_serving_options"):
        sql += f"SELECT a.id FROM {table} a JOIN r6_plan p ON p.id=a.food_item_id ORDER BY a.id FOR UPDATE OF a;\n"
    sql += "SELECT l.id FROM food_item_serving_option_localizations l JOIN food_item_serving_options o ON o.id=l.serving_option_id JOIN r6_plan p ON p.id=o.food_item_id ORDER BY l.id FOR UPDATE OF l;\n"
    return sql + f"""DO $$ DECLARE p record; BEGIN
 FOR p IN SELECT * FROM r6_plan ORDER BY id LOOP
 IF {name_state()}<>p.body THEN RAISE EXCEPTION 'Exact name/alias/portion state drift: %',p.id; END IF;
 END LOOP;
END $$;
"""


def name_apply(package):
    audits = package["aliasAudit"]
    if {r["id"] for r in audits} != {"181008", "181466"} or len(audits) != 2:
        raise ValueError("Exact two approved naming IDs required")
    sql = guard(package) + name_before([r["expectedBefore"] for r in audits])
    sql += "CREATE TEMP TABLE r6_name_receipts(id bigint PRIMARY KEY,body jsonb) ON COMMIT DROP;\n"
    for audit in audits:
        identifier = int(audit["id"])
        fields = ",".join(f"{k}={text(v)}" for k, v in audit["proposedFoodNames"].items())
        sql += f"UPDATE food_items SET {fields} WHERE id={identifier};\n"
        for locale in audit["expectedBefore"]["name_review"]["localizations"]:
            if locale["active"]:
                label = text(audit["proposedLocalizations"][locale["language"]])
                sql += f"UPDATE food_item_localizations SET display_name={label},short_display_name={label},updated_at=current_timestamp WHERE id={locale['id']};\n"
        for change in audit["aliasActions"]:
            sql += f"UPDATE food_item_search_aliases SET active=false WHERE id={change['before']['id']};\n"
            new = change["replaceWith"]
            if new:
                sql += f"INSERT INTO food_item_search_aliases(food_item_id,alias,normalized_alias,language,alias_type,source,active) VALUES({identifier},{text(new['alias'])},{text(new['normalized_alias'])},{text(new['language'])},'ADMIN_MANUAL','r6_name_review_v1',true);\n"
        sql += f"INSERT INTO r6_name_receipts SELECT {identifier},{name_state(str(identifier))};\n"
    sql += f"SELECT jsonb_build_object('kind','name_receipts','owner',{text(package['operation'])},'rows',(SELECT jsonb_agg(body ORDER BY id) FROM r6_name_receipts));\nCOMMIT;\n"
    return sql


def validate_names(package, rows):
    by_id = {str(r["id"]): r for r in rows}
    if len(rows) != 2 or len(by_id) != 2:
        raise ValueError("Two complete naming receipts required")
    for audit in package["aliasAudit"]:
        after = by_id[audit["id"]]
        before = audit["expectedBefore"]
        if after["food"] != {**before["food"], **audit["proposedFoodNames"]}:
            raise ValueError("Non-name food data changed")
        for key in ("assignments", "source_tags", "evidence"):
            if after[key] != before[key]:
                raise ValueError("Non-name classification/evidence changed")
        child, old = after["name_review"], before["name_review"]
        for key in ("options", "option_localizations"):
            if child[key] != old[key]:
                raise ValueError("Serving data changed")
        locales = {l["id"]: l for l in child["localizations"]}
        if set(locales) != {l["id"] for l in old["localizations"]}:
            raise ValueError("Localization cohort changed")
        for previous in old["localizations"]:
            actual = locales[previous["id"]]
            if previous["active"]:
                expected = {**previous, "display_name": audit["proposedLocalizations"][previous["language"]],
                            "short_display_name": audit["proposedLocalizations"][previous["language"]],
                            "updated_at": actual["updated_at"]}
            else:
                expected = previous
            if actual != expected:
                raise ValueError("Localization outside approved fields changed")
        aliases = {a["id"]: a for a in child["aliases"]}
        deactivated = {c["before"]["id"] for c in audit["aliasActions"]}
        for previous in old["aliases"]:
            if aliases.get(previous["id"]) != {**previous, **({"active": False} if previous["id"] in deactivated else {})}:
                raise ValueError("Unapproved old alias change")
        added = [a for a in child["aliases"] if a["id"] not in {p["id"] for p in old["aliases"]}]
        expected = [c["replaceWith"] for c in audit["aliasActions"] if c["replaceWith"]]
        if len(added) != len(expected) or len({a["id"] for a in added}) != len(added):
            raise ValueError("Added alias exact count failed")
        normalized = sorted((a["alias"], a["normalized_alias"], a["language"]) for a in added)
        if normalized != sorted((a["alias"], a["normalized_alias"], a["language"]) for a in expected):
            raise ValueError("Added alias text mismatch")
        if any(a["source"] != "r6_name_review_v1" or a["alias_type"] != "ADMIN_MANUAL" or not a["active"]
               or a["food_item_id"] != int(audit["id"]) for a in added):
            raise ValueError("Added alias ownership failed")


def name_undo(package, receipts):
    validate_names(package, receipts)
    sql = guard(package) + name_before(receipts)
    for audit in package["aliasAudit"]:
        before = audit["expectedBefore"]
        after = next(r for r in receipts if r["id"] == audit["id"])
        names = ",".join(f"{k}={text(before['food'][k])}" for k in audit["proposedFoodNames"])
        sql += f"UPDATE food_items SET {names} WHERE id={int(audit['id'])};\n"
        for locale in before["name_review"]["localizations"]:
            if locale["active"]:
                sql += f"UPDATE food_item_localizations SET display_name={text(locale['display_name'])},short_display_name={text(locale['short_display_name'])},updated_at={text(locale['updated_at'])}::timestamp WHERE id={locale['id']};\n"
        for change in audit["aliasActions"]:
            sql += f"UPDATE food_item_search_aliases SET active=true WHERE id={change['before']['id']};\n"
        previous_ids = {a["id"] for a in before["name_review"]["aliases"]}
        for added in after["name_review"]["aliases"]:
            if added["id"] not in previous_ids:
                sql += f"DELETE FROM food_item_search_aliases WHERE id={added['id']} AND to_jsonb(food_item_search_aliases)={literal(added)};\n"
        sql += f"DO $$ BEGIN IF {name_state(str(int(audit['id'])))}<>{literal(before)} THEN RAISE EXCEPTION 'Exact naming rollback failed'; END IF; END $$;\n"
    return sql + "SELECT jsonb_build_object('kind','name_rollback','count',2);\nCOMMIT;\n"


def category_apply(package):
    plan = package["categoryPlan"]
    if len(plan["rows"]) != 578 or len({r["id"] for r in plan["rows"]}) != 578:
        raise ValueError("Exact 578 unique category IDs required")
    if {r["id"] for r in plan["rows"]} & {"181008", "181466"}:
        raise ValueError("Name corrections are not category approvals")
    targets = [{"id": int(r["id"]), "category": r["targetCategory"]["id"]} for r in plan["rows"]]
    return guard(package) + prepare.before_guard(plan) + f"""
CREATE TEMP TABLE r6_targets(id bigint PRIMARY KEY,category bigint) ON COMMIT DROP;
INSERT INTO r6_targets SELECT (j->>'id')::bigint,(j->>'category')::bigint FROM jsonb_array_elements({literal(targets)}) j;
DO $$ BEGIN
 IF (SELECT jsonb_agg(to_jsonb(c) ORDER BY c.id) FROM food_categories c)<>{literal(sorted(package['categories'].values(),key=lambda c:c['id']))}
 THEN RAISE EXCEPTION 'Taxonomy drift'; END IF;
 IF EXISTS(SELECT 1 FROM food_item_categories a JOIN r6_targets p ON p.id=a.food_item_id)
 THEN RAISE EXCEPTION 'Existing assignment/repeated apply'; END IF;
END $$;
CREATE TEMP TABLE r6_receipts ON COMMIT DROP AS WITH added AS (
 INSERT INTO food_item_categories(food_item_id,category_id,primary_category,assignment_source,confidence_score,reviewed,created_by,updated_by)
 SELECT id,category,true,'ADMIN',NULL,true,{text(package['operation'])},{text(package['operation'])} FROM r6_targets ORDER BY id RETURNING *
) SELECT to_jsonb(added) AS body FROM added;
DO $$ BEGIN IF (SELECT count(*) FROM r6_receipts)<>578 OR (SELECT count(*) FROM food_item_categories a JOIN r6_targets p ON p.id=a.food_item_id)<>578
 THEN RAISE EXCEPTION 'Exact count failure'; END IF; END $$;
SELECT jsonb_build_object('kind','category_receipts','owner',{text(package['operation'])},'rows',(SELECT jsonb_agg(body ORDER BY (body->>'food_item_id')::bigint) FROM r6_receipts));
COMMIT;
"""


def category_undo(package, receipts):
    expected = {int(r["id"]): r["targetCategory"]["id"] for r in package["categoryPlan"]["rows"]}
    if len(receipts) != 578 or {r["food_item_id"] for r in receipts} != set(expected):
        raise ValueError("Exact category receipts required")
    for row in receipts:
        if (row["created_by"] != package["operation"] or row["updated_by"] != package["operation"]
                or row["category_id"] != expected[row["food_item_id"]] or not row["reviewed"] or not row["primary_category"]
                or row["assignment_source"] != "ADMIN" or row["confidence_score"] is not None):
            raise ValueError("Receipt ownership/category mismatch")
    return guard(package) + prepare.before_guard(package["categoryPlan"]) + f"""
CREATE TEMP TABLE r6_undo(body jsonb) ON COMMIT DROP;
INSERT INTO r6_undo SELECT j FROM jsonb_array_elements({literal(receipts)}) j;
SELECT a.food_item_id FROM food_item_categories a JOIN r6_plan p ON p.id=a.food_item_id ORDER BY a.food_item_id,a.category_id FOR UPDATE OF a;
DO $$ BEGIN
 IF (SELECT count(*) FROM food_item_categories a JOIN r6_plan p ON p.id=a.food_item_id)<>578 OR
 (SELECT count(*) FROM food_item_categories a JOIN r6_undo r ON to_jsonb(a)=r.body)<>578
 THEN RAISE EXCEPTION 'Later edit/extra assignment: rollback refused'; END IF;
END $$;
CREATE TEMP TABLE r6_removed ON COMMIT DROP AS WITH removed AS (
 DELETE FROM food_item_categories a USING r6_undo r WHERE to_jsonb(a)=r.body RETURNING a.food_item_id
) SELECT * FROM removed;
DO $$ BEGIN IF (SELECT count(*) FROM r6_removed)<>578 THEN RAISE EXCEPTION 'Rollback count failed'; END IF; END $$;
SELECT jsonb_build_object('kind','category_rollback','count',578); COMMIT;
"""


def build(args):
    now = datetime.now(timezone.utc)
    folder = args.output
    if (folder / "manifest.json").exists():
        raise ValueError("Never overwrite an existing operation package")
    shutil.copytree(args.before, folder / "aws")
    current, proof, categories, plan, audits = prepare.load_inputs(now, folder)
    if now - datetime.fromisoformat(proof["time"]) > timedelta(hours=1):
        raise ValueError("Fresh current read within one hour required")
    backup = aws("rds", "describe-db-snapshots", "--db-snapshot-identifier", args.backup)["DBSnapshots"][0]
    if (backup["Status"] != "available" or not backup["Encrypted"] or backup["DBInstanceIdentifier"] != "grun-staging-db"
            or backup["EngineVersion"] != "18.3" or now - datetime.fromisoformat(backup["SnapshotCreateTime"]) > timedelta(hours=1)):
        raise ValueError("Fresh available encrypted matching backup required")
    service = aws("ecs", "describe-services", "--cluster", "grun-staging-cluster", "--services", "grun-staging-api-service")["services"][0]
    deployed = aws("ecs", "describe-task-definition", "--task-definition", service["taskDefinition"])["taskDefinition"]
    package = {"operation": "r6-approved-" + uuid.uuid4().hex[:12], "createdAt": now.isoformat(),
               "expiresAt": (now + timedelta(hours=4)).isoformat(), "schemaVersion": "305", "categoryProducts": 578,
               "approval": "USER_EXPLICIT_TWO_NAMES_AND_578_CATEGORIES_2026_10_12", "backup": backup,
               "currentRows": current, "categories": categories, "categoryPlan": plan, "aliasAudit": audits,
               "image": deployed["containerDefinitions"][0]["image"], "snapshotTime": proof["time"],
               "inputHashes": {"producer": sha(Path(__file__)), "before": sha(folder / "aws/catalog.csv"),
                               "proof": sha(folder / "aws/snapshot-proof.json"), "originalPlan": sha(prepare.r6.FOLDER / "resolved-review/application-plan.json")}}
    for kind, generator in (("names", name_apply), ("categories", category_apply)):
        path = folder / (kind + "-apply.sql")
        path.write_text(generator(package), encoding="utf-8")
        package[kind + "SqlSha256"] = sha(path)
    write(folder / "manifest.json", package)
    print(json.dumps({"status": "PREPARED", "operation": package["operation"], "names": 2, "categories": 578, "backup": args.backup}))


def execute(args):
    folder = args.package
    package = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))
    rehearsal = json.loads((folder / "rehearsal/proof.json").read_text())
    kind = args.kind
    sql_path = folder / (kind + "-apply.sql")
    output = folder / ("applied-" + kind)
    if output.exists():
        raise ValueError("Prior task exists; inspect its commit, never blindly repeat")
    if (not args.execute_approved or rehearsal["status"] != "PASS" or rehearsal["manifestSha256"] != sha(folder / "manifest.json")
            or package["inputHashes"]["producer"] != sha(Path(__file__)) or sha(sql_path) != package[kind + "SqlSha256"]
            or datetime.now(timezone.utc) > datetime.fromisoformat(package["expiresAt"])):
        raise ValueError("Exact current approved, rehearsed, unexpired package required")
    if kind == "categories":
        names = json.loads((folder / "applied-names/receipt.json").read_text(encoding="utf-8"))
        validate_names(package, names["rows"])
    db = aws("rds", "describe-db-instances", "--db-instance-identifier", "grun-staging-db")["DBInstances"][0]
    backup = aws("rds", "describe-db-snapshots", "--db-snapshot-identifier", package["backup"]["DBSnapshotIdentifier"])["DBSnapshots"][0]
    if db["DBInstanceStatus"] != "available" or db["EngineVersion"] != "18.3" or not db["StorageEncrypted"] or db["DBName"] != "grun_calorie_db":
        raise ValueError("Wrong or unavailable database")
    if backup != package["backup"]:
        raise ValueError("Exact encrypted backup metadata drift")
    service = aws("ecs", "describe-services", "--cluster", "grun-staging-cluster", "--services", "grun-staging-api-service")["services"][0]
    deployed = aws("ecs", "describe-task-definition", "--task-definition", service["taskDefinition"])["taskDefinition"]
    if deployed["containerDefinitions"][0]["image"] != package["image"]:
        raise ValueError("Deployment changed")
    definition = json.loads(args.template.read_text(encoding="utf-8-sig"))
    if len(definition["containerDefinitions"]) != 1:
        raise ValueError("Single owned SQL container required")
    container = definition["containerDefinitions"][0]
    environment = {e["name"]: e["value"] for e in container["environment"]}
    if (environment.get("PGHOST") != db["Endpoint"]["Address"] or environment.get("PGDATABASE") != db["DBName"]
            or "PGPASSWORD" in environment or not any(s["name"] == "PGPASSWORD" for s in container["secrets"])
            or not container["image"].startswith("postgres@sha256:")):
        raise ValueError("Exact endpoint and secret reference required")
    definition["family"] = "grun-staging-r6-approved-patch"
    container["name"] = "r6-approved"
    container["logConfiguration"]["options"].update({"awslogs-stream-prefix": "r6-approved", "mode": "blocking"})
    container["environment"] = [e for e in container["environment"] if e["name"] != "PGOPTIONS"] + [
        {"name": "PGOPTIONS", "value": "-c default_transaction_read_only=off -c statement_timeout=90000 -c lock_timeout=3000"}]
    sql = f"SELECT set_config('grun.r6.operation',{text(package['operation'])},false);\n" + sql_path.read_text(encoding="utf-8")
    encoded = base64.b64encode(gzip.compress(sql.encode("utf-8"))).decode()
    container["entryPoint"] = ["sh", "-c"]
    # Linux limits each individual argv entry even when the entire task
    # definition fits ECS limits. Each payload argument stays below that cap.
    container["command"] = ["""for chunk in "$@"; do printf '%s' "$chunk"; done | base64 -d | gzip -d > /tmp/r6.sql || exit 1
psql -X -qAt -v ON_ERROR_STOP=1 -f /tmp/r6.sql -o /tmp/r6.jsonl || exit 1
gzip -c /tmp/r6.jsonl | base64 -w 60000 > /tmp/chunks || exit 1
printf '{"kind":"transport","chunks":%s,"sha256":"%s"}\\n' "$(wc -l < /tmp/chunks)" "$(sha256sum /tmp/r6.jsonl | cut -d ' ' -f 1)"
awk '{print "{\\"kind\\":\\"chunk\\",\\"sequence\\":" NR ",\\"data\\":\\"" $0 "\\"}"}' /tmp/chunks
sleep 15
""", "r6-payload", *[encoded[i:i + 60000] for i in range(0, len(encoded), 60000)]]
    output.mkdir(parents=True)
    write(output / "owned-task-definition.json", definition)
    arn = task = None
    stopped = False
    try:
        arn = aws("ecs", "register-task-definition", "--cli-input-json", "file://" + str((output / "owned-task-definition.json").resolve()))["taskDefinition"]["taskDefinitionArn"]
        start = aws("ecs", "run-task", "--cluster", "grun-staging-cluster", "--task-definition", arn, "--launch-type", "FARGATE", "--network-configuration", json.dumps(service["networkConfiguration"]))
        if start.get("failures") or len(start.get("tasks", [])) != 1:
            raise ValueError("Owned task start failed")
        task = start["tasks"][0]["taskArn"]
        write(output / "task-status.json", {"status": "STARTED_COMMIT_UNKNOWN", "taskArn": task, "kind": kind})
        deadline = time.monotonic() + 360
        while time.monotonic() < deadline:
            state = aws("ecs", "describe-tasks", "--cluster", "grun-staging-cluster", "--tasks", task)["tasks"][0]
            if state["lastStatus"] == "STOPPED":
                stopped = True
                break
            time.sleep(8)
        if not stopped or any(c.get("exitCode") != 0 for c in state["containers"]):
            raise ValueError("Task failed/commit unknown; read-only recovery required")
        time.sleep(20)
        stream = "r6-approved/r6-approved/" + task.rsplit("/", 1)[1]
        messages, token = [], None
        for _ in range(100):
            command = ["logs", "get-log-events", "--log-group-name", container["logConfiguration"]["options"]["awslogs-group"], "--log-stream-name", stream, "--start-from-head"]
            if token:
                command += ["--next-token", token]
            page = aws(*command)
            messages.extend(e["message"] for e in page["events"])
            if page["nextForwardToken"] == token:
                break
            token = page["nextForwardToken"]
        frames = transport.decode_transport([json.loads(m) for m in messages if m.strip().startswith("{")])
        receipts = [r for r in frames if r.get("kind") == ("name_receipts" if kind == "names" else "category_receipts")]
        if len(receipts) != 1 or receipts[0]["owner"] != package["operation"]:
            raise ValueError("Incomplete commit receipt; never repeat")
        receipt = receipts[0]
        if kind == "names":
            validate_names(package, receipt["rows"])
            undo = name_undo(package, receipt["rows"])
        else:
            undo = category_undo(package, receipt["rows"])
        write(output / "receipt.json", receipt)
        (output / "exact-undo.sql").write_text(undo, encoding="utf-8")
        write(output / "summary.json", {"status": "APPLIED_RECEIPT_COMPLETE", "kind": kind, "products": len(receipt["rows"]),
                                      "taskArn": task, "backup": backup["DBSnapshotIdentifier"], "receiptSha256": sha(output / "receipt.json"),
                                      "undoSha256": sha(output / "exact-undo.sql")})
        print(json.dumps({"status": "APPLIED_RECEIPT_COMPLETE", "kind": kind, "products": len(receipt["rows"])}))
    finally:
        if task and not stopped:
            aws("ecs", "stop-task", "--cluster", "grun-staging-cluster", "--task", task, "--reason", "Owned R6 timeout; inspect commit before retry")
        if arn:
            aws("ecs", "deregister-task-definition", "--task-definition", arn)


def rebind(args):
    """Preserve the operation/SQL/committed receipt when only dispatch changed."""
    original = json.loads((args.original / "manifest.json").read_text(encoding="utf-8"))
    receipt_path = args.original / "applied-names/receipt.json"
    summary = json.loads(receipt_path.with_name("summary.json").read_text(encoding="utf-8"))
    if summary["status"] != "APPLIED_RECEIPT_COMPLETE" or summary["receiptSha256"] != sha(receipt_path):
        raise ValueError("Complete original name receipt required")
    receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
    if receipt["owner"] != original["operation"] or datetime.now(timezone.utc) > datetime.fromisoformat(original["expiresAt"]):
        raise ValueError("Original operation receipt/expiry drift")
    validate_names(original, receipt["rows"])
    for kind, generator in (("names", name_apply), ("categories", category_apply)):
        path = args.original / (kind + "-apply.sql")
        if sha(path) != original[kind + "SqlSha256"] or path.read_text(encoding="utf-8") != generator(original):
            raise ValueError("Rebind may not change SQL or scope")
    shutil.copytree(args.original, args.output, ignore=shutil.ignore_patterns("rehearsal"))
    original["parentManifestSha256"] = sha(args.original / "manifest.json")
    original["rebindReason"] = "CHUNKED_ECS_ARGV_TRANSPORT_ONLY_SQL_SCOPE_AND_COMMITTED_NAMES_UNCHANGED"
    original["inputHashes"]["producer"] = sha(Path(__file__))
    write(args.output / "manifest.json", original)
    print(json.dumps({"status": "TRANSPORT_REBOUND_REHEARSAL_REQUIRED", "operation": original["operation"]}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(dest="command", required=True)
    build_parser = commands.add_parser("build")
    build_parser.add_argument("--before", type=Path, required=True)
    build_parser.add_argument("--backup", required=True)
    build_parser.add_argument("--output", type=Path, required=True)
    execute_parser = commands.add_parser("execute")
    execute_parser.add_argument("--package", type=Path, required=True)
    execute_parser.add_argument("--kind", choices=["names", "categories"], required=True)
    execute_parser.add_argument("--template", type=Path, required=True)
    execute_parser.add_argument("--execute-approved", action="store_true")
    rebind_parser = commands.add_parser("rebind-transport")
    rebind_parser.add_argument("--original", type=Path, required=True)
    rebind_parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()
    {"build": build, "execute": execute, "rebind-transport": rebind}[arguments.command](arguments)

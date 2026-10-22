"""Bounded, product-only AWS snapshot. No database or service mutation."""
import argparse
import base64
import csv
import gzip
import hashlib
import json
import os
import subprocess
import time
from pathlib import Path


def decode_transport(records):
    headers = [r for r in records if r["kind"] == "transport"]
    if not headers:
        return records
    chunks = [r for r in records if r["kind"] == "chunk"]
    if len(headers) != 1 or len(chunks) != headers[0]["chunks"]:
        raise ValueError("Compressed transport is incomplete")
    ordered = sorted(chunks, key=lambda r: r["sequence"])
    if [r["sequence"] for r in ordered] != list(range(1, len(chunks) + 1)):
        raise ValueError("Compressed transport sequence gap/duplicate")
    body = gzip.decompress(base64.b64decode("".join(r["data"] for r in ordered), validate=True))
    if hashlib.sha256(body).hexdigest() != headers[0]["sha256"]:
        raise ValueError("Compressed transport checksum mismatch")
    frames = [json.loads(line) for line in body.decode("utf-8").splitlines() if line.strip().startswith('{')]
    if any(not isinstance(frame, dict) or "kind" not in frame for frame in frames):
        raise ValueError("Unexpected compressed frame")
    return frames


def snapshot_rows(records):
    starts = [r for r in records if r["kind"] == "start"]
    ends = [r for r in records if r["kind"] == "end"]
    rows = [r["row"] for r in records if r["kind"] == "row"]
    if len(starts) != 1 or len(ends) != 1 or starts[0]["readonly"] != "on" or not rows:
        raise ValueError("Missing read-only/count proof")
    if starts[0]["count"] != ends[0]["count"] or len(rows) != starts[0]["count"] or len({r["id"] for r in rows}) != len(rows):
        raise ValueError(f"Snapshot count/uniqueness failure: expected={starts[0]['count']}, end={ends[0]['count']}, received={len(rows)}, unique={len({r['id'] for r in rows})}")
    return rows, starts[0]


def release_sql(decisions_path):
    decisions = [json.loads(line) for line in decisions_path.read_text(encoding="utf-8").splitlines()]
    ids = [int(d["id"]) for d in decisions]
    if not ids or len(ids) != len(set(ids)) or any(i <= 0 for i in ids) or len(ids) > 10000:
        raise ValueError("Unique bounded positive product IDs required")
    predicate = "NOT coalesce(f.is_custom,false) AND f.id IN (" + ",".join(map(str, sorted(ids))) + ")"
    return """BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout='90s'; SET LOCAL lock_timeout='3s';
""" + f"""WITH candidates AS MATERIALIZED (SELECT f.* FROM food_items f WHERE {predicate}), frames AS (
SELECT 0 AS frame,0::bigint AS row_id,json_build_object('kind','start','time',now(),'readonly',current_setting('transaction_read_only'),'count',(SELECT count(*) FROM candidates)) AS message
UNION ALL
SELECT 1,f.id,json_build_object('kind','row','row',json_build_object(
 'id',f.id,'food',to_jsonb(f),
 'categories',coalesce((SELECT json_agg(c.category_tag ORDER BY c.category_tag) FROM food_item_source_categories c WHERE c.food_item_id=f.id),'[]'::json),
 'markets',coalesce((SELECT json_agg(c.market_region ORDER BY c.market_region) FROM food_item_market_regions c WHERE c.food_item_id=f.id),'[]'::json),
 'options',coalesce((SELECT json_agg(to_jsonb(o) ORDER BY o.id) FROM food_item_serving_options o WHERE o.food_item_id=f.id),'[]'::json),
 'option_localizations',coalesce((SELECT json_agg(to_jsonb(l) ORDER BY l.id) FROM food_item_serving_option_localizations l JOIN food_item_serving_options o ON o.id=l.serving_option_id WHERE o.food_item_id=f.id),'[]'::json),
 'evidence',coalesce((SELECT json_agg(to_jsonb(e) ORDER BY e.id) FROM food_product_source_evidence e WHERE e.food_item_id=f.id),'[]'::json),
 'references',json_build_object('recipes',(SELECT count(*) FROM recipe_ingredients WHERE food_item_id=f.id),
 'templates',(SELECT count(*) FROM meal_template_items WHERE food_item_id=f.id),
 'plans',(SELECT count(*) FROM meal_plan_items WHERE food_item_id=f.id))))
FROM candidates f
UNION ALL
SELECT 2,0::bigint,json_build_object('kind','end','count',(SELECT count(*) FROM candidates)))
SELECT message FROM frames ORDER BY frame,row_id;
ROLLBACK;
"""


def name_review_sql(decisions_path):
    rows = [json.loads(line) for line in decisions_path.read_text(encoding="utf-8").splitlines()]
    ids = [r["id"] for r in rows]
    if not 1 <= len(ids) <= 100 or any(not isinstance(i, int) or isinstance(i, bool) or i <= 0 for i in ids) or len(set(ids)) != len(ids):
        raise ValueError("Bounded unique integer naming identities required")
    aliases = set()
    for row in rows:
        for alias in row["requestedAliases"]:
            language, normalized = alias["language"], alias["normalized_alias"]
            if language not in {"EN", "TR"} or not isinstance(normalized, str) or not normalized.strip() or len(normalized) > 255:
                raise ValueError("Invalid bounded alias request")
            aliases.add((language, normalized))
    if not aliases or len(aliases) > 200:
        raise ValueError("Bounded alias evidence required")
    quote = lambda value: "'" + value.replace("'", "''") + "'"
    sql = Path(__file__).with_name("sql").joinpath("export-r6-held-name-review.sql").read_text(encoding="utf-8")
    return sql.replace("__SELECTED_IDS__", ",".join(map(str, sorted(ids)))).replace(
        "__REQUESTED_ALIASES__", ",".join("(" + quote(lang) + "," + quote(value) + ")" for lang, value in sorted(aliases)))


def identity_review_sql(decisions_path):
    rows = [json.loads(line) for line in decisions_path.read_text(encoding="utf-8").splitlines()]
    if {r['id'] for r in rows} != {49682, 181002} or len(rows) != 2:
        raise ValueError('Exact two source-bound identities required')
    sql = name_review_sql(decisions_path)
    extra = Path(__file__).with_name('sql').joinpath('export-r6-identity-dependencies.sql').read_text(encoding='utf-8')
    return sql.replace('ROLLBACK;', extra + '\nROLLBACK;')


def lifeway_review_sql(decisions_path):
    rows = [json.loads(line) for line in decisions_path.read_text(encoding='utf-8').splitlines()]
    if len(rows) != 1 or rows[0]['id'] != 181453:
        raise ValueError('Exact LIFEWAY source record required')
    extra = Path(__file__).with_name('sql').joinpath('export-r6-lifeway-brand-review.sql').read_text(encoding='utf-8')
    return name_review_sql(decisions_path).replace('ROLLBACK;', extra + '\nROLLBACK;')


def edamame_review_sql(decisions_path):
    rows = [json.loads(line) for line in decisions_path.read_text(encoding='utf-8').splitlines()]
    if len(rows) != 1 or rows[0]['id'] != 181467:
        raise ValueError('Exact Edamame source record required')
    extra = Path(__file__).with_name('sql').joinpath('export-r6-edamame-review.sql').read_text(encoding='utf-8')
    return name_review_sql(decisions_path).replace('ROLLBACK;', extra + '\nROLLBACK;')


def common_review_sql(decisions_path):
    rows = [json.loads(line) for line in decisions_path.read_text(encoding='utf-8').splitlines()]
    registry = json.loads((Path(__file__).resolve().parents[1]/'data/catalog/r6-held-name-decisions-v1.json').read_text(encoding='utf-8'))
    expected = {int(r['id']) for r in registry['entries']} | {179890,179893}
    if len(rows)!=37 or {r['id'] for r in rows}!=expected:
        raise ValueError('Exact common 35-record scope and two protected companions required')
    folder = Path(__file__).with_name('sql')
    extra = '\n'.join(folder.joinpath(file).read_text(encoding='utf-8') for file in (
        'export-r6-identity-dependencies.sql','export-r6-lifeway-brand-review.sql','export-r6-edamame-review.sql'))
    common = folder.joinpath('export-r6-common-dependencies.sql').read_text(encoding='utf-8')
    common = common.replace('__SELECTED_IDS__',','.join(map(str,sorted(expected))))
    return name_review_sql(decisions_path).replace('ROLLBACK;',extra+'\n'+common+'\nROLLBACK;')


def common_option_review_sql(decisions_path):
    sql=common_review_sql(decisions_path)
    rows=[json.loads(line) for line in decisions_path.read_text(encoding='utf-8').splitlines()]
    extra=Path(__file__).with_name('sql').joinpath('export-r5-common-option-links.sql').read_text(encoding='utf-8')
    extra=extra.replace('__SELECTED_IDS__',','.join(map(str,sorted(r['id'] for r in rows))))
    return sql.replace('ROLLBACK;',extra+'\nROLLBACK;')


def collect(args):
    def aws(*command):
        result = subprocess.run(["aws", *command, "--profile", args.profile, "--region", "eu-west-1",
                                 "--output", "json"], capture_output=True, encoding="utf-8",
                                env={**os.environ, "AWS_PAGER": "", "PYTHONUTF8": "1", "PYTHONIOENCODING": "utf-8",
                                     "AWS_CLI_OUTPUT_ENCODING": "UTF-8"}, timeout=45)
        if result.returncode:
            raise RuntimeError("AWS command failed: " + command[0] + "/" + command[1])
        return json.loads(result.stdout)

    definition = json.loads(args.template.read_text(encoding="utf-8-sig"))
    if len(definition.get("containerDefinitions", [])) != 1:
        raise ValueError("Only one read-only PostgreSQL container is allowed")
    is_r6 = args.scope in {"r6-audit", "r6-everyday", "r6-release", "r6-integrity", "r6-names", "r6-identities", "r6-lifeway", "r6-edamame", "r6-common", "r5-common", "r7-presentation", "r7-title-case"}
    is_r5 = args.scope in {"r5-servings", "r5-release", "r5-comparison"} or is_r6
    prefix = "r6-readonly" if is_r6 else "r5-readonly" if is_r5 else "r3-readonly"
    family = "grun-staging-r6-audit-readonly" if is_r6 else "grun-staging-r5-serving-readonly" if is_r5 else "grun-staging-r3-source-readonly"
    definition["family"] = family
    container = definition["containerDefinitions"][0]
    if not container.get("image", "").startswith("postgres@sha256:"):
        raise ValueError("Use a pinned PostgreSQL client image")
    if any(s["name"] == "PGPASSWORD" for s in container.get("environment", [])):
        raise ValueError("Inline database passwords are forbidden")
    if not any(s["name"] == "PGPASSWORD" for s in container.get("secrets", [])):
        raise ValueError("A secret reference, not a password literal, is required")
    container["name"] = prefix
    container["logConfiguration"]["options"]["awslogs-stream-prefix"] = prefix
    # A dropped non-blocking log event would silently lose a catalog row.
    container["logConfiguration"]["options"]["mode"] = "blocking"
    sql = """
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout='90s';
SET LOCAL lock_timeout='3s';
"""
    predicate = "NOT coalesce(is_custom,false) AND (data_source='USDA_FOODDATA' OR catalog_type IN ('GENERIC_INGREDIENT','STANDARD_PREPARED_ITEM','LOCAL_DISH'))"
    sql += f"SELECT json_build_object('kind','start','time',now(),'readonly',current_setting('transaction_read_only'),'count',(SELECT count(*) FROM food_items WHERE {predicate}));\n"
    sql += f"""SELECT json_build_object('kind','row','row', json_build_object(
      'id',id,'source_key',source_key,'name',name,'display_name',display_name,
      'catalog_type',catalog_type,'data_source',data_source,'preparation_state',preparation_state,
      'nutrition_reference_unit',nutrition_reference_unit,'barcode',barcode,
      'normalized_barcode',normalized_barcode,'market_region',market_region,
      'calories',calories,'protein',protein,'fat',fat,'carbs',carbs,
      'canonical_food_key',canonical_food_key,'source_data_type',to_jsonb(f)->>'source_data_type',
      'food_concept_key',to_jsonb(f)->>'food_concept_key','composition_variant_key',to_jsonb(f)->>'composition_variant_key'
    )) FROM food_items f WHERE {predicate} ORDER BY id;
SELECT json_build_object('kind','end','count',(SELECT count(*) FROM food_items WHERE {predicate}));
ROLLBACK;
"""
    if args.scope == "r5-servings":
        sql = Path(__file__).with_name("sql").joinpath("export-r5-serving-review.sql").read_text(encoding="utf-8")
    if args.scope == "r6-everyday":
        sql = Path(__file__).with_name("sql").joinpath("export-r6-everyday-category-review.sql").read_text(encoding="utf-8")
    if args.scope == "r6-release":
        sql = Path(__file__).with_name("sql").joinpath("export-r6-category-release-review.sql").read_text(encoding="utf-8")
    if args.scope == "r6-names":
        if not args.decisions:
            raise ValueError("Exact naming selection required")
        sql = name_review_sql(args.decisions)
    if args.scope == "r7-presentation":
        chosen = [json.loads(line) for line in args.decisions.read_text(encoding="utf-8").splitlines()]
        if len(chosen) != 5 or {r["id"] for r in chosen} != {49340,181013,181138,181140,181155}:
            raise ValueError("Exact five R7 source-presentation identities required")
        sql = name_review_sql(args.decisions).replace("ROLLBACK;",
            Path(__file__).with_name("sql").joinpath("export-r7-presentation-dependencies.sql").read_text(encoding="utf-8") + "\nROLLBACK;")
    if args.scope == "r7-title-case":
        sql = Path(__file__).with_name("sql").joinpath("export-r7-title-case-candidates.sql").read_text(encoding="utf-8")
    if args.scope == "r7-naming-metrics":
        sql = Path(__file__).with_name("sql").joinpath("export-r7-naming-metrics.sql").read_text(encoding="utf-8")
    if args.scope == "r7-naming-issues":
        sql = Path(__file__).with_name("sql").joinpath("export-r7-naming-issues.sql").read_text(encoding="utf-8")
    if args.scope == "r6-identities":
        sql = identity_review_sql(args.decisions)
    if args.scope == 'r6-lifeway':
        sql = lifeway_review_sql(args.decisions)
    if args.scope == 'r6-edamame':
        sql = edamame_review_sql(args.decisions)
    if args.scope == 'r6-common':
        sql = common_review_sql(args.decisions)
    if args.scope == 'r5-common':
        sql = common_option_review_sql(args.decisions)
    if args.scope == "r5-release":
        if not args.decisions:
            raise ValueError("Release read requires an exact decision manifest")
        sql = release_sql(args.decisions)
    if args.scope == "r5-comparison":
        if not args.decisions: raise ValueError("Exact bounded comparison selection required")
        import importlib.util
        spec = importlib.util.spec_from_file_location("r5_current_comparison_query", Path(__file__).with_name("r5-current-comparison-query.py"))
        comparison = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(comparison)
        sql = comparison.build(args.decisions)
    if args.scope == "r6-audit":
        import importlib.util
        spec = importlib.util.spec_from_file_location("r6", Path(__file__).with_name("build-r6-catalog-audit.py"))
        r6 = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(r6)
        sql = r6.build()
    if args.scope == "r6-integrity":
        import importlib.util
        spec = importlib.util.spec_from_file_location("integrity", Path(__file__).with_name("r6-post-operation-integrity.py"))
        integrity = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(integrity)
        sql = integrity.query(args.operation)
    encoded = base64.b64encode(gzip.compress(sql.encode()) if args.scope in {"r5-release", "r5-comparison"} else sql.encode()).decode()
    decode_sql = "base64 -d | gzip -d" if args.scope in {"r5-release", "r5-comparison"} else "base64 -d"
    container["entryPoint"] = ["sh", "-c"]
    container["command"] = [f"printf '%s' '{encoded}' | {decode_sql} | psql -X -q -t -A -v ON_ERROR_STOP=1 -v FETCH_COUNT=200"]
    if is_r5:
        # PostgreSQL remains read-only. Disk-backed compression avoids per-row
        # logging loss and keeps each transport event below CloudWatch's limit.
        container["command"] = [f"""printf '%s' '{encoded}' | {decode_sql} > /tmp/r5-query.sql || exit 1
psql -X -q -t -A -v ON_ERROR_STOP=1 -v FETCH_COUNT=200 -f /tmp/r5-query.sql -o /tmp/r5-rows.jsonl || exit 1
gzip -c /tmp/r5-rows.jsonl > /tmp/r5-rows.gz || exit 1
base64 -w 60000 /tmp/r5-rows.gz > /tmp/r5-chunks.txt || exit 1
chunks=$(wc -l < /tmp/r5-chunks.txt)
digest=$(sha256sum /tmp/r5-rows.jsonl | cut -d ' ' -f 1)
printf '{{"kind":"transport","chunks":%s,"sha256":"%s"}}\\n' "$chunks" "$digest"
awk '{{print "{{\\"kind\\":\\"chunk\\",\\"sequence\\":" NR ",\\"data\\":\\"" $0 "\\"}}"}}' /tmp/r5-chunks.txt
sleep 15
"""]
    container["environment"] = [s for s in container.get("environment", []) if s["name"] != "PGOPTIONS"] + [
        {"name": "PGOPTIONS", "value": "-c default_transaction_read_only=on -c statement_timeout=90000 -c lock_timeout=3000"}]
    network = aws("ecs", "describe-services", "--cluster", args.cluster, "--services", args.service)["services"][0]["networkConfiguration"]
    args.output.mkdir(parents=True, exist_ok=True)
    payload = args.output / "readonly-task-definition.json"
    payload.write_text(json.dumps(definition), encoding="utf-8")
    arn = None
    task = None
    completed = False
    try:
        if args.existing_task:
            previous = aws("ecs", "describe-tasks", "--cluster", args.cluster, "--tasks", args.existing_task)["tasks"][0]
            if ":task-definition/" + family + ":" not in previous["taskDefinitionArn"] or previous["lastStatus"] != "STOPPED":
                raise RuntimeError("Only an already stopped owned snapshot task can be recovered")
            original = aws("ecs", "describe-task-definition", "--task-definition", previous["taskDefinitionArn"])["taskDefinition"]["containerDefinitions"][0]
            if original["command"] != container["command"] or original["image"] != container["image"]:
                raise RuntimeError("Recovered task query/image differs from the requested snapshot")
            task = previous["taskArn"]
            completed = True
        else:
            arn = aws("ecs", "register-task-definition", "--cli-input-json", "file://" + str(payload.resolve()))["taskDefinition"]["taskDefinitionArn"]
            run = aws("ecs", "run-task", "--cluster", args.cluster, "--task-definition", arn,
                      "--launch-type", "FARGATE", "--network-configuration", json.dumps(network))
            if run.get("failures") or len(run.get("tasks", [])) != 1:
                raise RuntimeError("Read-only task could not be started")
            task = run["tasks"][0]["taskArn"]
            (args.output / "task-status.json").write_text(json.dumps({"taskArn": task, "scope": args.scope}), encoding="utf-8")
        deadline = time.monotonic() + 360
        while time.monotonic() < deadline:
            state = aws("ecs", "describe-tasks", "--cluster", args.cluster, "--tasks", task)["tasks"][0]
            if state["lastStatus"] == "STOPPED":
                completed = True
                break
            time.sleep(8)
        if not completed or any(c.get("exitCode") != 0 for c in state["containers"]):
            raise RuntimeError("Incomplete read-only snapshot")
        if not args.existing_task:
            time.sleep(15)
        stream = prefix + "/" + prefix + "/" + task.rsplit("/", 1)[1]
        messages = []
        token = None
        for _ in range(1000):
            command = ["logs", "get-log-events", "--log-group-name", container["logConfiguration"]["options"]["awslogs-group"],
                       "--log-stream-name", stream, "--start-from-head"]
            if token:
                command += ["--next-token", token]
            page = aws(*command)
            messages.extend(e["message"] for e in page["events"])
            next_token = page["nextForwardToken"]
            if next_token == token:
                break
            token = next_token
        else:
            raise RuntimeError("Log pagination incomplete")
        records = decode_transport([json.loads(m.strip()) for m in messages if m.strip().startswith('{')])
        rows, start = snapshot_rows(records)
        path = args.output / "catalog.csv"
        with path.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
            writer.writeheader()
            writer.writerows({key: json.dumps(value, ensure_ascii=False) if isinstance(value, (dict, list)) else value
                             for key, value in row.items()} for row in rows)
        proof = {"origin": "AWS_READ_ONLY_SNAPSHOT", "time": start["time"], "rows": len(rows),
                 "transactionReadOnly": True, "taskArn": task, "exitCode": 0,
                 "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "scope": args.scope,
                 "metrics": [r["values"] for r in records if r["kind"] == "metrics"]}
        proof["sqlSha256"] = hashlib.sha256(sql.encode()).hexdigest()
        if is_r6 or args.scope == "r5-comparison":
            proof["schemaVersion"] = start.get("schema")
        if args.scope in {"r5-release", "r5-comparison", "r6-names", "r6-identities", "r6-lifeway", "r6-edamame", "r6-common", "r5-common", "r7-presentation"}:
            proof["decisionsSha256"] = hashlib.sha256(args.decisions.read_bytes()).hexdigest()
        (args.output / "snapshot-proof.json").write_text(json.dumps(proof, indent=2), encoding="utf-8")
        print(json.dumps(proof))
    finally:
        if task and not completed:
            aws("ecs", "stop-task", "--cluster", args.cluster, "--task", task, "--reason", "R3 read-only timeout cleanup")
        if arn:
            aws("ecs", "deregister-task-definition", "--task-definition", arn)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--template", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--profile", default="grun-staging")
    parser.add_argument("--cluster", default="grun-staging-cluster")
    parser.add_argument("--service", default="grun-staging-api-service")
    parser.add_argument("--existing-task", help="Recover logs of an already completed owned R3 task")
    parser.add_argument("--scope", choices=("r3-sources", "r5-servings", "r5-release", "r5-comparison", "r6-audit", "r6-everyday", "r6-release", "r6-integrity", "r6-names", "r6-identities", "r6-lifeway", "r6-edamame", "r6-common", "r5-common", "r7-presentation", "r7-title-case", "r7-naming-metrics", "r7-naming-issues"), default="r3-sources")
    parser.add_argument("--operation", help="Exact approved R6 operation marker for a read-only post-check")
    parser.add_argument("--decisions", type=Path, help="Exact product IDs for the R5 full before-image read")
    collect(parser.parse_args())

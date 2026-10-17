"""Real PostgreSQL apply/rollback/conflict rehearsal on an owned ephemeral DB.

The fixture is the actual reviewed product before-images. No existing local or
AWS database is contacted. Owned container/data is removed in finally.
"""
import argparse
import importlib.util
import json
import subprocess
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path

spec = importlib.util.spec_from_file_location("patch", Path(__file__).with_name("build-r5-controlled-patch.py"))
patch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(patch)


def columns(records, extra):
    keys = set(extra) | {k for r in records for k in r}
    result = {}
    for key in sorted(keys):
        if not key.replace("_", "").isalnum():
            raise ValueError("Unexpected fixture column")
        values = [r[key] for r in records if r.get(key) is not None]
        result[key] = extra.get(key, "boolean" if values and isinstance(values[0], bool) else
                                "double precision" if values and isinstance(values[0], (float, int)) else "text")
    return result


def setup(rows):
    table_rows = {"food_items": [r["before"]["food"] for r in rows],
                  "food_item_serving_options": [v for r in rows for v in r["before"]["options"]],
                  "food_item_serving_option_localizations": [v for r in rows for v in r["before"]["option_localizations"]],
                  "food_product_source_evidence": [v for r in rows for v in r["before"]["evidence"]]}
    extra = {"food_items": {"id": "bigint PRIMARY KEY", "serving_size_grams": "double precision"},
             "food_item_serving_options": {"id": "bigserial PRIMARY KEY", "food_item_id": "bigint", "label": "varchar(120)",
                "unit_type": "varchar(40)", "quantity": "double precision", "gram_weight": "double precision", "ml_volume": "double precision",
                "is_default": "boolean", "source": "varchar(40)", "quality_status": "varchar(40)", "user_visible": "boolean"},
             "food_item_serving_option_localizations": {"id": "bigserial PRIMARY KEY", "serving_option_id": "bigint", "language": "varchar(10)",
                "label": "varchar(120)", "source": "varchar(60)", "active": "boolean", "created_at": "timestamp", "updated_at": "timestamp"},
             "food_product_source_evidence": {"id": "bigserial PRIMARY KEY", "food_item_id": "bigint", "provider": "varchar(40)",
                "external_id": "varchar(255)", "field_name": "varchar(50)", "numeric_value": "double precision", "basis": "varchar(30)",
                "confidence_score": "integer", "fingerprint": "varchar(64) UNIQUE", "source_version": "varchar(120)",
                "reviewer_identity": "varchar(255)", "observed_at": "timestamp", "created_at": "timestamp"}}
    sql = []
    for table, data in table_rows.items():
        sql.append(f"CREATE TABLE {table}(" + ",".join(f'"{k}" {v}' for k,v in columns(data, extra[table]).items()) + ");")
        if data:
            sql.append(f"INSERT INTO {table} SELECT * FROM jsonb_populate_recordset(null::{table},{patch.literal(data)});")
        if table != "food_items":
            sql.append(f"SELECT setval(pg_get_serial_sequence('{table}','id'),coalesce((SELECT max(id) FROM {table}),0)+1,false);")
    for table in ("recipe_ingredients", "meal_template_items", "meal_plan_items", "food_logs"):
        sql.append(f"CREATE TABLE {table}(food_item_id bigint, food_id bigint, serving_option_id bigint, created_at timestamp);")
        if table != "food_logs":
            key = {"recipe_ingredients":"recipes", "meal_template_items":"templates", "meal_plan_items":"plans"}[table]
            for row in rows:
                for _ in range(row["before"]["references"][key]):
                    sql.append(f"INSERT INTO {table}(food_item_id) VALUES({int(row['id'])});")
    return "\n".join(sql)


def run(args):
    manifest = json.loads((args.package / "manifest.json").read_text())
    rows = [r for r in json.loads(args.review.read_text(encoding="utf-8"))["rows"] if r["status"] == "READY_FOR_BACKUP_REHEARSAL"]
    for batch in manifest["batches"]:
        if patch.gate.source.r5.closure.sha(args.package / batch["file"]) != batch["sha256"]:
            raise ValueError("SQL batch changed")
    args.output.mkdir(parents=True, exist_ok=True)
    owner = "r5-release-rehearsal-20261012"
    container = None
    def docker(*command, sql=None, expected=0):
        result = subprocess.run(["docker", *command], input=sql.encode() if sql is not None else None, capture_output=True, timeout=90)
        if expected == 0 and result.returncode or expected != 0 and not result.returncode:
            raise ValueError("Disposable PostgreSQL rehearsal failed: " + result.stderr.decode("utf-8",errors="replace")[:1200])
        return result.stdout.decode("utf-8")
    def execute(sql, expected=0):
        return docker("exec", "-i", container, "psql", "-X", "-q", "-t", "-A", "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", "r5test", sql=sql, expected=expected)
    tests = 0
    try:
        container = docker("run", "-d", "--label", "grun.test.owner=" + owner, "--tmpfs", "/var/lib/postgresql/data",
                           "-e", "POSTGRES_PASSWORD=r5-disposable-only", "-e", "POSTGRES_DB=r5test", "postgres:17").strip()
        for _ in range(30):
            probe = subprocess.run(["docker","exec",container,"pg_isready","-U","postgres","-d","r5test"],capture_output=True)
            if probe.returncode == 0:
                break
            time.sleep(1)
        execute(setup(rows))
        all_receipts = []
        for batch in manifest["batches"]:
            text = execute((args.package / batch["file"]).read_text(encoding="utf-8"))
            receipts = [json.loads(line)["row"] for line in text.splitlines() if line.startswith('{"kind": "row"') or line.startswith('{"kind" : "row"')]
            if len(receipts) != batch["products"]:
                raise ValueError("Incomplete rehearsal receipt")
            all_receipts.extend(receipts)
            execute((args.package / batch["file"]).read_text(encoding="utf-8"), expected=1)
            tests += 2
        for receipt in all_receipts:
            before, after = receipt["before"]["food"], receipt["after"]["food"]
            if any(before.get(field) != after.get(field) for field in patch.gate.FIELDS):
                raise ValueError("Nutrient number changed")
        tests += 1
        # Later use of an operation-owned portion must refuse rollback.
        portion = next(r for r in all_receipts if r["insertedOptionIds"])
        execute(f"INSERT INTO food_logs(food_id,serving_option_id,created_at) VALUES({int(portion['id'])},{portion['insertedOptionIds'][0]},now() AT TIME ZONE 'UTC');")
        rollback, _ = patch.generate([portion], portion["operation"], manifest["expiresAt"], True)
        execute(rollback, expected=1)
        execute("DELETE FROM food_logs;")
        tests += 1
        execute(f"INSERT INTO food_logs(food_id,created_at) VALUES({int(portion['id'])},now() AT TIME ZONE 'UTC');")
        execute(rollback, expected=1)
        execute("DELETE FROM food_logs;")
        tests += 1
        for batch in reversed(manifest["batches"]):
            group = [r for r in all_receipts if r["operation"] == batch["operation"]]
            rollback, _ = patch.generate(group, batch["operation"], manifest["expiresAt"], True)
            execute(rollback)
            for r in group:
                check = execute(f"SELECT to_jsonb(f)={patch.literal(r['before']['food'])} FROM food_items f WHERE id={int(r['id'])};")
                if check.strip() != 't':
                    raise ValueError("Food rollback mismatch")
                for table, key in (("food_item_serving_options","options"),("food_product_source_evidence","evidence")):
                    check = execute(f"SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY id),'[]'::jsonb)={patch.literal(r['before'][key])} FROM {table} t WHERE food_item_id={int(r['id'])};")
                    if check.strip() != 't':
                        raise ValueError("Owned row rollback mismatch")
            tests += 1
        first = rows[0]
        execute(f"UPDATE food_items SET name=name||' changed' WHERE id={int(first['id'])};")
        execute((args.package / manifest["batches"][0]["file"]).read_text(encoding="utf-8"), expected=1)
        tests += 1
        proof = {"status":"PASS", "tests":tests, "products":len(rows), "postgres":"17", "nutrientWrites":0,
                 "patchManifestSha256":patch.gate.source.r5.closure.sha(args.package / "manifest.json"),
                 "fullRdsRestoreRehearsal":False, "fixture":"ACTUAL_REVIEWED_PRODUCT_BEFORE_IMAGES"}
        (args.output / "rehearsal-proof.json").write_text(json.dumps(proof,indent=2)+"\n")
        print(json.dumps(proof,indent=2))
    finally:
        if container:
            label = docker("inspect",container,"--format",'{{index .Config.Labels "grun.test.owner"}}').strip()
            if label != owner:
                raise ValueError("Owned rehearsal container identity mismatch")
            docker("rm","-f","-v",container)


if __name__ == "__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("--review",type=Path,required=True)
    parser.add_argument("--package",type=Path,required=True)
    parser.add_argument("--output",type=Path,required=True)
    run(parser.parse_args())

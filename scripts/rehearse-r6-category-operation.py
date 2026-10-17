"""578-row snapshot-backed category apply/undo and backup/restore rehearsal.

Only Docker's owned, unexposed ephemeral PostgreSQL is used. This is a catalogue
projection, not a complete RDS schema/backup restore or backend/API acceptance.
"""
import argparse
import importlib.util
import json
import subprocess
import time
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

spec = importlib.util.spec_from_file_location("prepare", Path(__file__).with_name("prepare-r6-category-rehearsal.py"))
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)
IMAGE = "postgres@sha256:7e32e9833a6fb1c92c32552794cb6ed569d51b445a54907d35fc112ef39684db"
TABLES = ["food_items", "food_categories", "food_item_categories", "food_item_source_categories",
          "food_product_source_evidence", "food_item_search_aliases", "food_item_localizations",
          "food_item_serving_options", "food_item_serving_option_localizations"]


def fixture_columns(rows, extra):
    fields = set(extra) | {k for row in rows for k in row}
    result = {}
    for field in sorted(fields):
        if not field.replace("_", "").isalnum():
            raise ValueError("Unsafe fixture field")
        values = [row[field] for row in rows if row.get(field) is not None]
        if any(isinstance(v, (dict,list)) for v in values):
            raise ValueError("Nested fixture field requires explicit schema")
        result[field] = extra.get(field, "boolean" if values and isinstance(values[0],bool)
                                 else "double precision" if values and isinstance(values[0],(int,float)) else "text")
    return result


def fixture_sql(review):
    current = review["currentRows"]
    sentinel = dict(current[0]["food"], id=9000000, name="Untouched custom fixture", source_key="fixture:untouched", is_custom=True)
    payloads = {"food_items": [r["food"] for r in current] + [sentinel],
                "food_product_source_evidence": [e for r in current for e in r["evidence"]],
                "food_item_search_aliases": [e for r in current if r["name_review"] for e in r["name_review"]["aliases"]],
                "food_item_localizations": [e for r in current if r["name_review"] for e in r["name_review"]["localizations"]],
                "food_item_serving_options": [e for r in current if r["name_review"] for e in r["name_review"]["options"]],
                "food_item_serving_option_localizations": [e for r in current if r["name_review"] for e in r["name_review"]["option_localizations"]]}
    sql = []
    for table, rows in payloads.items():
        extra = {"id":"bigint PRIMARY KEY"}
        if table != "food_items":
            if table == "food_item_serving_option_localizations":
                extra["serving_option_id"] = "bigint NOT NULL REFERENCES food_item_serving_options(id)"
            else:
                extra["food_item_id"] = "bigint NOT NULL REFERENCES food_items(id)"
        sql.append(f"CREATE TABLE {table}(" + ",".join(f'"{k}" {t}' for k,t in fixture_columns(rows,extra).items()) + ");")
        if rows:
            sql.append(f"INSERT INTO {table} SELECT * FROM jsonb_populate_recordset(NULL::{table},{prepare.literal(rows)});")
        if table != "food_items" and table != "food_item_serving_option_localizations":
            sql.append(f"CREATE INDEX ON {table}(food_item_id);")
    # Actual category assignment constraints from V286; other tables above are
    # snapshot-backed projections, not a claim of complete production-schema parity.
    sql.append("""CREATE TABLE food_categories(
 id bigint PRIMARY KEY,parent_id bigint REFERENCES food_categories(id),slug varchar(160) NOT NULL UNIQUE,
 name_en varchar(255) NOT NULL,name_tr varchar(255) NOT NULL,description_en varchar(1000),description_tr varchar(1000),
 icon_key varchar(120),image_url varchar(1000),sort_order integer NOT NULL DEFAULT 0,active boolean NOT NULL DEFAULT true,
 created_at timestamp NOT NULL DEFAULT current_timestamp,updated_at timestamp NOT NULL DEFAULT current_timestamp,
 created_by varchar(255),updated_by varchar(255),CHECK(parent_id IS NULL OR parent_id<>id));
CREATE TABLE food_item_categories(
 food_item_id bigint NOT NULL REFERENCES food_items(id) ON DELETE CASCADE,
 category_id bigint NOT NULL REFERENCES food_categories(id),primary_category boolean NOT NULL DEFAULT false,
 assignment_source varchar(40) NOT NULL CHECK(assignment_source IN ('ADMIN','SOURCE_MAPPING','IMPORT','AI_SUGGESTION')),
 confidence_score integer CHECK(confidence_score IS NULL OR confidence_score BETWEEN 0 AND 100),reviewed boolean NOT NULL DEFAULT false,
 created_at timestamp NOT NULL DEFAULT current_timestamp,updated_at timestamp NOT NULL DEFAULT current_timestamp,
 created_by varchar(255),updated_by varchar(255),PRIMARY KEY(food_item_id,category_id));
CREATE UNIQUE INDEX ON food_item_categories(food_item_id) WHERE primary_category;
CREATE TABLE food_item_source_categories(food_item_id bigint REFERENCES food_items(id),category_tag text,
 PRIMARY KEY(food_item_id,category_tag));
CREATE UNIQUE INDEX ON food_item_search_aliases(food_item_id,normalized_alias,language);
CREATE UNIQUE INDEX ON food_item_localizations(food_item_id,language);
CREATE UNIQUE INDEX ON food_item_serving_option_localizations(serving_option_id,language);
""")
    sql.append(f"INSERT INTO food_categories SELECT * FROM jsonb_populate_recordset(NULL::food_categories,{prepare.literal(sorted(review['categories'].values(),key=lambda c:c['id']))});")
    tags = [{"food_item_id":int(r["id"]),"category_tag":tag} for r in current for tag in r["source_tags"]]
    if tags:
        sql.append(f"INSERT INTO food_item_source_categories SELECT * FROM jsonb_populate_recordset(NULL::food_item_source_categories,{prepare.literal(tags)});")
    if any(r["assignments"] for r in current):
        raise ValueError("Fixture needs explicit handling of existing assignments")
    sql.append("INSERT INTO food_item_categories(food_item_id,category_id,primary_category,assignment_source,created_by,updated_by) VALUES(9000000,1,true,'ADMIN','fixture-sentinel','fixture-sentinel');")
    return "\n".join(sql)


def state_sql():
    parts = []
    for table in TABLES:
        ordering = "food_item_id,category_id" if table == "food_item_categories" else "food_item_id,category_tag" if table == "food_item_source_categories" else "id"
        parts.append(f"'{table}',(SELECT md5(coalesce(jsonb_agg(to_jsonb(t) ORDER BY {ordering}),'[]'::jsonb)::text) FROM {table} t)")
    return "SELECT jsonb_build_object(" + ",".join(parts) + ");"


def run(args):
    review = json.loads(args.review.read_text(encoding="utf-8"))
    if review["liveApplyAllowed"] or review["categoryProducts"] != 578 or review["schemaVersion"] != "305":
        raise ValueError("Rehearsal-only exact cohort required")
    if review["inputHashes"]["producer"] != prepare.r6.everyday.sha(Path(__file__).with_name("prepare-r6-category-rehearsal.py")):
        raise ValueError("Prepared review producer drift")
    plan, categories = review["categoryPlan"], review["categories"]
    owner = "r6-rehearsal-" + uuid.uuid4().hex[:12]
    expires = (datetime.now(timezone.utc) + timedelta(hours=1)).isoformat()
    apply = prepare.apply_sql(plan,categories,owner,expires)
    container = None
    args.output.mkdir(parents=True,exist_ok=True)
    checks = []
    def docker(*command, stdin=None, failure=False):
        result = subprocess.run(["docker",*command],input=stdin.encode("utf-8") if stdin is not None else None,
                                capture_output=True,timeout=120)
        if (not failure and result.returncode) or (failure and not result.returncode):
            raise ValueError("Isolated rehearsal command failed: " + result.stderr.decode("utf-8",errors="replace")[-1800:])
        return result.stdout.decode("utf-8")
    def execute(sql, failure=False, database="r6_rehearsal", owned=True):
        marker = f"SELECT set_config('grun.rehearsal.owner',{prepare.text(owner)},false);\n" if owned else ""
        return docker("exec","-i",container,"psql","-X","-qAt","-v","ON_ERROR_STOP=1","-U","postgres","-d",database,
                      stdin=marker+sql,failure=failure)
    def state(database="r6_rehearsal"):
        lines = execute(state_sql(),database=database).splitlines()
        return next(json.loads(line) for line in lines if line.startswith('{'))
    def receipt(sql):
        output = execute(sql)
        frames = [json.loads(line) for line in output.splitlines() if line.startswith('{')]
        frame = next(f for f in frames if f.get("kind") == "receipts")
        if frame["owner"] != owner or len(frame["rows"]) != 578:
            raise ValueError("Incomplete owned receipt")
        return frame["rows"]
    def assert_noncategory(before):
        after = state()
        if any(after[t] != before[t] for t in TABLES if t != "food_item_categories"):
            raise ValueError("Non-category state changed")
    try:
        container = docker("run","-d","--label","grun.test.owner="+owner,"--tmpfs","/var/lib/postgresql",
                           "-e","POSTGRES_HOST_AUTH_METHOD=trust","-e","POSTGRES_DB=r6_rehearsal",IMAGE).strip()
        for _ in range(40):
            probe = subprocess.run(["docker","exec",container,"pg_isready","-U","postgres"],capture_output=True,timeout=10)
            if probe.returncode == 0:
                break
            time.sleep(.5)
        else:
            raise ValueError("Owned PostgreSQL did not start")
        version = execute("SELECT current_setting('server_version_num');").splitlines()[-1]
        if version != "180003":
            raise ValueError("PostgreSQL 18.3 parity required")
        execute(fixture_sql(review))
        before = state()
        docker("exec",container,"pg_dump","-U","postgres","-d","r6_rehearsal","-Fc","--file=/tmp/r6-before.dump")
        backup_hash = docker("exec",container,"sha256sum","/tmp/r6-before.dump").split()[0]
        docker("cp",container+":/tmp/r6-before.dump",str((args.output / "before-cohort.dump").resolve()))
        checks.append("BEFORE_COHORT_LOGICAL_BACKUP_CREATED")
        execute(apply,failure=True,owned=False)
        if state()!=before:
            raise ValueError("Wrong-session rejection changed data")
        checks.append("WRONG_SESSION_FAILS_BEFORE_WRITE")
        receipts = receipt(apply)
        assert_noncategory(before)
        checks.append("APPLY_578_NONCATEGORY_STATE_UNCHANGED")
        applied = state()
        execute(apply,failure=True)
        if state()!=applied:
            raise ValueError("Repeated apply changed state")
        checks.append("REPEATED_APPLY_REJECTED_ATOMICALLY")
        # Parent and direct-child category filters over the intended cohort.
        targets = {int(r["id"]):r["targetCategory"]["id"] for r in plan["rows"]}
        by_id = {c["id"]:c for c in categories.values()}
        for root in [c for c in categories.values() if c["parent_id"] is None]:
            expected = 0
            for category in targets.values():
                node = by_id[category]
                while node:
                    if node["id"] == root["id"]:
                        expected += 1
                        break
                    node = by_id.get(node["parent_id"])
            ids = ",".join(map(str,targets))
            count = execute(f"WITH RECURSIVE descendants AS(SELECT id FROM food_categories WHERE id={root['id']} UNION ALL SELECT c.id FROM food_categories c JOIN descendants d ON c.parent_id=d.id) SELECT count(*) FROM food_item_categories WHERE food_item_id IN({ids}) AND primary_category AND category_id IN(SELECT id FROM descendants);").splitlines()[-1]
            if int(count)!=expected:
                raise ValueError("Parent category filter count failed")
        checks.append("ALL_ROOT_CATEGORY_FILTER_COUNTS_MATCH")
        undo = prepare.undo_sql(plan,receipts,owner,expires)
        execute(undo)
        if state()!=before:
            raise ValueError("Exact rollback did not restore baseline")
        checks.append("UNDO_578_EXACT_BASELINE_RESTORED")
        execute(undo,failure=True)
        if state()!=before:
            raise ValueError("Repeated undo changed state")
        checks.append("REPEATED_UNDO_REJECTED")
        first = plan["rows"][0]
        identifier = int(first["id"])
        execute(f"UPDATE food_items SET name=name||' fixture drift' WHERE id={identifier};")
        drift = state()
        execute(apply,failure=True)
        if state()!=drift:
            raise ValueError("Food drift refusal was not atomic")
        execute(f"UPDATE food_items SET name={prepare.text(first['expectedBefore']['food']['name'])} WHERE id={identifier};")
        checks.append("FOOD_DRIFT_REFUSED_ATOMICALLY")
        evidence = first["expectedBefore"]["evidence"][0]
        execute(f"UPDATE food_product_source_evidence SET numeric_value=numeric_value+1 WHERE id={int(evidence['id'])};")
        drift = state()
        execute(apply,failure=True)
        if state()!=drift:
            raise ValueError("Evidence drift refusal was not atomic")
        execute(f"UPDATE food_product_source_evidence SET numeric_value={float(evidence['numeric_value'])} WHERE id={int(evidence['id'])};")
        checks.append("SOURCE_EVIDENCE_DRIFT_REFUSED")
        category = first["targetCategory"]["id"]
        execute(f"UPDATE food_categories SET active=false WHERE id={category};")
        drift = state()
        execute(apply,failure=True)
        if state()!=drift:
            raise ValueError("Taxonomy drift refusal was not atomic")
        execute(f"UPDATE food_categories SET active=true WHERE id={category};")
        checks.append("INACTIVE_TAXONOMY_REFUSED")
        execute(f"INSERT INTO food_item_categories(food_item_id,category_id,primary_category,assignment_source) VALUES({identifier},{category},true,'ADMIN');")
        drift = state()
        execute(apply,failure=True)
        if state()!=drift:
            raise ValueError("Existing admin assignment was overwritten")
        execute(f"DELETE FROM food_item_categories WHERE food_item_id={identifier};")
        checks.append("EXISTING_ADMIN_ASSIGNMENT_PRESERVED")
        last = int(plan["rows"][-1]["id"])
        execute(f"CREATE FUNCTION fail_last() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.food_item_id={last} THEN RAISE EXCEPTION 'Injected last-row failure'; END IF; RETURN NEW; END $$; CREATE TRIGGER fail_last BEFORE INSERT ON food_item_categories FOR EACH ROW EXECUTE FUNCTION fail_last();")
        execute(apply,failure=True)
        execute("DROP TRIGGER fail_last ON food_item_categories; DROP FUNCTION fail_last();")
        if state()!=before:
            raise ValueError("Partial insert was not rolled back")
        checks.append("LAST_ROW_FAILURE_ROLLS_BACK_WHOLE_OPERATION")
        receipts = receipt(apply)
        undo = prepare.undo_sql(plan,receipts,owner,expires)
        # READ COMMITTED plus row locks sees the admin's committed row after
        # waiting. A transaction-start snapshot could miss this later edit.
        concurrent = subprocess.Popen(["docker","exec","-i",container,"psql","-X","-qAt","-v","ON_ERROR_STOP=1",
                                       "-U","postgres","-d","r6_rehearsal"],stdin=subprocess.PIPE,
                                       stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,encoding="utf-8")
        admin_sql = f"BEGIN; UPDATE food_item_categories SET confidence_score=98 WHERE food_item_id={identifier}; SELECT 'ADMIN_READY'; SELECT pg_sleep(1); COMMIT;\n"
        concurrent.stdin.write(admin_sql)
        concurrent.stdin.flush()
        concurrent.stdin.close()
        concurrent.stdin = None
        try:
            if concurrent.stdout.readline().strip() != "ADMIN_READY":
                raise ValueError("Concurrent admin fixture failed to become ready")
            execute(undo,failure=True)
            _,err = concurrent.communicate(timeout=10)
            if concurrent.returncode:
                raise ValueError("Concurrent admin fixture failed: " + err[-300:])
        finally:
            if concurrent.poll() is None:
                concurrent.kill()
                concurrent.communicate(timeout=10)
        if execute(f"SELECT confidence_score FROM food_item_categories WHERE food_item_id={identifier};").splitlines()[-1] != '98':
            raise ValueError("Concurrent admin edit was overwritten")
        execute(f"UPDATE food_item_categories SET confidence_score=NULL WHERE food_item_id={identifier};")
        checks.append("REAL_CONCURRENT_ADMIN_EDIT_DURING_UNDO_PRESERVED")
        execute(f"UPDATE food_item_categories SET confidence_score=99 WHERE food_item_id={identifier};")
        drift = state()
        execute(undo,failure=True)
        if state()!=drift:
            raise ValueError("Later admin edit was removed")
        execute(f"UPDATE food_item_categories SET confidence_score=NULL WHERE food_item_id={identifier};")
        checks.append("LATER_ADMIN_EDIT_REFUSES_UNDO")
        other = next(c['id'] for c in categories.values() if c['id']!=category)
        execute(f"INSERT INTO food_item_categories(food_item_id,category_id,primary_category,assignment_source) VALUES({identifier},{other},false,'ADMIN');")
        drift = state()
        execute(undo,failure=True)
        if state()!=drift:
            raise ValueError("Later secondary assignment was removed")
        execute(f"DELETE FROM food_item_categories WHERE food_item_id={identifier} AND category_id={other};")
        checks.append("LATER_SECONDARY_ASSIGNMENT_REFUSES_UNDO")
        execute(undo)
        if state()!=before:
            raise ValueError("Final rollback failed")
        docker("exec",container,"createdb","-U","postgres","r6_restore")
        docker("exec",container,"pg_restore","-U","postgres","-d","r6_restore","--exit-on-error","/tmp/r6-before.dump")
        if state("r6_restore")!=before:
            raise ValueError("Logical backup restore does not match before state")
        execute(apply,failure=True,database="r6_restore")
        checks.append("LOGICAL_BACKUP_RESTORE_EXACT_AND_WRONG_DB_REFUSED")
        (args.output / "rehearsal-only-apply.sql").write_text(apply,encoding="utf-8")
        (args.output / "rehearsal-only-undo.sql").write_text(undo,encoding="utf-8")
        proof = {"status":"PASS","postgres":"18.3","schema":"SNAPSHOT_BACKED_PROJECTION_WITH_V286_CATEGORY_CONSTRAINTS",
                 "fullRdsRestore":False,"backendApiAcceptance":False,"realConcurrentSessionsTested":True,
                 "concurrentCoverage":["ADMIN_CATEGORY_EDIT_COMMIT_DURING_UNDO_WAIT"],
                 "capturedPublishedFoods":696,"syntheticOutOfScopeSentinels":1,"appliedPerCycle":578,"undonePerCycle":578,
                 "checks":checks,"beforeState":before,"finalState":state(),"restoredState":state("r6_restore"),
                 "backupSha256":backup_hash,"reviewSha256":prepare.r6.everyday.sha(args.review),
                 "producerSha256":prepare.r6.everyday.sha(Path(__file__)),"applySqlSha256":prepare.r6.everyday.sha(args.output / "rehearsal-only-apply.sql"),
                 "undoSqlSha256":prepare.r6.everyday.sha(args.output / "rehearsal-only-undo.sql"),
                 "awsCatalogWrites":0,"liveApplyAllowed":False,"ownedContainer":container}
        (args.output / "postgres-proof.json").write_text(json.dumps(proof,indent=2),encoding="utf-8")
        print(json.dumps({"status":"PASS","postgres":"18.3","checks":len(checks),"categoryProducts":578,
                          "backupRestore":"MATCH","foodNutritionAliasesServings":"UNCHANGED","awsWrites":0}))
    finally:
        if container:
            if docker("inspect",container,"--format",'{{index .Config.Labels "grun.test.owner"}}').strip()!=owner:
                raise ValueError("Owned container cleanup refused")
            docker("rm","-f","-v",container)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--review",type=Path,default=prepare.BASE / "prepared/prepared-review.json")
    parser.add_argument("--output",type=Path,default=prepare.BASE / "postgres-rehearsal")
    run(parser.parse_args())

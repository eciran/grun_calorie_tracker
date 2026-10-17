"""Exercise the exact approved SQL on an owned, unexposed PostgreSQL 18.3."""
import argparse
import json
import subprocess
import time
from pathlib import Path

import importlib.util


def load(name, file):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(file))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


ops = load("ops", "r6-approved-catalog-operation.py")
lab = load("lab", "rehearse-r6-category-operation.py")


def run(folder):
    package = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))
    names = (folder / "names-apply.sql").read_text(encoding="utf-8")
    categories = (folder / "categories-apply.sql").read_text(encoding="utf-8")
    if ops.sha(folder / "names-apply.sql") != package["namesSqlSha256"] or ops.sha(folder / "categories-apply.sql") != package["categoriesSqlSha256"]:
        raise ValueError("SQL drift")
    output = folder / "rehearsal"
    output.mkdir()
    owner = package["operation"]
    container = None
    checks = []

    def docker(*command, stdin=None, failure=False):
        result = subprocess.run(["docker", *command], input=stdin.encode("utf-8") if stdin is not None else None,
                                capture_output=True, timeout=120)
        if (not failure and result.returncode) or (failure and not result.returncode):
            raise ValueError("Rehearsal failed: " + result.stderr.decode("utf-8", errors="replace")[-1600:])
        return result.stdout.decode("utf-8")

    def execute(sql, failure=False, marked=True):
        marker = f"SELECT set_config('grun.r6.operation',{ops.text(owner)},false);\n" if marked else ""
        return docker("exec", "-i", container, "psql", "-X", "-qAt", "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", "grun_calorie_db", stdin=marker + sql, failure=failure)

    def state():
        return next(json.loads(line) for line in execute(lab.state_sql()).splitlines() if line.startswith("{"))

    def receipt(sql, kind):
        frames = [json.loads(line) for line in execute(sql).splitlines() if line.startswith("{")]
        return next(r["rows"] for r in frames if r.get("kind") == kind)

    def refused(sql, label, marked=True):
        baseline = state()
        execute(sql, failure=True, marked=marked)
        if state() != baseline:
            raise ValueError("Refusal was not atomic: " + label)
        checks.append(label)

    try:
        container = docker("run", "-d", "--label", "grun.test.owner=" + owner, "--tmpfs", "/var/lib/postgresql",
                           "-e", "POSTGRES_HOST_AUTH_METHOD=trust", "-e", "POSTGRES_DB=grun_calorie_db", lab.IMAGE).strip()
        for _ in range(40):
            probe = subprocess.run(["docker", "exec", container, "pg_isready", "-U", "postgres"], capture_output=True, timeout=10)
            if probe.returncode == 0:
                break
            time.sleep(.5)
        else:
            raise ValueError("Owned PostgreSQL did not start")
        if execute("SELECT current_setting('server_version_num');").splitlines()[-1] != "180003":
            raise ValueError("PostgreSQL version mismatch")
        execute(lab.fixture_sql(package))
        execute("ALTER TABLE food_item_localizations ALTER COLUMN created_at TYPE timestamp USING created_at::timestamp, ALTER COLUMN updated_at TYPE timestamp USING updated_at::timestamp; ALTER TABLE food_item_search_aliases ALTER COLUMN created_at TYPE timestamp USING created_at::timestamp;")
        execute("CREATE TABLE flyway_schema_history(installed_rank int,version text,success boolean); INSERT INTO flyway_schema_history VALUES(305,'305',true); CREATE SEQUENCE r6_fixture_alias_seq; SELECT setval('r6_fixture_alias_seq',(SELECT max(id) FROM food_item_search_aliases)); ALTER TABLE food_item_search_aliases ALTER COLUMN id SET DEFAULT nextval('r6_fixture_alias_seq'); ALTER TABLE food_item_search_aliases ALTER COLUMN created_at SET DEFAULT current_timestamp;")
        baseline = state()
        docker("exec", container, "pg_dump", "-U", "postgres", "-d", "grun_calorie_db", "-Fc", "--file=/tmp/before.dump")
        docker("cp", container + ":/tmp/before.dump", str((output / "before-cohort.dump").resolve()))
        refused(names, "NAMES_WRONG_SESSION_REFUSED", marked=False)
        execute("UPDATE food_item_search_aliases SET alias=alias||' drift' WHERE id=2530;")
        refused(names, "NAME_ALIAS_DRIFT_REFUSED")
        original = next(a for a in package["aliasAudit"][0]["expectedBefore"]["name_review"]["aliases"] if a["id"] == 2530)
        execute(f"UPDATE food_item_search_aliases SET alias={ops.text(original['alias'])} WHERE id=2530;")
        execute("CREATE FUNCTION fail_name() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Injected second-product failure'; END $$; CREATE TRIGGER fail_name BEFORE UPDATE ON food_item_localizations FOR EACH ROW WHEN(OLD.food_item_id=181466) EXECUTE FUNCTION fail_name();")
        refused(names, "NAMES_SECOND_PRODUCT_FAILURE_ATOMIC")
        execute("DROP TRIGGER fail_name ON food_item_localizations; DROP FUNCTION fail_name();")
        name_rows = receipt(names, "name_receipts")
        ops.validate_names(package, name_rows)
        checks.append("TWO_NAMES_ALL_NUTRIENTS_UNITS_PREPARATION_PORTIONS_PRESERVED")
        named = state()
        refused(names, "NAMES_REPEAT_REFUSED")
        undo_names = ops.name_undo(package, name_rows)
        execute("UPDATE food_item_localizations SET display_name='Later admin edit' WHERE id=4418;")
        refused(undo_names, "NAMES_LATER_ADMIN_EDIT_BLOCKS_UNDO")
        execute(f"UPDATE food_item_localizations SET display_name={ops.text(package['aliasAudit'][0]['proposedLocalizations']['EN'])} WHERE id=4418;")
        if state() != named:
            raise ValueError("Admin test recovery failed")
        execute(undo_names)
        if state() != baseline:
            raise ValueError("Name rollback was not exact")
        checks.append("NAME_EXACT_ROLLBACK_BASELINE_MATCH")
        name_rows = receipt(names, "name_receipts")
        ops.validate_names(package, name_rows)
        named = state()
        refused(categories, "CATEGORY_WRONG_SESSION_REFUSED", marked=False)
        execute("UPDATE food_categories SET active=false WHERE id=1;")
        refused(categories, "CATEGORY_TAXONOMY_DRIFT_REFUSED")
        execute("UPDATE food_categories SET active=true WHERE id=1;")
        last = int(package["categoryPlan"]["rows"][-1]["id"])
        execute(f"CREATE FUNCTION fail_category() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.food_item_id={last} THEN RAISE EXCEPTION 'Injected final-row failure'; END IF; RETURN NEW; END $$; CREATE TRIGGER fail_category BEFORE INSERT ON food_item_categories FOR EACH ROW EXECUTE FUNCTION fail_category();")
        refused(categories, "CATEGORY_LAST_ROW_FAILURE_ATOMIC")
        execute("DROP TRIGGER fail_category ON food_item_categories; DROP FUNCTION fail_category();")
        category_rows = receipt(categories, "category_receipts")
        undo_categories = ops.category_undo(package, category_rows)
        after = state()
        if any(after[t] != named[t] for t in lab.TABLES if t != "food_item_categories"):
            raise ValueError("Category apply changed other data")
        checks.append("CATEGORY_578_NONCATEGORY_STATE_PRESERVED")
        refused(categories, "CATEGORY_REPEAT_REFUSED")
        first = int(package["categoryPlan"]["rows"][0]["id"])
        execute(f"UPDATE food_item_categories SET confidence_score=99 WHERE food_item_id={first};")
        refused(undo_categories, "CATEGORY_LATER_EDIT_BLOCKS_UNDO")
        execute(f"UPDATE food_item_categories SET confidence_score=NULL WHERE food_item_id={first};")
        concurrent = subprocess.Popen(["docker", "exec", "-i", container, "psql", "-X", "-qAt", "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", "grun_calorie_db"],
                                      stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding="utf-8")
        try:
            concurrent.stdin.write(f"BEGIN; UPDATE food_item_categories SET confidence_score=98 WHERE food_item_id={first}; SELECT 'READY'; SELECT pg_sleep(1); COMMIT;\n")
            concurrent.stdin.flush()
            concurrent.stdin.close()
            concurrent.stdin = None
            if concurrent.stdout.readline().strip() != "READY":
                raise ValueError("Concurrent writer not ready")
            execute(undo_categories, failure=True)
            _, error = concurrent.communicate(timeout=10)
            if concurrent.returncode:
                raise ValueError("Concurrent writer failed: " + error[-300:])
        finally:
            if concurrent.poll() is None:
                concurrent.kill()
                concurrent.communicate(timeout=10)
        if execute(f"SELECT confidence_score FROM food_item_categories WHERE food_item_id={first};").splitlines()[-1] != "98":
            raise ValueError("Concurrent edit overwritten")
        checks.append("REAL_CONCURRENT_ADMIN_COMMIT_PRESERVED")
        execute(f"UPDATE food_item_categories SET confidence_score=NULL WHERE food_item_id={first};")
        execute(undo_categories)
        if state() != named:
            raise ValueError("Category rollback not exact")
        execute(ops.name_undo(package, name_rows))
        if state() != baseline:
            raise ValueError("Combined rollback not exact")
        checks.append("COMBINED_CATEGORY_AND_NAME_UNDO_EXACT_BASELINE")
        docker("exec", container, "createdb", "-U", "postgres", "r6_restore")
        docker("exec", container, "pg_restore", "-U", "postgres", "-d", "r6_restore", "--exit-on-error", "/tmp/before.dump")
        restore = docker("exec", "-i", container, "psql", "-X", "-qAt", "-U", "postgres", "-d", "r6_restore", stdin=lab.state_sql())
        if next(json.loads(line) for line in restore.splitlines() if line.startswith("{")) != baseline:
            raise ValueError("Backup restore failed")
        docker("exec", "-i", container, "psql", "-X", "-qAt", "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", "r6_restore", stdin=names, failure=True)
        checks.append("LOGICAL_BACKUP_RESTORE_MATCH_WRONG_DB_REFUSED")
        ops.write(output / "proof.json", {"status": "PASS", "postgres": "18.3", "checks": checks,
                  "manifestSha256": ops.sha(folder / "manifest.json"), "producerSha256": ops.sha(Path(__file__)),
                  "namesSqlSha256": ops.sha(folder / "names-apply.sql"), "categoriesSqlSha256": ops.sha(folder / "categories-apply.sql"),
                  "beforeState": baseline, "finalState": state(), "fullRdsRestore": False,
                  "schema": "SNAPSHOT_BACKED_PROJECTION_WITH_CATEGORY_AND_ALIAS_CONSTRAINTS", "awsWrites": 0})
        print(json.dumps({"status": "PASS", "postgres": "18.3", "checks": len(checks), "names": 2, "categories": 578, "rollback": "EXACT"}))
    finally:
        if container:
            if docker("inspect", container, "--format", '{{index .Config.Labels "grun.test.owner"}}').strip() != owner:
                raise ValueError("Foreign container cleanup refused")
            docker("rm", "-f", "-v", container)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--package", type=Path, required=True)
    run(parser.parse_args().package)

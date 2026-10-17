"""Snapshot-backed name/alias apply/undo in an owned disposable PG18.3 ONLY.

No AWS access; SQL is guarded by a dedicated rehearsal DB/session. This is a
catalogue projection, not a complete production migration or backup restore.
"""
import argparse
import copy
import json
import subprocess
import time
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path
from importlib.util import module_from_spec, spec_from_file_location


def module(name, file):
    spec = spec_from_file_location(name, Path(__file__).with_name(file))
    value = module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


prepare = module("name_proof_prepare", "prepare-r6-held-names.py")
fixture = module("name_proof_fixture", "rehearse-r6-category-operation.py")
operation = module("name_proof_guards", "r6-approved-catalog-operation.py")
sql_json, text = operation.literal, operation.text


def candidates(plan):
    if (plan.get("version") != "R6_HELD_NAME_APPLICATION_PREPARATION_V1" or plan.get("liveApplyAllowed") is not False
            or len(plan["rows"]) != 35 or len({r["id"] for r in plan["rows"]}) != 35):
        raise ValueError("Exact review-only 35-product plan required")
    return [r for r in plan["rows"] if not r["nameApplicationHolds"] and r["status"] == "NAME_ALIAS_PREPARATION_COMPLETE_NOT_APPROVED"
            and r["decision"] != "SOURCE_BRAND_SCOPE_HOLD"
            and (r["foodNameChanges"] or r["localizationActions"] or r["aliasActions"])]


def apply_sql(plan, owner, expires):
    rows = candidates(plan)
    return apply_rows(rows, owner, expires)


def apply_rows(rows, owner, expires):
    """Owned rehearsal rendering; a scope-specific caller validates the exact cohort."""
    if not rows:
        raise ValueError("No change-ready rehearsal candidates")
    sql = fixture.prepare.prefix(owner, expires) + operation.name_before([r["expectedBefore"] for r in rows])
    for row in rows:
        identifier = int(row["id"])
        if row["foodNameChanges"]:
            if not set(row["foodNameChanges"]) <= {"name", "display_name", "short_display_name"}:
                raise ValueError("Only naming fields may change")
            sql += "UPDATE food_items SET " + ",".join(k + "=" + text(v) for k, v in row["foodNameChanges"].items()) + f" WHERE id={identifier};\n"
        for action in row["localizationActions"]:
            if action["action"] == "UPDATE_EXACT_ACTIVE_LOCALIZATION":
                changes = action["changes"]
                if not set(changes) <= {"display_name", "short_display_name"}:
                    raise ValueError("Only localized names may change")
                sql += "UPDATE food_item_localizations SET " + ",".join(k + "=" + text(v) for k, v in changes.items())
                sql += f",updated_at=current_timestamp::text WHERE id={int(action['before']['id'])} AND food_item_id={identifier};\n"
            elif action["action"] == "ADD_MISSING_LOCALIZATION":
                sql += f"INSERT INTO food_item_localizations(food_item_id,language,display_name,short_display_name,source,active,created_at,updated_at) VALUES({identifier},{text(action['language'])},{text(action['display_name'])},{text(action['short_display_name'])},{text(owner)},true,current_timestamp::text,current_timestamp::text);\n"
            else:
                raise ValueError("Unknown localization operation")
        for action in row["aliasActions"]:
            if action["action"] == "DEACTIVATE_EXACT_MISLEADING_ALIAS":
                sql += f"UPDATE food_item_search_aliases SET active=false WHERE id={int(action['before']['id'])} AND food_item_id={identifier};\n"
            elif action["action"] == "ADD_SOURCE_QUALIFIED_ALIAS":
                sql += f"DO $$ BEGIN IF EXISTS(SELECT 1 FROM food_item_search_aliases WHERE active AND food_item_id<>{identifier} AND language={text(action['language'])} AND normalized_alias={text(action['normalized_alias'])}) THEN RAISE EXCEPTION 'New external alias collision'; END IF; END $$;\n"
                sql += f"INSERT INTO food_item_search_aliases(food_item_id,alias,normalized_alias,language,alias_type,source,active,created_at) VALUES({identifier},{text(action['alias'])},{text(action['normalized_alias'])},{text(action['language'])},'ADMIN_MANUAL',{text(owner)},true,current_timestamp::text);\n"
            else:
                raise ValueError("Unknown alias operation")
    sql += f"SELECT jsonb_build_object('kind','receipts','owner',{text(owner)},'rows',(SELECT jsonb_agg({operation.name_state()} ORDER BY p.id) FROM r6_plan p));\nCOMMIT;\n"
    return sql


def verify(plan, receipts, owner):
    rows = candidates(plan)
    verify_rows(rows, receipts, owner)


def verify_rows(rows, receipts, owner):
    """Validate exact receipts; callers must first validate their write allowlist."""
    if len(receipts) != len(rows) or {r["id"] for r in receipts} != {r["id"] for r in rows}:
        raise ValueError("Exact changed-product receipts required")
    after = {r["id"]: r for r in receipts}
    for row in rows:
        current, before = after[row["id"]], row["expectedBefore"]
        if current["food"] != {**before["food"], **row["foodNameChanges"]}:
            raise ValueError("Unapproved food profile change")
        for key in ("assignments", "source_tags", "evidence"):
            if current[key] != before[key]:
                raise ValueError("Non-name category/source state changed")
        child, previous = current["name_review"], before["name_review"]
        for key in ("options", "option_localizations"):
            if child[key] != previous[key]:
                raise ValueError("Serving state changed")
        deactivated = {a["before"]["id"] for a in row["aliasActions"] if a["action"].startswith("DEACTIVATE")}
        aliases = {a["id"]: a for a in child["aliases"]}
        for old in previous["aliases"]:
            if aliases.get(old["id"]) != {**old, **({"active": False} if old["id"] in deactivated else {})}:
                raise ValueError("Unapproved old alias modification")
        added = [a for a in child["aliases"] if a["id"] not in {v["id"] for v in previous["aliases"]}]
        expected = [a for a in row["aliasActions"] if a["action"].startswith("ADD")]
        if (len(added) != len(expected) or any(a["source"] != owner or a["food_item_id"] != int(row["id"])
                or not a["active"] or a["alias_type"] != "ADMIN_MANUAL" for a in added)
                or sorted((a["alias"], a["normalized_alias"], a["language"]) for a in added)
                != sorted((a["alias"], a["normalized_alias"], a["language"]) for a in expected)):
            raise ValueError("Added alias membership/content/owner differs")
        updates = {a["before"]["id"]: a["changes"] for a in row["localizationActions"] if a["action"].startswith("UPDATE")}
        locales = {l["id"]: l for l in child["localizations"]}
        for old in previous["localizations"]:
            actual = locales.get(old["id"])
            if actual is None:
                raise ValueError("Missing old localization")
            expected_locale = {**old, **updates.get(old["id"], {}), **({"updated_at": actual["updated_at"]} if old["id"] in updates else {})}
            if actual != expected_locale:
                raise ValueError("Unapproved localization changes")
        inserted = [l for l in child["localizations"] if l["id"] not in {v["id"] for v in previous["localizations"]}]
        requested = [a for a in row["localizationActions"] if a["action"].startswith("ADD")]
        if (len(inserted) != len(requested) or any(l["source"] != owner or not l["active"] or l["food_item_id"] != int(row["id"]) for l in inserted)
                or sorted((l["language"], l["display_name"], l["short_display_name"]) for l in inserted)
                != sorted((a["language"], a["display_name"], a["short_display_name"]) for a in requested)):
            raise ValueError("Inserted localized names differ")


def undo_sql(plan, receipts, owner, expires):
    verify(plan, receipts, owner)
    rows = candidates(plan)
    return undo_rows(rows, receipts, owner, expires)


def undo_rows(rows, receipts, owner, expires, transaction_guard=None):
    verify_rows(rows, receipts, owner)
    after = {r["id"]: r for r in receipts}
    sql = (transaction_guard if transaction_guard is not None else fixture.prepare.prefix(owner, expires)) + operation.name_before(receipts)
    for row in rows:
        identifier = int(row["id"])
        original = row["expectedBefore"]
        if row["foodNameChanges"]:
            sql += "UPDATE food_items SET " + ",".join(k + "=" + ("NULL" if original["food"][k] is None else text(str(original["food"][k]))) for k in row["foodNameChanges"]) + f" WHERE id={identifier};\n"
        for action in row["localizationActions"]:
            if action["action"].startswith("UPDATE"):
                before = action["before"]
                changes = {k: before[k] for k in action["changes"]}
                changes["updated_at"] = before["updated_at"]
                sql += "UPDATE food_item_localizations SET " + ",".join(k + "=" + text(v) for k, v in changes.items()) + f" WHERE id={int(before['id'])};\n"
        old_alias_ids = {a["id"] for a in original["name_review"]["aliases"]}
        for alias in after[row["id"]]["name_review"]["aliases"]:
            if alias["id"] not in old_alias_ids:
                sql += f"DELETE FROM food_item_search_aliases WHERE id={int(alias['id'])} AND source={text(owner)};\n"
        for action in row["aliasActions"]:
            if action["action"].startswith("DEACTIVATE"):
                sql += f"UPDATE food_item_search_aliases SET active=true WHERE id={int(action['before']['id'])};\n"
        old_locales = {l["id"] for l in original["name_review"]["localizations"]}
        for locale in after[row["id"]]["name_review"]["localizations"]:
            if locale["id"] not in old_locales:
                sql += f"DELETE FROM food_item_localizations WHERE id={int(locale['id'])} AND source={text(owner)};\n"
    return sql + "COMMIT;\n"


def run(args):
    plan = prepare.held.read_json(args.plan)
    summary = prepare.held.read_json(args.plan.parent / "summary.json")
    if summary["planSha256"] != prepare.held.everyday.sha(args.plan) or plan["inputHashes"]["producer"] != prepare.held.everyday.sha(Path(__file__).with_name("prepare-r6-held-names.py")):
        raise ValueError("Prepared plan/producer changed")
    owner = "r6-rehearsal-" + uuid.uuid4().hex[:12]
    expires = (datetime.now(timezone.utc) + timedelta(hours=1)).isoformat()
    rows = [r["expectedBefore"] for r in plan["rows"]]
    sql = fixture.fixture_sql({"currentRows": rows, "categories": {c["slug"]: c for c in prepare.held.read_json(args.categories)["metrics"][0]["categories"]}})
    for table in ("food_item_search_aliases", "food_item_localizations"):
        sql += f"\nCREATE SEQUENCE {table}_proof_id; SELECT setval('{table}_proof_id',coalesce((SELECT max(id)+1 FROM {table}),1),false); ALTER TABLE {table} ALTER COLUMN id SET DEFAULT nextval('{table}_proof_id');"
    sql += "\nCREATE TABLE flyway_schema_history(version text,success boolean,installed_rank integer); INSERT INTO flyway_schema_history VALUES('305',true,1);"
    container, checks = None, []
    args.output.mkdir(parents=True, exist_ok=False)

    def docker(*command, stdin=None, fail=False):
        result = subprocess.run(["docker", *command], input=stdin.encode("utf-8") if stdin is not None else None, capture_output=True, timeout=120)
        if (not fail and result.returncode) or (fail and not result.returncode):
            raise ValueError("Owned name rehearsal failed: " + result.stderr.decode("utf-8", errors="replace")[-1400:])
        return result.stdout.decode("utf-8")

    def execute(sql, fail=False, owned=True, database="r6_rehearsal"):
        marker = f"SELECT set_config('grun.rehearsal.owner',{text(owner)},false);\n" if owned else ""
        return docker("exec", "-i", container, "psql", "-X", "-qAt", "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", database, stdin=marker + sql, fail=fail)

    def state():
        return next(json.loads(line) for line in execute(fixture.state_sql()).splitlines() if line.startswith("{"))

    try:
        container = docker("run", "-d", "--label", "grun.test.owner=" + owner, "--tmpfs", "/var/lib/postgresql",
            "-e", "POSTGRES_HOST_AUTH_METHOD=trust", "-e", "POSTGRES_DB=r6_rehearsal", fixture.IMAGE).strip()
        for _ in range(40):
            status = subprocess.run(["docker", "exec", container, "pg_isready", "-U", "postgres"], capture_output=True)
            if status.returncode == 0:
                break
            time.sleep(.5)
        execute(sql)
        before = state()
        apply = apply_sql(plan, owner, expires)
        execute(apply, fail=True, owned=False)
        execute(apply, fail=True, database="postgres")
        checks.extend(["wrong_owner_rejected", "wrong_database_rejected"])
        execute(apply_sql(plan, owner, (datetime.now(timezone.utc) - timedelta(minutes=1)).isoformat()), fail=True)
        if state() != before:
            raise ValueError("Expired operation mutated data")
        checks.append("expired_apply_rejected")
        output = execute(apply)
        receipts = next(json.loads(line)["rows"] for line in output.splitlines() if line.startswith("{") and json.loads(line).get("kind") == "receipts")
        verify(plan, receipts, owner)
        checks.append("exact_source_bound_names_locales_aliases_applied")
        after = state()
        if any(after[t] != before[t] for t in fixture.TABLES if t not in {"food_items", "food_item_search_aliases", "food_item_localizations"}):
            raise ValueError("Protected source/category/portion tables changed")
        checks.append("protected_tables_unchanged")
        untouched_ids = [int(r["id"]) for r in plan["rows"] if r not in candidates(plan)] + [9000000]
        initial = {int(r["id"]): r["expectedBefore"]["food"] for r in plan["rows"]}
        for identifier in untouched_ids[:-1]:
            actual = next(json.loads(line) for line in execute("SELECT " + operation.name_state(str(identifier)) + ";").splitlines() if line.startswith("{"))
            expected = next(r["expectedBefore"] for r in plan["rows"] if int(r["id"]) == identifier)
            if actual != expected:
                raise ValueError("Held/no-op product or child metadata modified")
        sentinel = next(json.loads(line) for line in execute("SELECT to_jsonb(f) FROM food_items f WHERE id=9000000;").splitlines() if line.startswith("{"))
        expected_sentinel = {**rows[0]["food"], "id": 9000000, "name": "Untouched custom fixture", "source_key": "fixture:untouched", "is_custom": True}
        if sentinel != expected_sentinel:
            raise ValueError("Out-of-scope sentinel changed")
        checks.append("held_noop_and_unrelated_rows_and_children_unchanged")
        execute(apply, fail=True)
        if state() != after:
            raise ValueError("Rejected repeat mutated data")
        checks.append("repeat_apply_rejected_atomically")
        undo = undo_sql(plan, receipts, owner, expires)
        first_option = next(o for r in candidates(plan) for o in r["expectedBefore"]["name_review"]["options"])
        execute(f"UPDATE food_item_serving_options SET quantity=quantity+1 WHERE id={int(first_option['id'])};")
        changed = state()
        execute(undo, fail=True)
        if state() != changed:
            raise ValueError("Serving-conflict undo mutated data")
        checks.append("later_portion_change_blocks_undo")
        execute(f"UPDATE food_item_serving_options SET quantity=quantity-1 WHERE id={int(first_option['id'])};")
        changed_id = int(candidates(plan)[0]["id"])
        execute(f"UPDATE food_items SET calories=calories+1 WHERE id={changed_id};")
        changed = state()
        execute(undo, fail=True)
        if state() != changed:
            raise ValueError("Conflicting undo mutated data")
        checks.append("later_profile_change_blocks_undo")
        execute(f"UPDATE food_items SET calories=calories-1 WHERE id={changed_id};")
        execute(undo)
        if state() != before:
            raise ValueError("Undo did not restore exact table contents")
        checks.append("exact_owned_undo_restores_all_tables")
        first_alias = next(a for row in candidates(plan) for a in row["expectedBefore"]["name_review"]["aliases"])
        execute(f"UPDATE food_item_search_aliases SET alias=alias||' changed' WHERE id={int(first_alias['id'])};")
        changed = state()
        execute(apply, fail=True)
        if state() != changed:
            raise ValueError("Alias-drift apply changed data")
        checks.append("current_alias_drift_blocks_apply")
        (args.output / "receipts.json").write_text(json.dumps(receipts, ensure_ascii=False) + "\n", encoding="utf-8")
        (args.output / "rehearsal-only-apply.sql").write_text(apply, encoding="utf-8")
        (args.output / "rehearsal-only-undo.sql").write_text(undo, encoding="utf-8")
        version = execute("SHOW server_version;").splitlines()[-1]
        proof = {"status": "PASS", "postgresVersion": version, "checks": checks, "changedProducts": len(candidates(plan)),
            "allReviewedProducts": 35, "planSha256": summary["planSha256"], "awsWrites": 0, "liveApplyAllowed": False,
            "fullRdsRestoreOrConcurrencyCertification": False, "fixtureScope": "Owned snapshot-backed catalogue projection"}
        (args.output / "proof.json").write_text(json.dumps(proof, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(proof, indent=2))
    finally:
        if container:
            actual = docker("inspect", "--format", '{{index .Config.Labels "grun.test.owner"}}', container).strip()
            if actual != owner:
                raise ValueError("Owned fixture cleanup refused")
            docker("rm", "-f", container)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    for key in ("plan", "categories", "output"):
        parser.add_argument("--" + key, required=True, type=Path)
    run(parser.parse_args())

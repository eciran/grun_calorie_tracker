"""Fresh read/alias audit and SQL for an isolated rehearsal ONLY.

Generated SQL requires the dedicated rehearsal database and an owned session
marker. It is deliberately not a production application command.
"""
import argparse
import csv
import importlib.util
import json
import os
import re
import subprocess
import unicodedata
from collections import Counter
from datetime import datetime, timedelta, timezone
from pathlib import Path

spec = importlib.util.spec_from_file_location("r6", Path(__file__).with_name("resolve-r6-everyday-review.py"))
r6 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(r6)
ROOT = r6.ROOT
BASE = ROOT / "outputs/r6-category-rehearsal-20261012"


def literal(value):
    return "'" + json.dumps(value, ensure_ascii=False, separators=(",", ":")).replace("'", "''") + "'::jsonb"


def text(value):
    return "'" + value.replace("'", "''") + "'"


def normalize_alias(value):
    value = value.strip().replace("ı", "i").replace("İ", "I")
    value = "".join(c for c in unicodedata.normalize("NFD", value) if not unicodedata.combining(c))
    return " ".join(value.lower().split())


def audit_name(row, decision, source):
    if (row["id"] != decision["id"] or row["food"]["source_key"] != decision["sourceKey"]
            or row["food"]["name"] != decision["currentName"] or source["description"] != decision["sourceDescription"]
            or "USDA_FOODDATA:fdc:" + str(source["fdcId"]) != decision["sourceKey"]):
        raise ValueError("Name review identity drift")
    captured = row["name_review"]
    if not isinstance(captured, dict) or set(captured) != {"aliases", "localizations", "options", "option_localizations"}:
        raise ValueError("Full alias/localization/serving before-images required")
    active_locales = [l for l in captured["localizations"] if l["active"]]
    if Counter(l["language"] for l in active_locales) != {"EN": 1, "TR": 1}:
        raise ValueError("Exact active EN/TR localization pair required")
    changes, preserved = [], []
    for alias in captured["aliases"]:
        if not alias["active"]:
            preserved.append(alias["id"])
            continue
        words = set(re.findall(r"[a-z]+", normalize_alias(alias["alias"])))
        if row["id"] == "181008" and words & {"breast", "gogsu", "gogus"}:
            changes.append({"before": alias, "action": "DEACTIVATE_UNPROVEN_BREAST_ALIAS", "replaceWith": None})
        elif row["id"] == "181466" and words & {"omelet", "omelette", "omlet", "omleti", "scrambled", "cirpilmis"}:
            # These are valid source cooking alternatives, not false identities.
            # Keep their search meaning but explicitly retain the cooking-spray qualifier.
            if alias["language"] == "EN":
                replacement = alias["alias"] + " (cooking spray)"
            elif alias["language"] == "TR":
                replacement = "Pişirme spreyiyle " + alias["alias"]
            else:
                raise ValueError("Unknown alias language needs manual review")
            normalized = normalize_alias(replacement)
            if any(a["language"] == alias["language"] and a["normalized_alias"] == normalized for a in captured["aliases"]):
                raise ValueError("Qualified alias collides with an existing active/inactive row")
            changes.append({"before": alias, "action": "REPLACE_WITH_SOURCE_QUALIFIED_ALIAS",
                            "replaceWith": {"alias": replacement, "normalized_alias": normalized, "language": alias["language"]}})
        else:
            preserved.append(alias["id"])
    return {"id": row["id"], "sourceKey": decision["sourceKey"], "expectedBefore": row,
            "beforeSha256": r6.everyday.fingerprint(row), "sourceBodySha256": r6.everyday.fingerprint(source),
            "proposedFoodNames": {k: decision["displayNameEn"] for k in ("name", "display_name", "short_display_name")},
            "proposedLocalizations": {"EN": decision["displayNameEn"], "TR": decision["displayNameTr"]},
            "aliasActions": changes, "preserveAliasIds": preserved,
            "portionDataPreserved": True, "portionSourceAccuracyCertified": False,
            "liveApplyAllowed": False, "status": "BEFORE_IMAGES_CAPTURED_R4_APPROVAL_AND_PATCH_REHEARSAL_REQUIRED"}


def load_inputs(now, folder=BASE):
    catalog, proof_path = folder / "aws/catalog.csv", folder / "aws/snapshot-proof.json"
    proof = json.loads(proof_path.read_text(encoding="utf-8"))
    rows = r6.everyday.read_csv(catalog)
    sql_path = Path(__file__).with_name("sql") / "export-r6-category-release-review.sql"
    if (proof.get("scope") != "r6-release" or proof.get("origin") != "AWS_READ_ONLY_SNAPSHOT"
            or proof.get("transactionReadOnly") is not True or proof.get("exitCode") != 0
            or proof.get("sha256") != r6.everyday.sha(catalog) or proof.get("sqlSha256") != r6.everyday.sha(sql_path)
            or proof.get("rows") != len(rows) or not r6.everyday.fresh(proof["time"], now) or proof.get("schemaVersion") != "305"):
        raise ValueError("Fresh intact schema/scope-bound release read required")
    current = [{"id": row["id"], **{k: None if k == "name_review" and row[k] == "" else json.loads(row[k])
                for k in ("food", "assignments", "source_tags", "evidence", "name_review")}} for row in rows]
    by_id = r6.everyday.unique_index(current, "id")
    if len(current) != 696 or Counter(r["food"]["catalog_type"] for r in current) != r6.everyday.TYPES:
        raise ValueError("Current 696-row cohort changed")
    if len(proof["metrics"]) != 1 or proof["metrics"][0]["orphanAssignments"] != 0:
        raise ValueError("Current category integrity failed")
    categories = r6.everyday.unique_index(proof["metrics"][0]["categories"], "slug")
    plan_path = r6.FOLDER / "resolved-review/application-plan.json"
    decisions_path = r6.FOLDER / "resolved-review/resolved-category-dry-run.json"
    plan = json.loads(plan_path.read_text(encoding="utf-8"))
    decisions = json.loads(decisions_path.read_text(encoding="utf-8"))
    for key, path in (("producer", Path(__file__).with_name("resolve-r6-everyday-review.py")),
                      ("reviewRegistry", ROOT / "data/catalog/everyday-category-review-r6-v1.json")):
        if plan["inputHashes"][key] != r6.everyday.sha(path) or decisions["inputHashes"][key] != r6.everyday.sha(path):
            raise ValueError("Reviewed policy changed")
    eligible = {r["id"]: r for r in decisions["rows"] if r["status"] == "SAFE_CATEGORY_NOMINATION"}
    if plan["expectedProducts"] != 578 or plan["expectedNewAssignments"] != 578 or len(eligible) != 578:
        raise ValueError("Expected 578 nominations changed")
    if {r["id"] for r in plan["rows"]} != set(eligible) or len(plan["rows"]) != 578:
        raise ValueError("Duplicate/missing/out-of-scope plan IDs")
    for nomination in plan["rows"]:
        row = by_id[nomination["id"]]
        core = {k: row[k] for k in ("id", "food", "assignments", "source_tags", "evidence")}
        if (core != nomination["expectedBefore"] or r6.everyday.fingerprint(core) != nomination["beforeSha256"]
                or eligible[row["id"]]["reasons"] or eligible[row["id"]]["targetSlug"] != nomination["targetSlug"]
                or nomination["targetCategory"] != categories[nomination["targetSlug"]]
                or not r6.target_active(nomination["targetSlug"], categories) or row["assignments"]):
            raise ValueError("Current nomination/category/source before-image changed: " + row["id"])
    registry = json.loads((ROOT / "data/catalog/everyday-category-review-r6-v1.json").read_text(encoding="utf-8"))
    spec = importlib.util.spec_from_file_location("closure", Path(__file__).with_name("close-r3-usda-review.py"))
    closure = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(closure)
    source_folder = ROOT / "outputs/r3-aws-source-review-20261011"
    if plan["inputHashes"]["usda"] != r6.everyday.sha(source_folder / "usda/evidence.json"):
        raise ValueError("Reviewed official source receipt changed")
    _, _, sources = closure.verify_inputs(source_folder / "catalog.csv", source_folder / "snapshot-proof.json", source_folder / "usda/evidence.json")
    if not r6.everyday.fresh(json.loads((source_folder / "usda/evidence.json").read_text())["fetchedAt"], now):
        raise ValueError("Fresh source identity receipt required")
    audits = [audit_name(by_id[d["id"]], d, sources[d["sourceKey"].rsplit(":", 1)[-1]]) for d in registry["usdaNames"] if d["decision"] == "NAME_CORRECTION_REQUIRED"]
    if len(audits) != 2:
        raise ValueError("Exact two name audits required")
    return current, proof, categories, plan, audits


def prefix(owner, expires):
    if not re.fullmatch(r"r6-rehearsal-[a-f0-9]{12}", owner):
        raise ValueError("Owned rehearsal marker required")
    datetime.fromisoformat(expires)
    return f"""-- ISOLATED REHEARSAL ONLY. Not a production patch.
BEGIN ISOLATION LEVEL READ COMMITTED;
SET LOCAL statement_timeout='90s'; SET LOCAL lock_timeout='3s';
DO $$ BEGIN
 IF current_database()<>'r6_rehearsal' OR current_setting('grun.rehearsal.owner',true) IS DISTINCT FROM {text(owner)}
 THEN RAISE EXCEPTION 'Owned isolated rehearsal database/session required'; END IF;
 IF now()>{text(expires)}::timestamptz THEN RAISE EXCEPTION 'Rehearsal package expired'; END IF;
END $$;
"""


def before_guard(plan):
    expected = [{"id": int(r["id"]), "before": r["expectedBefore"]} for r in plan["rows"]]
    return f"""CREATE TEMP TABLE r6_plan(id bigint PRIMARY KEY,body jsonb) ON COMMIT DROP;
INSERT INTO r6_plan SELECT (j->>'id')::bigint,j->'before' FROM jsonb_array_elements({literal(expected)}) j;
SELECT f.id FROM food_items f JOIN r6_plan p ON p.id=f.id ORDER BY f.id FOR UPDATE OF f;
SELECT c.id FROM food_categories c ORDER BY c.id FOR SHARE;
SELECT e.id FROM food_product_source_evidence e JOIN r6_plan p ON p.id=e.food_item_id ORDER BY e.id FOR SHARE OF e;
SELECT s.food_item_id FROM food_item_source_categories s JOIN r6_plan p ON p.id=s.food_item_id ORDER BY s.food_item_id,s.category_tag FOR SHARE OF s;
DO $$ DECLARE p record; BEGIN
 IF (SELECT count(*) FROM r6_plan) <> {len(expected)} THEN RAISE EXCEPTION 'Exact cohort count failed'; END IF;
 FOR p IN SELECT * FROM r6_plan ORDER BY id LOOP
  IF NOT EXISTS(SELECT 1 FROM food_items f WHERE f.id=p.id AND to_jsonb(f)=p.body->'food')
  THEN RAISE EXCEPTION 'Food before-image drift: %',p.id; END IF;
  IF (SELECT coalesce(jsonb_agg(s.category_tag ORDER BY s.category_tag),'[]'::jsonb) FROM food_item_source_categories s WHERE s.food_item_id=p.id) <> p.body->'source_tags'
  THEN RAISE EXCEPTION 'Source-tag drift: %',p.id; END IF;
  IF (SELECT coalesce(jsonb_agg(to_jsonb(e) ORDER BY e.id),'[]'::jsonb) FROM food_product_source_evidence e WHERE e.food_item_id=p.id) <> p.body->'evidence'
  THEN RAISE EXCEPTION 'Source-evidence drift: %',p.id; END IF;
 END LOOP;
END $$;
"""


def apply_sql(plan, categories, owner, expires):
    if len(plan["rows"]) != 578 or len({r["id"] for r in plan["rows"]}) != 578:
        raise ValueError("Exact unique 578-row plan required")
    targets = [{"id": int(r["id"]), "category": int(r["targetCategory"]["id"])} for r in plan["rows"]]
    return prefix(owner, expires) + before_guard(plan) + f"""
CREATE TEMP TABLE r6_targets(id bigint PRIMARY KEY,category bigint) ON COMMIT DROP;
INSERT INTO r6_targets SELECT (j->>'id')::bigint,(j->>'category')::bigint FROM jsonb_array_elements({literal(targets)}) j;
DO $$ BEGIN
 IF (SELECT coalesce(jsonb_agg(to_jsonb(c) ORDER BY c.id),'[]'::jsonb) FROM food_categories c) <> {literal(sorted(categories.values(),key=lambda c:c['id']))}
 THEN RAISE EXCEPTION 'Exact live taxonomy drift'; END IF;
 IF EXISTS(SELECT 1 FROM food_item_categories a JOIN r6_targets p ON p.id=a.food_item_id)
 THEN RAISE EXCEPTION 'Existing category/repeated apply requires review'; END IF;
END $$;
CREATE TEMP TABLE r6_receipts ON COMMIT DROP AS
WITH added AS (
 INSERT INTO food_item_categories(food_item_id,category_id,primary_category,assignment_source,confidence_score,reviewed,created_by,updated_by)
 SELECT id,category,true,'ADMIN',NULL,false,{text(owner)},{text(owner)} FROM r6_targets ORDER BY id RETURNING *
) SELECT to_jsonb(added) AS body FROM added;
DO $$ BEGIN
 IF (SELECT count(*) FROM r6_receipts)<>578 OR (SELECT count(*) FROM food_item_categories a JOIN r6_targets p ON p.id=a.food_item_id)<>578
 THEN RAISE EXCEPTION 'Inserted-assignment exact-count failure'; END IF;
END $$;
SELECT jsonb_build_object('kind','receipts','owner',{text(owner)},'rows',(SELECT jsonb_agg(body ORDER BY (body->>'food_item_id')::bigint) FROM r6_receipts));
COMMIT;
"""


def undo_sql(plan, receipts, owner, expires):
    ids = [r["food_item_id"] for r in receipts]
    if (len(receipts) != 578 or len(set(ids)) != 578 or set(ids) != {int(r["id"]) for r in plan["rows"]}
            or any(r["created_by"] != owner or r["updated_by"] != owner for r in receipts)):
        raise ValueError("Complete owned receipt required for rollback")
    return prefix(owner, expires) + before_guard(plan) + f"""
CREATE TEMP TABLE r6_undo(body jsonb) ON COMMIT DROP;
INSERT INTO r6_undo SELECT j FROM jsonb_array_elements({literal(receipts)}) j;
SELECT a.food_item_id FROM food_item_categories a JOIN r6_plan p ON p.id=a.food_item_id ORDER BY a.food_item_id,a.category_id FOR UPDATE OF a;
DO $$ BEGIN
 IF (SELECT count(*) FROM food_item_categories a JOIN r6_plan p ON p.id=a.food_item_id)<>578
 THEN RAISE EXCEPTION 'Rollback refused: missing/extra category assignments'; END IF;
 IF (SELECT count(*) FROM food_item_categories a JOIN r6_undo r ON to_jsonb(a)=r.body)<>578
 THEN RAISE EXCEPTION 'Rollback refused: later admin edit or ownership drift'; END IF;
END $$;
CREATE TEMP TABLE r6_removed ON COMMIT DROP AS
WITH removed AS (DELETE FROM food_item_categories a USING r6_undo r WHERE to_jsonb(a)=r.body RETURNING a.food_item_id)
SELECT * FROM removed;
DO $$ BEGIN
 IF (SELECT count(*) FROM r6_removed)<>578 THEN RAISE EXCEPTION 'Rollback exact-count failure'; END IF;
END $$;
SELECT jsonb_build_object('kind','rollback','removed',(SELECT count(*) FROM r6_removed));
COMMIT;
"""


def build(args):
    now = datetime.now(timezone.utc)
    current, proof, categories, plan, audits = load_inputs(now)
    args.output.mkdir(parents=True, exist_ok=True)
    backup = None
    if args.check_aws_backup:
        result = subprocess.run(["aws","rds","describe-db-snapshots","--db-snapshot-identifier","grun-staging-before-r5-20261012",
                                 "--profile","grun-staging","--region","eu-west-1","--output","json"], capture_output=True,timeout=45,
                                 env={**os.environ,"AWS_PAGER":""})
        if result.returncode:
            raise ValueError("AWS backup read failed")
        snapshot = json.loads(result.stdout)["DBSnapshots"][0]
        if snapshot["Status"] != "available" or not snapshot["Encrypted"] or snapshot["DBInstanceIdentifier"] != "grun-staging-db":
            raise ValueError("Available encrypted matching RDS backup required")
        backup = {"origin":"AWS_READ_ONLY_BACKUP_CHECK","checkedAt":now.isoformat(),"id":snapshot["DBSnapshotIdentifier"],
                  "available":True,"encrypted":True,"createdAt":snapshot["SnapshotCreateTime"],"engineVersion":snapshot["EngineVersion"],
                  "newSnapshotCreated":False,"currentPreApplySnapshot":False,
                  "limitation":"Predates later R5 changes. This check is not an RDS restore rehearsal or a fresh live-apply backup approval."}
    value = {"version":"R6_ALIAS_AND_CATEGORY_REHEARSAL_PREPARATION_V1","liveApplyAllowed":False,"snapshotTime":proof["time"],
             "snapshotSha256":proof["sha256"],"schemaVersion":proof["schemaVersion"],"categoryProducts":578,
             "backup":backup,"aliasAudit":audits,"categories":categories,"categoryPlan":plan,
             "currentRows":current,"inputHashes":{"producer":r6.everyday.sha(Path(__file__)),
             "resolvedPlan":r6.everyday.sha(r6.FOLDER / "resolved-review/application-plan.json"),
             "awsCatalog":r6.everyday.sha(BASE / "aws/catalog.csv"),"awsProof":r6.everyday.sha(BASE / "aws/snapshot-proof.json")}}
    (args.output / "prepared-review.json").write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding="utf-8")
    print(json.dumps({"categoryBeforeImages":"MATCH_578","nameAuditProducts":len(audits),
                      "aliasActions":Counter(a["action"] for r in audits for a in r["aliasActions"]),
                      "activeLocalizations":sum(sum(l["active"] for l in r["expectedBefore"]["name_review"]["localizations"]) for r in audits),
                      "awsBackupAvailable":backup is not None,"liveApplyAllowed":False}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output",type=Path,default=BASE / "prepared")
    parser.add_argument("--check-aws-backup",action="store_true")
    build(parser.parse_args())

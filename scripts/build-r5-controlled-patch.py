"""Small transactional R5 data patch and exact owned-row rollback generator.

Requires full review, an available encrypted RDS backup and explicit operator
approval. Nutrition numbers are never updated, old evidence/portions retained.
Run/replay refuses concurrent drift. This tool does not execute SQL or deploy.
"""
import argparse
import gzip
import hashlib
import importlib.util
import json
import re
import subprocess
from datetime import datetime, timedelta, timezone
from pathlib import Path

spec = importlib.util.spec_from_file_location("review_gate", Path(__file__).with_name("close-r5-release-review.py"))
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


def literal(value):
    return "'" + json.dumps(value, ensure_ascii=False, allow_nan=False).replace("'", "''") + "'::jsonb"


def generate(rows, operation, expires, rollback=False):
    if not re.fullmatch(r"r5-[a-z0-9-]{5,36}", operation) or not 1 <= len(rows) <= 50:
        raise ValueError("Small approved R5 operation required")
    if rollback:
        if any(r.get("operation") != operation or r.get("status") != "APPLIED" for r in rows):
            raise ValueError("Exact committed operation receipt required for rollback")
        payload = rows
    else:
        payload = []
        for row in rows:
            if row["status"] != "READY_FOR_BACKUP_REHEARSAL" or row.get("nutritionWriteAllowed") is not False:
                raise ValueError("Only full-gate ready, non-nutrition-changing rows allowed")
            if set(row["changes"]) - {"nutrition_reference_unit", "serving_size_grams", "serving_unit"}:
                raise ValueError("Forbidden food column change")
            if row["changes"] and row["changes"] != {"nutrition_reference_unit": "PER_100ML", "serving_size_grams": None, "serving_unit": "MILLILITER"}:
                raise ValueError("Only source-proven native volume correction allowed")
            after = {**row["before"]["food"], **row["changes"]}
            options = [{"label": o["labels"]["EN"], "unit_type": o["unitType"], "quantity": o["quantity"],
                        "gram_weight": o["gramWeight"], "ml_volume": o["mlVolume"], "is_default": False,
                        "source": "OPEN_FOOD_FACTS" if after["data_source"] == "OPEN_FOOD_FACTS" else "ADMIN",
                        "quality_status": "VERIFIED", "user_visible": True, "labels": o["labels"]} for o in row["options"]]
            evidence = []
            if row["changes"]:
                for field, comparison in row["nutrients"].items():
                    if comparison["source"] is None:
                        continue
                    item = {"food_item_id": int(row["id"]), "provider": after["data_source"], "external_id": row["sourceKey"],
                            "field_name": field.upper(), "numeric_value": comparison["source"], "basis": "PER_100_ML",
                            "confidence_score": 75, "source_version": "R5:" + row["sourceFingerprint"],
                            "reviewer_identity": operation}
                    item["fingerprint"] = hashlib.sha256(json.dumps(item, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
                    evidence.append(item)
            payload.append({"id": int(row["id"]), "before": row["before"], "after_food": after, "options": options, "evidence": evidence})
    if "$r5_patch$" in json.dumps(payload, ensure_ascii=False):
        raise ValueError("SQL delimiter appears in data")
    head = f"""\\set ON_ERROR_STOP on
BEGIN;
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
SET LOCAL statement_timeout='60s'; SET LOCAL lock_timeout='3s';
SET LOCAL standard_conforming_strings=on;
LOCK TABLE food_items,food_item_serving_options,food_item_serving_option_localizations,food_product_source_evidence,
 recipe_ingredients,meal_template_items,meal_plan_items IN SHARE ROW EXCLUSIVE MODE;
CREATE TEMP TABLE r5_receipts(id bigint PRIMARY KEY, receipt jsonb) ON COMMIT DROP;
DO $r5_patch$
DECLARE p jsonb; o jsonb; e jsonb; v_food jsonb; v_opts jsonb; v_locs jsonb; v_evidence jsonb;
 v_id bigint; v_owned bigint[]; v_ev_owned bigint[]; v_op text := '{operation}'; v_after jsonb;
BEGIN
 IF clock_timestamp() > '{expires}'::timestamptz THEN RAISE EXCEPTION 'R5 approval expired'; END IF;
 FOR p IN SELECT value FROM jsonb_array_elements({literal(payload)}) LOOP
  SELECT to_jsonb(f) INTO STRICT v_food FROM food_items f WHERE f.id=(p->>'id')::bigint FOR NO KEY UPDATE;
  SELECT coalesce(jsonb_agg(to_jsonb(s) ORDER BY s.id),'[]'::jsonb) INTO v_opts FROM food_item_serving_options s WHERE food_item_id=(p->>'id')::bigint;
  SELECT coalesce(jsonb_agg(to_jsonb(l) ORDER BY l.id),'[]'::jsonb) INTO v_locs FROM food_item_serving_option_localizations l JOIN food_item_serving_options s ON s.id=l.serving_option_id WHERE s.food_item_id=(p->>'id')::bigint;
  SELECT coalesce(jsonb_agg(to_jsonb(ev) ORDER BY ev.id),'[]'::jsonb) INTO v_evidence FROM food_product_source_evidence ev WHERE food_item_id=(p->>'id')::bigint;
"""
    if rollback:
        body = """
  IF v_food<>p->'after'->'food' OR v_opts<>p->'after'->'options' OR v_locs<>p->'after'->'option_localizations' OR v_evidence<>p->'after'->'evidence'
  THEN RAISE EXCEPTION 'R5 rollback conflict on product %',p->>'id'; END IF;
  SELECT array_agg(value::bigint) INTO v_owned FROM jsonb_array_elements_text(p->'insertedOptionIds');
  SELECT array_agg(value::bigint) INTO v_ev_owned FROM jsonb_array_elements_text(p->'insertedEvidenceIds');
  IF EXISTS(SELECT 1 FROM food_logs WHERE serving_option_id=ANY(coalesce(v_owned,'{}'::bigint[])))
   OR EXISTS(SELECT 1 FROM recipe_ingredients WHERE serving_option_id=ANY(coalesce(v_owned,'{}'::bigint[])))
   OR EXISTS(SELECT 1 FROM meal_template_items WHERE serving_option_id=ANY(coalesce(v_owned,'{}'::bigint[])))
  THEN RAISE EXCEPTION 'R5 rollback would affect a subsequently used portion'; END IF;
  IF EXISTS(SELECT 1 FROM food_logs WHERE food_id=(p->>'id')::bigint AND created_at >= (p->>'appliedAt')::timestamp)
  THEN RAISE EXCEPTION 'R5 rollback would affect a subsequently logged food'; END IF;
  IF p->'before'->'references' <> jsonb_build_object('recipes',(SELECT count(*) FROM recipe_ingredients WHERE food_item_id=(p->>'id')::bigint),
   'templates',(SELECT count(*) FROM meal_template_items WHERE food_item_id=(p->>'id')::bigint),
   'plans',(SELECT count(*) FROM meal_plan_items WHERE food_item_id=(p->>'id')::bigint))
  THEN RAISE EXCEPTION 'R5 rollback dependency conflict'; END IF;
  DELETE FROM food_item_serving_option_localizations WHERE serving_option_id=ANY(coalesce(v_owned,'{}'::bigint[])) AND source=v_op;
  DELETE FROM food_item_serving_options WHERE id=ANY(coalesce(v_owned,'{}'::bigint[])) AND food_item_id=(p->>'id')::bigint;
  DELETE FROM food_product_source_evidence WHERE id=ANY(coalesce(v_ev_owned,'{}'::bigint[])) AND food_item_id=(p->>'id')::bigint AND reviewer_identity=v_op;
  UPDATE food_items SET nutrition_reference_unit=p->'before'->'food'->>'nutrition_reference_unit',
   serving_unit=p->'before'->'food'->>'serving_unit', serving_size_grams=(p->'before'->'food'->>'serving_size_grams')::double precision
   WHERE id=(p->>'id')::bigint;
  INSERT INTO r5_receipts VALUES((p->>'id')::bigint,jsonb_build_object('id',p->>'id','status','ROLLED_BACK'));
"""
    else:
        body = """
  IF v_food<>p->'before'->'food' OR v_opts<>p->'before'->'options' OR v_locs<>p->'before'->'option_localizations' OR v_evidence<>p->'before'->'evidence'
  THEN RAISE EXCEPTION 'R5 current before-image conflict on product %; do not retry a possibly committed patch',p->>'id'; END IF;
  IF p->'before'->'references' <> jsonb_build_object('recipes',(SELECT count(*) FROM recipe_ingredients WHERE food_item_id=(p->>'id')::bigint),
   'templates',(SELECT count(*) FROM meal_template_items WHERE food_item_id=(p->>'id')::bigint),
   'plans',(SELECT count(*) FROM meal_plan_items WHERE food_item_id=(p->>'id')::bigint))
  THEN RAISE EXCEPTION 'R5 dependency conflict'; END IF;
  v_owned:='{}'::bigint[]; v_ev_owned:='{}'::bigint[];
  UPDATE food_items SET nutrition_reference_unit=p->'after_food'->>'nutrition_reference_unit',
   serving_unit=p->'after_food'->>'serving_unit', serving_size_grams=(p->'after_food'->>'serving_size_grams')::double precision
   WHERE id=(p->>'id')::bigint;
  FOR o IN SELECT value FROM jsonb_array_elements(p->'options') LOOP
   INSERT INTO food_item_serving_options(food_item_id,label,unit_type,quantity,gram_weight,ml_volume,is_default,source,quality_status,user_visible)
   VALUES((p->>'id')::bigint,o->>'label',o->>'unit_type',(o->>'quantity')::double precision,(o->>'gram_weight')::double precision,
    (o->>'ml_volume')::double precision,false,o->>'source','VERIFIED',true) RETURNING id INTO v_id;
   v_owned:=array_append(v_owned,v_id);
   INSERT INTO food_item_serving_option_localizations(serving_option_id,language,label,source,active,created_at,updated_at)
   VALUES(v_id,'EN',o->'labels'->>'EN',v_op,true,now() AT TIME ZONE 'UTC',now() AT TIME ZONE 'UTC'),
         (v_id,'TR',o->'labels'->>'TR',v_op,true,now() AT TIME ZONE 'UTC',now() AT TIME ZONE 'UTC');
  END LOOP;
  FOR e IN SELECT value FROM jsonb_array_elements(p->'evidence') LOOP
   INSERT INTO food_product_source_evidence(food_item_id,provider,external_id,field_name,numeric_value,basis,confidence_score,observed_at,created_at,fingerprint,source_version,reviewer_identity)
   VALUES((p->>'id')::bigint,e->>'provider',e->>'external_id',e->>'field_name',(e->>'numeric_value')::double precision,e->>'basis',75,
    now() AT TIME ZONE 'UTC',now() AT TIME ZONE 'UTC',e->>'fingerprint',e->>'source_version',v_op) RETURNING id INTO v_id;
   v_ev_owned:=array_append(v_ev_owned,v_id);
  END LOOP;
  SELECT jsonb_build_object('food',to_jsonb(f),
   'options',coalesce((SELECT jsonb_agg(to_jsonb(s) ORDER BY s.id) FROM food_item_serving_options s WHERE food_item_id=f.id),'[]'::jsonb),
   'option_localizations',coalesce((SELECT jsonb_agg(to_jsonb(l) ORDER BY l.id) FROM food_item_serving_option_localizations l JOIN food_item_serving_options s ON s.id=l.serving_option_id WHERE s.food_item_id=f.id),'[]'::jsonb),
   'evidence',coalesce((SELECT jsonb_agg(to_jsonb(ev) ORDER BY ev.id) FROM food_product_source_evidence ev WHERE food_item_id=f.id),'[]'::jsonb))
   INTO v_after FROM food_items f WHERE id=(p->>'id')::bigint;
  IF v_after->'food'<>p->'after_food' THEN RAISE EXCEPTION 'Unexpected food-column change'; END IF;
  INSERT INTO r5_receipts VALUES((p->>'id')::bigint,jsonb_build_object('id',p->>'id','before',p->'before','after',v_after,
   'insertedOptionIds',to_jsonb(v_owned),'insertedEvidenceIds',to_jsonb(v_ev_owned),'operation',v_op,'appliedAt',now() AT TIME ZONE 'UTC','status','APPLIED'));
"""
    # Receipt rows are output before COMMIT but accepted only after psql exit 0.
    return head + body + """
 END LOOP;
END $r5_patch$;
SELECT json_build_object('kind','start','readonly',current_setting('transaction_read_only'),'count',count(*),'time',now()) FROM r5_receipts;
SELECT json_build_object('kind','row','row',receipt) FROM r5_receipts ORDER BY id;
SELECT json_build_object('kind','end','count',count(*)) FROM r5_receipts;
COMMIT;
""", payload


def run(args):
    args.output.mkdir(parents=True, exist_ok=True)
    if args.rollback_receipt:
        receipts = json.loads(args.rollback_receipt.read_text(encoding="utf-8"))["rows"]
        sql, _ = generate(receipts, args.operation, (datetime.now(timezone.utc) + timedelta(hours=1)).isoformat(), True)
        (args.output / "rollback.sql").write_text(sql, encoding="utf-8")
        print(json.dumps({"rollbackProducts": len(receipts), "executesSql": False}))
        return
    summary = json.loads(args.review.parent.joinpath("summary.json").read_text(encoding="utf-8"))
    if summary.get("reviewSha256") != gate.source.r5.closure.sha(args.review):
        raise ValueError("Full comparison manifest changed")
    if args.backup_identifier:
        response = subprocess.run(["aws", "rds", "describe-db-snapshots", "--db-snapshot-identifier", args.backup_identifier,
            "--profile", "grun-staging", "--region", "eu-west-1", "--output", "json"], capture_output=True, encoding="utf-8", timeout=30)
        if response.returncode:
            raise ValueError("Backup verification unavailable")
        snapshot = json.loads(response.stdout)["DBSnapshots"][0]
        backup = {"Identifier": snapshot["DBSnapshotIdentifier"], "Status": snapshot["Status"], "DB": snapshot["DBInstanceIdentifier"],
                  "Encrypted": snapshot["Encrypted"], "Arn": snapshot["DBSnapshotArn"], "Created": snapshot["SnapshotCreateTime"]}
        (args.output / "backup-proof.json").write_text(json.dumps(backup, indent=2) + "\n", encoding="utf-8")
    else:
        backup = json.loads(args.backup.read_text(encoding="utf-8"))
    if backup.get("Status") != "available" or backup.get("DB") != "grun-staging-db" or backup.get("Encrypted") is not True:
        raise ValueError("Available encrypted RDS before-patch backup required")
    rows = [r for r in json.loads(args.review.read_text(encoding="utf-8"))["rows"] if r["status"] == "READY_FOR_BACKUP_REHEARSAL"]
    if not args.approve_source_portions:
        raise ValueError("Explicit source/EN/TR portion rule approval required")
    expires = (datetime.now(timezone.utc) + timedelta(hours=1)).isoformat()
    batches, payload = [], []
    for start in range(0, len(rows), 15):
        operation = args.operation + f"-b{start // 15 + 1:02d}"
        sql, items = generate(rows[start:start + 15], operation, expires)
        path = args.output / f"apply-b{start // 15 + 1:02d}.sql"
        path.write_text(sql, encoding="utf-8")
        batches.append({"file": path.name, "operation": operation, "products": len(items), "sha256": gate.source.r5.closure.sha(path), "compressedBytes": len(gzip.compress(sql.encode()))})
        payload.extend(items)
    proof = {"operation": args.operation, "products": len(rows), "sourcePortions": sum(len(r["options"]) for r in rows),
             "referenceChanges": sum(bool(r["changes"]) for r in rows), "evidenceFacts": sum(len(p["evidence"]) for p in payload),
             "expiresAt": expires, "backup": backup, "batches": batches, "reviewSha256": summary["reviewSha256"],
             "requiredGates": ["Actual disposable PostgreSQL apply and exact rollback", "Pinned backend mass/volume serving contract", "Post-apply SQL/API reconciliation"],
             "approvedForLiveExecution": False, "executesSql": False}
    (args.output / "manifest.json").write_text(json.dumps(proof, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k:v for k,v in proof.items() if k!='backup'}, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--review", type=Path)
    parser.add_argument("--backup", type=Path)
    parser.add_argument("--backup-identifier", help="Verify the exact existing encrypted AWS before-patch snapshot")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--operation", required=True)
    parser.add_argument("--approve-source-portions", action="store_true")
    parser.add_argument("--rollback-receipt", type=Path)
    run(parser.parse_args())

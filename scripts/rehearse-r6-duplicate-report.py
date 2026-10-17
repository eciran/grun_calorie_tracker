"""Read-only duplicate report fixtures in an owned disposable PostgreSQL.

Never connects to AWS or existing DBs. No automatic merge/collapse approval.
"""
import argparse
import importlib.util
import json
import subprocess
import time
import uuid
from pathlib import Path

spec = importlib.util.spec_from_file_location("report", Path(__file__).with_name("build-branded-product-duplicate-analysis.py"))
report = importlib.util.module_from_spec(spec)
spec.loader.exec_module(report)
spec = importlib.util.spec_from_file_location("audit", Path(__file__).with_name("build-r6-catalog-audit.py"))
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


def docker(*args, stdin=None, check=True):
    result = subprocess.run(["docker", *args], input=stdin, text=True, capture_output=True, timeout=45)
    if check and result.returncode:
        raise ValueError(result.stderr[-2000:])
    return result


def run(args):
    owner = "r6-report-" + uuid.uuid4().hex[:12]
    container = None
    try:
        container = docker("run", "--detach", "--label", "grun.test.owner=" + owner,
            "--tmpfs", "/var/lib/postgresql/data", "-e", "POSTGRES_HOST_AUTH_METHOD=trust", "postgres:17").stdout.strip()
        for _ in range(40):
            if docker("exec", container, "pg_isready", "-U", "postgres", check=False).returncode == 0:
                break
            time.sleep(.5)
        else:
            raise ValueError("Owned PostgreSQL did not become ready")
        fixture = """
CREATE TABLE food_brands(id bigint PRIMARY KEY,canonical_name text);
CREATE TABLE food_items(id bigint PRIMARY KEY,brand_id bigint,brand text,name text,display_name text,
 normalized_barcode text,source_key text,market_region text,preparation_state text,serving_size_grams double precision,
 serving_unit text,nutrition_reference_unit text,calories double precision,protein double precision,carbs double precision,
 fat double precision,quality_score int,verification_status text,data_source text,catalog_type text,is_custom boolean,
 publication_status text);
INSERT INTO food_items SELECT n,NULL,'Fixture Brand',label,NULL,'5449000000996','barcode:5449000000996','UK_IE',
 'PREPARED',100,'GRAM','PER_100G',60,3,5,1,80,'NEEDS_REVIEW','OPEN_FOOD_FACTS','BRANDED_PRODUCT',false,'PUBLISHED'
 FROM (VALUES(1,'Equal Profile'),(2,'Equal Profile'),(3,'Mixed Basis'),(4,'Mixed Basis'),
 (5,'Partial Identity'),(6,'Partial Identity'),(7,'Missing Core'),(8,'Missing Core'),
 (9,'Precision Difference'),(10,'Precision Difference'),(11,'Same Source'),(12,'Same Source')) v(n,label);
UPDATE food_items SET nutrition_reference_unit='PER_100ML' WHERE id=4;
UPDATE food_items SET normalized_barcode=NULL WHERE id IN(5,6,11,12);
UPDATE food_items SET source_key=NULL WHERE id=6;
UPDATE food_items SET protein=NULL WHERE id IN(7,8);
UPDATE food_items SET fat=1.04 WHERE id=10;
"""
        docker("exec", "-i", container, "psql", "-U", "postgres", "-X", "-qAt", "-v", "ON_ERROR_STOP=1", stdin=fixture)
        sql = report.build(100, 50)
        result = docker("exec", "-i", container, "psql", "-U", "postgres", "-X", "-qAt", "-F", "\t",
                        "-v", "ON_ERROR_STOP=1", stdin=sql)
        clusters = {parts[2]: parts[4] for line in result.stdout.splitlines() if
                    (parts := line.split("\t"))[0] == "CLUSTER"}
        expected = {"Equal Profile": "CANDIDATE_SAME_GTIN", "Mixed Basis": "REVIEW_VARIANT_CONFLICT",
                    "Partial Identity": "REVIEW_MISSING_SOURCE_KEY", "Missing Core": "REVIEW_INCOMPLETE_PROFILE",
                    "Precision Difference": "REVIEW_VARIANT_CONFLICT", "Same Source": "CANDIDATE_SAME_SOURCE_KEY"}
        if clusters != expected or "AUTO_SAFE" in result.stdout:
            raise ValueError("Duplicate classification did not match safety fixtures")
        count = docker("exec", container, "psql", "-U", "postgres", "-X", "-qAt", "-c", "select count(*) from food_items").stdout.strip()
        if count != "12":
            raise ValueError("Read-only report changed fixture products")
        audit_fixture = """
CREATE TABLE food_categories(id bigint PRIMARY KEY,slug text,name_en text,name_tr text,active boolean,parent_id bigint);
CREATE TABLE food_item_categories(food_item_id bigint,category_id bigint,primary_category boolean);
CREATE TABLE food_item_source_categories(food_item_id bigint,category_tag text);
CREATE TABLE food_branded_duplicate_search_collapses(active boolean);
CREATE TABLE food_item_serving_options(id bigint,food_item_id bigint);
CREATE TABLE food_product_source_evidence(id bigint,food_item_id bigint,field_name text,numeric_value double precision,
 basis text,provider text,external_id text,source_version text,observed_at timestamp,fingerprint text);
CREATE TABLE flyway_schema_history(version text,success boolean,installed_rank int);
INSERT INTO flyway_schema_history VALUES('305',true,1);
INSERT INTO food_categories VALUES(1,'milk','Milk','Sut',true,NULL),(2,'inactive','Inactive','Pasif',false,NULL);
INSERT INTO food_item_categories VALUES(1,1,true),(1,2,true),(2,2,true);
INSERT INTO food_item_source_categories VALUES(3,'en:beverages'),(4,'en:beverages');
"""
        docker("exec", "-i", container, "psql", "-U", "postgres", "-X", "-qAt", "-v", "ON_ERROR_STOP=1", stdin=audit_fixture)
        audit_result = docker("exec", "-i", container, "psql", "-U", "postgres", "-X", "-qAt",
                              "-v", "ON_ERROR_STOP=1", stdin=audit.build())
        frames = [json.loads(line) for line in audit_result.stdout.splitlines() if line.startswith('{')]
        headers = [f for f in frames if f["kind"] == "start"]
        endings = [f for f in frames if f["kind"] == "end"]
        sections = {f["row"]["id"]: f["row"]["values"] for f in frames if f["kind"] == "row"}
        if len(headers) != 1 or headers[0]["readonly"] != "on" or headers[0]["count"] != 9 or len(sections) != 9 or endings[0]["count"] != 9:
            raise ValueError("R6 audit read-only coverage/frame proof failed")
        inventory = sections["inventory"]
        if (inventory["published"], inventory["withActivePrimary"], inventory["withoutActivePrimaryWithTags"],
                inventory["withoutActivePrimaryWithoutTags"]) != (12, 1, 2, 9):
            raise ValueError("Category/source-tag partitions were conflated")
        if sections["integrity"]["multiplePrimaryPublished"] != 1 or sections["integrity"]["inactiveOnlyPrimary"] != 1:
            raise ValueError("Category integrity fixture missed")
        if sections["duplicate_summary"]["groups"] != 6 or any(g["applyAllowed"] for g in sections["duplicate_samples"]):
            raise ValueError("Duplicate audit candidate-only scope failed")
        everyday_fixture = """
INSERT INTO food_items(id,name,source_key,catalog_type,is_custom,publication_status) VALUES
 (21,'Fixture banana','fixture:21','GENERIC_INGREDIENT',false,'PUBLISHED'),
 (22,'Fixture soup','fixture:22','LOCAL_DISH',false,'PUBLISHED'),
 (23,'Fixture coffee','fixture:23','STANDARD_PREPARED_ITEM',false,'PUBLISHED'),
 (24,'Unpublished food','fixture:24','LOCAL_DISH',false,'DRAFT'),
 (25,'Custom food','fixture:25','GENERIC_INGREDIENT',true,'PUBLISHED');
INSERT INTO food_item_categories VALUES(21,1,true);
INSERT INTO food_product_source_evidence(id,food_item_id,provider,external_id) VALUES(1,21,'USDA_FOODDATA','123');
"""
        docker("exec", "-i", container, "psql", "-U", "postgres", "-X", "-qAt", "-v", "ON_ERROR_STOP=1", stdin=everyday_fixture)
        everyday_sql = Path(__file__).with_name("sql").joinpath("export-r6-everyday-category-review.sql").read_text(encoding="utf-8")
        everyday_result = docker("exec", "-i", container, "psql", "-U", "postgres", "-X", "-qAt", "-v", "ON_ERROR_STOP=1", stdin=everyday_sql)
        everyday_frames = [json.loads(line) for line in everyday_result.stdout.splitlines() if line.startswith('{')]
        everyday_rows = {f['row']['id']: f['row'] for f in everyday_frames if f['kind'] == 'row'}
        start = next(f for f in everyday_frames if f['kind'] == 'start')
        end = next(f for f in everyday_frames if f['kind'] == 'end')
        metrics = next(f['values'] for f in everyday_frames if f['kind'] == 'metrics')
        if set(everyday_rows) != {21,22,23} or start['count'] != 3 or end['count'] != 3 or start['readonly'] != 'on':
            raise ValueError('Everyday category snapshot scope/count/read-only proof failed')
        if (len(everyday_rows[21]['assignments']), len(everyday_rows[21]['evidence']), len(metrics['categories'])) != (1,1,2):
            raise ValueError('Everyday snapshot lost category/source proof')
        if docker('exec',container,'psql','-U','postgres','-X','-qAt','-c','select count(*) from food_items').stdout.strip() != '17':
            raise ValueError('Everyday read changed fixture products')
        proof = {"status": "PASS", "postgres": "17", "fixtures": len(expected), "rowsRetained": 12,
                 "classified": clusters, "fixture": "SYNTHETIC_NOT_AWS_CATALOG", "catalogWrites": 0,
                 "automaticApprovals": 0, "backendSqlExecution": False, "auditChecks": 4, "auditSections": 9,
                 "everydaySnapshotChecks": 3, "everydaySelectedRows": 3, "everydayFixtureTotalRowsRetained": 17}
        args.output.mkdir(parents=True, exist_ok=True)
        (args.output / "postgres-proof.json").write_text(json.dumps(proof, indent=2) + "\n")
        print(json.dumps(proof, indent=2))
    finally:
        if container:
            actual_owner = docker("inspect", container, "--format", '{{index .Config.Labels "grun.test.owner"}}').stdout.strip()
            if actual_owner != owner:
                raise ValueError("Disposable container ownership mismatch")
            docker("rm", "-f", "-v", container)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    run(parser.parse_args())

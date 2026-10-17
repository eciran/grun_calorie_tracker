"""Deliver unchanged rehearsed SQL through an owned encrypted temporary S3 object.

ECS task definitions have a 64 KiB size cap. The signed URL is short-lived,
private operational output only; no public ACL, persistent bucket or deploy.
"""
import argparse
import base64
import gzip
import importlib.util
import json
import os
import re
import hashlib
import ssl
import subprocess
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlparse
from urllib.request import urlopen

spec = importlib.util.spec_from_file_location("ops", Path(__file__).with_name("r6-approved-catalog-operation.py"))
ops = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ops)


def aws(*command):
    result = subprocess.run(["aws", *command, "--profile", "grun-staging", "--region", "eu-west-1", "--output", "json"],
                            capture_output=True, encoding="utf-8", timeout=60, env={**os.environ, "AWS_PAGER": "", "PYTHONUTF8": "1"})
    if result.returncode:
        raise ValueError("AWS operation failed: " + "/".join(command[:2]))
    return json.loads(result.stdout) if result.stdout.strip() else {}


def download_script(url, digest, ca=None):
    parsed = urlparse(url)
    if (parsed.scheme != "https" or not parsed.netloc.endswith(".s3.eu-west-1.amazonaws.com")
            or any(c in url for c in "'\r\n") or not re.fullmatch(r"[a-f0-9]{64}", digest)):
        raise ValueError("Owned HTTPS S3 URL and exact checksum required")
    request = f"GET {parsed.path}?{parsed.query} HTTP/1.1\\r\\nHost: {parsed.netloc}\\r\\nConnection: close\\r\\n\\r\\n"
    prefix = ""
    trust = "-CApath /etc/ssl/certs"
    if ca:
        encoded = base64.b64encode(ca).decode()
        prefix = f"printf '%s' '{encoded}' | base64 -d > /tmp/r6-amazon-root.pem || exit 1\n"
        trust = "-CAfile /tmp/r6-amazon-root.pem"
    return prefix + f"""printf '%b' '{request}' | timeout 60 openssl s_client -quiet -ignore_unexpected_eof -verify_return_error -verify_hostname '{parsed.netloc}' {trust} -connect '{parsed.netloc}:443' -servername '{parsed.netloc}' > /tmp/r6-response || exit 1
head -n 1 /tmp/r6-response | grep -q '^HTTP/1.1 200' || exit 1
sed '1,/^\\r$/d' /tmp/r6-response > /tmp/r6.sql.gz || exit 1
printf '%s  /tmp/r6.sql.gz\\n' '{digest}' | sha256sum -c - >/dev/null || exit 1
gzip -dc /tmp/r6.sql.gz > /tmp/r6.sql || exit 1
"""


def run(args):
    folder = args.package
    output = args.output or folder / "applied-categories-s3"
    if output.exists() or not args.execute_approved:
        raise ValueError("Explicit approval and never-started S3 dispatcher required")
    package = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))
    proof = json.loads((folder / "rehearsal/proof.json").read_text())
    path = folder / "categories-apply.sql"
    if (package["inputHashes"]["producer"] != ops.sha(Path(__file__).with_name("r6-approved-catalog-operation.py"))
            or ops.sha(path) != package["categoriesSqlSha256"] or path.read_text(encoding="utf-8") != ops.category_apply(package)
            or proof["status"] != "PASS" or proof["manifestSha256"] != ops.sha(folder / "manifest.json")
            or datetime.now(timezone.utc) > datetime.fromisoformat(package["expiresAt"])):
        raise ValueError("Exact unexpired unchanged approved/rehearsed SQL required")
    names = json.loads((folder / "applied-names/receipt.json").read_text(encoding="utf-8"))
    summary = json.loads((folder / "applied-names/summary.json").read_text())
    if summary["status"] != "APPLIED_RECEIPT_COMPLETE" or summary["receiptSha256"] != ops.sha(folder / "applied-names/receipt.json"):
        raise ValueError("Complete committed name receipt required")
    ops.validate_names(package, names["rows"])
    db = aws("rds", "describe-db-instances", "--db-instance-identifier", "grun-staging-db")["DBInstances"][0]
    backup = aws("rds", "describe-db-snapshots", "--db-snapshot-identifier", package["backup"]["DBSnapshotIdentifier"])["DBSnapshots"][0]
    if (db["DBInstanceStatus"] != "available" or db["EngineVersion"] != "18.3" or not db["StorageEncrypted"]
            or db["DBName"] != "grun_calorie_db" or backup != package["backup"]):
        raise ValueError("Exact available database and encrypted backup required")
    service = aws("ecs", "describe-services", "--cluster", "grun-staging-cluster", "--services", "grun-staging-api-service")["services"][0]
    deployed = aws("ecs", "describe-task-definition", "--task-definition", service["taskDefinition"])["taskDefinition"]
    if deployed["containerDefinitions"][0]["image"] != package["image"]:
        raise ValueError("Deployed image changed")
    definition = json.loads(args.template.read_text(encoding="utf-8-sig"))
    if len(definition["containerDefinitions"]) != 1:
        raise ValueError("Single SQL container required")
    container = definition["containerDefinitions"][0]
    env = {e["name"]: e["value"] for e in container["environment"]}
    if (env.get("PGHOST") != db["Endpoint"]["Address"] or env.get("PGDATABASE") != db["DBName"]
            or "PGPASSWORD" in env or not any(s["name"] == "PGPASSWORD" for s in container["secrets"])
            or not container["image"].startswith("postgres@sha256:")):
        raise ValueError("Exact DB endpoint, pinned client and secret reference required")
    output.mkdir()
    # The pinned SQL image lacks a general public root store. Retrieve the
    # official root over verified HTTPS and check its published DER digest;
    # never disable TLS peer/hostname verification to compensate.
    with urlopen("https://www.amazontrust.com/repository/AmazonRootCA1.pem", timeout=30) as response:
        ca = response.read(10000)
    der = ssl.PEM_cert_to_DER_cert(ca.decode("ascii"))
    if hashlib.sha256(der).hexdigest() != "8ecde6884f3d87b1125ba31ac3fcb13d7016de7f57cc904fe1cb97c6ae98196e":
        raise ValueError("Official Amazon Root CA 1 fingerprint mismatch")
    (output / "AmazonRootCA1.pem").write_bytes(ca)
    sql = f"SELECT set_config('grun.r6.operation',{ops.text(package['operation'])},false);\n" + path.read_text(encoding="utf-8")
    payload = output / "sql-payload.gz"
    payload.write_bytes(gzip.compress(sql.encode("utf-8")))
    bucket = "grun-r6-private-transfer-" + uuid.uuid4().hex[:20]
    key = "approved.sql.gz"
    account = aws("sts", "get-caller-identity")["Account"]
    created = uploaded = stopped = False
    arn = task = None
    try:
        aws("s3api", "create-bucket", "--bucket", bucket, "--create-bucket-configuration", "LocationConstraint=eu-west-1")
        created = True
        ops.write(output / "transfer-status.json", {"bucket": bucket, "key": key, "owner": account, "status": "OWNED_PRIVATE_TRANSFER_CREATED"})
        aws("s3api", "put-public-access-block", "--bucket", bucket, "--public-access-block-configuration", "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true")
        aws("s3api", "put-bucket-encryption", "--bucket", bucket, "--server-side-encryption-configuration", json.dumps({"Rules": [{"ApplyServerSideEncryptionByDefault": {"SSEAlgorithm": "AES256"}}]}))
        aws("s3api", "put-bucket-tagging", "--bucket", bucket, "--tagging", json.dumps({"TagSet": [{"Key": "grun-operation", "Value": package["operation"]}]}))
        stored = aws("s3api", "put-object", "--bucket", bucket, "--key", key, "--body", str(payload.resolve()), "--server-side-encryption", "AES256", "--expected-bucket-owner", account)
        uploaded = True
        if stored.get("ServerSideEncryption") != "AES256":
            raise ValueError("Payload encryption not confirmed")
        presign = subprocess.run(["aws", "s3", "presign", f"s3://{bucket}/{key}", "--expires-in", "900", "--profile", "grun-staging", "--region", "eu-west-1"], capture_output=True, text=True, timeout=30)
        if presign.returncode:
            raise ValueError("Short-lived signed download could not be created")
        download = download_script(presign.stdout.strip(), ops.sha(payload), ca)
        # Verify TLS, HTTP framing and the exact compressed body locally. This
        # transport-only smoke never invokes psql or reads a user's database.
        smoke = subprocess.run(["docker", "run", "--rm", "-i", "--label", "grun.test.owner=" + package["operation"], "--entrypoint", "sh", container["image"], "-s"],
                               input=(download + "echo TRANSPORT_TLS_SHA256_PASS\n").encode("utf-8"), capture_output=True, timeout=100)
        (output / "transport-smoke.stderr").write_bytes(smoke.stderr)
        if smoke.returncode or b"TRANSPORT_TLS_SHA256_PASS" not in smoke.stdout:
            raise ValueError("TLS/checksum transport smoke failed; no SQL execution attempted")
        definition["family"] = "grun-staging-r6-approved-patch"
        container["name"] = "r6-approved"
        container["logConfiguration"]["options"].update({"awslogs-stream-prefix": "r6-approved", "mode": "blocking"})
        container["environment"] = [e for e in container["environment"] if e["name"] != "PGOPTIONS"] + [{"name": "PGOPTIONS", "value": "-c default_transaction_read_only=off -c statement_timeout=90000 -c lock_timeout=3000"}]
        container["entryPoint"] = ["sh", "-c"]
        container["command"] = [download + """psql -X -qAt -v ON_ERROR_STOP=1 -f /tmp/r6.sql -o /tmp/r6.jsonl || exit 1
gzip -c /tmp/r6.jsonl | base64 -w 60000 > /tmp/chunks || exit 1
printf '{"kind":"transport","chunks":%s,"sha256":"%s"}\\n' "$(wc -l < /tmp/chunks)" "$(sha256sum /tmp/r6.jsonl | cut -d ' ' -f 1)"
awk '{print "{\\"kind\\":\\"chunk\\",\\"sequence\\":" NR ",\\"data\\":\\"" $0 "\\"}"}' /tmp/chunks
sleep 15
"""]
        ops.write(output / "owned-task-definition.json", definition)
        if (output / "owned-task-definition.json").stat().st_size > 65536:
            raise ValueError("Thin task definition still exceeds ECS limit")
        arn = aws("ecs", "register-task-definition", "--cli-input-json", "file://" + str((output / "owned-task-definition.json").resolve()))["taskDefinition"]["taskDefinitionArn"]
        start = aws("ecs", "run-task", "--cluster", "grun-staging-cluster", "--task-definition", arn, "--launch-type", "FARGATE", "--network-configuration", json.dumps(service["networkConfiguration"]))
        if start.get("failures") or len(start.get("tasks", [])) != 1:
            raise ValueError("Owned worker did not start")
        task = start["tasks"][0]["taskArn"]
        ops.write(output / "task-status.json", {"status": "STARTED_COMMIT_UNKNOWN", "taskArn": task})
        deadline = time.monotonic() + 360
        while time.monotonic() < deadline:
            state = aws("ecs", "describe-tasks", "--cluster", "grun-staging-cluster", "--tasks", task)["tasks"][0]
            if state["lastStatus"] == "STOPPED":
                stopped = True
                break
            time.sleep(8)
        if not stopped or any(c.get("exitCode") != 0 for c in state["containers"]):
            raise ValueError("Commit unknown/failed; read-only recovery required, never retry")
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
        frames = ops.transport.decode_transport([json.loads(m) for m in messages if m.strip().startswith("{")])
        receipts = [r for r in frames if r.get("kind") == "category_receipts"]
        if len(receipts) != 1 or receipts[0]["owner"] != package["operation"]:
            raise ValueError("Incomplete receipt; read-only recovery required")
        receipt = receipts[0]
        undo = ops.category_undo(package, receipt["rows"])
        ops.write(output / "receipt.json", receipt)
        (output / "exact-undo.sql").write_text(undo, encoding="utf-8")
        ops.write(output / "summary.json", {"status": "APPLIED_RECEIPT_COMPLETE", "products": 578, "taskArn": task,
                  "backup": backup["DBSnapshotIdentifier"], "receiptSha256": ops.sha(output / "receipt.json"),
                  "undoSha256": ops.sha(output / "exact-undo.sql"), "sqlSha256": package["categoriesSqlSha256"],
                  "transport": "TLS_VERIFIED_AES256_PRIVATE_S3_SHORT_LIVED_URL_SHA256", "transportSmoke": "PASS",
                  "dispatcherSha256": ops.sha(Path(__file__))})
        print(json.dumps({"status": "APPLIED_RECEIPT_COMPLETE", "products": 578, "transportSmoke": "PASS"}))
    finally:
        if task and not stopped:
            aws("ecs", "stop-task", "--cluster", "grun-staging-cluster", "--task", task, "--reason", "Owned R6 timeout; inspect commit before retry")
        if arn:
            aws("ecs", "deregister-task-definition", "--task-definition", arn)
        if created:
            tags = aws("s3api", "get-bucket-tagging", "--bucket", bucket, "--expected-bucket-owner", account).get("TagSet", [])
            if {"Key": "grun-operation", "Value": package["operation"]} not in tags:
                raise ValueError("Foreign/unconfirmed bucket cleanup refused")
            if uploaded:
                aws("s3api", "delete-object", "--bucket", bucket, "--key", key, "--expected-bucket-owner", account)
            aws("s3api", "delete-bucket", "--bucket", bucket, "--expected-bucket-owner", account)
            ops.write(output / "transfer-status.json", {"bucket": bucket, "status": "OWNED_PAYLOAD_AND_BUCKET_REMOVED"})


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--package", type=Path, required=True)
    parser.add_argument("--template", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--execute-approved", action="store_true")
    run(parser.parse_args())

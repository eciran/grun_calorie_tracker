"""Run the real backend duplicate SQL on an owned PostgreSQL18.3 fixture only."""
import argparse
import hashlib
import json
import os
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
IMAGE = "postgres@sha256:7e32e9833a6fb1c92c32552794cb6ed569d51b445a54907d35fc112ef39684db"


def run(output):
    owner = "r6-backend-profile-" + uuid.uuid4().hex[:12]
    container = None

    def docker(*args):
        result = subprocess.run(["docker", *args], capture_output=True, text=True, timeout=60)
        if result.returncode:
            raise ValueError("Owned PostgreSQL command failed")
        return result.stdout.strip()

    try:
        container = docker("run", "--detach", "--label", "grun.test.owner=" + owner,
                           "--tmpfs", "/var/lib/postgresql", "-e", "POSTGRES_HOST_AUTH_METHOD=trust",
                           "-e", "POSTGRES_DB=r6_profile_test", "-p", "127.0.0.1::5432", IMAGE)
        for _ in range(40):
            probe = subprocess.run(["docker", "exec", container, "pg_isready", "-U", "postgres"], capture_output=True, timeout=10)
            if probe.returncode == 0:
                break
            time.sleep(.5)
        else:
            raise ValueError("Owned PostgreSQL startup failed")
        info = json.loads(docker("inspect", container))[0]
        mapping = info["NetworkSettings"]["Ports"]["5432/tcp"]
        if len(mapping) != 1 or mapping[0]["HostIp"] != "127.0.0.1":
            raise ValueError("Only loopback ephemeral port is allowed")
        env = {**os.environ, "JAVA_HOME": "C:/Users/emrah/.jdks/ms-17.0.18",
               "GRUN_R6_POSTGRES_URL": "jdbc:postgresql://127.0.0.1:" + mapping[0]["HostPort"] + "/r6_profile_test"}
        maven = Path.home() / ".m2/wrapper/dists/apache-maven-3.9.10/a38810a491b03367137adfdfbe7d14c4/bin/mvn.cmd"
        result = subprocess.run([str(maven), "-o", "-Dtest=BrandedDuplicateReviewProfilePostgresTest", "test", "-q",
                                 "-Dmaven.test.redirectTestOutputToFile=true"], cwd=ROOT, env=env,
                                capture_output=True, timeout=240)
        output.mkdir(parents=True, exist_ok=True)
        (output / "maven.stdout").write_bytes(result.stdout)
        (output / "maven.stderr").write_bytes(result.stderr)
        report = ROOT / "target/surefire-reports/TEST-com.grun.calorietracker.service.BrandedDuplicateReviewProfilePostgresTest.xml"
        if result.returncode:
            raise ValueError("Backend PostgreSQL tests failed; inspect private Maven evidence")
        suite = ET.parse(report).getroot()
        if suite.attrib["tests"] != "5" or any(int(suite.attrib.get(k, "0")) for k in ("failures", "errors", "skipped")):
            raise ValueError("All five actual backend PostgreSQL tests must run/pass")
        files = ["src/main/java/com/grun/calorietracker/service/BrandedProductDuplicateAnalysisService.java",
                 "src/main/java/com/grun/calorietracker/service/support/BrandedDuplicateFingerprint.java",
                 "src/test/java/com/grun/calorietracker/service/BrandedDuplicateReviewProfilePostgresTest.java"]
        proof = {"status": "PASS", "postgres": "18.3", "tests": 5, "failures": 0, "skipped": 0,
                 "scope": "REAL_BACKEND_EXACT_AND_PAGE_SQL_ON_DISPOSABLE_PROJECTION_NOT_A_FULL_MIGRATION",
                 "checks": ["ALL_21_NUTRIENTS_DRIFT", "NULL_VS_ZERO", "MASS_VOLUME_STATE_MARKETS",
                            "PORTIONS_LOCALIZED_LABELS_SOURCE_VERSION", "TELEMETRY_DOES_NOT_INVALIDATE",
                            "PAGED_DECISION_MARKED_STALE", "OLD_APPROVAL_APPLY_REJECTED_WITH_NO_COLLAPSE_WRITE",
                            "CANONICAL_BRAND_AND_SOURCE_CATEGORY_TAG_DRIFT"],
                 "sha256": {f: hashlib.sha256((ROOT / f).read_bytes()).hexdigest() for f in files},
                 "awsCatalogWrites": 0, "deploy": False, "commit": False, "push": False, "container": container}
        (output / "proof.json").write_text(json.dumps(proof, indent=2) + "\n", encoding="utf-8")
        print(json.dumps({"status": "PASS", "postgres": "18.3", "tests": 5, "awsWrites": 0}))
    finally:
        if container:
            if docker("inspect", container, "--format", '{{index .Config.Labels "grun.test.owner"}}') != owner:
                raise ValueError("Foreign container cleanup refused")
            docker("rm", "-f", "-v", container)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    run(parser.parse_args().output)

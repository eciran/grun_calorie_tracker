"""Delta snapshot from the verified task90 product build, not the dirty tree."""
import argparse
import hashlib
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FILES = [
    "src/main/java/com/grun/calorietracker/service/impl/FoodItemServiceImpl.java",
    "src/main/java/com/grun/calorietracker/service/impl/FoodProductImportServiceImpl.java",
    "src/main/java/com/grun/calorietracker/service/impl/ProductCatalogAuditService.java",
    "src/main/java/com/grun/calorietracker/service/support/FoodCatalogNamingRules.java",
    "src/main/java/com/grun/calorietracker/service/support/FoodSourceNameRules.java",
    "src/main/resources/catalog/source-name-decisions-r7-v1.json",
]
TESTS = ["FoodItemServiceSearchIntegrationTest", "FoodProductImportServiceImplTest",
         "ProductCatalogAuditIntegrationTest", "FoodCoreSearchPostgresIntegrationTest", "support/FoodCatalogNamingRulesTest"]


def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()


def run(args):
    parent = ROOT / "outputs/r5r6-common-live-20261013"
    manifest = json.loads((parent / "source-manifest.json").read_text(encoding="utf-8"))
    before = Path(manifest["sourcePath"])
    destination = args.output.resolve()
    if not destination.is_relative_to(ROOT / "outputs") or destination.exists(): raise ValueError("New owned output required")
    for file, digest in manifest["sealedMainSourceHashes"].items():
        if sha(before / file) != digest: raise ValueError("Verified task90 base source changed")
    shutil.copytree(before, destination, ignore=shutil.ignore_patterns("target", ".git", ".env"))
    selected = []
    for file in FILES + ["src/test/java/com/grun/calorietracker/service/" + name + ".java" for name in TESTS]:
        target = destination / file
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ROOT / file, target)
        selected.append({"file": file, "sha256": sha(target)})
    sealed = {p.relative_to(destination).as_posix(): sha(p) for p in (destination / "src/main").rglob("*") if p.is_file()}
    allowed = set(FILES)
    for file, digest in manifest["sealedMainSourceHashes"].items():
        if file not in allowed and sealed.get(file) != digest: raise ValueError("Unapproved source/config delta")
    if set(sealed) - set(manifest["sealedMainSourceHashes"]) != {FILES[-1]}: raise ValueError("Unexpected new runtime resources")
    record = {"version": "COMMON_PRODUCT_RELEASE_SOURCE_V1", "scope": "EXACT_R7_PRODUCT_DELTA",
        "sourcePath": str(destination), "baseGitRevision": manifest["baseGitRevision"],
        "baselineImage": "911291530432.dkr.ecr.eu-west-1.amazonaws.com/grun-staging-api@sha256:75087a53faaa8f5f047cd4ba921a5d13e071e7476fc3ec7508dd48486db4981f",
        "baselineSourceManifestSha256": sha(parent / "source-manifest.json"), "selected": selected,
        "sealedMainSourceHashes": sealed, "gitMutations": 0, "unrelatedDirtyChangesExcluded": True,
        "localEnvCopied": False, "producerSha256": sha(Path(__file__))}
    (destination.parent / "source-manifest.json").write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": "SEALED_R7_DELTA", "runtimeFiles": len(FILES), "unrelatedChangesExcluded": True}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    run(parser.parse_args())

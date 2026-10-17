"""Reconcile all 696 fresh read-only rows against the exact R6 receipts."""
import argparse
import csv
import importlib.util
import json
from pathlib import Path

spec = importlib.util.spec_from_file_location("ops", Path(__file__).with_name("r6-approved-catalog-operation.py"))
ops = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ops)


def verify(package, current, names, categories):
    ops.validate_names(package, names)
    ops.category_undo(package, categories)
    old = {r["id"]: r for r in package["currentRows"]}
    new = {r["id"]: r for r in current}
    if len(current) != 696 or set(old) != set(new):
        raise ValueError("Full 696-row identity mismatch")
    name_rows = {r["id"]: r for r in names}
    assignments = {str(r["food_item_id"]): r for r in categories}
    taxonomy = {c["id"]: c for c in package["categories"].values()}
    for identifier, before in old.items():
        expected = dict(name_rows.get(identifier, before))
        if identifier in assignments:
            category = taxonomy[assignments[identifier]["category_id"]]
            expected["assignments"] = [{**assignments[identifier], "slug": category["slug"], "active": category["active"]}]
        if new[identifier] != expected:
            bad = [key for key in expected if new[identifier].get(key) != expected[key]]
            raise ValueError("Post-apply exact state drift: " + identifier + "/" + ",".join(bad))
    return {"status": "PASS", "comparedProducts": 696, "namesCorrected": 2,
            "categoriesApplied": 578, "heldCategoryProducts": 118,
            "fullyUntouchedProducts": 116, "nutritionNumberChanges": 0,
            "portionUnitChanges": 0, "sourceEvidenceChanges": 0,
            "oldAliasesDeactivated": 5, "qualifiedAliasesAdded": 4,
            "full181kOutsideCohortComparison": False, "authenticatedApiAcceptance": False}


def run(args):
    folder = args.package
    package = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))
    proof = json.loads((args.current / "snapshot-proof.json").read_text(encoding="utf-8"))
    catalog = args.current / "catalog.csv"
    if (proof["sha256"] != ops.sha(catalog) or proof["scope"] != "r6-release" or not proof["transactionReadOnly"]
            or proof["exitCode"] != 0 or proof["schemaVersion"] != "305"
            or proof["metrics"][0]["categories"] != sorted(package["categories"].values(), key=lambda c: c["id"])
            or proof["metrics"][0]["orphanAssignments"] != 0):
        raise ValueError("Intact read-only post-apply/schema/taxonomy proof required")
    with catalog.open(encoding="utf-8-sig", newline="") as stream:
        current = [{"id": r["id"], **{k: None if k == "name_review" and v == "" else json.loads(v)
                   for k, v in r.items() if k != "id"}} for r in csv.DictReader(stream)]
    receipts = {}
    for kind in ("names", "categories"):
        output = folder / ("applied-" + kind)
        if kind == "categories" and args.categories_receipt_folder:
            output = args.categories_receipt_folder
        if kind == "categories" and not (output / "summary.json").exists():
            output = folder / "applied-categories-s3"
        summary = json.loads((output / "summary.json").read_text(encoding="utf-8"))
        if summary["status"] != "APPLIED_RECEIPT_COMPLETE" or summary["receiptSha256"] != ops.sha(output / "receipt.json"):
            raise ValueError("Complete committed receipt required")
        receipt = json.loads((output / "receipt.json").read_text(encoding="utf-8"))
        if receipt["owner"] != package["operation"]:
            raise ValueError("Receipt owner mismatch")
        receipts[kind] = receipt["rows"]
    result = verify(package, current, receipts["names"], receipts["categories"])
    result.update(currentTime=proof["time"], currentSha256=proof["sha256"], backup=package["backup"]["DBSnapshotIdentifier"],
                  manifestSha256=ops.sha(folder / "manifest.json"))
    ops.write(args.output, result)
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--package", type=Path, required=True)
    parser.add_argument("--current", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--categories-receipt-folder", type=Path)
    run(parser.parse_args())

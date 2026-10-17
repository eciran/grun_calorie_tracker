"""Exclusive held-product queues and source-age inventory; no database access.

Keeps every overlapping blocker, unknown nutrient and source version visible.
The freshness queue is evidence collection only, never an import approval.
"""
import argparse
import csv
import importlib.util
import json
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

spec = importlib.util.spec_from_file_location("gate", Path(__file__).with_name("close-r5-release-review.py"))
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)
spec = importlib.util.spec_from_file_location("fetch", Path(__file__).with_name("fetch-r5-off-evidence.py"))
fetch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fetch)


def primary(holds):
    for key, matches in (
        ("CLASSIFICATION_PREPARATION", {"R3_R4_CLASSIFICATION_OR_PREPARATION_GATE", "SOURCE_PREPARATION_CONFLICT"}),
        ("SOURCE_IDENTITY", {"CURRENT_NAME_SOURCE_IDENTITY_REVIEW", "EXACT_GTIN_CONFLICT", "EXACT_FDC_CONFLICT"}),
        ("NUTRIENT_COMPARISON", {"FULL_NUTRITION_COMPARISON_REQUIRED"}),
        ("LIQUID_DIMENSION", {"LIQUID_LABEL_OR_MASS_VOLUME_SOURCE_REVIEW", "LIQUID_ML_CONTRACT_REVIEW"}),
        ("PORTION_CONTRACT", {"PORTION_COUNT_OR_CROSS_DIMENSION_MOBILE_REVIEW", "EXACT_SOURCE_PORTION_REBUILD_REQUIRED", "EXISTING_EQUIVALENT_OPTION_REVIEW_REQUIRED"}),
        ("ADMIN_APPROVAL", {"EXISTING_ADMIN_APPROVAL_REVIEW_REQUIRED"}),
    ):
        if matches & set(holds):
            return key
    return "OTHER_REVIEW" if holds else "NONE"


def partition(rows):
    if len({str(r["id"]) for r in rows}) != len(rows):
        raise ValueError("Duplicate held product ID")
    return dict(Counter(primary(r["holds"]) for r in rows))


def freshness(checked, now):
    try:
        stamp = datetime.fromisoformat(checked.replace("Z", "+00:00"))
        age = (now - stamp).total_seconds()
        return "FUTURE_INVALID" if age < 0 else "FRESH_24H" if age <= 86400 else "RECHECK_REQUIRED"
    except (TypeError, ValueError, AttributeError):
        return "TIME_UNPROVEN"


def run(args):
    original_summary = json.loads(args.original.parent.joinpath("summary.json").read_text())
    current_summary = json.loads(args.review.parent.joinpath("summary.json").read_text())
    for path, summary in ((args.original, original_summary), (args.review, current_summary)):
        if summary["reviewSha256"] != gate.source.r5.closure.sha(path):
            raise ValueError("Review manifest hash mismatch")
    original = json.loads(args.original.read_text(encoding="utf-8"))["rows"]
    current = json.loads(args.review.read_text(encoding="utf-8"))["rows"]
    if len({r["id"] for r in current}) != len(current) or {r["id"] for r in original} != {r["id"] for r in current}:
        raise ValueError("Exact old/new review membership required")
    old_held = [r for r in original if r["status"] == "HOLD"]
    by_id = {r["id"]: r for r in current}
    selected = [by_id[r["id"]] for r in old_held]
    outstanding = [r for r in selected if r["status"] == "HOLD"]
    field_issues = Counter((field, v["state"]) for r in outstanding for field, v in r["nutrients"].items()
                           if v["state"] not in {"MATCH", "UNKNOWN_CURRENT_RETAINED"})
    args.output.mkdir(parents=True, exist_ok=True)
    with (args.output / "held-products.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=["id", "name", "source_key", "original_queue", "current_queue", "status", "all_holds", "nutrient_issues", "new_options"])
        writer.writeheader()
        for before, row in zip(old_held, selected):
            issues = {k: v for k, v in row["nutrients"].items() if v["state"] not in {"MATCH", "UNKNOWN_CURRENT_RETAINED"}}
            writer.writerow(dict(id=row["id"], name=row["before"]["food"]["name"], source_key=row["sourceKey"],
                original_queue=primary(before["holds"]), current_queue=primary(row["holds"]), status=row["status"],
                all_holds=json.dumps(row["holds"]), nutrient_issues=json.dumps(issues), new_options=len(row["options"])))
    all_decisions = [json.loads(line) for line in args.all_decisions.read_text(encoding="utf-8").splitlines()]
    source_summary = json.loads(args.all_decisions.parent.joinpath("summary.json").read_text())
    if source_summary["decisionsSha256"] != gate.source.r5.closure.sha(args.all_decisions):
        raise ValueError("Source proposal digest mismatch")
    ready_ids = {r["id"] for r in selected if r["status"] == "READY_FOR_BACKUP_REHEARSAL"}
    ready_decisions = [d for d in all_decisions if d["id"] in ready_ids]
    if len(ready_decisions) != len(ready_ids):
        raise ValueError("Ready source decision membership mismatch")
    (args.output / "ready-decisions.jsonl").write_text("".join(json.dumps(d, ensure_ascii=False) + "\n" for d in ready_decisions), encoding="utf-8")
    usda = json.loads(args.usda_evidence.read_text(encoding="utf-8"))
    source_times, queue, receipts = Counter(), [], {}
    now = datetime.now(timezone.utc)
    for d in all_decisions:
        if d["sourceKey"].startswith("USDA_FOODDATA:"):
            state = freshness(usda.get("fetchedAt"), now)
        elif d.get("evidenceOrigin") == "CACHED_OFF_DUMP":
            state = "BULK_ORIGIN_TIME_UNPROVEN"
            if d["decision"] == "BULK_SOURCE_BASIS_CORRECTION_REQUIRES_FULL_BEFORE_IMAGE":
                queue.append({"id": d["id"], "source_key": d["sourceKey"]})
        else:
            path = Path(d["evidenceFile"])
            if gate.source.r5.closure.sha(path) != d["evidenceSha256"]:
                raise ValueError("OFF source receipt digest mismatch")
            receipt = receipts.setdefault(str(path), json.loads(path.read_text(encoding="utf-8-sig")))
            if not fetch.cached_valid(receipt, d["sourceKey"].removeprefix("barcode:"), now):
                state = "COMPLETE_RECEIPT_UNPROVEN"
            else:
                state = freshness(receipt.get("checkedAt"), now)
        source_times[state] += 1
    with (args.output / "off-freshness-queue.csv").open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=["id", "source_key"])
        writer.writeheader()
        writer.writerows(queue)
    report = {"originalHeld": len(old_held), "originalExclusiveQueues": partition(old_held),
              "reviewedHeldStates": dict(Counter(r["status"] for r in selected)),
              "remainingExclusiveQueues": partition(outstanding), "remainingHeld": len(outstanding),
              "overlappingHolds": dict(Counter(h for r in outstanding for h in r["holds"])),
              "nutrientIssues": [{"field": f, "state": s, "products": n} for (f, s), n in sorted(field_issues.items())],
              "freshnessAsOf": now.isoformat(), "sourceProducts": len(all_decisions), "sourceFreshness": dict(source_times),
              "freshnessQueueRows": len(queue), "newPortionsReady": sum(len(r["options"]) for r in selected if r["status"] == "READY_FOR_BACKUP_REHEARSAL"),
              "databaseWrites": 0, "nutritionWrites": 0, "sourceFreshnessIsNotApproval": True,
              "additionalPreviouslyUnheldReviews": [{"id": r["id"], "name": r["before"]["food"]["name"], "holds": r["holds"]}
                  for r in current if r["status"] == "HOLD" and r["id"] not in {o["id"] for o in old_held}],
              "inputHashes": {str(p): gate.source.r5.closure.sha(p) for p in (args.original, args.review, args.all_decisions, args.usda_evidence)}}
    (args.output / "summary.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    for name in ("original", "review", "all-decisions", "usda-evidence", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    run(parser.parse_args())

"""Rebuild proposals from one new complete OFF receipt, never mixed old fields.

No imports or database writes. A fresh body is not full current DB approval.
"""
import argparse
import csv
import importlib.util
import json
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

spec = importlib.util.spec_from_file_location("queues", Path(__file__).with_name("audit-r5-held-queues.py"))
queues = importlib.util.module_from_spec(spec)
spec.loader.exec_module(queues)
source = queues.gate.source


def rebuild(row, receipt, previous, now):
    code = row["source_key"].removeprefix("barcode:")
    if not queues.fetch.cached_valid(receipt, code, now) or queues.freshness(receipt.get("checkedAt"), now) != "FRESH_24H":
        raise ValueError("Fresh exact-GTIN complete official receipt required")
    result = source.off_decision(row, receipt, now)
    old_core, new_core = previous.get("sourceCore"), result.get("sourceCore")
    core_changed = old_core != new_core if isinstance(old_core, dict) and isinstance(new_core, dict) else None
    result.update(previousSourceFingerprint=previous["sourceFingerprint"],
                  sourceBodyChanged=previous["sourceFingerprint"] != result["sourceFingerprint"],
                  sourceCoreChanged=core_changed,
                  receiptCheckedAt=receipt["checkedAt"], currentFullBeforeImageRequired=True,
                  applyAllowed=False, nutritionWriteAllowed=False)
    return result


def run(args):
    proof = json.loads(args.proof.read_text())
    if proof.get("sha256") != source.r5.closure.sha(args.catalog) or proof.get("transactionReadOnly") is not True or proof.get("exitCode") != 0:
        raise ValueError("Proven catalog nomination snapshot required")
    previous = [json.loads(line) for line in args.decisions.read_text(encoding="utf-8").splitlines()]
    if source.r5.closure.sha(args.decisions) != json.loads(args.decisions.parent.joinpath("summary.json").read_text())["decisionsSha256"]:
        raise ValueError("Previous source proposals changed")
    old = {d["id"]: d for d in previous}
    if len(old) != len(previous):
        raise ValueError("Previous source IDs duplicated")
    records = {p.stem.removeprefix("barcode-"): (p, json.loads(p.read_text(encoding="utf-8"))) for p in args.receipts.glob("barcode-*.json")}
    proposals, seen, statuses = [], set(), Counter()
    now = datetime.now(timezone.utc)
    with args.catalog.open(encoding="utf-8-sig", newline="") as stream:
        rows = csv.DictReader(stream)
        count = 0
        for raw in rows:
            count += 1
            row = source.r5.decode_row(raw)
            key = row.get("source_key", "")
            code = key.removeprefix("barcode:") if key.startswith("barcode:") else None
            if code not in records:
                continue
            path, receipt = records[code]
            if row["id"] not in old or old[row["id"]]["sourceKey"] != key:
                raise ValueError("New source receipt outside approved nomination set")
            seen.add(code)
            if receipt.get("status") != "FETCHED":
                statuses[receipt.get("status", "UNPROVEN")] += 1
                continue
            proposal = rebuild(row, receipt, old[row["id"]], now)
            proposal.update(evidenceFile=str(path), evidenceSha256=source.r5.closure.sha(path))
            proposals.append(proposal)
            statuses[proposal["decision"]] += 1
    if count != proof["rows"] or seen != set(records):
        raise ValueError("Incomplete snapshot scan or orphan source receipt")
    args.output.mkdir(parents=True, exist_ok=True)
    path = args.output / "refreshed-decisions.jsonl"
    path.write_text("".join(json.dumps(p, ensure_ascii=False) + "\n" for p in proposals), encoding="utf-8")
    summary = {"sourceReceipts": len(records), "productsReviewed": len(proposals), "decisions": dict(statuses),
               "changedSourceBodies": sum(p["sourceBodyChanged"] for p in proposals),
               "sourceCoreComparison": dict(Counter("UNMEASURED" if p["sourceCoreChanged"] is None else "CHANGED" if p["sourceCoreChanged"] else "UNCHANGED" for p in proposals)),
               "basisCorrectionNominations": sum(p["decision"] == "SOURCE_BASIS_CORRECTION_REQUIRES_FULL_BEFORE_IMAGE" for p in proposals),
               "checkedAt": now.isoformat(), "sourceNominationSnapshotTime": proof["time"],
               "databaseWrites": 0, "nutritionWrites": 0, "currentFullBeforeImageRequired": True,
               "decisionsSha256": source.r5.closure.sha(path)}
    (args.output / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    for name in ("catalog", "proof", "decisions", "receipts", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    run(parser.parse_args())

"""Independent read-only post-apply reconciliation against committed receipts."""
import argparse
import csv
import hashlib
import json
from pathlib import Path


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify(receipts, current):
    if len({r["id"] for r in receipts})!=len(receipts):
        raise ValueError("Duplicate applied product ID")
    for receipt in receipts:
        live=current[receipt["id"]]
        for key in ("food","options","option_localizations","evidence"):
            if live[key]!=receipt["after"][key]:
                raise ValueError("Post-apply drift on product "+receipt["id"]+"/"+key)
        protected=set(receipt["before"]["food"])-{"nutrition_reference_unit","serving_size_grams","serving_unit"}
        if any(receipt["before"]["food"][k]!=live["food"][k] for k in protected):
            raise ValueError("Unapproved product field changed")
        if live["references"]!=receipt["before"]["references"]:
            raise ValueError("Dependent recipe/template/plan changed during release")
    return {"status":"PASS","products":len(receipts),
            "referenceChanges":sum(r["before"]["food"]["nutrition_reference_unit"]!=r["after"]["food"]["nutrition_reference_unit"] for r in receipts),
            "newPortions":sum(len(r["insertedOptionIds"]) for r in receipts),
            "newLocalizations":sum(len(r["insertedOptionIds"])*2 for r in receipts),
            "newEvidenceFacts":sum(len(r["insertedEvidenceIds"]) for r in receipts),
            "nutritionNumberChanges":0,"historyWritesByPatch":0,"historyRowsIndependentlyCompared":False,
            "authenticatedMobileAcceptance":False}


def run(args):
    proof=json.loads(args.proof.read_text())
    if proof.get("scope")!="r5-release" or proof.get("transactionReadOnly") is not True or proof.get("sha256")!=sha(args.current) or proof.get("exitCode")!=0:
        raise ValueError("Post-apply read-only proof required")
    with args.current.open(encoding="utf-8-sig",newline="") as stream:
        rows=[{k:json.loads(v) if k!="id" else v for k,v in r.items()} for r in csv.DictReader(stream)]
    current={r["id"]:r for r in rows}
    if len(rows)!=proof["rows"] or len(current)!=len(rows):
        raise ValueError("Post-apply snapshot incomplete")
    receipts=[]
    for path in args.receipt:
        summary=json.loads(path.parent.joinpath("summary.json").read_text())
        if summary.get("status")!="APPLIED_RECEIPT_COMPLETE" or summary.get("receiptSha256")!=sha(path):
            raise ValueError("Committed complete receipt required")
        receipts.extend(json.loads(path.read_text(encoding="utf-8"))["rows"])
    result=verify(receipts,current)
    result.update(currentSnapshotTime=proof["time"],currentSha256=sha(args.current),receiptHashes={str(p):sha(p) for p in args.receipt})
    args.output.mkdir(parents=True,exist_ok=True)
    (args.output/"post-apply-proof.json").write_text(json.dumps(result,indent=2)+"\n")
    print(json.dumps(result,indent=2))


if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("--receipt",action="append",type=Path,required=True)
    for name in ("current","proof","output"):
        parser.add_argument("--"+name,type=Path,required=True)
    run(parser.parse_args())

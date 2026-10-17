"""Compare an independent read-only AWS snapshot with committed exact receipts."""
import argparse
import csv
import json
from datetime import datetime, timezone
from pathlib import Path
from importlib.util import module_from_spec, spec_from_file_location

spec=spec_from_file_location('r7_reconcile_operation',Path(__file__).with_name('r7-approved-presentation-operation.py'))
operation=module_from_spec(spec)
spec.loader.exec_module(operation)


def run(args):
    package,plan,before=operation.load(args.package)
    receipt=operation.read(args.package/'applied/receipt.json')
    summary=operation.read(args.package/'applied/summary.json')
    if (summary['status']!='APPLIED_RECEIPT_COMPLETE'
            or summary['receiptSha256']!=operation.sha(args.package/'applied/receipt.json')
            or summary['undoSha256']!=operation.sha(args.package/'applied/exact-undo.sql')):
        raise ValueError('Intact committed receipt and owned undo required')
    proof=operation.read(args.current/'snapshot-proof.json')
    now=datetime.now(timezone.utc)
    if (proof['origin']!='AWS_READ_ONLY_SNAPSHOT' or proof['transactionReadOnly'] is not True
            or proof['exitCode']!=0 or proof['schemaVersion']!='307' or proof['rows']!=5
            or proof['sha256']!=operation.sha(args.current/'catalog.csv')
            or not 0<=(now-datetime.fromisoformat(proof['time'])).total_seconds()<=1800):
        raise ValueError('Fresh intact independent read-only schema307 snapshot required')
    with (args.current/'catalog.csv').open(encoding='utf-8-sig',newline='') as handle:
        rows=[{'id':r['id'],**{k:json.loads(v) for k,v in r.items() if k!='id'}} for r in csv.DictReader(handle)]
    current={'kind':'r7_receipt','owner':package['operation'],'planDigest':package['planDigest'],
             'rows':rows,'dependencies':proof['metrics'][-1]}
    operation.verify(plan,before,package,current)
    if ({r['id']:r for r in current['rows']}!={r['id']:r for r in receipt['rows']}
            or current['dependencies']!=receipt['dependencies']):
        raise ValueError('Independent snapshot differs from committed after-images/FK proofs')
    report={'status':'PASS','operation':package['operation'],'products':5,'schemaVersion':'307',
            'referenceConstraints':len(before['dependencies']),'sourceEvidenceRowsRetained':104,
            'servingOptionsRetained':6,'all105NutritionFieldsUnchanged':True,
            'receiptSha256':summary['receiptSha256'],'postSnapshotSha256':proof['sha256'],
            'time':now.isoformat(),'undoPreparedNotExecuted':True,'databaseWrites':0}
    operation.write(args.output,report)
    print(json.dumps(report))


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    for key in ('package','current','output'):
        parser.add_argument('--'+key,type=Path,required=True)
    run(parser.parse_args())

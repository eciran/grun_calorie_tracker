"""Independent exact read after commit, including the reviewed hidden Lifeway row."""
import argparse
import copy
import csv
import json
from pathlib import Path
from types import SimpleNamespace
from importlib.util import module_from_spec,spec_from_file_location


def module(name,file):
    spec=spec_from_file_location(name,Path(__file__).with_name(file))
    value=module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


ops=module('common_reconcile_ops','approved-common-catalog-operation.py')
collector=module('common_independent_reader','collect-r3-source-snapshot.py')


def run(args):
    manifest,cp,rp=ops.validate_pair(args.package)
    receipt=ops.read(args.applied/'receipt.json')
    summary=ops.read(args.applied/'summary.json')
    if summary['status']!='APPLIED_RECEIPT_COMPLETE' or summary['receiptSha256']!=ops.sha(args.applied/'receipt.json'):
        raise ValueError('Complete immutable committed receipt required')
    original=collector.name_review_sql
    def post_names(selection):
        sql=original(selection)
        predicate="AND f.publication_status='PUBLISHED'"
        if sql.count(predicate)!=1: raise ValueError('Unexpected exact public cohort predicate')
        # One reviewed source row is intentionally hidden, never a private-food expansion.
        return sql.replace(predicate,"AND (f.publication_status='PUBLISHED' OR (f.id=181453 AND f.publication_status='INTERNAL_REVIEW'))")
    collector.name_review_sql=post_names
    if args.read:
        collector.collect(SimpleNamespace(template=args.template,output=args.output,profile='grun-staging',
                          cluster='grun-staging-cluster',service='grun-staging-api-service',scope='r5-common',
                          decisions=args.selection,existing_task=None,operation=None))
    proof=ops.read(args.output/'snapshot-proof.json')
    if (proof['rows']!=37 or proof['schemaVersion']!=cp['schemaVersion'] or proof['transactionReadOnly'] is not True
            or proof['exitCode']!=0 or proof['sha256']!=ops.sha(args.output/'catalog.csv')):
        raise ValueError('Independent complete read required')
    with (args.output/'catalog.csv').open(encoding='utf-8-sig',newline='') as stream:
        rows=[{k:v if k=='id' else json.loads(v) for k,v in row.items()} for row in csv.DictReader(stream)]
    observed=copy.deepcopy(receipt)
    observed['rows']=rows
    observed['dependencies']=proof['metrics'][4]
    observed['optionDependencies']=proof['metrics'][5]
    ops.pair.verify(cp,rp,observed,manifest['operation'],cp['dependencyBefore'],rp['optionDependenciesBefore'])
    if rows!=receipt['rows'] or observed['dependencies']!=receipt['dependencies'] or observed['optionDependencies']!=receipt['optionDependencies']:
        raise ValueError('Independent exact after-image drift; investigate without destructive rollback')
    brand=receipt['commonReceipt']['brand']
    alias=receipt['commonReceipt']['brandAlias']
    if brand not in proof['metrics'][2]['brands'] or alias not in proof['metrics'][2]['brandAliases']:
        raise ValueError('Independent brand receipt mismatch')
    result={'status':'PASS','products':37,'mutatingCandidates':35,'protectedProducts':2,'parentNutrientWrites':0,
            'oldOptionsPreserved':28,'newOptions':13,'newLabels':26,'oldOptionLogLinksPreserved':7,
            'fullAfterImagesMatchReceipt':True,'productFkPaths':26,'optionConsumerFkPaths':4,
            'actualSchema':proof['schemaVersion'],'snapshotTime':proof['time'],
            'receiptSha256':summary['receiptSha256'],'snapshotSha256':proof['sha256'],
            'producerSha256':ops.sha(Path(__file__)),'userHistoryContentsExported':False}
    ops.write(args.output/'reconciliation.json',result)
    print(json.dumps(result,indent=2))


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    for key in ('package','applied','template','selection','output'): parser.add_argument('--'+key,type=Path,required=True)
    parser.add_argument('--read',action='store_true')
    run(parser.parse_args())

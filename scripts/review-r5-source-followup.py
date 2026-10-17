"""Refresh held R5 source/portion decisions against a pinned historical review.

No database client or live SQL. A fresh source is not a fresh database before-image.
Later operation receipts quarantine superseded rows; original nutrition is retained.
"""
import argparse
import copy
import importlib.util
import json
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path


def module(name, file):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(file))
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


gate = module('r5_followup_gate', 'close-r5-release-review.py')
fetch = module('r5_followup_fetch', 'fetch-r5-off-evidence.py')
fdc = module('r5_followup_fdc', 'fetch-r3-usda-evidence.py')


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def work_queue(row):
    disposition = row['disposition']
    if disposition == 'SUPERSEDED_BASELINE_REBASE_REQUIRED': return 'REBASE_LATER_APPLIED_RECORDS'
    if disposition == 'ARCHIVED_CONTRACT_PASS_REBASE_REQUIRED': return 'PREPARED_REBASE_AND_APPROVAL'
    if disposition == 'FRESH_COMPLETE_SOURCE_REQUIRED': return 'FRESH_SOURCE_REQUIRED'
    holds = set(row.get('holds', []))
    if holds & {'R3_R4_CLASSIFICATION_OR_PREPARATION_GATE', 'ADDITIVE_SOURCE_TYPE_OR_BASIS_REVIEW', 'SOURCE_PREPARATION_CONFLICT'}:
        return 'CLASSIFICATION_OR_PREPARATION_PREREQUISITE'
    if 'CURRENT_NAME_SOURCE_IDENTITY_REVIEW' in holds: return 'SOURCE_IDENTITY_AND_NAME_REVIEW'
    states = {v['state'] for v in row.get('nutrients', {}).values()}
    if 'SOURCE_VARIANT_CONFLICT' in states: return 'INVALID_OR_DUPLICATE_TYPED_SOURCE'
    if states & {'UNIT_OR_FORM_ATTESTATION_REQUIRED', 'SOURCE_DEFINITION_REVIEW_REQUIRED'}:
        return 'NUTRIENT_UNIT_FORM_OR_DEFINITION_REVIEW'
    if 'FULL_NUTRITION_COMPARISON_REQUIRED' in holds: return 'NUTRIENT_DIFFERENCE_OR_MISSING_SOURCE'
    if holds & {'LIQUID_ML_CONTRACT_REVIEW', 'LIQUID_LABEL_OR_MASS_VOLUME_SOURCE_REVIEW'}:
        return 'EXPLICIT_LIQUID_PAIR_OR_LABEL_REQUIRED'
    if 'EXISTING_EQUIVALENT_OPTION_REVIEW_REQUIRED' in holds: return 'EXISTING_EQUIVALENT_OPTION_APPROVAL'
    return 'OTHER_ADMIN_OR_OPTION_REVIEW'


def review_row(original, previous, body, checked_at, now, superseded=False):
    current = copy.deepcopy(original['before'])
    food = current['food']
    if str(food['id']) != original['id'] or food['source_key'] != original['sourceKey'] or previous['sourceKey'] != original['sourceKey']:
        raise ValueError('Historical product/source identity mismatch')
    row = {**food, 'id': str(food['id'])}
    if food['data_source'] == 'USDA_FOODDATA':
        r4_keys = {original['sourceKey']} if any('R3/R4' in g for g in previous.get('requiredGates', [])) else set()
        decision = gate.source.usda_decision(row, body, r4_keys)
        candidates, unsupported = gate.source.usda_portion_options(body)
        valid_options = {gate.source.r5.fingerprint(o) for o in candidates}
    else:
        decision = gate.source.off_decision(row, {'product': body, 'barcode': food.get('normalized_barcode') or food.get('barcode'),
                 'status': 'FETCHED', 'checkedAt': checked_at}, now)
        candidates, unsupported, valid_options = decision['options'], [], set()
    result = gate.review(decision, current, body, checked_at, now, approve_additive_usda=True)
    if decision['decision'] not in gate.READY:
        result['holds'] = sorted(set(result['holds']) | {'SOURCE_DECISION_NOT_READY'})
        result['status'] = 'HOLD'
    exact = sum(gate.source.r5.fingerprint(o) in valid_options for o in result['options']) if valid_options else 0
    result.update(sourceDecision=decision['decision'], sourceCheckedAt=checked_at,
                  previousSourceFingerprint=previous['sourceFingerprint'],
                  sourceBodyChanged=previous['sourceFingerprint'] != gate.source.r5.fingerprint(body),
                  sourceOptions=copy.deepcopy(candidates), unsupportedSourceMeasures=unsupported,
                  exactSourceOptionsInProposal=exact, archivedGateStatus=result['status'],
                  status='REVIEW_ONLY_ARCHIVED_BASELINE',
                  currentDatabaseBeforeImageRequired=True, applyAllowed=False, databaseWrites=0,
                  legacyMicronutrientUnitsCertified=False,
                  disposition='ARCHIVED_CONTRACT_PASS_REBASE_REQUIRED' if result['status'] == 'READY_FOR_BACKUP_REHEARSAL'
                  else 'ARCHIVED_NO_CHANGE_RECHECK_REQUIRED' if result['status'] == 'NO_CHANGE' else 'HELD')
    if superseded:
        result['disposition'] = 'SUPERSEDED_BASELINE_REBASE_REQUIRED'
    if original['before'] != current:
        raise ValueError('Historical before-image changed during review')
    return result


def run(args):
    now = datetime.now(timezone.utc)
    hashes = {}
    def pin(path):
        hashes[str(path)] = gate.source.r5.closure.sha(path)
        return read(path)
    manifest = pin(args.review)
    summary = pin(args.review.parent / 'summary.json')
    if summary['reviewSha256'] != hashes[str(args.review)]:
        raise ValueError('Historical review digest mismatch')
    decision_summary = pin(args.decisions.parent / 'summary.json')
    hashes[str(args.decisions)] = gate.source.r5.closure.sha(args.decisions)
    if decision_summary['decisionsSha256'] != hashes[str(args.decisions)]:
        raise ValueError('Historical source decision digest mismatch')
    previous = {d['id']: d for d in (json.loads(line) for line in args.decisions.read_text(encoding='utf-8').splitlines())}
    held = [r for r in manifest['rows'] if r['status'] == 'HOLD']
    if len({r['id'] for r in held}) != len(held): raise ValueError('Duplicate historical held ID')
    expected_fdc = {gate.source.r5.closure.review.fdc_id(r['sourceKey']) for r in held if r['sourceKey'].startswith('USDA_FOODDATA:')}
    expected_off = {r['sourceKey'].removeprefix('barcode:') for r in held if r['sourceKey'].startswith('barcode:')}
    sources, fdc_seen = {}, set()
    for folder in args.usda_folder:
        for path in sorted(folder.glob('batch-*.json')):
            if path.name.endswith('.receipt.json'): continue
            receipt = pin(path.with_suffix('.receipt.json'))
            pin(path)
            fdc.cached_batch_receipt(path, receipt['ids'], now)
            for food in fdc.validate_batch(receipt['ids'], read(path)):
                key = str(food['fdcId'])
                if key in fdc_seen or key not in expected_fdc: raise ValueError('Unexpected/duplicate USDA source identity')
                fdc_seen.add(key)
                sources['USDA_FOODDATA:fdc:' + key] = (food, receipt['fetchedAt'])
    off_seen = set()
    for path in sorted(args.off_folder.glob('barcode-*.json')):
        receipt = pin(path)
        code = receipt.get('barcode')
        if code not in expected_off or code in off_seen: raise ValueError('Unexpected/duplicate OFF receipt')
        off_seen.add(code)
        if fetch.cached_valid(receipt, code, now):
            checked = datetime.fromisoformat(receipt['checkedAt'].replace('Z', '+00:00'))
            if 0 <= (now - checked).total_seconds() <= 86400:
                sources['barcode:' + code] = (receipt['product'], receipt['checkedAt'])
    superseded = set()
    for path in args.superseding_receipt:
        receipt = pin(path)
        if not isinstance(receipt.get('rows'), list): raise ValueError('Expected full later-operation receipt rows')
        superseded.update(str(r['id']) for r in receipt['rows'])
    result = []
    for original in held:
        key = original['sourceKey']
        if original['id'] not in previous: raise ValueError('Missing original source decision')
        if key not in sources:
            result.append({'id': original['id'], 'sourceKey': key, 'disposition': 'FRESH_COMPLETE_SOURCE_REQUIRED',
                           'status': 'REVIEW_ONLY_ARCHIVED_BASELINE', 'archivedGateStatus': 'SOURCE_REQUIRED',
                           'holds': original['holds'], 'applyAllowed': False, 'liveApplyApproved': False,
                           'nutritionWriteAllowed': False, 'currentDatabaseBeforeImageRequired': True})
            continue
        body, checked = sources[key]
        result.append(review_row(original, previous[original['id']], body, checked, now, original['id'] in superseded))
    for row in result:
        row['workQueue'] = work_queue(row)
    report = {'version': 'R5_SOURCE_PORTION_FOLLOWUP_V1', 'gateRuleset': gate.RULESET,
              'reviewedAt': now.isoformat(), 'scope': 'HISTORICAL_HELD_SELECTION_ONLY',
              'products': len(held), 'freshUsdaIdentities': len(fdc_seen),
              'freshOffIdentities': sum(k.startswith('barcode:') for k in sources), 'offReceiptsSeen': len(off_seen),
              'productsWithFreshSource': sum(r['sourceKey'] in sources for r in held),
              'dispositions': dict(Counter(r['disposition'] for r in result)),
              'archivedGateStates': dict(Counter(r.get('archivedGateStatus', 'SOURCE_REQUIRED') for r in result)),
              'exclusiveWorkQueues': dict(Counter(r['workQueue'] for r in result)),
              'typedFieldReviewStates': dict(Counter(v['state'] for r in result for v in r.get('nutrients', {}).values() if v.get('typedSourceReviewOnly'))),
              'typedFieldNumericMatches': sum(bool(v['matchingSourceIds']) for r in result for v in r.get('nutrientVariants', {}).values()),
              'overlappingHolds': dict(Counter(h for r in result for h in r.get('holds', []))),
              'sourceOptionsRebuilt': sum(len(r.get('sourceOptions', [])) for r in result),
              'exactSourceOptionsInProposals': sum(r.get('exactSourceOptionsInProposal', 0) for r in result),
              'unsupportedSourceMeasures': sum(len(r.get('unsupportedSourceMeasures', [])) for r in result),
              'sourceBodyChanges': sum(r.get('sourceBodyChanged', False) for r in result),
              'databaseWrites': 0, 'nutritionWrites': 0, 'liveApplyAllowed': False, 'globalR5Completed': False,
              'currentDatabaseMeasured': False, 'inputHashes': hashes}
    args.output.mkdir(parents=True, exist_ok=True)
    path = args.output / 'source-review.json'
    path.write_text(json.dumps({'summary': report, 'rows': result}, ensure_ascii=False, indent=2, allow_nan=False), encoding='utf-8')
    report['reviewSha256'] = gate.source.r5.closure.sha(path)
    (args.output / 'summary.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    for name in ('review', 'decisions', 'off-folder', 'output'): parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--usda-folder', action='append', type=Path, required=True)
    parser.add_argument('--superseding-receipt', action='append', type=Path, default=[])
    run(parser.parse_args())

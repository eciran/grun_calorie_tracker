"""Prepare direct-source Vitamin A RAE evidence for the exact87 F4 unit queue.

No IU conversion, numeric writes or retroactive certification of legacy scalars.
New typed source evidence still needs current DB rebase, storage and approval.
"""
import argparse
import ast
import copy
import csv
import importlib.util
import json
import math
from collections import Counter
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from pathlib import Path


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


names = module('r5_typed_names', 'prepare-r5-source-names.py')
gate, sha, fingerprint = names.gate, names.sha, names.fingerprint
VERSION = 'R5_SOURCE_TYPED_RAE_REATTESTATION_REVIEW_V1'
CONTRACT_VERSION = 'usda-typed-nutrient-contract-r5-v1'
QUEUE = 'NUTRIENT_UNIT_FORM_OR_DEFINITION_REVIEW'
EXACT_A = {'nutrientId': 1106, 'nutrientNumber': '320', 'sourceUnit': 'ug',
           'semantic': 'VITAMIN_A_RAE', 'canonicalUnit': 'MCG_RAE'}


def contract_spec(contract):
    if (contract.get('version') != CONTRACT_VERSION or contract.get('basis') != 'PER_100G'
            or contract.get('fields', {}).get('vitamin_a') != EXACT_A
            or not contract.get('policy') or any(value is not False for value in contract['policy'].values())):
        raise ValueError('Exact non-converting RAE contract required')
    return contract['fields']['vitamin_a']


def source_rae(body, spec):
    records = [n for n in body.get('foodNutrients', []) if n.get('nutrient', {}).get('id') == spec['nutrientId']]
    if len(records) != 1:
        return None, 'MISSING_OR_DUPLICATE_EXACT_RAE_SOURCE'
    record = records[0]
    nutrient = record['nutrient']
    unit = str(nutrient.get('unitName', '')).lower().replace('\u00b5', 'u').replace('\u03bc', 'u')
    if (str(nutrient.get('number')) != spec['nutrientNumber'] or unit != spec['sourceUnit']
            or nutrient.get('name') != 'Vitamin A, RAE'):
        return None, 'EXACT_RAE_UNIT_FORM_OR_NUMBER_CONFLICT'
    amount = record.get('amount')
    if isinstance(amount, bool) or not isinstance(amount, (int, float)) or not math.isfinite(amount) or amount < 0:
        return None, 'INVALID_EXACT_RAE_SOURCE_AMOUNT'
    return {'field': 'vitamin_a', 'provider': 'USDA_FOODDATA', 'sourceNutrientId': spec['nutrientId'],
            'sourceNutrientNumber': spec['nutrientNumber'], 'sourceNutrientName': nutrient['name'],
            'sourceUnit': nutrient['unitName'], 'normalizedSourceUnit': unit, 'semantic': spec['semantic'],
            'canonicalUnit': spec['canonicalUnit'], 'basis': 'PER_100G', 'sourceAmount': amount,
            'sourceNutrientRecordFingerprint': fingerprint(record), 'numericConversion': False,
            'legacyDatabaseUnitAttested': False, 'applyAllowed': False}, None


def review_row(row, body, contract, now, seed=None):
    original = copy.deepcopy(row)
    spec = contract_spec(contract)
    before, food = row['before'], row['before']['food']
    holds = []
    if (row.get('projectedWorkQueue') != QUEUE or row.get('status') != 'REVIEW_ONLY_ARCHIVED_BASELINE'
            or row.get('applyAllowed') is not False or row.get('liveApplyApproved') is not False
            or row.get('nutritionWriteAllowed') is not False or row.get('runtimeRegistryEnabled') is not False):
        raise ValueError('Only the exact review-only F4 unit queue can be inspected')
    if (row['beforeFingerprint'] != fingerprint(before) or str(food['id']) != row['id']
            or food.get('source_key') != row['sourceKey'] or food.get('data_source') != 'USDA_FOODDATA'
            or names.prereq.closure.review.fdc_id(row['sourceKey']) != str(body.get('fdcId'))
            or row['sourceFingerprint'] != fingerprint(body) or row['sourceDescription'] != body.get('description')
            or body.get('dataType') not in {'SR Legacy', 'Foundation'}
            or food.get('nutrition_reference_unit') != 'PER_100G' or food.get('nutrition_basis') != 'SOURCE_REPORTED'
            or food.get('is_custom') is not False or food.get('publication_status') != 'PUBLISHED'
            or food.get('barcode') or food.get('normalized_barcode') or body.get('gtinUpc') or body.get('brandOwner')):
        raise ValueError('Exact protected source/before-image identity required')
    try:
        stamp = datetime.fromisoformat(row['sourceCheckedAt'].replace('Z', '+00:00'))
        if stamp.tzinfo is None or not 0 <= (now - stamp).total_seconds() <= 86400:
            holds.append('SOURCE_FRESHNESS_RECHECK_REQUIRED')
    except (ValueError, TypeError, AttributeError):
        holds.append('SOURCE_FRESHNESS_UNPROVEN')
    # Rebuild every comparison from the same body before considering new evidence.
    projected = copy.deepcopy(before)
    if set(row['foodChanges']) - {'catalog_type', 'name', 'display_name', 'short_display_name'}:
        raise ValueError('Unexpected predecessor numeric or preparation proposal')
    projected['food'].update(row['foodChanges'])
    decision = gate.source.usda_decision({**projected['food'], 'id': row['id']}, body, set())
    check = gate.review(decision, projected, body, row['sourceCheckedAt'], now, approve_additive_usda=True)
    if check['nutrients'] != row['numericComparison'] or check['nutrientVariants'] != row['nutrientVariants']:
        raise ValueError('Source comparison/typed alternatives drift')
    failing = {k for k, value in check['nutrients'].items() if value['state'] not in {'MATCH', 'UNKNOWN_CURRENT_RETAINED'}}
    if failing != {'vitamin_a'}:
        holds.append('ADDITIONAL_OR_DIFFERENT_NUTRIENT_REVIEW_REQUIRED')
    if any(v['sourceConflict'] for v in check['nutrientVariants'].values()):
        holds.append('TYPED_SOURCE_ALTERNATIVE_CONFLICT')
    proposal, error = source_rae(body, spec)
    if error:
        holds.append(error)
    actual = food.get('vitamin_a')
    if isinstance(actual, bool) or not isinstance(actual, (int, float)) or not math.isfinite(actual) or actual < 0:
        holds.append('CURRENT_VALUE_UNKNOWN_OR_INVALID_DO_NOT_FILL')
    elif proposal and Decimal(str(actual)) != Decimal(str(proposal['sourceAmount'])):
        holds.append('CANONICAL_NUMBER_DIFFERENCE_REQUIRES_EXPLICIT_NUTRITION_REVIEW')
    old_evidence = [e for e in before['evidence'] if e.get('field_name') == 'VITAMIN_A']
    if (len(old_evidence) != 1 or old_evidence[0].get('provider') != 'USDA_FOODDATA'
            or old_evidence[0].get('external_id') != row['sourceKey'] or old_evidence[0].get('basis') != 'PER_100_G'
            or old_evidence[0].get('numeric_value') != actual):
        holds.append('LEGACY_EVIDENCE_IDENTITY_OR_VALUE_REBASE_REQUIRED')
    if any(e.get(k) is not None for e in old_evidence for k in ('unit', 'nutrient_unit', 'semantic', 'source_nutrient_id')):
        holds.append('EXISTING_TYPED_EVIDENCE_REQUIRES_EXPLICIT_REBASE')
    if proposal:
        proposal.update(externalId=row['sourceKey'], sourceBodyFingerprint=fingerprint(body),
                        sourceCheckedAt=row['sourceCheckedAt'], sourceUrl='https://fdc.nal.usda.gov/food-details/' + str(body['fdcId']) + '/nutrients',
                        intendedAction='APPEND_NEW_TYPED_SOURCE_ATTESTATION_AFTER_REBASE_AND_APPROVAL',
                        replacesLegacyEvidence=False, databaseStorageSchemaAvailable=False)
    seed_info = {'measured': seed is not None, 'sameSourceKey': False, 'sameVitaminANumber': False,
                 'provesImportExecuted': False, 'certifiesLegacyUnit': False}
    if seed is not None:
        seed_info['sameSourceKey'] = seed.get('source_key') == row['sourceKey']
        try:
            seed_info['sameVitaminANumber'] = Decimal(seed['vitamin_a']) == Decimal(str(actual))
        except (InvalidOperation, TypeError, ValueError):
            seed_info['sameVitaminANumber'] = False
        seed_info['sourceNote'] = seed.get('source_note')
    if row != original or projected['options'] != before['options']:
        raise ValueError('Historical data mutated')
    return {'id': row['id'], 'sourceKey': row['sourceKey'], 'before': copy.deepcopy(before),
            'beforeFingerprint': row['beforeFingerprint'], 'sourceFingerprint': fingerprint(body),
            'sourceCheckedAt': row['sourceCheckedAt'], 'originalNumericComparison': copy.deepcopy(row['numericComparison']),
            'originalTypedVariants': copy.deepcopy(row['nutrientVariants']),
            'existingHolds': row['projectedHolds'][:], 'newSourceEvidenceProposal': proposal,
            'legacyEvidenceFingerprint': fingerprint(old_evidence), 'historicalSeedCorroboration': seed_info,
            'sourceReviewState': 'HELD' if holds else 'DIRECT_RAE_SOURCE_REATTESTATION_PREPARED',
            'sourceReviewHolds': sorted(set(holds)),
            'releaseHolds': sorted(set(row['projectedHolds']) | {
                'TYPED_NUTRIENT_PROVENANCE_STORAGE_AND_CURRENT_REBASE_REQUIRED',
                'NEW_SOURCE_REATTESTATION_REQUIRES_EXPLICIT_APPROVAL'}),
            'status': 'REVIEW_ONLY_ARCHIVED_BASELINE', 'numericChanges': {}, 'legacyUnitAttested': False,
            'numericConversionAllowed': False, 'nutritionWriteAllowed': False, 'applyAllowed': False,
            'liveApplyApproved': False, 'currentDatabaseBeforeImageRequired': True,
            'databaseWrites': 0, 'currentEvidenceReplaced': False}


def run(args):
    if args.output.exists():
        raise ValueError('New output directory required')
    now, hashes = datetime.now(timezone.utc), {}

    def pin(path):
        hashes[str(path)] = sha(path)
        return names.prereq.followup.read(path)

    prior = pin(args.review)
    summary = pin(args.review.parent / 'summary.json')
    contract = pin(args.contract)
    contract_spec(contract)
    if (summary['reviewSha256'] != hashes[str(args.review)] or prior['summary']['version'] != names.VERSION
            or prior['summary']['currentDatabaseMeasured'] is not False
            or prior['summary']['liveApplyAllowed'] is not False):
        raise ValueError('Pinned F4 ancestry required')
    names.prereq.unique_index(prior['rows'], 'id')
    names.prereq.unique_index(prior['rows'], 'sourceKey')
    selected = [r for r in prior['rows'] if r['projectedWorkQueue'] == QUEUE]
    if len(selected) != 87:
        raise ValueError('Exact87 unit-review queue required')
    hashes[str(args.historical_seed)] = sha(args.historical_seed)
    with args.historical_seed.open(encoding='utf-8-sig', newline='') as handle:
        seed = names.prereq.unique_index(list(csv.DictReader(handle)), 'source_key')
    hashes[str(args.seed_builder)] = sha(args.seed_builder)
    assignments = [n for n in ast.parse(args.seed_builder.read_text(encoding='utf-8')).body
                   if isinstance(n, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'NUTRIENTS' for t in n.targets)]
    if len(assignments) != 1 or ast.literal_eval(assignments[0].value).get('1106') != 'vitamin_a':
        raise ValueError('Historical builder mapping changed; no lineage corroboration')
    sources = {}
    for folder in args.usda_folder:
        for path in sorted(folder.glob('batch-*.json')):
            if path.name.endswith('.receipt.json'):
                continue
            receipt = pin(path.with_suffix('.receipt.json'))
            pin(path)
            for item in (path, path.with_suffix('.receipt.json')):
                if prior['summary']['inputHashes'].get(str(item)) != hashes[str(item)]:
                    raise ValueError('Changed/restamped F4 source evidence')
            names.prereq.followup.fdc.cached_batch_receipt(path, receipt['ids'], now)
            for food in names.prereq.followup.fdc.validate_batch(receipt['ids'], names.prereq.followup.read(path)):
                key = str(food['fdcId'])
                if key in sources:
                    raise ValueError('Duplicate source identity')
                sources[key] = food, receipt['fetchedAt']
    result = []
    for row in selected:
        source_id = names.prereq.closure.review.fdc_id(row['sourceKey'])
        if source_id not in sources:
            raise ValueError('Missing complete exact source')
        body, checked_at = sources[source_id]
        if checked_at != row['sourceCheckedAt']:
            raise ValueError('Source receipt timestamp differs from F4')
        result.append(review_row(row, body, contract, now, seed.get(row['sourceKey'])))
    report = {'version': VERSION, 'reviewedAt': now.isoformat(), 'products': len(result),
              'scope': 'EXACT87_HISTORICAL_F4_UNIT_QUEUE_NOT_CURRENT_CATALOG',
              'sourceReviewStates': dict(Counter(r['sourceReviewState'] for r in result)),
              'sourceReviewHolds': dict(Counter(h for r in result for h in r['sourceReviewHolds'])),
              'newTypedEvidenceProposals': sum(r['sourceReviewState'] != 'HELD' for r in result),
              'legacyUnitsCertified': 0, 'historicalSeedNumericMatches': sum(r['historicalSeedCorroboration']['sameVitaminANumber'] for r in result),
              'unselectedF4RecordsUntouched': len(prior['rows']) - len(result),
              'numericChangeProposals': 0, 'nutritionWrites': 0, 'databaseWrites': 0,
              'currentDatabaseMeasured': False, 'liveApplyAllowed': False, 'releaseReadyProducts': 0,
              'typedProvenanceStorageReady': False, 'globalR5Completed': False, 'inputHashes': hashes}
    args.output.mkdir(parents=True, exist_ok=False)
    path = args.output / 'typed-reattestation-review.json'
    path.write_text(json.dumps({'summary': report, 'rows': result}, ensure_ascii=False, indent=2, allow_nan=False), encoding='utf-8')
    report['reviewSha256'] = sha(path)
    (args.output / 'summary.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps({k: v for k, v in report.items() if k != 'inputHashes'}, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    for name in ('review', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--contract', type=Path, default=Path('src/main/resources/catalog/usda-typed-nutrient-contract-r5-v1.json'))
    parser.add_argument('--historical-seed', type=Path, default=Path('outputs/athlete-food-catalog-v2.csv'))
    parser.add_argument('--seed-builder', type=Path, default=Path('scripts/build-athlete-food-catalog.py'))
    parser.add_argument('--usda-folder', type=Path, action='append', required=True)
    run(parser.parse_args())

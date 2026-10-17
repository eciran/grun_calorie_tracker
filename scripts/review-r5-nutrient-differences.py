"""Review the exact22 archived R5 source-difference rows. Never writes DB values.

Source units, app target units and legacy unknown units are distinct. This file
must not clear old holds or turn a source-only observation into an approved patch.
"""
import argparse
import copy
import importlib.util
import json
import math
from collections import Counter
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path

spec = importlib.util.spec_from_file_location('r5_difference_followup', Path(__file__).with_name('review-r5-source-followup.py'))
followup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(followup)
gate = followup.gate
fingerprint = gate.source.r5.fingerprint
sha = gate.source.r5.closure.sha
PINNED_REVIEW = 'be835bf5832ecd9a8983357f74fe9e73ac13e8368ba55bd409348a415bfd1507'
QUEUE = 'NUTRIENT_DIFFERENCE_OR_MISSING_SOURCE'
VERSION = 'R5_EXACT22_TYPED_DIFFERENCE_REVIEW_V1'
USDA_FIELDS = {
    'potassium': (1092, '306', 'Potassium, K', 'mg', 'POTASSIUM', 'MG'),
    'vitamin_e': (1109, '323', 'Vitamin E (alpha-tocopherol)', 'mg', 'VITAMIN_E_ALPHA_TOCOPHEROL', 'MG'),
    'vitamin_b12': (1178, '418', 'Vitamin B-12', 'ug', 'VITAMIN_B12', 'MCG'),
}


def number(value):
    try:
        valid = not isinstance(value, bool) and isinstance(value, (int, float)) and math.isfinite(value) and value >= 0
    except OverflowError:
        valid = False
    if not valid:
        raise ValueError('Nonnegative finite numeric source amount required')
    return Decimal(str(value))


def fresh(stamp, now):
    if not isinstance(stamp, str) or not isinstance(now, datetime) or now.tzinfo is None:
        raise ValueError('Original timezone-aware source receipt required')
    parsed = datetime.fromisoformat(stamp.replace('Z', '+00:00'))
    if parsed.tzinfo is None or not 0 <= (now - parsed).total_seconds() <= 86400:
        raise ValueError('Original source receipt expired/future/unproven; never restamp')


def usda_fact(field, body):
    if field not in USDA_FIELDS:
        raise ValueError('Unreviewed USDA difference field')
    nid, num, name, unit, semantic, canonical = USDA_FIELDS[field]
    records = [n for n in body.get('foodNutrients', [])
               if type(n.get('nutrient', {}).get('id')) is int and n['nutrient']['id'] == nid]
    if len(records) != 1:
        raise ValueError('Exactly one typed source nutrient ID required')
    record, nutrient = records[0], records[0]['nutrient']
    declared = str(nutrient.get('unitName', '')).lower().replace('\u00b5', 'u').replace('\u03bc', 'u')
    if str(nutrient.get('number')) != num or nutrient.get('name') != name or declared != unit:
        raise ValueError('Source ID/number/form/unit conflict')
    amount = number(record.get('amount'))
    return {'field': field, 'sourceNutrientId': nid, 'sourceNutrientNumber': num,
            'sourceName': name, 'declaredSourceUnit': nutrient['unitName'], 'normalizedSourceUnit': unit,
            'sourceAmount': str(amount), 'canonicalSourceAmount': str(amount), 'canonicalSourceUnit': canonical,
            'semantic': semantic, 'sourceBasis': 'PER_100G', 'sourceUnitConversionApplied': False,
            'sourceRecordFingerprint': fingerprint(record)}


def off_fact(field, body):
    # This cohort uses explicit legacy g-labelled, native-100g/ml source entries.
    # Do not guess semantics of another OFF schema/unit/display-value combination.
    if field not in {'sodium', 'fiber'} or body.get('nutrition_data_per') not in {'100g', '100ml'}:
        raise ValueError('Reviewed OFF field and explicit native reference required')
    n = body.get('nutriments', {})
    if field + '_100g' not in n:
        return None
    if str(n.get(field + '_unit', '')).lower() != 'g' or n.get(field + '_modifier') not in {None, ''}:
        raise ValueError('Explicit exact source grams required; inequalities are not exact amounts')
    amount = number(n[field + '_100g'])
    # Native reference means these declared display/source values must agree.
    for key in (field, field + '_value'):
        if key in n and number(n[key]) != amount:
            raise ValueError('OFF source value/unit representation conflict')
    factor, canonical = (Decimal(1000), 'MG') if field == 'sodium' else (Decimal(1), 'G')
    return {'field': field, 'sourceField': field + '_100g', 'declaredSourceUnit': 'g',
            'normalizedSourceUnit': 'g', 'sourceAmount': str(amount),
            'canonicalSourceAmount': str(amount * factor), 'canonicalSourceUnit': canonical,
            'semantic': 'SODIUM' if field == 'sodium' else 'DIETARY_FIBER_REPORTED',
            'sourceBasis': 'PER_100ML' if body['nutrition_data_per'] == '100ml' else 'PER_100G',
            'sourceUnitConversionApplied': factor != 1,
            'sourceRecordFingerprint': fingerprint({k: v for k, v in n.items() if k == field or k.startswith(field + '_')})}


def review_row(row, body, stamp, now):
    original = copy.deepcopy(row)
    if (row.get('workQueue') != QUEUE or row.get('disposition') != 'HELD'
            or row.get('status') != 'REVIEW_ONLY_ARCHIVED_BASELINE'
            or row.get('applyAllowed') is not False or row.get('liveApplyApproved') is not False
            or row.get('nutritionWriteAllowed') is not False or row.get('legacyMicronutrientUnitsCertified') is not False
            or row.get('sourceFingerprint') != fingerprint(body) or row.get('sourceCheckedAt') != stamp
            or row.get('beforeFingerprint') != fingerprint(row['before'])):
        raise ValueError('Exact immutable unapproved F2 difference row required')
    fresh(stamp, now)
    food = row['before']['food']
    if (str(food.get('id')) != row['id'] or food.get('source_key') != row['sourceKey']
            or food.get('is_custom') is not False or food.get('publication_status') != 'PUBLISHED'
            or food.get('nutrition_basis') != 'SOURCE_REPORTED'):
        raise ValueError('Exact protected source-reported catalog identity required')
    provider = food['data_source']
    if provider == 'USDA_FOODDATA':
        if (row['sourceKey'] != 'USDA_FOODDATA:fdc:' + str(body.get('fdcId'))
                or body.get('dataType') not in {'Foundation', 'SR Legacy'}
                or food.get('nutrition_reference_unit') != 'PER_100G'):
            raise ValueError('Exact USDA identity/type/native basis required')
        values = gate.all_usda_fields(body)
    elif provider == 'OPEN_FOOD_FACTS':
        code = food.get('normalized_barcode') or food.get('barcode')
        if not gate.source.r5.valid_gtin(code) or str(body.get('code')) != code or row['sourceKey'] != 'barcode:' + code:
            raise ValueError('Exact valid GTIN/source identity required')
        values = gate.all_off_fields(body)
    else:
        raise ValueError('Unsupported provider')
    rebuilt = gate.comparison(food, values, provider)
    if rebuilt != row['nutrients']:
        raise ValueError('Original full21-field comparison drift')
    findings = []
    for field, check in rebuilt.items():
        if check['state'] in {'MATCH', 'UNKNOWN_CURRENT_RETAINED'}:
            continue
        if check['state'] not in {'DIFFERS', 'SOURCE_VALUE_REQUIRED'}:
            raise ValueError('Unreviewed source comparison state')
        number(food[field])
        fact = usda_fact(field, body) if provider == 'USDA_FOODDATA' else off_fact(field, body)
        if fact is None:
            if check['source'] is not None:
                raise ValueError('Missing-source classification drift')
            queue = 'MISSING_SOURCE_OPTIONAL_VALUE_RETAIN_CURRENT'
        elif provider == 'OPEN_FOOD_FACTS':
            queue = 'OFF_SOURCE_UNIT_AND_LEGACY_ZERO_REVIEW'
        elif number(food[field]) == 0 and Decimal(fact['sourceAmount']) > 0:
            queue = 'USDA_CURRENT_ZERO_SOURCE_POSITIVE_REVIEW'
        else:
            queue = 'USDA_NONZERO_SOURCE_PROFILE_DIFFERENCE_REVIEW'
        findings.append({'field': field, 'reviewQueue': queue, 'currentScalar': food[field],
                         'currentScalarUnit': 'UNATTESTED_LEGACY_UNIT', 'legacyComparison': copy.deepcopy(check),
                         'sourceFact': fact, 'sourceFactPrepared': fact is not None,
                         'canonicalCurrentComparison': 'NOT_COMPARABLE_WITHOUT_LEGACY_UNIT_ATTESTATION',
                         'missingSourceMeansZero': False, 'currentZeroProvenMeasured': False,
                         'legacyNumericConversionAllowed': False, 'nutritionWriteAllowed': False,
                         'sourceFactIsCurrentDatabaseUnitProof': False, 'existingTypedLedgerSupportsThisField': False})
    if not findings:
        raise ValueError('Selected difference row unexpectedly has no findings')
    if row != original:
        raise ValueError('Historical row mutated')
    return {'id': row['id'], 'sourceKey': row['sourceKey'], 'provider': provider,
            'before': copy.deepcopy(row['before']), 'beforeFingerprint': row['beforeFingerprint'],
            'sourceFingerprint': row['sourceFingerprint'], 'sourceCheckedAt': stamp,
            'sourceUrl': 'https://fdc.nal.usda.gov/food-details/' + str(body['fdcId']) + '/nutrients'
                if provider == 'USDA_FOODDATA' else 'https://world.openfoodfacts.org/product/' + str(body['code']),
            'originalNumericComparison': copy.deepcopy(row['nutrients']),
            'originalOptions': copy.deepcopy(row['options']), 'originalChanges': copy.deepcopy(row['changes']),
            'originalHolds': row['holds'][:], 'fieldReviews': findings,
            'releaseHolds': sorted(set(row['holds']) | {'FRESH_CURRENT_V2_DEPENDENCY_REBASE_REQUIRED',
                'TYPED_FIELD_LEDGER_AND_EXPLICIT_NUTRITION_REVIEW_REQUIRED', 'CONSOLIDATED_RELEASE_APPROVAL_REQUIRED'}),
            'status': 'REVIEW_ONLY_ARCHIVED_BASELINE', 'nutritionChanges': {},
            'legacyUnitAttested': False, 'nutritionWriteAllowed': False, 'applyAllowed': False,
            'liveApplyApproved': False, 'currentDatabaseMeasured': False, 'databaseWrites': 0}


def run(args):
    if args.output.exists():
        raise ValueError('New output directory required')
    if sha(args.review) != PINNED_REVIEW:
        raise ValueError('Pinned F2 review changed')
    manifest = followup.read(args.review)
    hashes = {str(args.review): sha(args.review)}
    for filename, expected in manifest['summary']['inputHashes'].items():
        if sha(Path(filename)) != expected:
            raise ValueError('Original review/source ancestry changed: ' + filename)
        hashes[filename] = expected
    rows = [r for r in manifest['rows'] if r['workQueue'] == QUEUE]
    if len(rows) != 22 or len({r['id'] for r in rows}) != 22 or len({r['sourceKey'] for r in rows}) != 22:
        raise ValueError('Exact22 exclusive selection required')
    now, sources = datetime.now(timezone.utc), {}
    for folder in args.usda_folder:
        for path in sorted(folder.glob('batch-*.json')):
            if path.name.endswith('.receipt.json'): continue
            receipt = followup.read(path.with_suffix('.receipt.json'))
            followup.fdc.cached_batch_receipt(path, receipt['ids'], now)
            for body in followup.fdc.validate_batch(receipt['ids'], followup.read(path)):
                key = 'USDA_FOODDATA:fdc:' + str(body['fdcId'])
                if key in sources: raise ValueError('Duplicate complete source identity')
                sources[key] = body, receipt['fetchedAt']
            hashes[str(path)] = sha(path); hashes[str(path.with_suffix('.receipt.json'))] = sha(path.with_suffix('.receipt.json'))
    for row in rows:
        if row['before']['food']['data_source'] != 'OPEN_FOOD_FACTS':
            continue
        code = row['sourceKey'].removeprefix('barcode:')
        path = args.off_folder / ('barcode-' + code + '.json')
        receipt = followup.read(path)
        if not followup.fetch.cached_valid(receipt, code, now): raise ValueError('OFF complete receipt/response binding changed')
        fresh(receipt['checkedAt'], now)
        sources[row['sourceKey']] = receipt['product'], receipt['checkedAt']; hashes[str(path)] = sha(path)
    results = [review_row(row, *sources[row['sourceKey']], now) for row in rows]
    fields = [f for row in results for f in row['fieldReviews']]
    report = {'version': VERSION, 'reviewedAt': now.isoformat(), 'products': len(results),
              'scope': 'EXACT22_F2_DIFFERENCE_QUEUE_NOT_CURRENT_AWS',
              'fieldFindings': len(fields), 'sourceFactsPrepared': sum(f['sourceFactPrepared'] for f in fields),
              'fieldQueues': dict(Counter(f['reviewQueue'] for f in fields)),
              'unselectedF2RowsUntouched': len(manifest['rows']) - len(results),
              'originalFullComparisonsRetained': len(results) * len(gate.FIELDS),
              'currentDatabaseMeasured': False, 'legacyUnitsCertified': 0, 'nutritionWrites': 0,
              'databaseWrites': 0, 'awsCalls': 0, 'sourceRequests': 0, 'releaseReadyProducts': 0,
              'globalR5Completed': False, 'applyAllowed': False, 'inputHashes': hashes}
    args.output.mkdir(parents=True, exist_ok=False)
    path = args.output / 'typed-difference-review.json'
    path.write_text(json.dumps({'summary': report, 'rows': results}, ensure_ascii=False, indent=2, allow_nan=False), encoding='utf-8')
    report['reviewSha256'] = sha(path)
    (args.output / 'summary.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps({k: v for k, v in report.items() if k != 'inputHashes'}, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--review', type=Path, default=Path('outputs/r5-nutrient-holds-20261015/review-final/source-review.json'))
    parser.add_argument('--usda-folder', type=Path, action='append', required=True)
    parser.add_argument('--off-folder', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    run(parser.parse_args())

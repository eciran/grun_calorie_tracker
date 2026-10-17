"""Prepare unapproved v2 previews from immutable F8 facts. No DB/HTTP/apply client."""
import argparse
import copy
import hashlib
import importlib.util
import json
from datetime import datetime, timezone
from pathlib import Path

spec = importlib.util.spec_from_file_location('r5_new_fields_review', Path(__file__).with_name('review-r5-nutrient-differences.py'))
review = importlib.util.module_from_spec(spec)
spec.loader.exec_module(review)
PINNED_REVIEW = 'cbb1289326246f1e08d1978b35e63d9be67fab480188b6ab64ba6bfa8106c820'
CONTRACT = 'typed-source-observation-r5-v2'
SCOPE = 'PRODUCT_PARENT_LEGACY_AND_FK_DEPENDENCIES_V2'


def pack(row, body, stamp, now):
    original = copy.deepcopy(row)
    review.fresh(stamp, now)
    if (row.get('status') != 'REVIEW_ONLY_ARCHIVED_BASELINE'
            or row.get('sourceCheckedAt') != stamp or row.get('sourceFingerprint') != review.fingerprint(body)
            or row.get('beforeFingerprint') != review.fingerprint(row['before'])
            or any(row.get(flag) is not False for flag in ('nutritionWriteAllowed', 'legacyUnitAttested',
                                                          'applyAllowed', 'liveApplyApproved', 'currentDatabaseMeasured'))
            or row.get('nutritionChanges') != {}):
        raise ValueError('Immutable unapproved F8 row/source required')
    key = ('USDA_FOODDATA:fdc:' + str(body.get('fdcId'))) if row['provider'] == 'USDA_FOODDATA' else 'barcode:' + str(body.get('code'))
    if key != row['sourceKey'] or row['before']['food']['source_key'] != key:
        raise ValueError('Source identity drift')
    source_json = json.dumps(body, ensure_ascii=False, sort_keys=True, separators=(',', ':'), allow_nan=False)
    if len(source_json) > 1000000:
        raise ValueError('Bounded source JSON required')
    proposals, missing = [], []
    for finding in row['fieldReviews']:
        field = finding['field']
        fact = review.usda_fact(field, body) if row['provider'] == 'USDA_FOODDATA' else review.off_fact(field, body)
        if fact != finding['sourceFact'] or finding['sourceFactPrepared'] != (fact is not None):
            raise ValueError('Typed source fact drift')
        if fact is None:
            missing.append({'productId': int(row['id']), 'field': field,
                            'reason': 'MISSING_SOURCE_OPTIONAL_VALUE_RETAIN_CURRENT', 'previewRequest': None,
                            'nutritionWriteAllowed': False, 'applyAllowed': False})
            continue
        proposals.append({
            'productId': int(row['id']), 'sourceKey': key, 'sourceFact': copy.deepcopy(fact),
            'archivedBeforeFingerprintNotCurrent': row['beforeFingerprint'],
            'previewPath': f"/api/v1/admin/products/{int(row['id'])}/typed-nutrient-evidence/source-observations/preview",
            'previewRequest': {'contractVersion': CONTRACT, 'provider': row['provider'], 'field': field,
                               'sourceJson': source_json, 'sourceSha256': hashlib.sha256(source_json.encode('utf-8')).hexdigest(),
                               'observedAt': stamp, 'expectedCurrentFingerprint': None, 'approved': False, 'approvalReason': None},
            'originalHolds': copy.deepcopy(row['originalHolds']), 'releaseHolds': copy.deepcopy(row['releaseHolds']),
            'numericReviewRequired': True, 'requiredRuntimeSnapshotScope': SCOPE,
            'trustState': 'ADMIN_REVIEWED_NOT_PROVIDER_FETCH_VERIFIED',
            'legacyUnitAttested': False, 'nutritionWriteAllowed': False, 'applyAllowed': False,
            'requiresRuntimePreviewAndExplicitSourceOnlyApproval': True,
        })
    if row != original:
        raise ValueError('Historical row mutated')
    return proposals, missing


def run(args):
    if args.output.exists() or review.sha(args.review) != PINNED_REVIEW:
        raise ValueError('New output and pinned F8 review required')
    prior = review.followup.read(args.review)
    hashes = {str(args.review): review.sha(args.review)}
    for name, expected in prior['summary']['inputHashes'].items():
        if review.sha(Path(name)) != expected:
            raise ValueError('Historical source ancestry drift: ' + name)
        hashes[name] = expected
    sources = {}
    now = datetime.now(timezone.utc)
    for folder in args.usda_folder:
        for path in sorted(folder.glob('batch-*.json')):
            if path.name.endswith('.receipt.json'):
                continue
            receipt = review.followup.read(path.with_suffix('.receipt.json'))
            review.followup.fdc.cached_batch_receipt(path, receipt['ids'], now)
            for body in review.followup.fdc.validate_batch(receipt['ids'], review.followup.read(path)):
                key = 'USDA_FOODDATA:fdc:' + str(body['fdcId'])
                if key in sources:
                    raise ValueError('Duplicate source identity')
                sources[key] = body, receipt['fetchedAt']
            hashes[str(path)] = review.sha(path)
            hashes[str(path.with_suffix('.receipt.json'))] = review.sha(path.with_suffix('.receipt.json'))
    proposals, missing = [], []
    if len(prior['rows']) != 22 or len({r['id'] for r in prior['rows']}) != 22:
        raise ValueError('Exact22 F8 cohort required')
    for row in prior['rows']:
        if row['provider'] == 'USDA_FOODDATA':
            body, stamp = sources[row['sourceKey']]
        else:
            code = row['sourceKey'].removeprefix('barcode:')
            path = args.off_folder / ('barcode-' + code + '.json')
            receipt = review.followup.read(path)
            if not review.followup.fetch.cached_valid(receipt, code, now):
                raise ValueError('Exact OFF receipt required')
            body, stamp = receipt['product'], receipt['checkedAt']
            hashes[str(path)] = review.sha(path)
        prepared, held = pack(row, body, stamp, now)
        proposals.extend(prepared)
        missing.extend(held)
    if len(proposals) != 20 or len(missing) != 4:
        raise ValueError('Exact20 source facts/four missing values required')
    resource = Path('src/main/resources/catalog/typed-source-observation-r5-v2.json')
    hashes[str(resource)] = review.sha(resource)
    summary = {'version': 'R5_NEW_FIELD_SOURCE_PREVIEWS_V1', 'preparedAt': now.isoformat(),
               'sourceFactPreviews': 20, 'productsWithFacts': len({p['productId'] for p in proposals}),
               'missingSourceValuesHeld': 4, 'requestsApproved': 0, 'databaseWrites': 0, 'awsCalls': 0,
               'sourceRequests': 0, 'currentDatabaseMeasured': False, 'nutritionWriteAllowed': False,
               'applyAllowed': False, 'legacyUnitsCertified': 0, 'sourceReceiptTimesPreserved': True, 'inputHashes': hashes}
    args.output.mkdir(parents=True, exist_ok=False)
    output = args.output / 'unapproved-source-previews.json'
    output.write_text(json.dumps({'summary': summary, 'rows': proposals, 'missingSourceHolds': missing},
                                 ensure_ascii=False, indent=2, allow_nan=False), encoding='utf-8')
    summary['previewSha256'] = review.sha(output)
    (args.output / 'summary.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
    print(json.dumps({k: v for k, v in summary.items() if k != 'inputHashes'}, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--review', type=Path, default=Path('outputs/r5-nutrient-differences-20261015/review-accepted/typed-difference-review.json'))
    parser.add_argument('--usda-folder', type=Path, action='append', required=True)
    parser.add_argument('--off-folder', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    run(parser.parse_args())

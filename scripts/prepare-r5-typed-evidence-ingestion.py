"""Build unapproved preview requests, never a DB/AWS/apply client.

Original batch receipts remain immutable. An archived before fingerprint must
never be substituted for the runtime API's current parent/legacy fingerprint.
"""
import argparse
import copy
import hashlib
import importlib.util
import json
from datetime import datetime, timezone
from pathlib import Path

spec = importlib.util.spec_from_file_location('r5_ingestion_typed_review', Path(__file__).with_name('review-r5-typed-reattestation.py'))
typed = importlib.util.module_from_spec(spec)
spec.loader.exec_module(typed)
PINNED_REVIEW = 'c0de32170ce2ab3bcf4c3674c787da3a0b5b284d75136c822bb9ab8b30881d95'
RUNTIME_SCOPE = 'PRODUCT_PARENT_LEGACY_AND_FK_DEPENDENCIES_V2'


def pack_row(row, body, checked_at, now):
    if (row.get('status') != 'REVIEW_ONLY_ARCHIVED_BASELINE'
            or row.get('sourceReviewState') != 'DIRECT_RAE_SOURCE_REATTESTATION_PREPARED'
            or row.get('applyAllowed') is not False or row.get('liveApplyApproved') is not False
            or row.get('nutritionWriteAllowed') is not False or row.get('legacyUnitAttested') is not False
            or row.get('numericChanges') != {} or row.get('sourceReviewHolds')
            or row.get('sourceFingerprint') != typed.fingerprint(body)
            or row.get('beforeFingerprint') != typed.fingerprint(row['before'])
            or row.get('sourceKey') != 'USDA_FOODDATA:fdc:' + str(body.get('fdcId'))):
        raise ValueError('Exact immutable unapproved F5 row/source required')
    stamp = datetime.fromisoformat(checked_at.replace('Z', '+00:00'))
    if (stamp.tzinfo is None or not 0 <= (now - stamp).total_seconds() <= 86400
            or checked_at != row['sourceCheckedAt']):
        raise ValueError('Original source receipt expired, changed or unproven; never restamp')
    proposal, error = typed.source_rae(body, typed.EXACT_A)
    old = row['newSourceEvidenceProposal']
    if (error or any(old.get(k) != v for k, v in proposal.items())
            or old.get('sourceBodyFingerprint') != typed.fingerprint(body)
            or old.get('sourceCheckedAt') != checked_at or old.get('replacesLegacyEvidence') is not False):
        raise ValueError('Typed proposal/source identity drift')
    source_json = json.dumps(body, ensure_ascii=False, sort_keys=True, separators=(',', ':'), allow_nan=False)
    if len(source_json) > 1000000:
        raise ValueError('Source body exceeds bounded API contract')
    return {
        'productId': int(row['id']), 'sourceKey': row['sourceKey'],
        'previewPath': f"/api/v1/admin/products/{int(row['id'])}/typed-nutrient-evidence/preview",
        'archivedBeforeFingerprintNotCurrent': row['beforeFingerprint'],
        'legacyEvidenceFingerprint': row['legacyEvidenceFingerprint'],
        'existingHolds': copy.deepcopy(row['existingHolds']),
        'releaseHolds': copy.deepcopy(row['releaseHolds']),
        'previewRequest': {'field': 'vitamin_a', 'sourceJson': source_json,
                           'sourceSha256': hashlib.sha256(source_json.encode('utf-8')).hexdigest(),
                           'observedAt': checked_at, 'expectedCurrentFingerprint': None,
                           'approved': False, 'approvalReason': None},
        'trustState': 'ADMIN_REVIEWED_NOT_PROVIDER_FETCH_VERIFIED',
        'requiresRuntimePreviewAndExplicitApproval': True,
        'requiredRuntimeSnapshotScope': RUNTIME_SCOPE,
        'legacyUnitAttested': False, 'nutritionWriteAllowed': False, 'applyAllowed': False,
    }


def run(args):
    if typed.sha(args.review) != PINNED_REVIEW:
        raise ValueError('Pinned F5 review changed')
    prior = json.loads(args.review.read_text(encoding='utf-8'))
    hashes = {str(args.review): typed.sha(args.review)}
    for filename, expected in prior['summary']['inputHashes'].items():
        path = Path(filename)
        if typed.sha(path) != expected:
            raise ValueError('Prior source/contract input drift: ' + filename)
        hashes[str(path)] = expected
    now = datetime.now(timezone.utc)
    sources = {}
    for folder in args.usda_folder:
        for path in sorted(folder.glob('batch-*.json')):
            if path.name.endswith('.receipt.json'):
                continue
            fdc = typed.names.prereq.followup.fdc
            receipt_path = path.with_suffix('.receipt.json')
            receipt = json.loads(receipt_path.read_text(encoding='utf-8'))
            checked = fdc.cached_batch_receipt(path, receipt['ids'], now)
            if checked != receipt:
                raise ValueError('Original receipt binding drift')
            for food in fdc.validate_batch(receipt['ids'], json.loads(path.read_text(encoding='utf-8'))):
                key = str(food['fdcId'])
                if key in sources:
                    raise ValueError('Duplicate complete source identity')
                sources[key] = food, receipt['fetchedAt']
            hashes[str(path)] = typed.sha(path)
            hashes[str(receipt_path)] = typed.sha(receipt_path)
    if len(prior['rows']) != 87 or len({r['id'] for r in prior['rows']}) != 87:
        raise ValueError('Exact87 membership required')
    rows = []
    for row in prior['rows']:
        food, checked_at = sources[row['sourceKey'].split(':')[-1]]
        rows.append(pack_row(row, food, checked_at, now))
    report = {'version': 'R5_TYPED_SOURCE_INGESTION_PREVIEW_V2', 'preparedAt': now.isoformat(),
              'products': len(rows), 'requestsApproved': 0, 'databaseWrites': 0,
              'currentDatabaseMeasured': False, 'awsCalls': 0, 'applyAllowed': False,
              'nutritionWriteAllowed': False, 'legacyUnitsCertified': 0,
              'sourceReceiptTimesPreserved': True, 'inputHashes': hashes,
              'runtimeScope': RUNTIME_SCOPE, 'currentDeclaredDependenciesMeasured': False,
              'globalAliasCollisionOrReferenceRewriteApproved': False}
    args.output.mkdir(parents=True, exist_ok=False)
    output = args.output / 'unapproved-preview-requests.json'
    output.write_text(json.dumps({'summary': report, 'rows': rows}, ensure_ascii=False, indent=2, allow_nan=False), encoding='utf-8')
    report['previewRequestsSha256'] = typed.sha(output)
    (args.output / 'summary.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps({k: v for k, v in report.items() if k != 'inputHashes'}, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--review', type=Path, default=Path('outputs/r5-typed-reattestation-20261015/review-final/typed-reattestation-review.json'))
    parser.add_argument('--usda-folder', type=Path, action='append', required=True)
    parser.add_argument('--output', type=Path, required=True)
    run(parser.parse_args())

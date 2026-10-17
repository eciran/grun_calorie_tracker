"""Link held R5 rows to exact R3/R4 decisions, without approving any writes.

Metadata is projected into a private copy solely to re-run the existing R5 gate.
This is not a patch generator: historical snapshots never become current DB proof.
"""
import argparse
import copy
import json
import importlib.util
from collections import Counter
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from pathlib import Path

def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


followup = module('r5_prerequisite_followup', 'review-r5-source-followup.py')
gate = followup.gate
closure = gate.source.r5.closure
fingerprint = gate.source.r5.fingerprint
QUEUE = 'CLASSIFICATION_OR_PREPARATION_PREREQUISITE'
VERSION = 'R5_R3_R4_PREREQUISITE_LINK_V1'
PREREQUISITES = {'R3_R4_CLASSIFICATION_OR_PREPARATION_GATE',
                 'ADDITIVE_SOURCE_TYPE_OR_BASIS_REVIEW', 'SOURCE_PREPARATION_CONFLICT'}
R4_FIELDS = {'name', 'display_name', 'short_display_name', 'preparation_state'}
NUMERIC = {'calories', 'protein', 'fat', 'carbs'}
# The reviewed R4 source explicitly names a manufacturer, despite its SR subtype.
# Lack of a GTIN is not evidence that a manufacturer-specific profile is generic.
SOURCE_NAMED_BRANDS = {'USDA_FOODDATA:fdc:168876': "Uncle Ben's"}


def unique_index(rows, key):
    result = {}
    for row in rows:
        raw = row.get(key)
        value = str(raw)
        if raw is None or isinstance(raw, bool) or not value or value in result:
            raise ValueError('Missing/duplicate decision ' + key)
        result[value] = row
    return result


def same_snapshot_value(key, left, right):
    if left in ('', None) and right in ('', None):
        return True
    if key in NUMERIC:
        try:
            a, b = Decimal(str(left)), Decimal(str(right))
            return a.is_finite() and b.is_finite() and a == b
        except (InvalidOperation, ValueError):
            return False
    return str(left) == str(right)


def link(row, r3, r4, body, now):
    original = copy.deepcopy(row)
    blockers, changes = [], {}
    current = row['before']
    food = current['food']
    key, identity = row['sourceKey'], str(row['id'])
    result = {'id': identity, 'sourceKey': key, 'before': copy.deepcopy(current),
              'beforeFingerprint': fingerprint(current), 'sourceFingerprint': fingerprint(body),
              'sourceCheckedAt': row['sourceCheckedAt'], 'originalHolds': row['holds'][:],
              'applyAllowed': False, 'liveApplyApproved': False, 'nutritionWriteAllowed': False,
              'databaseWrites': 0, 'currentDatabaseBeforeImageRequired': True,
              'status': 'REVIEW_ONLY_ARCHIVED_BASELINE', 'metadataChanges': {}, 'localizationPlan': {},
              'undoMetadataValues': {}, 'undoMetadataFieldPresence': {}, 'prerequisiteBlockers': blockers,
              'freshBeforeImagesRequired': ['food', 'options', 'option_localizations', 'references',
                                           'localizations', 'search_aliases', 'canonical_references'],
              'aliasPolicy': 'No alias insertion/deactivation without exact current alias before-images and approval.'}
    if row.get('workQueue') != QUEUE or row.get('disposition') != 'HELD':
        blockers.append('NOT_CURRENT_SELECTED_HELD_QUEUE')
    if row.get('applyAllowed') is not False or row.get('liveApplyApproved') is not False:
        blockers.append('UNEXPECTED_INPUT_APPROVAL')
    if (str(current['id']) != identity or str(food['id']) != identity or food.get('source_key') != key
            or row['beforeFingerprint'] != fingerprint(current)):
        blockers.append('R5_BEFORE_IMAGE_OR_IDENTITY_CONFLICT')
    if row['sourceFingerprint'] != fingerprint(body):
        blockers.append('R5_SOURCE_VERSION_CHANGED')
    try:
        age = (now - datetime.fromisoformat(row['sourceCheckedAt'].replace('Z', '+00:00'))).total_seconds()
        if not 0 <= age <= 86400:
            blockers.append('SOURCE_FRESHNESS_RECHECK_REQUIRED')
    except (ValueError, TypeError, AttributeError):
        blockers.append('SOURCE_FRESHNESS_UNPROVEN')
    if r3 is None:
        blockers.append('EXACT_R3_DECISION_REQUIRED')
    else:
        result['r3DecisionFingerprint'] = fingerprint(r3)
        if (str(r3['id']) != identity or r3['sourceKey'] != key or r3['before'].get('source_key') != key
                or r3['beforeFingerprint'] != fingerprint(r3['before'])):
            blockers.append('R3_IDENTITY_OR_BEFORE_PROOF_CONFLICT')
        if (r3['sourceFingerprint'] != fingerprint(body) or r3['sourceDescription'] != body.get('description')
                or r3['sourceDataType'] != body.get('dataType')
                or str(r3['sourceFdcId']) != str(body.get('fdcId'))):
            blockers.append('R3_SOURCE_VERSION_CHANGED')
        drift = [k for k, v in r3['before'].items()
                 if not same_snapshot_value(k, v, food.get(k))]
        result['historicalSnapshotDriftFields'] = sorted(drift)
        if drift:
            blockers.append('R3_R5_HISTORICAL_METADATA_DRIFT')
        if (r3['classificationDecision'] != 'RECLASSIFY_TO_GENERIC'
                or r3['proposedType'] != 'GENERIC_INGREDIENT'
                or r3['mergeDecision'] != 'KEEP_SEPARATE_SOURCE_PROFILE' or r3['sameSourceTargets']
                or not {'catalog_type', 'source_data_type'} <= set(r3['allowedFutureFields'])):
            blockers.append('R3_CLASSIFICATION_OR_REFERENCE_REVIEW')
        if r3['findings'] and r4 is None:
            blockers.append('UNRESOLVED_R3_FINDINGS_REQUIRE_R4')
    if (food.get('data_source') != 'USDA_FOODDATA' or food.get('catalog_type') != 'BRANDED_PRODUCT'
            or body.get('dataType') not in {'Foundation', 'SR Legacy'}
            or closure.review.fdc_id(key) != str(body.get('fdcId'))
            or food.get('barcode') or food.get('normalized_barcode') or body.get('gtinUpc')
            or food.get('brand') or food.get('brand_id')
            or food.get('is_custom') is not False or food.get('publication_status') != 'PUBLISHED'
            or food.get('nutrition_basis') not in {None, 'SOURCE_REPORTED'}
            or food.get('nutrition_reference_unit') != 'PER_100G'):
        blockers.append('EXACT_UNBRANDED_MASS_SOURCE_REQUIRED')
    if key in SOURCE_NAMED_BRANDS or body.get('brandOwner') or body.get('brandName'):
        blockers.append('SOURCE_NAMED_BRAND_CLASSIFICATION_REVIEW')
        result['sourceNamedBrand'] = SOURCE_NAMED_BRANDS.get(key) or body.get('brandOwner') or body.get('brandName')
    serving_review = False
    if r4 is not None:
        result['r4DecisionFingerprint'] = fingerprint(r4)
        if (r3 is None or str(r4['id']) != identity or r4['sourceKey'] != key
                or r4['beforeFingerprint'] != fingerprint(r4['before'])
                or r4['before'] != r3['before']):
            blockers.append('R4_BEFORE_OR_IDENTITY_CONFLICT')
        if (r4['sourceFingerprint'] != fingerprint(body) or r4['decision'] != 'SOURCE_NAMING_RESOLVED'
                or set(r4['changes']) != R4_FIELDS or r4['changes'].get('name') != body.get('description')
                or not r4.get('stateRationale') or not isinstance(r4.get('servingReviewRequired'), bool)
                or set(r4.get('localizations', {})) != {'EN', 'TR'}):
            blockers.append('EXACT_REVIEWED_R4_METADATA_REQUIRED')
        serving_review = r4['servingReviewRequired']
        changes.update(r4['changes'])
    else:
        states = closure.source_states(body.get('description', ''))
        explicit_coarse_cooked = (food.get('preparation_state') == 'COOKED'
                                 and 'cooked' in closure.review.match_name(body.get('description', '')).split())
        if len(states) != 1 or not (food.get('preparation_state') in states or explicit_coarse_cooked):
            blockers.append('EXPLICIT_SINGLE_PREPARATION_OR_R4_REQUIRED')
    changes['catalog_type'] = 'GENERIC_INGREDIENT'
    result['servingReviewRequiredByR4'] = serving_review
    result['classificationLink'] = 'BLOCKED' if blockers else 'R3_EXACT_SOURCE_LINKED'
    result['preparationLink'] = 'BLOCKED' if blockers else 'R4_EXPLICIT_DECISION_LINKED' if r4 else 'UNCHANGED_EXPLICIT_SOURCE_STATE'
    result['prerequisiteState'] = 'HELD' if blockers else 'LINKED_FOR_LOCAL_PROJECTION'
    if not blockers:
        virtual = copy.deepcopy(current)
        virtual['food'].update(changes)
        decision = gate.source.usda_decision({**virtual['food'], 'id': identity}, body, set())
        after = gate.review(decision, virtual, body, row['sourceCheckedAt'], now, approve_additive_usda=True)
        if decision['decision'] not in gate.READY:
            after['holds'].append('SOURCE_DECISION_NOT_READY')
        # A naming decision is not a serving or existing-admin-approval decision.
        if serving_review:
            after['holds'].append('R4_SOURCE_SERVING_REVIEW_REQUIRED')
        if 'EXISTING_ADMIN_APPROVAL_REVIEW_REQUIRED' in row['holds']:
            after['holds'].append('EXISTING_ADMIN_APPROVAL_REVIEW_REQUIRED')
        after['holds'].append('CURRENT_METADATA_ALIAS_AND_ADMIN_APPROVAL_REQUIRED')
        after['holds'] = sorted(set(after['holds']))
        if any(virtual['food'].get(f) != food.get(f) for f in gate.FIELDS):
            raise ValueError('Forbidden nutrition projection')
        for field in current.keys() - {'food'}:
            if virtual[field] != current[field]:
                raise ValueError('Forbidden child/reference projection')
        if after['nutrients'] != row['nutrients'] or after.get('nutrientVariants') != row.get('nutrientVariants'):
            raise ValueError('Nutrient review changed while projecting metadata')
        result.update(metadataChanges={k: v for k, v in changes.items() if food.get(k) != v},
                      undoMetadataValues={k: food.get(k) for k, v in changes.items() if food.get(k) != v},
                      undoMetadataFieldPresence={k: k in food for k, v in changes.items() if food.get(k) != v},
                      localizationPlan=copy.deepcopy(r4['localizations']) if r4 else {},
                      projectedSourceDecision=decision['decision'], projectedHolds=after['holds'],
                      resolvedPrerequisiteHolds=sorted(set(row['holds']) & PREREQUISITES - set(after['holds'])),
                      projectedOptions=after['options'], nutrientComparisonUnchanged=True,
                      portionsAndReferencesUnchanged=True,
                      projectedWorkQueue=followup.work_queue({**after, 'disposition': 'HELD'}))
        if 'CURRENT_NAME_SOURCE_IDENTITY_REVIEW' not in after['holds'] and not r4:
            result['sourceNameAlreadyExact'] = True
    else:
        result.update(projectedHolds=row['holds'][:], projectedOptions=[], resolvedPrerequisiteHolds=[],
                      projectedWorkQueue=QUEUE, nutrientComparisonUnchanged=True,
                      portionsAndReferencesUnchanged=True)
    if row != original:
        raise ValueError('Input review mutated')
    return result


def run(args):
    if (isinstance(args.expected_records, bool) or not isinstance(args.expected_records, int)
            or args.expected_records <= 0 or args.output.exists()):
        raise ValueError('Positive exact-count gate and a new output directory are required')
    now, hashes = datetime.now(timezone.utc), {}

    def pin(path):
        path = Path(path)
        hashes[str(path)] = closure.sha(path)
        return followup.read(path)

    def verified(path, digest_key):
        data, summary = pin(path), pin(path.parent / 'summary.json')
        if summary[digest_key] != hashes[str(path)]:
            raise ValueError('Input manifest digest mismatch')
        return data

    review = verified(args.review, 'reviewSha256')
    r3 = verified(args.r3_manifest, 'manifestSha256')
    r4 = verified(args.r4_manifest, 'manifestSha256')
    registry = pin(args.registry)
    if (review['summary']['scope'] != 'HISTORICAL_HELD_SELECTION_ONLY'
            or review['summary']['currentDatabaseMeasured'] is not False
            or review['summary']['liveApplyAllowed'] is not False
            or review['summary']['gateRuleset'] != gate.RULESET
            or r3['ruleset'] != 'R3_EXACT_SOURCE_COMPATIBILITY_V1' or r4['ruleset'] != registry['version']
            or registry['version'] != 'catalog-naming-r4-v1'
            or r4['inputHashes']['r3_manifest'] != hashes[str(args.r3_manifest)]
            or r4['inputHashes']['registry'] != hashes[str(args.registry)]
            or any(r3['inputHashes'][k] != r4['inputHashes'][k] for k in ('catalog', 'proof', 'evidence'))):
        raise ValueError('R3/R4 provenance chain mismatch')
    r3_by_key, r4_by_key = unique_index(r3['decisions'], 'sourceKey'), unique_index(r4['decisions'], 'sourceKey')
    unique_index(r3['decisions'], 'id')
    unique_index(r4['decisions'], 'id')
    registry_by_key = unique_index(registry['entries'], 'sourceKey')
    if set(registry_by_key) != set(r4_by_key):
        raise ValueError('Incomplete R4 registry coverage')
    for key, decision in r4_by_key.items():
        entry = registry_by_key[key]
        expected_changes = {'name': entry['sourceDescription'], 'display_name': entry['displayNameEn'],
                            'short_display_name': entry['displayNameEn'], 'preparation_state': entry['targetState']}
        expected_localizations = {lang: {'displayName': entry[field], 'shortDisplayName': entry[field]}
                                  for lang, field in (('EN', 'displayNameEn'), ('TR', 'displayNameTr'))}
        if (decision['changes'] != expected_changes or decision['localizations'] != expected_localizations
                or decision['servingReviewRequired'] != entry['servingReviewRequired']
                or decision['stateRationale'] != entry['stateRationale']):
            raise ValueError('R4 reviewed registry drift')
    unique_index(review['rows'], 'id')
    unique_index(review['rows'], 'sourceKey')
    selected = [r for r in review['rows'] if r.get('workQueue') == QUEUE]
    if len(selected) != args.expected_records:
        raise ValueError('Exact prerequisite queue count mismatch')
    expected_keys = {r['sourceKey'] for r in review['rows'] if r['sourceKey'].startswith('USDA_FOODDATA:')}
    sources = {}
    for folder in args.usda_folder:
        for path in sorted(folder.glob('batch-*.json')):
            if path.name.endswith('.receipt.json'):
                continue
            receipt = pin(path.with_suffix('.receipt.json'))
            pin(path)
            for pinned_path in (path, path.with_suffix('.receipt.json')):
                if review['summary']['inputHashes'].get(str(pinned_path)) != hashes[str(pinned_path)]:
                    raise ValueError('Source/receipt differs from pinned R5 evidence')
            followup.fdc.cached_batch_receipt(path, receipt['ids'], now)
            for body in followup.fdc.validate_batch(receipt['ids'], followup.read(path)):
                key = 'USDA_FOODDATA:fdc:' + str(body['fdcId'])
                if key not in expected_keys or key in sources:
                    raise ValueError('Unexpected/duplicate fresh source identity')
                sources[key] = (body, receipt['fetchedAt'])
    result = []
    for row in selected:
        if row['sourceKey'] not in sources:
            raise ValueError('Missing exact complete source; no partial completion report')
        body, checked_at = sources[row['sourceKey']]
        if row['sourceCheckedAt'] != checked_at:
            raise ValueError('R5 source timestamp differs from original receipt')
        result.append(link(row, r3_by_key.get(row['sourceKey']), r4_by_key.get(row['sourceKey']), body, now))
    summary = {'version': VERSION, 'reviewedAt': now.isoformat(), 'selectedProducts': len(result),
               'prerequisiteStates': dict(Counter(r['prerequisiteState'] for r in result)),
               'preparationLinks': dict(Counter(r['preparationLink'] for r in result)),
               'r4ServingReviewsRequired': sum(r['servingReviewRequiredByR4'] for r in result),
               'linkedR4ServingReviewsRequired': sum(r['servingReviewRequiredByR4'] and
                                                     r['prerequisiteState'] == 'LINKED_FOR_LOCAL_PROJECTION' for r in result),
               'prerequisiteBlockers': dict(Counter(h for r in result for h in r['prerequisiteBlockers'])),
               'projectedWorkQueues': dict(Counter(r['projectedWorkQueue'] for r in result)),
               'projectedHolds': dict(Counter(h for r in result for h in r['projectedHolds'])),
               'resolvedPrerequisiteHoldCounts': dict(Counter(h for r in result for h in r['resolvedPrerequisiteHolds'])),
               'unselectedReviewRowsPreserved': len(review['rows']) - len(selected),
               'databaseWrites': 0, 'nutritionWrites': 0, 'liveApplyAllowed': False,
               'currentDatabaseMeasured': False, 'releaseReadyProducts': 0, 'globalR5Completed': False,
               'inputHashes': hashes,
               'scope': 'Local metadata projection against archived before-images; NOT a deploy/import/SQL package.'}
    args.output.mkdir(parents=True, exist_ok=False)
    path = args.output / 'prerequisite-review.json'
    path.write_text(json.dumps({'summary': summary, 'rows': result}, ensure_ascii=False, indent=2, allow_nan=False), encoding='utf-8')
    summary['reviewSha256'] = closure.sha(path)
    (args.output / 'summary.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
    print(json.dumps(summary, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    for name in ('review', 'r3-manifest', 'r4-manifest', 'output'):
        parser.add_argument('--' + name, required=True, type=Path)
    parser.add_argument('--registry', type=Path, default=Path('src/main/resources/catalog/source-name-decisions-r4-v1.json'))
    parser.add_argument('--usda-folder', action='append', type=Path, required=True)
    parser.add_argument('--expected-records', type=int, default=132)
    run(parser.parse_args())

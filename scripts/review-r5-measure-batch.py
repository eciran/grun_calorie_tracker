"""Verify an append-only, review-only measure delta against two frozen R5 cycles."""
import argparse
import copy
import importlib.util
import json
from collections import Counter, defaultdict
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path


spec = importlib.util.spec_from_file_location('r5_measure_batch_cycle', Path(__file__).with_name('audit-r5-comparison-cycle.py'))
cycle = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cycle)
qualified = cycle.qualified
PROHIBITIONS = {'applyAllowed': False, 'nutritionWriteAllowed': False, 'legacyUnitAttested': False,
                'runtimeApprovalFingerprint': None, 'liveApplyApproved': False}
FETA_MEASURED_BEFORE_SHA = 'fbfe6c0990187d5f7389d4083b2c7496123e3427a508dd183aafeb0b1face8a0'
FETA_MEASURED_OPTION_SHA = '0bc869091435f1e8a4d43cf7cef2f9fe6a534d68c78fed951b19c2f0223962d3'
FETA_MEASURED_PROPOSAL_SHA = '5c5c885df589ac3f32d70acc27d698cf8cc95bee8873d9fa8e8e5899a8f56137'
NEXT_EVIDENCE = {
    'REGULATORY_REFERENCE_NOT_HOUSEHOLD_PORTION': 'Keep reference measure separate; explicit product/admin policy before household display',
    'YIELD_AND_PREPARATION_QUALIFIERS_REQUIRE_REVIEW': 'Exact edible yield, purchased/raw versus final preparation and source qualifier labels',
    'EDIBLE_PART_AND_PROFILE_REQUIRE_REVIEW': 'Bone/skin/refuse/edible-part alignment with the full source profile',
    'VOLUME_OR_GEOMETRY_NOT_AN_ML_BRIDGE': 'Explicit volume standard and source pair; never infer ml or density from mass/name',
    'PACKAGE_OR_BLOCK_SIZE_NOT_UNIVERSAL': 'Exact source package/size/drained fraction base; no universal can/bottle/block size',
    'EXACT_SIZE_OR_DIMENSIONS_REQUIRE_LABEL_REVIEW': 'Complete unambiguous source dimensions and product-specific EN/TR wording',
    'QUALIFIED_MASS_DESCRIPTOR_REQUIRES_REVIEW': 'Complete mass descriptor plus its edible-part/shape/count qualifiers',
    'OTHER_EXACT_SHAPE_OR_COUNT_REQUIRES_REVIEW': 'Exact source noun/shape meaning and full product profile, not a guessed whole piece',
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def no_approval(value):
    require(all(key in value and value[key] == expected and type(value[key]) is type(expected)
                for key, expected in PROHIBITIONS.items()), 'Review must not carry approval/write permission')


def existing_choice(identity, parent, proposal, options):
    """Only the measured Feta pair; not a global SERVING/PORTION alias or live approval."""
    candidate = proposal['sourceProposal']
    matches = [o for o in options if o.get('id') == 3708]
    if (identity == '180991' and parent['sourceKey'] == 'USDA_FOODDATA:fdc:173420'
            and candidate.get('sourcePortionId') == 92490 and candidate.get('unitType') == 'SERVING'
            and candidate.get('sourcePortionFingerprint') == 'f3bb1a49cada6028706eac3feb8703d36d9451fb524c79e3ba3b3a647d5ed0ce'
            and parent['before']['food'].get('nutrition_reference_unit') == 'PER_100G' and len(matches) == 1
            and cycle.digest(parent['before']) == FETA_MEASURED_BEFORE_SHA
            and cycle.digest(matches[0]) == FETA_MEASURED_OPTION_SHA
            and cycle.digest(candidate) == FETA_MEASURED_PROPOSAL_SHA):
        old = matches[0]
        if (old.get('unit_type') == 'PORTION' and old.get('source') == 'ADMIN'
                and old.get('quality_status') == 'VERIFIED' and old.get('user_visible') is True
                and old.get('is_default') is True and old.get('label') == candidate['labels']['EN']
                and old.get('quantity') == candidate.get('quantity') == 1
                and old.get('gram_weight') == candidate.get('gramWeight') == 28.35
                and old.get('ml_volume') is None and candidate.get('mlVolume') is None):
            return {'decision': 'PREFER_EXISTING_APPROVED_OPTION_SKIP_NEW_DUPLICATE_NO_DATA_CHANGE',
                    'existingOptionId': 3708, 'selectionScope': 'MEASURED_SNAPSHOT_ONLY_NOT_RUNTIME_APPROVAL',
                    'existingOptionFingerprint': cycle.digest(old),
                    'parentAndDependenciesFingerprintNotApproval': cycle.digest(parent['before']),
                    'sourceProposalFingerprint': cycle.digest(candidate),
                    'changeExistingOption': False, 'appendNewOption': False, **PROHIBITIONS}
    return {'decision': 'RETAIN_CURRENT_REVIEW_TYPE_QUANTITY_MASS_VOLUME_AND_LABEL_NO_AUTO_APPEND', **PROHIBITIONS}


def load_cycle(folder):
    report = cycle.read(folder / 'cycle-report.json')
    checkpoint = cycle.read(folder / 'checkpoint.json')
    require(report.get('scope') == 'CURRENT_EXACT_262_AWS_READONLY' and report.get('completedStages') == 4,
            'Exact four-stage measured262 review required')
    require(report.get('globalR5Done') is False, 'Global closure is not granted')
    require(report['checkpointSha256'] == cycle.sha(folder / 'checkpoint.json'), 'Checkpoint hash changed')
    require(checkpoint['contextSha256'] == cycle.digest(report['context']), 'Context changed')
    no_approval(report)
    entries = checkpoint['stages']
    require(len(entries) == 4, 'All four ordered stages required')
    stages = []
    for number, entry in enumerate(entries, 1):
        require(entry['stage'] == number and entry['file'] == f'stage-{number}.json', 'Stage path/order changed')
        path = folder / entry['file']
        require(cycle.sha(path) == entry['sha256'], 'Stage hash changed')
        value = cycle.read(path)
        require(value['summary']['contextSha256'] == checkpoint['contextSha256'], 'Mixed stage context')
        require(len(value['rows']) == 262 and len({r['id'] for r in value['rows']}) == 262, 'Exact unique262 rows required')
        no_approval(value['summary'])
        stages.append(value)
    for path, expected in report['context']['inputHashes'].items():
        # Historical code hashes describe that earlier cycle, not permission to rewrite it.
        if not Path(path).as_posix().startswith('scripts/'):
            require(cycle.sha(Path(path)) == expected, 'Snapshot/source/contract input changed: ' + path)
    return stages, report


def compare(baseline, current):
    for index in (0, 1, 3):
        require(baseline[index]['rows'] == current[index]['rows'], 'Parent/source/unit/release evidence changed')
    parents = {r['id']: r for r in current[0]['rows']}
    before = {r['id']: r for r in baseline[2]['rows']}
    after = {r['id']: r for r in current[2]['rows']}
    require(before.keys() == after.keys() == parents.keys(), 'Selection changed')
    resolved, remaining, added_holds, conflicts = [], [], Counter(), []
    for identity, previous in before.items():
        reviewed, parent = after[identity], parents[identity]
        no_approval(previous)
        no_approval(reviewed)
        for field in ('oldOptionsRetained', 'optionLocalizationsRetained'):
            require(previous[field] == reviewed[field], 'Current portions/labels were changed')
        old_proposals = {cycle.digest(p['sourceProposal']): p for p in previous['sourceProposals']}
        new_proposals = {cycle.digest(p['sourceProposal']): p for p in reviewed['sourceProposals']}
        require(len(old_proposals) == len(previous['sourceProposals']) and len(new_proposals) == len(reviewed['sourceProposals']),
                'Duplicate candidate fingerprint requires separate review')
        require(old_proposals.keys() <= new_proposals.keys(), 'Existing source candidate changed/removed')
        for key, old in old_proposals.items():
            new = new_proposals[key]
            require(set(old['holds']) <= set(new['holds']), 'An existing hold was removed')
            for field in ('equivalentOptionIds', 'labelConflictOptionIds'):
                require(old[field] == new[field], 'Existing comparison changed without review')
            added_holds.update(set(new['holds']) - set(old['holds']))
        for proposal in reviewed['sourceProposals']:
            no_approval(proposal)
            require('CURRENT_RUNTIME_REBASE_AND_EXPLICIT_PORTION_APPROVAL_REQUIRED' in proposal['holds'], 'Missing runtime gate')
            ids = proposal.get('localizedLabelConflictOptionIds', [])
            if ids:
                choice = existing_choice(identity, parent, proposal, reviewed['oldOptionsRetained'])
                conflicts.append({'id': identity, 'sourceKey': parent['sourceKey'],
                    'sourceProposal': proposal['sourceProposal'], 'holds': proposal['holds'],
                    'currentOptionsKept': [o for o in reviewed['oldOptionsRetained'] if o['id'] in ids],
                    'choiceReview': choice, 'decision': choice['decision']})
        held = {u['sourcePortion']['id']: u for u in previous['unsupportedSourceMeasures']}
        still = {u['sourcePortion']['id']: u for u in reviewed['unsupportedSourceMeasures']}
        require(len(held) == len(previous['unsupportedSourceMeasures']) and len(still) == len(reviewed['unsupportedSourceMeasures']),
                'Duplicate held source measure')
        require(still.keys() <= held.keys() and all(held[k]['sourcePortion'] == v['sourcePortion'] for k, v in still.items()),
                'Pending source measure changed')
        consumed = set()
        for key in new_proposals.keys() - old_proposals.keys():
            proposal = new_proposals[key]
            option = proposal['sourceProposal']
            source_id = option.get('sourcePortionId')
            require(source_id in held and source_id not in still and source_id not in consumed, 'New candidate not from exact pending measure')
            source = held[source_id]['sourcePortion']
            require(option['sourcePortionFingerprint'] == cycle.digest(source), 'Changed source measure fingerprint')
            require(option['quantity'] == source['amount'] and option['gramWeight'] == source['gramWeight']
                    and option['mlVolume'] is None, 'No weight/count/volume conversion is allowed')
            require(option['defaultOption'] is False and option['userVisible'] is False and option['qualityStatus'] == 'NEEDS_REVIEW',
                    'New option must remain invisible and unapproved')
            require(option.get('sourceSpecificNotUniversalMeasure') is True, 'Source-specific measure flag required')
            if 'sourceContextFingerprint' in option:
                require(option['sourceContextFingerprint'] == parent['sourceFingerprint'], 'Source context fingerprint mismatch')
                family = 'EXACT_FULL_SOURCE_CONTEXT'
            elif qualified.dimension_labels(option['sourceDescriptor'].lower()):
                family = 'EXACT_SOURCE_DIMENSIONS'
            elif qualified.mass_labels(option['sourceDescriptor'].lower()):
                family = 'EXACT_SOURCE_MASS_DESCRIPTOR'
            else:
                family = 'EXACT_HOUSEHOLD_SHAPE_OR_COUNT'
            consumed.add(source_id)
            resolved.append({'id': identity, 'sourceKey': parent['sourceKey'],
                'currentName': parent['before']['food']['name'], 'family': family,
                'originalSourcePortion': copy.deepcopy(source), 'preparedReview': copy.deepcopy(proposal)})
        require(consumed == held.keys() - still.keys(), 'A pending measure disappeared without a candidate')
        for value in still.values():
            reason = qualified.pending_reason(value['sourcePortion'])
            reference = qualified.semantics.reference_disposition(value['sourcePortion'])
            remaining.append({'id': identity, 'sourceKey': parent['sourceKey'],
                'currentName': parent['before']['food']['name'], 'remainingReason': reason,
                'nextRequiredEvidence': NEXT_EVIDENCE[reason],
                'referenceOnlyPolicy': reference,
                'interpretationDisposition': 'REFERENCE_ONLY_RETAINED_NOT_USER_SUGGESTION' if reference else 'ADDITIONAL_SOURCE_EVIDENCE_REQUIRED',
                'originalSourcePortion': copy.deepcopy(value['sourcePortion']), 'originalHolds': parent['originalHolds'],
                **PROHIBITIONS})
    fractions = defaultdict(list)
    for row in remaining:
        portion = row['originalSourcePortion']
        descriptor = portion.get('modifier') or portion.get('portionDescription') or ''
        if descriptor == 'block':
            fractions[row['id'], descriptor].append(portion)
    size_reviews = []
    for (identity, descriptor), measures in fractions.items():
        implied = {Decimal(str(m['gramWeight'])) / Decimal(str(m['amount'])) for m in measures}
        if len(implied) > 1:
            size_reviews.append({'id': identity, 'sourceDescriptor': descriptor,
                'sourcePortionsPreserved': measures, 'wholeSizeMayBeDerived': False,
                'reason': 'MULTIPLE_SOURCE_SIZES_OR_FRACTION_BASE_REQUIRED_DO_NOT_DERIVE_WHOLE'})
    return {'resolved': sorted(resolved, key=lambda r: (int(r['id']), r['originalSourcePortion']['id'])),
            'remaining': remaining, 'localizedConflicts': conflicts, 'fractionBaseReviews': size_reviews,
            'summary': {'products': len(parents), 'nutrientComparisonsPreserved': len(parents)*21,
                'previousSourceCandidatesPreserved': sum(len(r['sourceProposals']) for r in before.values()),
                'currentOptionsPreserved': sum(len(r['oldOptionsRetained']) for r in before.values()),
                'currentOptionLabelsPreserved': sum(len(r['optionLocalizationsRetained']) for r in before.values()),
                'additionalMeasuresPrepared': len(resolved), 'additionalMeasureProducts': len({r['id'] for r in resolved}),
                'newMeasuresByFamily': dict(Counter(r['family'] for r in resolved)),
                'remainingMeasures': len(remaining), 'remainingByReason': dict(Counter(r['remainingReason'] for r in remaining)),
                'referenceOnlyMeasuresRetained': sum(r['referenceOnlyPolicy'] is not None for r in remaining),
                'unresolvedInterpretationMeasures': sum(r['referenceOnlyPolicy'] is None for r in remaining),
                'newSourceInterpretationsByFamily': dict(Counter(r['preparedReview']['sourceProposal'].get('sourceInterpretationFamily', 'OTHER') for r in resolved)),
                'additionalHoldEventsOnOldCandidates': dict(added_holds),
                'localizedConflictCandidates': len(conflicts), 'fractionBaseReviewProducts': len(size_reviews),
                'globalR5Done': False, **PROHIBITIONS}}


def run(args):
    require(not args.output.exists(), 'Preserve previous report; choose a new output file')
    baseline, old_report = load_cycle(args.baseline)
    current, new_report = load_cycle(args.current)
    for path, expected in old_report['context']['inputHashes'].items():
        if not Path(path).as_posix().startswith('scripts/'):
            require(new_report['context']['inputHashes'].get(path) == expected, 'Source/snapshot ancestry changed')
    result = compare(baseline, current)
    result['summary'].update({'version': 'R5_MEASURE_BATCH_DELTA_V1', 'reviewedAt': datetime.now(timezone.utc).isoformat(),
        'scope': 'FROZEN_EXACT262_AWS_REPLAY_NOT_A_NEW_DB_READ', 'providerRequests': 0, 'databaseWrites': 0,
        'newAwsCalls': 0, 'deployments': 0, 'commitOrPush': False, 'sourceTimesPreserved': True})
    result['inputHashes'] = {str(folder / name): cycle.sha(folder / name) for folder in (args.baseline, args.current)
        for name in ('cycle-report.json', 'checkpoint.json', 'stage-1.json', 'stage-2.json', 'stage-3.json', 'stage-4.json')}
    result['codeHashes'] = {str(path): cycle.sha(path) for path in
        (Path(__file__), Path(__file__).with_name('r5-qualified-source-portions.py'), Path(__file__).with_name('audit-r5-comparison-cycle.py'))}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    cycle.save(args.output, result)
    print(json.dumps(result['summary'], indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--baseline', type=Path, required=True)
    parser.add_argument('--current', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    run(parser.parse_args())

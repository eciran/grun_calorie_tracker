"""Independent frozen R5 candidate/hold conservation checks. No network or apply path."""
import argparse
import importlib.util
import json
import unicodedata
from collections import Counter
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path

spec = importlib.util.spec_from_file_location('r5_portion_integrity_batch', Path(__file__).with_name('review-r5-measure-batch.py'))
batch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(batch)
cycle = batch.cycle
require = batch.require
PROHIBITIONS = batch.PROHIBITIONS
UNITS = {'PIECE', 'SLICE', 'CUP', 'TABLESPOON', 'TEASPOON', 'SERVING', 'PORTION'}


def positive(value):
    require(type(value) in (int, float), 'A positive numeric measure is required, not bool/string')
    number = Decimal(str(value))
    require(number.is_finite() and number > 0, 'Finite positive measure required')
    return number


def label_key(text, language):
    value = unicodedata.normalize('NFKC', text)
    if language == 'TR':
        value = value.replace('I', 'ı').replace('İ', 'i')
    return ' '.join(value.casefold().split())


def signature(option, existing=False):
    fields = ('unit_type', 'quantity', 'gram_weight', 'ml_volume') if existing else ('unitType', 'quantity', 'gramWeight', 'mlVolume')
    return tuple(option.get(key) for key in fields)


def validate_candidate(option):
    require(isinstance(option, dict) and option.get('unitType') in UNITS, 'Explicit supported option type required')
    positive(option.get('quantity'))
    require(option.get('gramWeight') is not None or option.get('mlVolume') is not None, 'A measured mass or volume is required')
    for key in ('gramWeight', 'mlVolume'):
        require(key in option, 'Explicit nullable dimensions required')
        if option[key] is not None:
            positive(option[key])
    require(cycle.qualified.valid_labels(option.get('labels')), 'Complete bounded control-free EN/TR labels required')
    measured = option['gramWeight'] if option['gramWeight'] is not None else option['mlVolume']
    suffix = ' g)' if option['gramWeight'] is not None else ' ml)'
    require(all(label.endswith('(' + cycle.qualified.number_label(measured) + suffix)
                for label in option['labels'].values()), 'Label must retain exact measured mass/volume')
    require(option.get('defaultOption') is False and option.get('userVisible') is False
            and option.get('qualityStatus') == 'NEEDS_REVIEW', 'Review candidates cannot be visible/default/approved')


def usda_coverage(body, proposals, held):
    measures = body.get('foodPortions')
    require(isinstance(measures, list), 'Complete source measures required')
    by_id = {}
    for measure in measures:
        require(isinstance(measure, dict) and type(measure.get('id')) is int and measure['id'] > 0,
                'Positive integer source measure identity required')
        require(measure['id'] not in by_id, 'Duplicate source measure identity')
        by_id[measure['id']] = measure
    direct, derived, held_ids = {}, [], set()
    fingerprints = set()
    for wrapper in proposals:
        batch.no_approval(wrapper)
        option = wrapper['sourceProposal']
        validate_candidate(option)
        fingerprint = cycle.digest(option)
        require(fingerprint not in fingerprints, 'Duplicate candidate payload')
        fingerprints.add(fingerprint)
        identity = option.get('sourcePortionId')
        require(type(identity) is int and identity in by_id, 'Candidate must refer to exact source measure')
        measure = by_id[identity]
        require(option.get('sourcePortionFingerprint') == cycle.digest(measure), 'Source measure changed')
        require(measure.get('footnote') in (None, ''), 'Unreviewed footnote must stay held')
        unit = measure.get('measureUnit', {})
        require(isinstance(unit, dict), 'Structured source unit required')
        raw = measure.get('modifier') or measure.get('portionDescription') or ''
        unit_name = (unit.get('name') or '').strip().lower()
        descriptor = raw.strip() if unit_name in ('', 'undetermined') else unit_name + (' ' + raw.strip() if raw else '')
        require(option.get('sourceDescriptor') == descriptor, 'Complete source descriptor required')
        if 'sourceContextFingerprint' in option:
            require(option['sourceContextFingerprint'] == cycle.digest(body)
                    and option.get('sourceContextFdcId') == body.get('fdcId')
                    and option.get('sourceContextDescription') == body.get('description'), 'Source context changed')
        if 'derivation' in option:
            derived.append(option)
            continue
        require(identity not in direct, 'Multiple direct candidates for one source measure')
        direct[identity] = option
        require(positive(option['quantity']) == positive(measure.get('amount'))
                and positive(option['gramWeight']) == positive(measure.get('gramWeight')), 'Source count or total mass changed')
        explicit_ml = positive(measure['amount']) if descriptor.lower() in ('ml', 'milliliter', 'milliliters') else None
        require((positive(option['mlVolume']) if option['mlVolume'] is not None else None) == explicit_ml,
                'Names, cups, fluid ounces and mass do not attest ml')
    for option in derived:
        derivation = option['derivation']
        require(isinstance(derivation, dict) and derivation.get('method') == 'EXACT_SOURCE_PIECE_FRACTION',
                'Only explicit half/quarter source-piece derivations are supported')
        fraction = positive(derivation.get('fraction'))
        require(fraction in (Decimal('.5'), Decimal('.25')), 'Unsupported source fraction')
        parent_id = derivation.get('parentPortionId')
        require(type(parent_id) is int and parent_id == option['sourcePortionId'] and parent_id in direct,
                'Derived option needs its exact retained direct parent')
        parent = direct[parent_id]
        require(parent['unitType'] == option['unitType'] == 'PIECE' and positive(parent['quantity']) == 1,
                'Only source-defined whole pieces may be fractioned')
        require(positive(option['quantity']) == fraction
                and positive(option['gramWeight']) == positive(parent['gramWeight']) * fraction, 'Derived count/mass changed')
        require(option['mlVolume'] is None and parent['mlVolume'] is None, 'No derived fluid bridge')
    require(len({(o['sourcePortionId'], o['derivation']['fraction']) for o in derived}) == len(derived),
            'Duplicate derived fraction')
    for pending in held:
        measure = pending.get('sourcePortion')
        require(isinstance(measure, dict) and type(measure.get('id')) is int and measure['id'] in by_id,
                'Every pending measure must retain source identity')
        identity = measure['id']
        require(identity not in held_ids and identity not in direct and cycle.digest(measure) == cycle.digest(by_id[identity]),
                'Held measure changed, duplicated or also emitted as a candidate')
        held_ids.add(identity)
    require(set(direct) | held_ids == set(by_id), 'A source measure disappeared from both candidate and hold queues')
    return {'sourceMeasures': len(by_id), 'directCandidates': len(direct), 'derivedCandidates': len(derived),
            'heldMeasures': len(held_ids)}


def collision_checks(row):
    wrappers = row['sourceProposals']
    proposals = [w['sourceProposal'] for w in wrappers]
    old = row['oldOptionsRetained']
    old_by_id = {o['id']: o for o in old}
    require(len(old_by_id) == len(old), 'Duplicate existing option ID')
    localized = [(o['id'], 'EN', o['label']) for o in old if isinstance(o.get('label'), str)]
    incomplete_locales = False
    for label in row['optionLocalizationsRetained']:
        if label.get('active') is True:
            if (label.get('serving_option_id') not in old_by_id or label.get('language') not in ('EN', 'TR')
                    or not isinstance(label.get('label'), str) or not label['label'].strip()):
                incomplete_locales = True
                continue
            localized.append((label['serving_option_id'], label['language'], label['label']))
    events = Counter()
    for index, (option, wrapper) in enumerate(zip(proposals, wrappers)):
        holds = set(wrapper['holds'])
        require('CURRENT_RUNTIME_REBASE_AND_EXPLICIT_PORTION_APPROVAL_REQUIRED' in holds, 'Missing runtime approval gate')
        if incomplete_locales:
            require('CURRENT_OPTION_LOCALIZATION_COVERAGE_INCOMPLETE' in holds, 'Incomplete active locale must remain held')
        for other_index, other in enumerate(proposals[index+1:], index+1):
            required = set()
            if signature(option) == signature(other):
                required.add('SOURCE_MEASURE_EQUIVALENCE_REVIEW_REQUIRED')
            if (option.get('sourceDescriptor') is not None and option.get('sourceDescriptor') == other.get('sourceDescriptor')
                    and option['unitType'] == other['unitType'] and option['quantity'] == other['quantity']
                    and (option['gramWeight'], option['mlVolume']) != (other['gramWeight'], other['mlVolume'])):
                required.add('SOURCE_DESCRIPTOR_WEIGHT_CONFLICT')
            if any(label_key(option['labels'][lang], lang) == label_key(other['labels'][lang], lang)
                   for lang in ('EN', 'TR')) and signature(option) != signature(other):
                required.add('SOURCE_LOCALIZED_LABEL_CONFLICT')
            other_wrapper = wrappers[other_index]
            require(required <= holds and required <= set(other_wrapper['holds']), 'Missing symmetric source collision hold')
            events.update(required)
        equivalent = sorted(o['id'] for o in old if signature(o, True) == signature(option))
        require(sorted(wrapper['equivalentOptionIds']) == equivalent, 'Existing equivalence IDs changed')
        if equivalent:
            require('EXISTING_EQUIVALENT_OPTION_REVIEW_REQUIRED' in holds, 'Existing equivalent cannot be silently duplicated')
            events['EXISTING_EQUIVALENT_OPTION_REVIEW_REQUIRED'] += 1
        conflict = sorted({identity for identity, lang, label in localized
                           if label_key(label, lang) == label_key(option['labels'][lang], lang)
                           and signature(old_by_id[identity], True) != signature(option)})
        require(wrapper.get('localizedLabelConflictOptionIds', []) == conflict, 'Localized current conflict IDs changed')
        if conflict:
            require('EXISTING_LOCALIZED_LABEL_CONFLICT' in holds, 'Current label conflict must stay held')
            events['EXISTING_LOCALIZED_LABEL_CONFLICT'] += 1
        if len(old) + len(proposals) > 20:
            require('SOURCE_OPTION_BUDGET_REVIEW_NO_TRUNCATION' in holds, 'Option budget must not truncate candidates')
    return dict(events)


def source_freshness(parent, body, stamp, now):
    require(parent['sourceFingerprint'] == cycle.digest(body), 'Source body changed; full re-review required')
    require(parent['sourceCheckedAt'] == stamp, 'Source check time changed; do not restamp a frozen decision')
    return {'sourceCheckedAt': stamp, 'freshAtAudit': cycle.fresh(stamp, now),
            'requiredAction': 'CURRENT_RUNTIME_REBASE_REQUIRED' if cycle.fresh(stamp, now) else 'SOURCE_REFRESH_REQUIRED',
            **PROHIBITIONS}


def load_sources(report):
    hashes = report['context']['inputHashes']
    sources = {}
    for name in hashes:
        path = Path(name)
        if path.name.startswith('batch-') and path.name.endswith('.receipt.json'):
            receipt = cycle.read(path)
            body_path = path.with_name(path.name.replace('.receipt.json', '.json'))
            require(str(body_path) in hashes and receipt['sha256'] == cycle.sha(body_path), 'Missing complete hashed source body')
            bodies = cycle.followup.fdc.validate_batch(receipt['ids'], cycle.read(body_path))
            entries = [('USDA_FOODDATA:fdc:' + str(b['fdcId']), b, receipt['fetchedAt']) for b in bodies]
        elif path.name.startswith('barcode-') and path.suffix == '.json':
            receipt = cycle.read(path)
            code = path.stem.removeprefix('barcode-')
            checked = datetime.fromisoformat(receipt['checkedAt'].replace('Z', '+00:00'))
            require(cycle.followup.fetch.cached_valid(receipt, code, checked), 'Complete OFF receipt required')
            entries = [('barcode:' + code, receipt['product'], receipt['checkedAt'])]
        else:
            continue
        for key, body, stamp in entries:
            require(key not in sources, 'Ambiguous repeated source identity')
            sources[key] = body, stamp
    return sources


def audit(stages, sources, now):
    parents = {r['id']: r for r in stages[0]['rows']}
    rows = {r['id']: r for r in stages[2]['rows']}
    require(parents.keys() == rows.keys(), 'Mixed product selection')
    for stage in stages:
        require(len(stage['rows']) == len(parents) and {r['id'] for r in stage['rows']} == parents.keys(), 'Cross-stage identity/count mismatch')
    metrics, collision_events, out = Counter(), Counter(), []
    for identity, parent in parents.items():
        row = rows[identity]
        batch.no_approval(row)
        require(parent['sourceBindingMatchesCurrent'] is True and parent['sourceKey'] in sources, 'Exact source identity required')
        body, stamp = sources[parent['sourceKey']]
        freshness = source_freshness(parent, body, stamp, now)
        require(row['oldOptionsRetained'] == parent['before']['options']
                and row['optionLocalizationsRetained'] == parent['before']['option_localizations'], 'Current options/locales changed')
        if parent['provider'] == 'USDA_FOODDATA':
            require(parent['sourceKey'] == 'USDA_FOODDATA:fdc:' + str(body['fdcId']), 'USDA source key changed')
            coverage = usda_coverage(body, row['sourceProposals'], row['unsupportedSourceMeasures'])
            metrics.update(coverage)
        else:
            require(parent['provider'] == 'OPEN_FOOD_FACTS' and parent['sourceKey'] == 'barcode:' + body['code'], 'OFF source key changed')
            source_time = datetime.fromisoformat(stamp.replace('Z', '+00:00'))
            decision = cycle.gate.source.off_decision({**parent['before']['food'], 'id': identity},
                {'product': body, 'barcode': body['code'], 'status': 'FETCHED', 'checkedAt': stamp}, source_time)
            require([w['sourceProposal'] for w in row['sourceProposals']] == decision.get('options', []),
                    'OFF serving is not the exact complete-body measured serving')
            for wrapper in row['sourceProposals']:
                batch.no_approval(wrapper)
                validate_candidate(wrapper['sourceProposal'])
            metrics['offMeasuredCandidates'] += len(row['sourceProposals'])
            coverage = {'offMeasuredCandidates': len(row['sourceProposals'])}
        collision_events.update(collision_checks(row))
        metrics['candidates'] += len(row['sourceProposals'])
        metrics['existingOptionsRetained'] += len(row['oldOptionsRetained'])
        metrics['existingOptionLabelsRetained'] += len(row['optionLocalizationsRetained'])
        metrics['freshSourcesAtAudit'] += freshness['freshAtAudit']
        choices = [batch.existing_choice(identity, parent, w, row['oldOptionsRetained'])
                   for w in row['sourceProposals'] if w.get('localizedLabelConflictOptionIds')]
        out.append({'id': identity, 'sourceKey': parent['sourceKey'], 'sourceFreshness': freshness,
                    'coverage': coverage, 'candidateSetFingerprint': cycle.digest(row['sourceProposals']),
                    'currentOptionsAndLocalesFingerprint': cycle.digest([row['oldOptionsRetained'], row['optionLocalizationsRetained']]),
                    'existingChoiceReviews': choices, **PROHIBITIONS})
    return {'summary': {'version': 'R5_COMPLETE_PORTION_INTEGRITY_V1', 'products': len(parents),
                **dict(metrics), 'collisionEvents': dict(collision_events), 'integrityChecksPassed': True,
                'scope': 'FROZEN_SOURCE_AND_SNAPSHOT_REPLAY_NOT_NEW_AWS_READ', 'auditTime': now.isoformat(),
                'releaseReadyProducts': 0, 'globalR5Done': False, 'databaseWrites': 0,
                'newAwsCalls': 0, 'providerRequests': 0, **PROHIBITIONS}, 'rows': out}


def run(args):
    require(not args.output.exists(), 'Preserve previous evidence; choose a new output')
    stages, report = batch.load_cycle(args.review)
    result = audit(stages, load_sources(report), datetime.now(timezone.utc))
    paths = [args.review / name for name in ('cycle-report.json', 'checkpoint.json', 'stage-1.json', 'stage-2.json', 'stage-3.json', 'stage-4.json')]
    result['inputHashes'] = {str(p): cycle.sha(p) for p in paths}
    result['codeHashes'] = {str(p): cycle.sha(p) for p in (Path(__file__), Path(__file__).with_name('review-r5-measure-batch.py'),
                                                        Path(__file__).with_name('r5-qualified-source-portions.py'))}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    cycle.save(args.output, result)
    print(json.dumps(result['summary'], indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--review', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    run(parser.parse_args())

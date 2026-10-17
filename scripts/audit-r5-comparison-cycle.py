"""Four bounded read/review stages. No provider/DB client, approval, patch or apply option.

A frozen AWS read is distinct from an archived projection and from a runtime
approval fingerprint. Every stage keeps that distinction and all previous holds.
"""
import argparse
import copy
import csv
import hashlib
import importlib.util
import json
import unicodedata
from collections import Counter
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path

spec = importlib.util.spec_from_file_location('r5_cycle_followup', Path(__file__).with_name('review-r5-source-followup.py'))
followup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(followup)
spec = importlib.util.spec_from_file_location('r5_cycle_qualified_portions', Path(__file__).with_name('r5-qualified-source-portions.py'))
qualified = importlib.util.module_from_spec(spec)
spec.loader.exec_module(qualified)
gate = followup.gate
VERSION = 'R5_FOUR_STAGE_COMPARISON_V1'
PIN = 'be835bf5832ecd9a8983357f74fe9e73ac13e8368ba55bd409348a415bfd1507'
FIELDS = tuple(gate.FIELDS)
COMMON = {'applyAllowed': False, 'nutritionWriteAllowed': False, 'legacyUnitAttested': False,
          'runtimeApprovalFingerprint': None, 'liveApplyApproved': False}
SPECS = {
    'protein': (1003, '203', 'g', 'G'), 'fat': (1004, '204', 'g', 'G'),
    'carbs': (1005, '205', 'g', 'G'), 'fiber': (1079, '291', 'g', 'G'),
    'sugar': (2000, '269', 'g', 'G'), 'sodium': (1093, '307', 'mg', 'MG'),
    'potassium': (1092, '306', 'mg', 'MG'), 'cholesterol': (1253, '601', 'mg', 'MG'),
    'calcium': (1087, '301', 'mg', 'MG'), 'iron': (1089, '303', 'mg', 'MG'),
    'magnesium': (1090, '304', 'mg', 'MG'), 'zinc': (1095, '309', 'mg', 'MG'),
    'vitamin_a': (1106, '320', 'ug', 'MCG_RAE'), 'vitamin_c': (1162, '401', 'mg', 'MG'),
    'vitamin_d': (1114, '328', 'ug', 'MCG'), 'vitamin_e': (1109, '323', 'mg', 'MG'),
    'vitamin_b12': (1178, '418', 'ug', 'MCG'), 'saturated_fat': (1258, '606', 'g', 'G'),
    'trans_fat': (1257, '605', 'g', 'G'),
}
CANONICAL = {k: v[3] for k, v in SPECS.items()} | {'calories': 'KCAL', 'sugar_alcohol': 'G'}
GRAMS = {'protein', 'fat', 'carbs', 'fiber', 'sugar', 'saturated_fat', 'trans_fat', 'sugar_alcohol'}
MICRO_MCG = {'vitamin_a', 'vitamin_d', 'vitamin_b12'}


def reject_duplicates(pairs):
    out = {}
    for key, value in pairs:
        if key in out: raise ValueError('Duplicate JSON key: ' + key)
        out[key] = value
    return out


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'), object_pairs_hook=reject_duplicates,
                      parse_constant=lambda v: (_ for _ in ()).throw(ValueError('Nonfinite JSON: ' + v)))


def digest(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()).hexdigest()


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def save(path, value):
    pending = path.with_suffix(path.suffix + '.pending')
    pending.write_text(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False), encoding='utf-8')
    pending.replace(path)


def fresh(stamp, now):
    try:
        parsed = datetime.fromisoformat(stamp.replace('Z', '+00:00'))
        return parsed.tzinfo is not None and 0 <= (now - parsed).total_seconds() <= 86400
    except (AttributeError, TypeError, ValueError): return False


def numeric(value):
    if isinstance(value, bool) or not isinstance(value, (int, float, Decimal)):
        raise ValueError('Numeric source value required, missing is not zero')
    number = Decimal(str(value))
    if not number.is_finite() or number < 0 or abs(number.adjusted()) > 100:
        raise ValueError('Bounded finite nonnegative amount required')
    return number


def source_fact(field, provider, body):
    if provider == 'USDA_FOODDATA':
        definitions = [(1008, '208', 'kcal', 'KCAL'), (2048, '958', 'kcal', 'KCAL'), (2047, '957', 'kcal', 'KCAL')] if field == 'calories' else [SPECS[field]] if field in SPECS else []
        for nid, number, unit, canonical in definitions:
            matches = [n for n in body.get('foodNutrients', []) if type(n.get('nutrient', {}).get('id')) is int and n['nutrient']['id'] == nid]
            if not matches: continue
            if len(matches) != 1: raise ValueError('Duplicate exact nutrient ID')
            row, meta = matches[0], matches[0]['nutrient']
            normalized = str(meta.get('unitName', '')).lower().replace('\u00b5', 'u').replace('\u03bc', 'u')
            if meta.get('number') != number or normalized != unit:
                raise ValueError('Exact source number/unit required')
            form = {'vitamin_a': 'Vitamin A, RAE', 'vitamin_e': 'Vitamin E (alpha-tocopherol)', 'vitamin_b12': 'Vitamin B-12'}.get(field)
            if form and meta.get('name') != form: raise ValueError('Exact source form required')
            amount = numeric(row.get('amount'))
            return {'provider': provider, 'sourceNutrientId': nid, 'sourceNutrientNumber': number,
                    'sourceName': meta.get('name'), 'sourceAmount': str(amount), 'sourceUnit': unit,
                    'canonicalAmount': str(amount), 'canonicalUnit': canonical, 'basis': 'PER_100G',
                    'sourceRecordFingerprint': digest(row), 'energyChoicePolicy': 'EXPLICIT_ID_PRIORITY_NOT_NEAREST_CURRENT' if field == 'calories' else None}
        return None
    if provider != 'OPEN_FOOD_FACTS': raise ValueError('Unsupported provider')
    if body.get('nutrition_data_per') not in ('100g', '100ml'):
        raise ValueError('Explicit native OFF reference required; serving/schema needs separate review')
    name = gate.FIELDS[field]
    n = body.get('nutriments', {})
    if name + '_100g' not in n: return None
    unit = 'kcal' if field == 'calories' else 'g'
    if str(n.get(name + '_unit', '')).lower() != unit:
        raise ValueError('Explicit native source unit required')
    if n.get(name + '_modifier') not in (None, ''): raise ValueError('Inequality is not an exact amount')
    amount = numeric(n[name + '_100g'])
    for key in (name, name + '_value'):
        if key in n and numeric(n[key]) != amount: raise ValueError('Contradictory OFF display value/unit')
    # Vitamin A source grams do not identify the retinol/RAE/IU definition.
    if field == 'vitamin_a': raise ValueError('OFF Vitamin A form requires separate source attestation')
    factor = Decimal(1) if field in GRAMS or field == 'calories' else Decimal(1000000 if field in MICRO_MCG else 1000)
    return {'provider': provider, 'sourceField': name + '_100g', 'sourceAmount': str(amount), 'sourceUnit': unit,
            'canonicalAmount': str(amount * factor), 'canonicalUnit': CANONICAL[field],
            'basis': 'PER_100ML' if body['nutrition_data_per'] == '100ml' else 'PER_100G',
            'sourceRecordFingerprint': digest({k: v for k, v in n.items() if k == name or k.startswith(name + '_')})}


def compare_row(original, current, source, measured, now):
    before = copy.deepcopy(current)
    food = current['food']
    body, checked_at = source
    if str(food.get('id')) != str(original['id']):
        raise ValueError('Current selected product ID changed')
    provider = food.get('data_source')
    binding = food.get('source_key') == original['sourceKey'] and provider == original['before']['food'].get('data_source')
    if not binding:
        # A later legitimate identity repair must not compare the new product to the OLD provider body.
        # Quarantine just this row so the rest of the bounded review can still finish.
        return {**COMMON, 'id': str(original['id']), 'sourceKey': original['sourceKey'], 'currentSourceKey': food.get('source_key'),
                'provider': provider, 'sourceBindingMatchesCurrent': False, 'originalWorkQueue': original['workQueue'],
                'originalHolds': sorted(set(original.get('holds', [])) | {'SOURCE_IDENTITY_CHANGED_OLD_EVIDENCE_QUARANTINED'}),
                'before': before, 'currentReviewFingerprintNotRuntimeApproval': digest(before),
                'currentDatabaseMeasured': measured, 'sourceCheckedAt': checked_at, 'sourceFresh': False,
                'archivedSourceReceiptFresh': fresh(checked_at, now), 'sourceFingerprint': digest(body),
                'sourceFacts': dict.fromkeys(FIELDS), 'sourceFactHolds': dict.fromkeys(FIELDS, 'SOURCE_IDENTITY_CHANGED'),
                'all21FieldsMeasured': all(f in food for f in FIELDS),
                'numericComparisons': {f: {'state': 'SOURCE_IDENTITY_CHANGED', 'current': food.get(f), 'source': None} for f in FIELDS},
                'archivedParentDrift': {f: {'archived': original['before']['food'].get(f), 'current': food.get(f)}
                                      for f in sorted(set(original['before']['food']) | set(food))
                                      if f not in {'usage_count', 'search_selection_count'} and original['before']['food'].get(f) != food.get(f)},
                'dependencies': current.get('dependencies'), 'sourceBodyChangedSinceF2': None}
    if provider == 'USDA_FOODDATA':
        if (original['sourceKey'] != 'USDA_FOODDATA:fdc:' + str(body.get('fdcId'))
                or body.get('dataType') not in ('Foundation', 'SR Legacy')): raise ValueError('Exact USDA identity required')
        values = gate.all_usda_fields(body)
    elif provider == 'OPEN_FOOD_FACTS':
        code = food.get('normalized_barcode') or food.get('barcode')
        if not gate.source.r5.valid_gtin(code) or original['sourceKey'] != 'barcode:' + code or body.get('code') != code:
            raise ValueError('Exact source GTIN required')
        values = gate.all_off_fields(body)
    else: raise ValueError('Unsupported selected provider')
    compared = gate.comparison(food, values, provider)
    drift = {key: {'archived': original['before']['food'].get(key), 'current': food.get(key)}
             for key in sorted(set(original['before']['food']) | set(food))
             if key not in {'usage_count', 'search_selection_count'} and original['before']['food'].get(key) != food.get(key)}
    facts, fact_holds = {}, {}
    for field in FIELDS:
        try: facts[field] = source_fact(field, provider, body)
        except (ValueError, TypeError, KeyError) as error:
            facts[field] = None; fact_holds[field] = str(error)
    if current != before: raise ValueError('Comparison mutated its input')
    return {**COMMON, 'id': str(original['id']), 'sourceKey': original['sourceKey'], 'provider': provider,
            'originalWorkQueue': original['workQueue'], 'originalHolds': copy.deepcopy(original.get('holds', [])),
            'sourceBindingMatchesCurrent': True, 'currentSourceKey': food.get('source_key'),
            'before': before, 'currentReviewFingerprintNotRuntimeApproval': digest(before),
            'currentDatabaseMeasured': measured, 'sourceCheckedAt': checked_at, 'sourceFresh': fresh(checked_at, now),
            'sourceFingerprint': digest(body), 'sourceFacts': facts, 'sourceFactHolds': fact_holds,
            'all21FieldsMeasured': all(f in food for f in FIELDS), 'numericComparisons': compared,
            'archivedParentDrift': drift, 'dependencies': current.get('dependencies'),
            'sourceBodyChangedSinceF2': original.get('sourceFingerprint') != gate.source.r5.fingerprint(body)}


def unit_review(row, body):
    findings = []
    variants = gate.typed_usda_variants(row['before']['food'], body) if row['provider'] == 'USDA_FOODDATA' and row['sourceBindingMatchesCurrent'] else {}
    for field, comparison in row['numericComparisons'].items():
        fact = row['sourceFacts'][field]
        raw = row['before']['food'].get(field)
        state = ('CURRENT_FIELD_UNMEASURED' if field not in row['before']['food'] else
                 'SOURCE_UNIT_OR_FORM_REVIEW' if field in row['sourceFactHolds'] else
                 'CURRENT_UNKNOWN_RETAINED' if raw is None else
                 'MISSING_SOURCE_RETAIN_CURRENT' if fact is None else 'SOURCE_FACT_READY_LEGACY_UNIT_NOT_ATTESTED')
        findings.append({'field': field, 'state': state, 'sourceFact': fact,
                         'legacyNumericComparison': comparison, 'currentScalarUnit': 'UNATTESTED_LEGACY_UNIT',
                         'typedAlternatives': variants.get(field), **COMMON})
    return {'id': row['id'], 'originalWorkQueue': row['originalWorkQueue'], 'fields': findings, **COMMON}


def portion_review(row, body):
    old = copy.deepcopy(row['before'].get('options', []))
    if not row['sourceBindingMatchesCurrent']:
        return {'id': row['id'], 'originalWorkQueue': row['originalWorkQueue'], 'oldOptionsRetained': old,
                'optionLocalizationsRetained': copy.deepcopy(row['before'].get('option_localizations', [])),
                'sourceProposals': [], 'unsupportedSourceMeasures': [{'reason': 'SOURCE_IDENTITY_CHANGED_OLD_EVIDENCE_QUARANTINED'}], **COMMON}
    proposals, unsupported = [], []
    if row['provider'] == 'USDA_FOODDATA':
        proposals, unsupported = gate.source.usda_portion_options(body)
        remaining = []
        for held in unsupported:
            candidate, reason = qualified.proposal(held['sourcePortion'], body)
            if candidate: proposals.append(candidate)
            else: remaining.append({**held, 'qualifiedReviewReason': reason})
        unsupported = remaining
    else:
        decision = gate.source.off_decision({**row['before']['food'], 'id': row['id']},
            {'product': body, 'barcode': body['code'], 'status': 'FETCHED', 'checkedAt': row['sourceCheckedAt']},
            datetime.fromisoformat(row['sourceCheckedAt'].replace('Z', '+00:00')))
        # Source clock above reconstructs source-only proposals; source freshness is independently retained below.
        proposals = decision.get('options', [])
        if decision['decision'] not in gate.READY: unsupported.append({'reason': decision['decision']})
    reviewed = []
    basis = row['before']['food'].get('nutrition_reference_unit')
    def signature(option, source=False):
        fields = ('unitType', 'quantity', 'gramWeight', 'mlVolume') if source else ('unit_type', 'quantity', 'gram_weight', 'ml_volume')
        return tuple(option.get(field) for field in fields)

    def label_key(label, language):
        value = unicodedata.normalize('NFKC', label)
        if language == 'TR':
            value = value.replace('I', 'ı').replace('İ', 'i')
        return ' '.join(value.casefold().split())

    old_by_id = {o['id']: o for o in old}
    localized = [(o['id'], 'EN', o['label']) for o in old if isinstance(o.get('label'), str)]
    incomplete_labels = False
    for label in row['before'].get('option_localizations', []):
        if label.get('active') is not True:
            continue
        if (label.get('serving_option_id') not in old_by_id or label.get('language') not in ('EN', 'TR')
                or not isinstance(label.get('label'), str) or not label['label'].strip()):
            incomplete_labels = True
            continue
        localized.append((label['serving_option_id'], label['language'], label['label']))
    for proposal in proposals:
        volume, mass = proposal.get('mlVolume'), proposal.get('gramWeight')
        holds = ['CURRENT_RUNTIME_REBASE_AND_EXPLICIT_PORTION_APPROVAL_REQUIRED']
        if not row['sourceFresh']: holds.append('SOURCE_REFRESH_REQUIRED')
        if volume is not None and basis != 'PER_100ML': holds.append('MASS_VOLUME_BRIDGE_OR_REFERENCE_REVIEW_REQUIRED')
        if volume is None and mass is not None and basis == 'PER_100ML': holds.append('MASS_VOLUME_BRIDGE_REQUIRED')
        equivalents = [o['id'] for o in old if (o.get('gram_weight'), o.get('ml_volume')) == (mass, volume)
                       and o.get('quantity') == proposal.get('quantity') and o.get('unit_type') == proposal.get('unitType')]
        if equivalents: holds.append('EXISTING_EQUIVALENT_OPTION_REVIEW_REQUIRED')
        label_conflicts = [o['id'] for o in old if o.get('label') == proposal['labels']['EN']
                           and (o.get('gram_weight'), o.get('ml_volume')) != (mass, volume)]
        if label_conflicts: holds.append('EXISTING_LABEL_WEIGHT_CONFLICT')
        localized_conflicts = sorted({identity for identity, language, label in localized
            if language in proposal['labels'] and label_key(label, language) == label_key(proposal['labels'][language], language)
            and signature(old_by_id[identity]) != signature(proposal, True)})
        if localized_conflicts: holds.append('EXISTING_LOCALIZED_LABEL_CONFLICT')
        if incomplete_labels: holds.append('CURRENT_OPTION_LOCALIZATION_COVERAGE_INCOMPLETE')
        source_conflicts = []
        for other in proposals:
            if other is proposal:
                continue
            conflict = False
            if signature(other, True) == signature(proposal, True):
                holds.append('SOURCE_MEASURE_EQUIVALENCE_REVIEW_REQUIRED')
                conflict = True
            same_descriptor = (proposal.get('sourceDescriptor') is not None
                and proposal.get('sourceDescriptor') == other.get('sourceDescriptor')
                and proposal['unitType'] == other['unitType'] and proposal['quantity'] == other['quantity'])
            if same_descriptor and (mass, volume) != (other.get('gramWeight'), other.get('mlVolume')):
                holds.append('SOURCE_DESCRIPTOR_WEIGHT_CONFLICT')
                conflict = True
            for language, label in proposal['labels'].items():
                if (language in other['labels'] and label_key(label, language) == label_key(other['labels'][language], language)
                        and signature(other, True) != signature(proposal, True)):
                    holds.append('SOURCE_LOCALIZED_LABEL_CONFLICT')
                    conflict = True
            if conflict and other.get('sourcePortionId') is not None:
                source_conflicts.append(other['sourcePortionId'])
        if len(old) + len(proposals) > 20:
            holds.append('SOURCE_OPTION_BUDGET_REVIEW_NO_TRUNCATION')
        reviewed.append({'sourceProposal': proposal, 'equivalentOptionIds': equivalents,
                         'labelConflictOptionIds': label_conflicts,
                         'localizedLabelConflictOptionIds': localized_conflicts,
                         'sourceMeasureConflictPortionIds': sorted(set(source_conflicts)),
                         'holds': list(dict.fromkeys(holds)), **COMMON})
    return {'id': row['id'], 'originalWorkQueue': row['originalWorkQueue'], 'oldOptionsRetained': old,
            'optionLocalizationsRetained': copy.deepcopy(row['before'].get('option_localizations', [])),
            'sourceProposals': reviewed, 'unsupportedSourceMeasures': unsupported, **COMMON}


def stage(number, comparisons, sources, previous=None):
    if number == 1:
        return {'rows': comparisons, 'summary': {'products': len(comparisons), 'fieldComparisons': len(comparisons) * 21,
                'currentDatabaseMeasured': all(r['currentDatabaseMeasured'] for r in comparisons),
                'parentDriftProducts': sum(bool(r['archivedParentDrift']) for r in comparisons),
                'sourceIdentityDriftQuarantined': sum(not r['sourceBindingMatchesCurrent'] for r in comparisons),
                'freshSourceProducts': sum(r['sourceFresh'] for r in comparisons),
                'factHoldFields': sum(len(r['sourceFactHolds']) for r in comparisons)}}
    if number == 2:
        rows = [unit_review(r, sources[r['sourceKey']][0]) for r in comparisons]
        return {'rows': rows, 'summary': {'products': len(rows), 'fieldDecisions': len(rows) * 21,
                'states': dict(Counter(f['state'] for r in rows for f in r['fields'])),
                'originalExclusiveUnitQueue': sum(r['originalWorkQueue'] == 'NUTRIENT_UNIT_FORM_OR_DEFINITION_REVIEW' for r in rows),
                'legacyUnitsCertified': 0}}
    if number == 3:
        rows = [portion_review(r, sources[r['sourceKey']][0]) for r in comparisons]
        return {'rows': rows, 'summary': {'products': len(rows), 'sourceOptionsReviewed': sum(len(r['sourceProposals']) for r in rows),
                'unsupportedSourceMeasures': sum(len(r['unsupportedSourceMeasures']) for r in rows),
                'oldOptionsRetained': sum(len(r['oldOptionsRetained']) for r in rows), 'newOptionsApproved': 0}}
    rows = []
    for row in comparisons:
        holds = set(row['originalHolds']) | {'EXPLICIT_UNIT_NUTRITION_PORTION_DECISIONS_REQUIRED', 'CONSOLIDATED_RELEASE_APPROVAL_REQUIRED', 'CURRENT_RUNTIME_V2_PREVIEW_REQUIRED'}
        if not row['currentDatabaseMeasured']: holds.add('FRESH_CURRENT_DB_REQUIRED')
        if not row['sourceFresh']: holds.add('SOURCE_REFRESH_REQUIRED')
        if not row['all21FieldsMeasured']: holds.add('CURRENT_NUTRIENT_FIELD_COVERAGE_INCOMPLETE')
        deps = row.get('dependencies') or {}
        if not deps.get('complete'): holds.add('CURRENT_DECLARED_FK_REBASE_INCOMPLETE')
        if row['currentDatabaseMeasured'] and deps.get('sourceEvidenceComplete') is not True:
            holds.add('CURRENT_SOURCE_EVIDENCE_COVERAGE_INCOMPLETE')
        rows.append({'id': row['id'], 'sourceKey': row['sourceKey'], 'holds': sorted(holds), **COMMON})
    return {'rows': rows, 'summary': {'products': len(rows), 'releaseReadyProducts': 0,
            'holds': dict(Counter(h for r in rows for h in r['holds'])), 'globalR5Done': False}}


def load_snapshot(path, proof_path, selected, now):
    proof = read(proof_path)
    if (proof.get('origin') != 'AWS_READ_ONLY_SNAPSHOT' or proof.get('transactionReadOnly') is not True
            or proof.get('sha256') != sha(path) or proof.get('scope') != 'r5-comparison'
            or proof.get('exitCode') != 0
            or proof.get('schemaVersion') not in {'307', '308', '309', '310', '311'}
            or not fresh(proof.get('time'), now)):
        raise ValueError('Fresh exact readonly AWS comparison snapshot required')
    with path.open(encoding='utf-8-sig', newline='') as stream:
        raw = list(csv.DictReader(stream))
    rows = []
    for row in raw:
        parsed = {k: json.loads(v, object_pairs_hook=reject_duplicates) if k != 'id' else int(v) for k, v in row.items()}
        rows.append(parsed)
    expected = {str(r['id']) for r in selected}
    if len(rows) != len(expected) or len(rows) != proof.get('rows') or {str(r['id']) for r in rows} != expected:
        raise ValueError('Exact current selection/count coverage required')
    return {str(r['id']): r for r in rows}


def run(args):
    if sha(args.review) != PIN: raise ValueError('Pinned F2 selection required')
    original = read(args.review)
    selected = original['rows']
    if len(selected) != 262 or len({r['id'] for r in selected}) != 262: raise ValueError('Exact262 review selection required')
    hashes = {str(args.review): sha(args.review)}
    for filename, expected in original['summary']['inputHashes'].items():
        if sha(Path(filename)) != expected: raise ValueError('Historical ancestry drift: ' + filename)
        hashes[filename] = expected
    now, sources = datetime.now(timezone.utc), {}
    for folder in args.usda_folder:
        for path in sorted(folder.glob('batch-*.json')):
            if path.name.endswith('.receipt.json'): continue
            receipt_path = path.with_suffix('.receipt.json')
            receipt = read(receipt_path)
            # Integrity is mandatory even when stale; stale data remains explicitly held, never restamped.
            if receipt.get('sha256') != sha(path): raise ValueError('USDA receipt/body mismatch')
            for body in followup.fdc.validate_batch(receipt['ids'], read(path)):
                key = 'USDA_FOODDATA:fdc:' + str(body['fdcId'])
                if key in sources: raise ValueError('Duplicate source key')
                sources[key] = body, receipt['fetchedAt']
            hashes[str(path)] = sha(path); hashes[str(receipt_path)] = sha(receipt_path)
    for row in selected:
        if not row['sourceKey'].startswith('barcode:'): continue
        code = row['sourceKey'].removeprefix('barcode:')
        path = args.off_folder / ('barcode-' + code + '.json')
        receipt = read(path)
        checked = datetime.fromisoformat(receipt['checkedAt'].replace('Z', '+00:00'))
        if not followup.fetch.cached_valid(receipt, code, checked): raise ValueError('OFF complete receipt integrity required')
        sources[row['sourceKey']] = receipt['product'], receipt['checkedAt']
        hashes[str(path)] = sha(path)
    if args.archived:
        if args.snapshot or args.snapshot_proof: raise ValueError('Do not combine archive and current DB inputs')
        current = {r['id']: r['before'] for r in selected}
    else:
        if not args.snapshot or not args.snapshot_proof: raise ValueError('Current snapshot required; use --archived only for explicit historical analysis')
        current = load_snapshot(args.snapshot, args.snapshot_proof, selected, now)
        hashes[str(args.snapshot)] = sha(args.snapshot); hashes[str(args.snapshot_proof)] = sha(args.snapshot_proof)
    for path in sorted(Path('scripts').glob('*.py')):
        if any(s in path.name for s in ('r3', 'r4', 'r5')) and not path.name.startswith('test_'): hashes[str(path)] = sha(path)
    for path in Path('src/main/resources/catalog').glob('*nutrient*.json'): hashes[str(path)] = sha(path)
    context = {'version': VERSION, 'inputHashes': hashes, 'archived': args.archived,
               'schemaVersion': None if args.archived else read(args.snapshot_proof)['schemaVersion'],
               'sourceFreshness': {key: fresh(stamp, now) for key, (_, stamp) in sorted(sources.items())}}
    context_sha = digest(context)
    checkpoint = args.output / 'checkpoint.json'
    if args.output.exists() and not args.resume: raise ValueError('New output required; only verified same-input --resume is allowed')
    if args.resume and not checkpoint.exists(): raise ValueError('Verified checkpoint required for resume')
    progress = read(checkpoint) if checkpoint.exists() else {'contextSha256': context_sha, 'stages': []}
    if progress['contextSha256'] != context_sha: raise ValueError('Input/code/receipt drift; do not reuse old stage decisions')
    if not isinstance(progress.get('stages'), list) or len(progress['stages']) > 4: raise ValueError('Invalid checkpoint stage count')
    for number, entry in enumerate(progress['stages'], 1):
        if entry.get('stage') != number or entry.get('file') != f'stage-{number}.json': raise ValueError('Nonsequential checkpoint')
        if sha(args.output / entry['file']) != entry['sha256']: raise ValueError('Checkpoint stage evidence changed')
    compared = [compare_row(r, current[r['id']], sources[r['sourceKey']], not args.archived, now) for r in selected]
    args.output.mkdir(parents=True, exist_ok=True)
    for n in range(1, 5):
        path = args.output / f'stage-{n}.json'
        if n <= len(progress['stages']): continue
        if path.exists(): raise ValueError('Uncommitted stage file; retain it and use a new output directory')
        result = stage(n, compared, sources)
        result['summary'].update(COMMON | {'version': VERSION, 'stage': n, 'reviewedAt': now.isoformat(),
            'scope': 'ARCHIVED_262_REVIEW_NOT_CURRENT_DB' if args.archived else 'CURRENT_EXACT_262_AWS_READONLY',
            'contextSha256': context_sha, 'providerRequests': 0, 'databaseWrites': 0})
        save(path, result)
        progress['stages'].append({'stage': n, 'file': path.name, 'sha256': sha(path)})
        save(checkpoint, progress)
    report = {'version': VERSION, 'completedStages': 4, 'scope': 'ARCHIVED_262_REVIEW_NOT_CURRENT_DB' if args.archived else 'CURRENT_EXACT_262_AWS_READONLY',
              'context': context, 'checkpointSha256': sha(checkpoint), 'stageSummaries': [read(args.output / r['file'])['summary'] for r in progress['stages']],
              'currentRuntimePreviewRequired': True, 'globalR5Done': False, **COMMON}
    save(args.output / 'cycle-report.json', report)
    print(json.dumps({k: v for k, v in report.items() if k != 'context'}, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--review', type=Path, default=Path('outputs/r5-nutrient-holds-20261015/review-final/source-review.json'))
    parser.add_argument('--usda-folder', action='append', type=Path, required=True)
    parser.add_argument('--off-folder', type=Path, required=True)
    parser.add_argument('--snapshot', type=Path)
    parser.add_argument('--snapshot-proof', type=Path)
    parser.add_argument('--archived', action='store_true')
    parser.add_argument('--resume', action='store_true')
    parser.add_argument('--output', type=Path, required=True)
    run(parser.parse_args())

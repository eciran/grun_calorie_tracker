"""Bounded source review, not a conversion/import/approval command."""
import argparse
import importlib.util
import json
from datetime import datetime, timezone
from pathlib import Path

spec = importlib.util.spec_from_file_location('r5_seven_integrity', Path(__file__).with_name('audit-r5-portion-integrity.py'))
integrity = importlib.util.module_from_spec(spec)
spec.loader.exec_module(integrity)
cycle = integrity.cycle
require = integrity.require
EXPECTED = {'49697': (174158, [93845]), '49700': (169098, [84232]),
            '180995': (170882, [87487]), '180998': (170875, [87468, 87469]), '49681': (172448, [90639, 90640])}
DOCUMENTS = [
    {'url': 'https://www.ars.usda.gov/ARSUserFiles/80400525/Data/SR/SR28/sr28_doc.pdf',
     'section': 'Weights and Measures p26; AppendixB',
     'finding': 'Edible source grams are distinct from a household description; abbreviation alone does not bind a metric volume standard'},
    {'url': 'https://www.ars.usda.gov/ARSUserFiles/80400525/Data/SR-Legacy/SR-Legacy_Doc.pdf',
     'section': 'Weight file section4.9 and AppendixB',
     'finding': 'Count, measure description and gram weight remain distinct source facts'},
    {'url': 'https://ods.od.nih.gov/pubs/usdandb/Iron-Food.pdf',
     'section': 'USDA SR28 report p225, NDB16126 and NDB16159',
     'finding': 'NDB16126 is represented by half-cup126g; the listed one-fifth-block91g represents NDB16159 instead',
     'inferenceLimit': 'Different representative measures do not prove the current API record wrong; no source/profile reassignment is authorized'},
    {'url': 'https://www.fda.gov/regulatory-information/search-fda-guidance-documents/guidance-industry-guidelines-determining-metric-equivalents-household-measures',
     'section': 'Nutrition-label household measures',
     'finding': 'Label-specific definitions cannot silently substitute for an unqualified legacy source measure'},
]


def review(stages, sources, now):
    parents = {r['id']: r for r in stages[0]['rows']}
    portions = {r['id']: r for r in stages[2]['rows']}
    findings = []
    for identity, (fdc, ids) in EXPECTED.items():
        parent, row = parents[identity], portions[identity]
        key = 'USDA_FOODDATA:fdc:' + str(fdc)
        require(parent['sourceKey'] == key and parent['sourceBindingMatchesCurrent'] is True, 'Exact measured source binding required')
        body, stamp = sources[key]
        freshness = integrity.source_freshness(parent, body, stamp, now)
        held = {p['sourcePortion']['id']: p['sourcePortion'] for p in row['unsupportedSourceMeasures']}
        for source_id in ids:
            matches = [p for p in body['foodPortions'] if p['id'] == source_id]
            require(len(matches) == 1 and cycle.digest(matches[0]) == cycle.digest(held[source_id]), 'Pending source measure changed')
            measure = matches[0]
            require(cycle.qualified.proposal(measure, body)[0] is None, 'A formerly unresolved measure unexpectedly became a candidate')
            findings.append({'id': identity, 'sourceKey': key, 'sourcePortionId': source_id,
                'sourcePortion': measure, 'sourceBodyFingerprint': cycle.digest(body),
                'sourceFreshness': freshness, 'decision': 'RETAIN_SOURCE_MEASURE_HOLD',
                'reason': 'FRACTION_BASE_AND_HISTORICAL_PROFILE_REVIEW' if fdc == 172448 else 'SOURCE_VOLUME_STANDARD_NOT_BOUND',
                'wholeBlockDerivationAllowed': False, 'mlConversionAllowed': False, **integrity.PROHIBITIONS})
    water = parents['49697']
    body, stamp = sources[water['sourceKey']]
    source_ml = [p for p in body['foodPortions'] if p['id'] == 93847]
    require(len(source_ml) == 1, 'Unique exact water ml portion required')
    measure = source_ml[0]
    option = next(w for w in portions['49697']['sourceProposals'] if w['sourceProposal'].get('sourcePortionId') == 93847)
    integrity.validate_candidate(option['sourceProposal'])
    candidate = option['sourceProposal']
    require(measure['modifier'] == 'ml' and (measure['amount'], measure['gramWeight']) == (1, 1)
            and candidate['sourcePortionFingerprint'] == cycle.digest(measure)
            and (candidate['quantity'], candidate['gramWeight'], candidate['mlVolume']) == (1, 1, 1),
            'Only this exact explicitly paired source measure may support the water alternative')
    alternative = {'id': '49697', 'sourceKey': water['sourceKey'], 'sourcePortionId': 93847,
        'sourceBodyFingerprint': cycle.digest(body), 'sourcePortionFingerprint': cycle.digest(measure),
        'parentAndDependenciesFingerprint': cycle.digest(water['before']),
        'proposedOption': candidate, 'priorCandidateHoldsRetained': option['holds'],
        'existingOptionsKept': portions['49697']['oldOptionsRetained'],
        'decision': 'PREFER_EXPLICIT_SOURCE_ML_PAIR_FOR_ADMIN_REVIEW_NOT_UNQUALIFIED_FL_OZ',
        'sourcePairScope': 'THIS_EXACT_WATER_SOURCE_MEASURE_ONLY_NOT_A_UNIVERSAL_DENSITY',
        'existingVolumeOnlyCupAttestsDensity': False,
        'consumerInputUnitsAfterSeparateApproval': ['GRAM', 'MILLILITER', 'SERVING'],
        'changeCurrentOptionOrReferenceBasis': False, 'appendOrPublishOption': False,
        'requiredGates': ['Fresh full current/source comparison', 'Explicit serving/bridge review',
                          'Current runtime V2 and consolidated release approval'], **integrity.PROHIBITIONS}
    return {'summary': {'version': 'R5_SEVEN_SOURCE_MEASURE_REVIEW_V1', 'reviewedAt': now.isoformat(),
            'scope': 'EXACT7_MEASURES_FROZEN_SOURCE_REVIEW_NOT_CURRENT_AWS_READ',
            'sourceMeasuresReviewed': 7, 'unresolvedSourceMeasuresRetained': 7,
            'volumeStandardMeasuresHeld': 5, 'tofuFractionBaseMeasuresHeld': 2,
            'waterExplicitMlAlternativePrepared': 1, 'newCandidates': 0, 'newSourceApprovals': 0,
            'databaseWrites': 0, 'foodProviderRequests': 0, 'globalR5Done': False, **integrity.PROHIBITIONS},
        'documents': DOCUMENTS, 'findings': findings, 'waterAlternative': alternative,
        'tofuReview': {'sourceKey': 'USDA_FOODDATA:fdc:172448', 'ndbNumber': 16126,
            'observation': 'The two current source fractions have different implicit whole-block bases; historical91g also occurs on another profile',
            'inference': 'POTENTIAL_PROFILE_OR_PACKAGE_AMBIGUITY_NOT_PROVEN_PROVIDER_ERROR',
            'sourceIdReassignmentAllowed': False, 'chooseClosestNutritionAllowed': False, 'deriveWholeBlockAllowed': False}}


def run(args):
    require(not args.output.exists(), 'Use a new report path, never replace evidence')
    stages, report = integrity.batch.load_cycle(args.review)
    result = review(stages, integrity.load_sources(report), datetime.now(timezone.utc))
    result['inputHashes'] = {str(args.review / name): cycle.sha(args.review / name)
                           for name in ('cycle-report.json', 'checkpoint.json', 'stage-1.json', 'stage-2.json', 'stage-3.json', 'stage-4.json')}
    result['codeHashes'] = {str(Path(__file__)): cycle.sha(Path(__file__))}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    cycle.save(args.output, result)
    print(json.dumps(result['summary'], indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--review', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    run(parser.parse_args())

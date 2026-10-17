"""Exact-source EN/TR naming and qualified alias proposals, never a DB patch.

Only the selected F3 name queue is projected. Old aliases are unmeasured and
therefore cannot be copied, retired or declared safe by this offline review.
"""
import argparse
import copy
import importlib.util
import json
import re
import unicodedata
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


prereq = module('r5_names_prerequisites', 'review-r5-classification-prerequisites.py')
gate, fingerprint, sha = prereq.gate, prereq.fingerprint, prereq.closure.sha
VERSION = 'R5_EXACT_SOURCE_EN_TR_NAMES_AND_ALIASES_V1'
QUEUE = 'SOURCE_IDENTITY_AND_NAME_REVIEW'
REGISTRY_VERSION = 'catalog-naming-r5-f4-v1'
PREPARATION_PREFIXES = {
    'EN': ('Dry-Roasted', 'Oil-Roasted', 'Honey-Roasted', 'Roasted and Toasted',
           'Roasted', 'Grilled', 'Steamed', 'Boiled', 'Cooked', 'Raw'),
    'TR': ('Kuru Kavrulmuş', 'Yağda Kavrulmuş', 'Bal ile Kavrulmuş', 'Buharda Pişmiş',
           'Fırınlanmış', 'Kavrulmuş', 'Izgara', 'Haşlanmış', 'Pişmiş', 'Çiğ'),
}
# These original names deliberately have no asserted Turkish local substitute.
RETAINED_SOURCE_TERMS = ('Haddock', 'King Mackerel', 'Alaska Pollock', 'Atlantic Pollock',
                         'Spanish Mackerel', 'Queen Crab', 'Greenland Halibut', 'Blue Mussel',
                         'Chum', 'Chinook', 'Sockeye', 'Eye of Round', 'Top Round', 'Top Sirloin',
                         'Navy', 'Pinto', 'Adzuki', 'French', 'Great Northern', 'Cranberry', 'Roman',
                         'Kidney', 'Royal Red', 'California Red', 'Casaba', 'Anjou', 'Choice', 'Select')

# Positive source predicates require their meaning to survive both localized names.
# These are coverage checks for this bounded registry, not a translation engine.
QUALIFIERS = (
    ('RAW', r'\braw\b', r'\braw\b', r'\bçiğ\b'),
    ('COOKED', r'\bcooked\b', r'\b(cooked|roasted|grilled|boiled|steamed)\b', r'\b(pişmiş|fırınlanmış|kavrulmuş|ızgara|haşlanmış)\b'),
    ('ROASTED', r'\broasted\b', r'\broasted\b', r'\b(fırınlanmış|kavrulmuş)\b'),
    ('BOILED', r'\bboiled\b', r'\bboiled\b', r'\bhaşlanmış\b'),
    ('GRILLED', r'\bgrilled\b', r'\bgrilled\b', r'\bızgara\b'),
    ('STEAMED', r'\bsteamed\b', r'\bsteamed\b', r'\bbuharda pişmiş\b'),
    ('TOASTED', r'\btoasted\b', r'\btoasted\b', r'\bkavrulmuş\b'),
    ('DRY_ROASTED', r'\bdry roasted\b', r'\bdry[- ]roasted\b', r'\bkuru kavrulmuş\b'),
    ('OIL_ROASTED', r'\boil roasted\b', r'\boil[- ]roasted\b', r'\byağda kavrulmuş\b'),
    ('DRY_PROFILE', r', dry$', r'\bdry\b', r'\bkuru\b'),
    ('NO_ADDED_SALT', r'\bwithout salt added\b', r'\bno added salt\b', r'\btuz ilavesiz\b'),
    ('UNSALTED', r'\bwithout salt\b(?! added)', r'\bunsalted\b', r'\btuzsuz\b'),
    ('SALTED', r'\bwith salt\b', r'\bsalted\b', r'\btuzlu\b'),
    ('MATURE_SEEDS', r'\bmature\b', r'\bmature seeds\b', r'\bolgun tohum\b'),
    ('FROZEN', r'\bfrozen\b', r'\bfrozen\b', r'\bdondurulmuş\b'),
    ('DRAINED', r'\bdrained\b', r'\bdrained\b', r'\bsüzülmüş\b'),
    ('ENRICHED', r'\benriched\b', r'\benriched\b', r'\bzenginleştirilmiş\b'),
    ('GLUTEN_FREE', r'\bgluten-free\b', r'\bgluten-free\b', r'\bglutensiz\b'),
    ('WITHOUT_EGG', r'\bwithout egg\b', r'\bwithout egg\b', r'\byumurtasız\b'),
    ('WITH_EGG', r'\begg\b', r'\begg\b', r'\byumurtalı\b'),
    ('HOMEMADE', r'\bhomemade\b', r'\bhomemade\b', r'\bev yapımı\b'),
    ('FRESH_REFRIGERATED', r'\bfresh-refrigerated\b', r'\bfresh-refrigerated\b', r'\btaze soğutulmuş\b'),
    ('PLAIN', r'\bplain\b', r'\bplain\b', r'\bsade\b'),
    ('MEDIUM_GRAIN', r'\bmedium-grain\b', r'\bmedium-grain\b', r'\borta taneli\b'),
    ('MEAT_ONLY', r'\bmeat only\b', r'\bmeat only\b', r'\byalnızca et\b'),
    ('DARK_MEAT', r'\bdark meat\b', r'\bdark meat\b', r'\bkoyu et\b'),
    ('LEAN_PART', r'\bseparable lean only\b', r'\bseparable lean only\b', r'\bayrılabilir yağsız kısım\b'),
    ('BONELESS', r'\bboneless\b', r'\bboneless\b', r'\bkemiksiz\b'),
    ('CHOICE', r'\bchoice\b', r'\bchoice\b', r'\bchoice\b'),
    ('SELECT', r'\bselect\b', r'\bselect\b', r'\bselect\b'),
    ('ALL_GRADES', r'\ball grades\b', r'\ball grades\b', r'\btüm sınıflar\b'),
    ('FAT_TRIM_EIGHTH', r'trimmed to 1/8" fat', r'1/8 in fat trim', r'1/8 inç yağ trimi'),
    ('FAT_TRIM_ZERO', r'trimmed to 0" fat', r'\b0 in fat trim', r'\b0 inç yağ trimi'),
    ('SKIN_COOKED', r'\bcooked in skin\b', r'\bcooked in skin\b', r'\bkabukta pişirilmiş\b'),
    ('SKIN_NOT_COOKED', r'\bcooked without skin\b', r'\bcooked without skin\b', r'\bkabuksuz pişirilmiş\b'),
    ('WITHOUT_SKIN', r'\bwithout skin\b', r'\bwithout skin\b', r'\bkabuksuz\b'),
    ('WITH_PEEL', r'\bwith peel\b', r'\bwith peel\b', r'\bkabuklu\b'),
    ('SKIN_INCLUDED', r'\bincludes skin\b', r'\bskin included\b', r'\bkabuklu\b'),
    ('FLESH', r'\bflesh\b', r'\bflesh only\b', r'\byalnızca içi\b'),
    ('WHOLE', r'\bwhole\b', r'\bwhole\b', r'\bbütün\b'),
    ('HONEY_ROASTED', r'\bhoney roasted\b', r'\bhoney-roasted\b', r'\bbal ile kavrulmuş\b'),
    ('UNBLANCHED', r'\bunblanched\b', r'\bunblanched\b', r'\biç kabuğu soyulmamış\b'),
    ('STONE_GROUND', r'\bstone ground\b', r'\bstone-ground\b', r'\btaşta öğütülmüş\b'),
    ('MIXED_SPECIES', r'\bmixed species\b', r'\bmixed species\b', r'\bkarma türler\b'),
    ('ALL_VARIETIES', r'\ball varieties\b', r'\ball varieties\b', r'\btüm çeşitler\b'),
    ('COMMERCIAL_VARIETIES', r'\ball commercial varieties\b', r'\ball commercial varieties\b', r'\btüm ticari çeşitler\b'),
    ('TRADITIONAL_VARIETIES', r'\btraditional varieties\b', r'\btraditional varieties\b', r'\bgeleneksel çeşitler\b'),
    ('EXTRA_SWEET', r'\bextra sweet variety\b', r'\bextra sweet variety\b', r'\bekstra tatlı çeşit\b'),
    ('PROTEIN_FORTIFIED', r'\bprotein-fortified\b', r'\bprotein-fortified\b', r'\bproteinle zenginleştirilmiş\b'),
    ('ALL_TYPES', r'\ball types\b', r'\ball types\b', r'\btüm türler\b'),
    ('CHOPPED_OR_LEAF', r'\bchopped or leaf\b', r'\bchopped or leaf\b', r'\bdoğranmış veya yaprak\b'),
    ('KING_MACKEREL', r'\bmackerel, king\b', r'\bking mackerel\b', r'\bking mackerel\b'),
    ('SPANISH_MACKEREL', r'\bmackerel, spanish\b', r'\bspanish mackerel\b', r'\bspanish mackerel\b'),
    ('ALASKA_POLLOCK', r'\bpollock, alaska\b', r'\balaska pollock\b', r'\balaska pollock\b'),
    ('ATLANTIC_POLLOCK', r'\bpollock, atlantic\b', r'\batlantic pollock\b', r'\batlantic pollock\b'),
    ('GREENLAND_HALIBUT', r'\bhalibut, greenland\b', r'\bgreenland halibut\b', r'\bgreenland halibut\b'),
    ('BLUE_MUSSEL', r'\bmussel, blue\b', r'\bblue mussel\b', r'\bblue mussel\b'),
    ('QUEEN_CRAB', r'\bcrab, queen\b', r'\bqueen crab\b', r'\bqueen crab\b'),
    ('ATLANTIC', r'\batlantic\b', r'\batlantic\b', r'\b(atlantik|atlantic)\b'),
    ('PACIFIC', r'\bpacific\b', r'\bpacific\b', r'\b(pasifik|pacific)\b'),
    ('PINK', r'\bpink\b', r'\bpink\b', r'\bpembe\b'),
    ('RED', r'\bred\b', r'\bred\b', r'\b(kırmızı|red)\b'),
    ('WHITE', r'\bwhite\b', r'\bwhite\b', r'\bbeyaz\b'),
    ('BLACK', r'\bblack\b', r'\bblack\b', r'\bsiyah\b'),
    ('YELLOW', r'\byellow\b', r'\byellow\b', r'\bsarı\b'),
    ('SPLIT', r'\bsplit\b', r'\bsplit\b', r'\bkırık\b'),
    ('SPINACH', r'\bspinach\b', r'\bspinach\b', r'\bıspanak(lı)?\b'),
    ('CORN', r'\bcorn\b', r'\bcorn\b', r'\bmısır\b'),
    ('RICE', r'\brice\b', r'\brice\b', r'\bpirinç\b'),
    ('SWEET_POTATO_LEAVES', r'\bsweet potato leaves\b', r'\bsweet potato leaves\b', r'\btatlı patates yaprağı\b'),
    ('CHICKEN', r'\bchicken\b', r'\bchicken\b', r'\btavuk\b'),
    ('TURKEY', r'\bturkey\b', r'\bturkey\b', r'\bhindi\b'),
    ('DRUMSTICK', r'\bdrumstick\b', r'\bdrumstick\b', r'\bbaget\b'),
    ('LEG', r'\bleg\b', r'\bleg\b', r'\bbut\b'),
    ('BEEF', r'\bbeef\b', r'\bbeef\b', r'\bdana\b'),
    ('PORK', r'\bpork\b', r'\bpork\b', r'\bdomuz\b'),
    ('TENDERLOIN', r'\btenderloin\b', r'\btenderloin\b', r'\bbonfile\b'),
    ('STEAK', r'\bsteak\b', r'\bsteak\b', r'\bbiftek\b'),
    ('COD', r'\bcod\b', r'\bcod\b', r'\bmorinası\b'),
    ('SWORDFISH', r'\bswordfish\b', r'\bswordfish\b', r'\bkılıç balığı\b'),
    ('SHRIMP', r'\bshrimp\b', r'\bshrimp\b', r'\bkarides\b'),
    ('SALMON', r'\bsalmon\b', r'\bsalmon\b', r'\bsomon(u)?\b'),
    ('TROUT', r'\btrout\b', r'\btrout\b', r'\balabalık\b'),
    ('QUINOA', r'\bquinoa\b', r'\bquinoa\b', r'\bkinoa\b'),
    ('MILLET', r'\bmillet\b', r'\bmillet\b', r'\bdarı\b'),
    ('BUCKWHEAT', r'\bbuckwheat\b', r'\bbuckwheat\b', r'\bkarabuğday\b'),
    ('GROATS', r'\bgroats\b', r'\bgroats\b', r'\btaneleri\b'),
    ('PEARS', r'\bpears\b', r'\bpear\b', r'\b(?:armut|armudu)\b'),
    ('BANANAS', r'\bbananas\b', r'\bbanana\b', r'\bmuz\b'),
    ('ORANGES', r'\boranges\b', r'\borange\b', r'\bportakal\b|\bmandalina\b'),
    ('APPLES', r'\bapples\b', r'\bapple\b', r'\belma\b'),
    ('PINEAPPLE', r'\bpineapple\b', r'\bpineapple\b', r'\bananas\b'),
)


def valid_text(value):
    if (not isinstance(value, str) or not value or len(value.encode('utf-16-le')) // 2 > 255
            or value != unicodedata.normalize('NFC', value) or value.strip() != value
            or re.search(r'\s{2,}|[<>]', value)
            or any(unicodedata.category(c) in {'Cc', 'Cf'} or c == '\ufffd' for c in value)):
        raise ValueError('Invalid name/alias format')
    depth = 0
    for c in value:
        depth += 1 if c == '(' else -1 if c == ')' else 0
        if depth < 0:
            raise ValueError('Unbalanced name/alias parentheses')
    if depth:
        raise ValueError('Unbalanced name/alias parentheses')


def check_qualifiers(description, en, tr):
    for label in (en, tr):
        valid_text(label)
    checks = []
    for code, source, english, turkish in QUALIFIERS:
        if code == 'WITH_EGG' and 'without egg' in description.lower():
            continue
        if re.search(source, description, re.I):
            if not re.search(english, en, re.I) or not re.search(turkish, tr, re.I):
                raise ValueError('Source qualifier lost: ' + code)
            checks.append(code)
    for term in RETAINED_SOURCE_TERMS:
        # Derive required identity from SOURCE, never from the proposed name.
        if re.search(r'\b' + re.escape(term) + r'\b', description, re.I):
            if not all(re.search(r'\b' + re.escape(term) + r'\b', label, re.I) for label in (en, tr)):
                raise ValueError('Source-specific identity lost: ' + term)
            checks.append('RETAIN_SOURCE_TERM:' + term)
    # Reject claims absent from the source, especially approximate local substitutes.
    for en_word, tr_word, source_pattern in (
        (r'\braw\b', r'\bçiğ\b', r'\braw\b'),
        (r'\bboiled\b', r'\bhaşlanmış\b', r'\bboiled\b'),
        (r'\bgrilled\b', r'\bızgara\b', r'\bgrilled\b'),
        (r'\bsteamed\b', r'\bbuharda pişmiş\b', r'\bsteamed\b'),
        (r'\broasted\b', r'\bfırınlanmış\b', r'\broasted\b'),
        (r'\bfried\b', r'\bkızartılmış\b', r'\bfried\b'),
        (r'\bcooked\b', r'\bpişmiş\b', r'\b(cooked|roasted|grilled|boiled|steamed|toasted|fried|baked)\b'),
        (r'\bunsalted\b', r'\btuzsuz\b', r'\bwithout salt\b'),
        (r'\bsalted\b', r'\btuzlu\b', r'\bwith salt\b'),
        (r'\bno added salt\b', r'\btuz ilavesiz\b', r'\bwithout salt added\b'),
        (r'\bfrozen\b', r'\bdondurulmuş\b', r'\bfrozen\b'),
        (r'\bboneless\b', r'\bkemiksiz\b', r'\bboneless\b'),
        (r'\bgluten-free\b', r'\bglutensiz\b', r'\bgluten-free\b'),
        (r'\bwithout egg\b', r'\byumurtasız\b', r'\bwithout egg\b'),
    ):
        if (re.search(en_word, en, re.I) or re.search(tr_word, tr, re.I)) and not re.search(source_pattern, description, re.I):
            raise ValueError('Unproven name claim')
    if re.search(r'\bfat[- ]free\b|\bzero sodium\b', en, re.I) or re.search(r'\byağsız ürün\b|\bsıfır sodyum\b', tr, re.I):
        raise ValueError('Unproven nutritional marketing claim')
    if ' dry' in description.lower() and description.lower().endswith(', dry'):
        if re.search(r'\bcooked\b', en, re.I) or re.search(r'\bpişmiş\b', tr, re.I):
            raise ValueError('Dry profile cannot become ready-cooked')
    return checks


def rotate_preparation(label, language):
    for prefix in PREPARATION_PREFIXES[language]:
        if label.startswith(prefix + ' '):
            return label[len(prefix) + 1:] + ' (' + prefix + ')'
    raise ValueError('An explicit reviewed preparation prefix is required')


def aliases(entry):
    result = [{'language': 'EN', 'alias': entry['sourceDescription'], 'evidence': 'EXACT_OFFICIAL_SOURCE_DESCRIPTION'}]
    for lang, field in (('EN', 'displayNameEn'), ('TR', 'displayNameTr')):
        label = rotate_preparation(entry[field], lang)
        valid_text(label)
        result.append({'language': lang, 'alias': label, 'evidence': 'REORDERED_REVIEWED_NAME_ALL_QUALIFIERS_RETAINED'})
    for alias in result:
        valid_text(alias['alias'])
        alias.update(aliasType='SYNONYM', active=False, applyAllowed=False)
    return result


def review_row(row, entry, body, now):
    original = copy.deepcopy(row)
    if (row.get('projectedWorkQueue') != QUEUE or row['prerequisiteState'] != 'LINKED_FOR_LOCAL_PROJECTION'
            or row['prerequisiteBlockers'] or row['applyAllowed'] is not False or row['liveApplyApproved'] is not False):
        raise ValueError('Only the safe F3 name-review cohort may be projected')
    before, food = row['before'], row['before']['food']
    if (row['beforeFingerprint'] != fingerprint(before) or str(food['id']) != row['id']
            or food['source_key'] != row['sourceKey'] or row['sourceKey'] != 'USDA_FOODDATA:fdc:' + entry['fdcId']
            or str(body.get('fdcId')) != entry['fdcId'] or body.get('description') != entry['sourceDescription']
            or row['sourceFingerprint'] != fingerprint(body) or body.get('dataType') not in {'SR Legacy', 'Foundation'}
            or food.get('barcode') or food.get('normalized_barcode') or food.get('brand') or food.get('brand_id')
            or body.get('gtinUpc') or body.get('brandOwner') or row['sourceKey'] in prereq.SOURCE_NAMED_BRANDS
            or food.get('data_source') != 'USDA_FOODDATA' or food.get('is_custom') is not False
            or food.get('publication_status') != 'PUBLISHED' or food.get('nutrition_reference_unit') != 'PER_100G'):
        raise ValueError('Exact source and protected food identity required')
    age = (now - datetime.fromisoformat(row['sourceCheckedAt'].replace('Z', '+00:00'))).total_seconds()
    if not 0 <= age <= 86400:
        raise ValueError('Expired/future source evidence; never restamp')
    checks = check_qualifiers(entry['sourceDescription'], entry['displayNameEn'], entry['displayNameTr'])
    retained = [t for t in RETAINED_SOURCE_TERMS if re.search(r'\b' + re.escape(t) + r'\b', body['description'], re.I)]
    if any(not re.search(r'\b' + re.escape(t) + r'\b', entry['displayNameTr'], re.I) for t in retained):
        raise ValueError('Unverified local substitute for a retained source term')
    candidates = aliases(entry)
    check_qualifiers(entry['sourceDescription'], candidates[1]['alias'], candidates[2]['alias'])
    projected = copy.deepcopy(before)
    allowed = {'catalog_type'}
    if set(row['metadataChanges']) - allowed or row['metadataChanges'].get('catalog_type') != 'GENERIC_INGREDIENT':
        raise ValueError('Unexpected predecessor metadata/preparation change')
    changes = {**row['metadataChanges'], 'name': entry['sourceDescription'],
               'display_name': entry['displayNameEn'], 'short_display_name': entry['displayNameEn']}
    projected['food'].update(changes)
    decision = gate.source.usda_decision({**projected['food'], 'id': row['id']}, body, set())
    reviewed = gate.review(decision, projected, body, row['sourceCheckedAt'], now, approve_additive_usda=True)
    holds = set(reviewed['holds'])
    if decision['decision'] not in gate.READY:
        holds.add('SOURCE_DECISION_NOT_READY')
    holds.update({'CURRENT_METADATA_ALIAS_AND_ADMIN_APPROVAL_REQUIRED', 'CURRENT_ALIAS_COLLISION_AND_BEFORE_IMAGE_REQUIRED'})
    if 'EXISTING_ADMIN_APPROVAL_REVIEW_REQUIRED' in row['originalHolds']:
        holds.add('EXISTING_ADMIN_APPROVAL_REVIEW_REQUIRED')
    if row['servingReviewRequiredByR4']:
        holds.add('R4_SOURCE_SERVING_REVIEW_REQUIRED')
    for field in gate.FIELDS:
        if projected['food'].get(field) != food.get(field):
            raise ValueError('Nutrient mutation')
    for field in before.keys() - {'food'}:
        if projected[field] != before[field]:
            raise ValueError('Child/reference mutation')
    if reviewed['options'] != row['projectedOptions']:
        raise ValueError('Serving proposal changed during naming review')
    if row != original:
        raise ValueError('Input row mutated')
    return {'id': row['id'], 'sourceKey': row['sourceKey'], 'sourceDescription': body['description'],
            'sourceFingerprint': fingerprint(body), 'sourceCheckedAt': row['sourceCheckedAt'],
            'scientificNameAtSource': body.get('scientificName') or None,
            'before': copy.deepcopy(before), 'beforeFingerprint': fingerprint(before),
            'foodChanges': {k: v for k, v in changes.items() if food.get(k) != v},
            'undoFoodValues': {k: food.get(k) for k, v in changes.items() if food.get(k) != v},
            'undoFoodFieldPresence': {k: k in food for k, v in changes.items() if food.get(k) != v},
            'localizationProposals': {lang: {'displayName': entry[field], 'shortDisplayName': entry[field], 'applyAllowed': False}
                                      for lang, field in (('EN', 'displayNameEn'), ('TR', 'displayNameTr'))},
            'aliasProposals': candidates, 'oldAliasActions': [], 'currentAliasesMeasured': False,
            'currentLocalizationsMeasured': False, 'retainedSourceTerms': retained,
            'translationAuthority': 'INTERNAL_SOURCE_QUALIFIED_PROPOSAL_NOT_OFFICIAL_USDA_TR',
            'qualifierChecks': checks, 'projectedHolds': sorted(holds),
            'projectedWorkQueue': prereq.followup.work_queue({**reviewed, 'holds': sorted(holds), 'disposition': 'HELD'}),
            'numericComparison': reviewed['nutrients'], 'nutrientVariants': reviewed.get('nutrientVariants', {}),
            'projectedOptions': reviewed['options'], 'status': 'REVIEW_ONLY_ARCHIVED_BASELINE',
            'applyAllowed': False, 'liveApplyApproved': False, 'nutritionWriteAllowed': False,
            'currentDatabaseBeforeImageRequired': True, 'databaseWrites': 0, 'runtimeRegistryEnabled': False}


def collisions(rows):
    index = defaultdict(set)
    for row in rows:
        for lang, value in row['localizationProposals'].items():
            index[(lang, prereq.closure.review.match_name(value['displayName']))].add(row['id'])
        for alias in row['aliasProposals']:
            index[(alias['language'], prereq.closure.review.match_name(alias['alias']))].add(row['id'])
    return [{'language': lang, 'diagnosticKey': key, 'ids': sorted(ids)}
            for (lang, key), ids in sorted(index.items()) if len(ids) > 1]


def run(args):
    if args.output.exists():
        raise ValueError('New output directory required')
    now, hashes = datetime.now(timezone.utc), {}

    def pin(path):
        hashes[str(path)] = sha(path)
        return prereq.followup.read(path)

    prior = pin(args.review)
    summary = pin(args.review.parent / 'summary.json')
    registry = pin(args.registry)
    r3 = pin(args.r3_manifest)
    if (summary['reviewSha256'] != hashes[str(args.review)] or prior['summary']['version'] != prereq.VERSION
            or registry['version'] != REGISTRY_VERSION or registry['status'] != 'OFFLINE_PROPOSALS_ONLY_NOT_RUNTIME_APPROVED'
            or registry['selectionReviewSha256'] != hashes[str(args.review)]
            or registry['sourceManifestSha256'] != hashes[str(args.r3_manifest)]
            or prior['summary']['currentDatabaseMeasured'] is not False or prior['summary']['liveApplyAllowed'] is not False):
        raise ValueError('Predecessor/registry ancestry mismatch')
    prereq.unique_index(prior['rows'], 'id')
    prereq.unique_index(prior['rows'], 'sourceKey')
    columns = ['fdcId', 'sourceDescription', 'displayNameEn', 'displayNameTr']
    if registry['columns'] != columns or any(not isinstance(r, list) or len(r) != len(columns) for r in registry['entries']):
        raise ValueError('Exact naming registry schema required')
    entries = prereq.unique_index([dict(zip(columns, r)) for r in registry['entries']], 'fdcId')
    selected = [r for r in prior['rows'] if r['projectedWorkQueue'] == QUEUE]
    if (len(selected) != 118 or set(entries) != {prereq.closure.review.fdc_id(r['sourceKey']) for r in selected}):
        raise ValueError('Exact118 source-name queue coverage required')
    r3_index = prereq.unique_index(r3['decisions'], 'sourceKey')
    sources = {}
    for folder in args.usda_folder:
        for path in sorted(folder.glob('batch-*.json')):
            if path.name.endswith('.receipt.json'):
                continue
            receipt = pin(path.with_suffix('.receipt.json'))
            pin(path)
            for item in (path, path.with_suffix('.receipt.json')):
                if prior['summary']['inputHashes'].get(str(item)) != hashes[str(item)]:
                    raise ValueError('Changed/restamped F3 source evidence')
            prereq.followup.fdc.cached_batch_receipt(path, receipt['ids'], now)
            for food in prereq.followup.fdc.validate_batch(receipt['ids'], prereq.followup.read(path)):
                key = str(food['fdcId'])
                if key in sources:
                    raise ValueError('Duplicate source body')
                sources[key] = (food, receipt['fetchedAt'])
    result = []
    for row in selected:
        source_id = prereq.closure.review.fdc_id(row['sourceKey'])
        if source_id not in sources:
            raise ValueError('Missing complete exact source')
        body, checked = sources[source_id]
        decision = r3_index[row['sourceKey']]
        if (decision['sourceFingerprint'] != fingerprint(body) or decision['sourceDescription'] != body['description']
                or str(decision['id']) != row['id'] or checked != row['sourceCheckedAt']):
            raise ValueError('R3 source identity/version/timestamp mismatch')
        result.append(review_row(row, entries[source_id], body, now))
    conflicts = collisions(result)
    if conflicts:
        raise ValueError('Cross-profile display/alias collision: ' + json.dumps(conflicts))
    counters = Counter(h for r in result for h in r['projectedHolds'])
    report = {'version': VERSION, 'reviewedAt': now.isoformat(), 'products': len(result),
              'scope': 'EXACT118_HISTORICAL_NAME_QUEUE_NOT_CURRENT_CATALOG',
              'localizedNameProposals': len(result) * 2, 'aliasProposals': sum(len(r['aliasProposals']) for r in result),
              'cohortAliasOrDisplayCollisions': len(conflicts), 'currentCatalogAliasCollisionsMeasured': False,
              'rowsRetainingSourceSpecificTerms': sum(bool(r['retainedSourceTerms']) for r in result),
              'sourceQualifierChecks': sum(len(r['qualifierChecks']) for r in result),
              'projectedHolds': dict(counters), 'projectedWorkQueues': dict(Counter(r['projectedWorkQueue'] for r in result)),
              'sourceNameHoldRemovedLocally': sum('CURRENT_NAME_SOURCE_IDENTITY_REVIEW' not in r['projectedHolds'] for r in result),
              'unselectedPredecessorRowsUntouched': len(prior['rows']) - len(selected),
              'releaseReadyProducts': 0, 'databaseWrites': 0, 'nutritionWrites': 0, 'liveApplyAllowed': False,
              'currentDatabaseMeasured': False, 'runtimeRegistryEnabled': False, 'globalR5Completed': False,
              'inputHashes': hashes}
    args.output.mkdir(parents=True, exist_ok=False)
    path = args.output / 'name-alias-review.json'
    path.write_text(json.dumps({'summary': report, 'rows': result}, ensure_ascii=False, indent=2, allow_nan=False), encoding='utf-8')
    report['reviewSha256'] = sha(path)
    (args.output / 'summary.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps({k: v for k, v in report.items() if k != 'inputHashes'}, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    for name in ('review', 'r3-manifest', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--registry', type=Path, default=Path('src/main/resources/catalog/source-name-proposals-r5-f4-v1.json'))
    parser.add_argument('--usda-folder', type=Path, action='append', required=True)
    run(parser.parse_args())

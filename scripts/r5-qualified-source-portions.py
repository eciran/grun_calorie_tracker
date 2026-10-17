"""Source-specific qualified mass portions; never a cup-to-ml assumption."""
import hashlib
import importlib.util
import json
import math
import re
import unicodedata
from decimal import Decimal
from fractions import Fraction
from pathlib import Path

spec = importlib.util.spec_from_file_location('r5_measure_semantics', Path(__file__).with_name('r5-source-measure-semantics.py'))
semantics = importlib.util.module_from_spec(spec)
spec.loader.exec_module(semantics)

CUP_FORMS = {
    'slices': ('sliced', 'dilimlenmiş'), 'sliced': ('sliced', 'dilimlenmiş'),
    'cubes': ('cubed', 'küp doğranmış'), 'chunks': ('chunks', 'iri parçalanmış'),
    'pieces': ('pieces', 'parçalanmış'), 'chopped': ('chopped', 'doğranmış'),
    'diced': ('diced', 'küçük küp doğranmış'), 'chopped or diced': ('chopped or diced', 'doğranmış veya küçük küp doğranmış'),
    'whole': ('whole', 'bütün'), 'mashed': ('mashed', 'ezilmiş'), 'sifted': ('sifted', 'elenmiş'),
    'sections': ('sections', 'dilimlere ayrılmış'), 'solid or chunks': ('solid or chunks', 'bütün veya iri parçalar'),
    'pieces or slices': ('pieces or slices', 'parça veya dilim'), 'drained': ('drained', 'süzülmüş'),
    'shells': ('shells', 'kabuk biçimli'), 'rotini': ('rotini', 'rotini'),
    'elbows': ('elbows', 'dirsek biçimli'), 'spaghetti': ('spaghetti', 'spagetti'),
    'penne': ('penne', 'penne'), 'farfalle': ('farfalle', 'farfalle'),
    'lasagna': ('lasagna', 'lazanya'), 'crumbs': ('crumbs', 'kırıntı'),
    'crumbled': ('crumbled', 'ufalanmış'), 'halves and whole': ('halves and whole', 'yarım ve bütün'),
    'whole kernels': ('whole kernels', 'bütün iç taneler'), 'strips': ('strips', 'şerit doğranmış'),
    'pureed': ('pureed', 'püre'), 'halves': ('halves', 'yarımlar'),
}
CUTS = {'fillet': 'fileto', 'steak': 'biftek', 'breast': 'göğüs parçası', 'thigh': 'but parçası',
        'thigh without skin': 'derisiz but parçası', 'thigh with skin': 'derili but parçası',
        'drumstick': 'baget', 'drumstick without skin': 'derisiz baget', 'drumstick with skin': 'derili baget'}
COUNTS = {'leaf': 'yaprak', 'leaves': 'yaprak', 'leek': 'pırasa',
          'carrot': 'havuç', 'tomatoes': 'domates', 'mushroom': 'mantar', 'kernel': 'iç tane',
          'spears': 'uzun parça', 'berries': 'tane', 'raspberries': 'ahududu', 'melon': 'kavun'}
SIZE = r'(?:[1-9]\d{0,3}-[1-9]\d{0,3}/[1-9]\d{0,3}|[1-9]\d{0,3}/[1-9]\d{0,3}|(?:0|[1-9]\d{0,3})(?:\.\d{1,4})?)'
CONTEXT_LABELS = {
    ('sprout', 'Brussels sprouts, cooked, boiled, drained, without salt'):
        ('SERVING', 'Brussels sprout', 'Brüksel lahanası tanesi'),
    ('jumbo', 'Egg, whole, raw, fresh'): ('SERVING', 'jumbo egg', 'jumbo yumurta'),
    ('cup (4.86 large eggs)', 'Egg, whole, raw, fresh'):
        ('CUP', 'US measuring cup (4.86 large eggs)', 'ABD ölçü kabı (4.86 büyük yumurta)'),
    ('fruit without seeds', 'Oranges, raw, with peel'):
        ('SERVING', 'fruit without seeds (including peel)', 'meyve (çekirdeksiz, kabuğu dahil)'),
}


def fingerprint(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()).hexdigest()


def number_label(value):
    text = format(Decimal(str(value)), 'f')
    return text.rstrip('0').rstrip('.') if '.' in text else text


def valid_labels(labels):
    return (isinstance(labels, dict) and set(labels) == {'EN', 'TR'}
            and all(isinstance(label, str) and label == label.strip() and 0 < len(label) <= 120
                    and not any(unicodedata.category(c).startswith('C') for c in label)
                    for label in labels.values()))


def exact_member(portion, food):
    if (not isinstance(food, dict) or type(food.get('fdcId')) is not int or food['fdcId'] <= 0
            or food.get('dataType') not in ('SR Legacy', 'Foundation')
            or not isinstance(food.get('description'), str) or not food['description'].strip()
            or not isinstance(food.get('foodPortions'), list)):
        return False
    members = [p for p in food['foodPortions'] if isinstance(p, dict) and p.get('id') == portion['id']]
    return len(members) == 1 and fingerprint(members[0]) == fingerprint(portion)


def valid_size(text):
    if not re.fullmatch(SIZE, text):
        return False
    if '-' in text:
        whole, fractional = text.split('-')
        fraction = Fraction(fractional)
        return 0 < fraction < 1 and 0 < Fraction(whole) + fraction <= 1000
    return 0 < Fraction(text) <= 1000


def dimension_labels(descriptor):
    patterns = (
        (rf'slice(?:, (thin|medium|large))? \(({SIZE})" dia x ({SIZE})" thick\)', 'SLICE', 'disc'),
        (rf'slice(?:, (thin|medium|large))? \(({SIZE})" thick\)', 'SLICE', 'thickness'),
        (rf'piece \(({SIZE})" x ({SIZE})" x ({SIZE})"\)', 'SERVING', 'piece'),
        (rf'pat \(({SIZE})" sq, ({SIZE})" high\)', 'SERVING', 'pat'),
        (rf'pods \(({SIZE})" long\)', 'SERVING', 'pods'),
        (rf'spears \(({SIZE})" base\)', 'SERVING', 'spears'),
        (rf'potato \(({SIZE})" dia, sphere\)', 'SERVING', 'potato'),
        (rf'fruit \(({SIZE})" dia\)', 'SERVING', 'fruit'),
    )
    for pattern, shape, kind in patterns:
        match = re.fullmatch(pattern, descriptor)
        if not match:
            continue
        groups = match.groups()
        size, values = (groups[0], groups[1:]) if kind in ('disc', 'thickness') else (None, groups)
        if not all(valid_size(v) for v in values):
            return None
        if kind in ('disc', 'thickness'):
            en = (size + ' ' if size else '') + 'slice'
            tr = ({'thin': 'ince ', 'medium': 'orta ', 'large': 'büyük '}.get(size, '')) + 'dilim'
            if kind == 'disc':
                return shape, f'{en} (diameter {values[0]} in, thickness {values[1]} in)', f'{tr} (çap {values[0]} inç, kalınlık {values[1]} inç)'
            return shape, f'{en} (thickness {values[0]} in)', f'{tr} (kalınlık {values[0]} inç)'
        if kind == 'piece':
            dims = ' x '.join(values)
            return shape, f'piece ({dims} in)', f'parça ({dims} inç)'
        if kind == 'pat':
            return shape, f'pat (square side {values[0]} in, height {values[1]} in)', f'küçük parça (kare kenarı {values[0]} inç, yükseklik {values[1]} inç)'
        en, tr = {'pods': ('pods', 'tane'), 'spears': ('spears', 'uzun parça'),
                  'potato': ('potato', 'patates'), 'fruit': ('fruit', 'meyve')}[kind]
        label_en, label_tr = {'pods': ('length', 'uzunluk'), 'spears': ('base', 'taban'),
                              'potato': ('sphere, diameter', 'küre, çap'), 'fruit': ('diameter', 'çap')}[kind]
        return shape, f'{en} ({label_en} {values[0]} in)', f'{tr} ({label_tr} {values[0]} inç)'
    return None


def mass_labels(descriptor):
    if descriptor == 'lb':
        return 'SERVING', 'lb source measure', 'kaynak libre ölçüsü'
    match = re.fullmatch(rf'(slice|wedge) \(({SIZE}) oz\)|patty, ({SIZE}) oz|oz \(([1-9]\d{{0,3}}) (kernels|seeds)\)', descriptor)
    if not match:
        return None
    shape, ounces, patty, count, noun = match.groups()
    if ounces and valid_size(ounces):
        en, tr = ('slice', 'dilim') if shape == 'slice' else ('wedge', 'kama biçimli parça')
        return 'SLICE' if shape == 'slice' else 'SERVING', f'{en} ({ounces} oz source measure)', f'{tr} (kaynak ölçüsü {ounces} ons)'
    if patty and valid_size(patty):
        return 'SERVING', f'patty ({patty} oz source measure)', f'köfte ölçüsü (kaynak ölçüsü {patty} ons)'
    if count:
        tr = 'iç tane' if noun == 'kernels' else 'tohum'
        return 'SERVING', f'oz source measure ({count} {noun})', f'kaynak ons ölçüsü ({count} {tr})'
    return None


def pending_reason(portion):
    descriptor = str(portion.get('modifier') or portion.get('portionDescription') or
                     (portion.get('measureUnit') or {}).get('name') or '').lower()
    if descriptor in ('racc', 'nlea serving'):
        return 'REGULATORY_REFERENCE_NOT_HOUSEHOLD_PORTION'
    if re.search(r'\byields?\b', descriptor):
        return 'YIELD_AND_PREPARATION_QUALIFIERS_REQUIRE_REVIEW'
    if re.search(r'\b(?:bones?|boneless|skin|refuse|meat only|without seeds)\b', descriptor):
        return 'EDIBLE_PART_AND_PROFILE_REQUIRE_REVIEW'
    if descriptor in ('fl oz', 'quart', 'cubic inch') or re.search(r'\bpint\b', descriptor):
        return 'VOLUME_OR_GEOMETRY_NOT_AN_ML_BRIDGE'
    if re.search(r'\b(?:packages?|cans?|bottles?|block|stick)\b', descriptor):
        return 'PACKAGE_OR_BLOCK_SIZE_NOT_UNIVERSAL'
    if '"' in descriptor or re.search(r'\b(?:dia|cubic)\b', descriptor):
        return 'EXACT_SIZE_OR_DIMENSIONS_REQUIRE_LABEL_REVIEW'
    if re.search(r'\b(?:lb|oz)\b', descriptor):
        return 'QUALIFIED_MASS_DESCRIPTOR_REQUIRES_REVIEW'
    return 'OTHER_EXACT_SHAPE_OR_COUNT_REQUIRES_REVIEW'


def proposal(portion, food=None):
    if not isinstance(portion, dict):
        return None, 'INVALID_SOURCE_PORTION_STRUCTURE'
    amount, grams = portion.get('amount'), portion.get('gramWeight')
    if (not isinstance(portion.get('id'), int) or isinstance(portion['id'], bool) or portion['id'] <= 0
            or any(isinstance(x, bool) or not isinstance(x, (int, float)) or not math.isfinite(x) or x <= 0 for x in (amount, grams))):
        return None, 'INVALID_SOURCE_PORTION_ID_OR_AMOUNT'
    if portion.get('footnote') not in (None, ''):
        return None, 'SOURCE_FOOTNOTE_REQUIRES_REVIEW'
    if food is not None and not exact_member(portion, food):
        return None, 'EXACT_SOURCE_CONTEXT_AND_MEASURE_MEMBERSHIP_REQUIRED'
    measure = portion.get('measureUnit', {})
    modifier, description = portion.get('modifier'), portion.get('portionDescription')
    if (not isinstance(measure, dict) or any(v is not None and not isinstance(v, str)
            for v in (measure.get('name'), modifier, description))):
        return None, 'INVALID_SOURCE_DESCRIPTOR_OR_UNIT'
    if modifier and description and modifier.strip() != description.strip():
        return None, 'CONFLICTING_SOURCE_DESCRIPTORS_REQUIRE_REVIEW'
    unit = (measure.get('name') or '').lower().strip()
    raw = (modifier or description or '').strip()
    descriptor = raw if unit in ('', 'undetermined') else unit + (' ' + raw if raw else '')
    lower = descriptor.lower()
    context = {}
    matched = re.fullmatch(r'cup(?:,? )(.+)', lower)
    if lower == 'cup shredded' and isinstance(food, dict) and food['dataType'] == 'Foundation':
        shape, en, tr = 'CUP', 'source measuring cup (shredded)', 'kaynak ölçü kabı (rendelenmiş)'
        context = {'sourceContextFingerprint': fingerprint(food), 'sourceContextFdcId': food['fdcId'],
                   'sourceContextDescription': food['description']}
    elif matched and matched[1] in CUP_FORMS:
        en_form, tr_form = CUP_FORMS[matched[1]]
        shape, en, tr = 'CUP', f'US measuring cup ({en_form})', f'ABD ölçü kabı ({tr_form})'
    elif lower in CUTS:
        shape, en, tr = 'SERVING', lower, CUTS[lower]
    elif lower in COUNTS:
        # Source amount is the count for this total mass, not a universal piece size.
        shape, en, tr = 'SERVING', lower, COUNTS[lower]
    elif lower in ('slice, thin', 'slice, thick'):
        shape, en, tr = 'SLICE', lower.replace(', ', ' (' ) + ')', 'ince dilim' if lower.endswith('thin') else 'kalın dilim'
    elif lower in ('slice, large', 'slice, regular', 'slice, snack-size'):
        shape, en, tr = 'SLICE', lower.replace(', ', ' (') + ')', {
            'slice, large': 'büyük dilim', 'slice, regular': 'normal dilim', 'slice, snack-size': 'atıştırmalık dilim'}[lower]
    elif lower == 'bunch':
        shape, en, tr = 'SERVING', 'bunch', 'demet'
    elif lower in ('tbsp chopped', 'serving 1 tbsp'):
        shape = 'TABLESPOON'
        en, tr = ('US measuring tablespoon (chopped)', 'ABD yemek kaşığı (doğranmış)') if lower == 'tbsp chopped' else ('serving (1 US measuring tablespoon)', 'porsiyon (1 ABD yemek kaşığı)')
    elif labels := dimension_labels(lower):
        shape, en, tr = labels
    elif labels := mass_labels(lower):
        shape, en, tr = labels
    elif isinstance(food, dict) and isinstance(food.get('description'), str) and (lower, food['description']) in CONTEXT_LABELS:
        members = food.get('foodPortions')
        if (type(food.get('fdcId')) is not int or food['fdcId'] <= 0
                or food.get('dataType') not in ('SR Legacy', 'Foundation') or not isinstance(members, list)
                or sum(isinstance(p, dict) and p.get('id') == portion['id'] for p in members) != 1
                or not any(p == portion for p in members)):
            return None, 'EXACT_SOURCE_CONTEXT_AND_MEASURE_MEMBERSHIP_REQUIRED'
        shape, en, tr = CONTEXT_LABELS[lower, food['description']]
        context = {'sourceContextFingerprint': fingerprint(food), 'sourceContextFdcId': food['fdcId'],
                   'sourceContextDescription': food['description']}
    elif interpreted := semantics.interpretation(portion, food):
        shape, en, tr = 'SERVING', interpreted['en'], interpreted['tr']
        context = {key: value for key, value in interpreted.items() if key not in ('en', 'tr')}
    else:
        return None, 'QUALIFIER_OR_SOURCE_DIMENSIONS_REQUIRE_REVIEW'
    labels = {'EN': f'{number_label(amount)} {en} ({number_label(grams)} g)',
              'TR': f'{number_label(amount)} {tr} ({number_label(grams)} g)'}
    if not valid_labels(labels):
        return None, 'COMPLETE_BOUNDED_EN_TR_LABELS_REQUIRED_NO_TRUNCATION'
    return {'sourcePortionId': portion['id'],
            'sourcePortionFingerprint': fingerprint(portion),
            'sourceDescriptor': descriptor, 'unitType': shape, 'quantity': amount,
            'gramWeight': grams, 'mlVolume': None,
            'labels': labels,
            'defaultOption': False, 'qualityStatus': 'NEEDS_REVIEW', 'userVisible': False,
            'sourceSpecificNotUniversalMeasure': True, **context}, None

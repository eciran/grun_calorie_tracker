"""Narrow SR source-measure interpretations for offline, unapproved review only."""
import hashlib
import json
import math


CONTRACT = 'USDA_SR_EDIBLE_SOURCE_MEASURE_REVIEW_V1'
DOCUMENTS = [
    {'url': 'https://www.ars.usda.gov/ARSUserFiles/80400525/Data/SR/SR28/sr28_doc.pdf',
     'section': 'Weights and Measures, printed page26; edible/refuse, page6; poultry, page117',
     'meaning': 'SR measure grams describe edible food; purchased/raw references do not replace the final edible gram weight'},
    {'url': 'https://www.ars.usda.gov/ARSUserFiles/80400525/Data/SR-Legacy/SR-Legacy_Doc.pdf',
     'section': 'Section4.9, Weight file, printed pages15-16',
     'meaning': 'Measure count, complete description and gram weight are separate source facts'},
]
# Labels describe ONLY the exact selected source profile/measure; no global noun rule.
# All weights/counts still come from the complete source body, never from this table.
RULES = {
    169098: ("Orange juice, raw (Includes foods for USDA's Food Distribution Program)", {
        'fruit yields': ('juice yielded by source fruit', 'kaynak meyveden elde edilen portakal suyu', 'YIELD_FINAL_EDIBLE')}),
    170850: ('Cheese, provolone', {
        'package (6 oz)': ('source-specific package (6 oz)', 'kaynak paketi (6 ons)', 'SOURCE_PACKAGE'),
        'cubic inch': ('cubic inch source measure', 'kübik inçlik kaynak ölçüsü', 'SOLID_GEOMETRY')}),
    173410: ('Butter, salted', {
        'stick': ('source-specific butter stick', 'kaynak tereyağı çubuğu', 'SOURCE_PACKAGE')}),
    173430: ('Butter, without salt', {
        'stick': ('source-specific butter stick', 'kaynak tereyağı çubuğu', 'SOURCE_PACKAGE')}),
    174491: ('Chicken, liver, all classes, cooked, pan-fried', {
        'liver': ('pan-fried chicken liver (edible part)', 'tavada pişmiş tavuk ciğeri (yenebilir kısım)', 'EDIBLE_ENTITY')}),
    171481: ('Turkey, whole, meat only, cooked, roasted', {
        'bird': ('roasted whole-turkey yield (meat only)', 'bütün hindiden fırınlanmış yalnızca et', 'EDIBLE_ENTITY')}),
    171109: ('Chicken, cornish game hens, meat only, cooked, roasted', {
        'bird': ('roasted Cornish hen yield (meat only)', 'Cornish piliçten fırınlanmış yalnızca et', 'EDIBLE_ENTITY'),
        'bird whole': ('whole Cornish hen yield (roasted meat only)', 'bütün Cornish piliçten fırınlanmış yalnızca et', 'EDIBLE_ENTITY')}),
    171054: ('Chicken, broilers or fryers, meat only, cooked, roasted', {
        'unit (yield from 1 lb ready-to-cook chicken)': ('roasted meat yield from 1 lb ready-to-cook chicken',
            'pişirmeye hazır 1 libre tavuktan pişmiş et verimi', 'YIELD_FINAL_EDIBLE')}),
    172380: ('Chicken, broilers or fryers, leg, meat only, cooked, roasted', {
        'drumstick bone and skin': ('roasted meat from source drumstick (bone/skin excluded)',
            'kaynak bagetten fırınlanmış et (kemik ve deri hariç)', 'EDIBLE_PART'),
        'thigh bone and skin': ('roasted meat from source thigh (bone/skin excluded)',
            'kaynak üst buttan fırınlanmış et (kemik ve deri hariç)', 'EDIBLE_PART'),
        'back bone and skin': ('roasted meat from source back (bone/skin excluded)',
            'kaynak sırttan fırınlanmış et (kemik ve deri hariç)', 'EDIBLE_PART'),
        'leg (sum of drumstick+thigh+back meat only)': ('roasted leg meat (drumstick+thigh+back, meat only)',
            'fırınlanmış bacak eti (baget+üst but+sırt, yalnızca et)', 'EDIBLE_PART')}),
    173691: ('Fish, salmon, sockeye, raw', {
        'oz, boneless': ('oz source measure (raw, boneless)', 'kaynak ons ölçüsü (çiğ, kemiksiz)', 'EDIBLE_PART')}),
    174204: ('Crustaceans, crab, blue, raw', {
        'crab': ('raw blue crab yield (edible part)', 'çiğ mavi yengeçten yenebilir kısım', 'EDIBLE_ENTITY')}),
    174005: ('Beef, loin, tenderloin roast, boneless, separable lean only, trimmed to 0" fat, choice, cooked, roasted', {
        'roast': ('source roast (cooked boneless separable lean only)',
            'kaynak rostodan pişmiş ayrılabilir et (kemiksiz)', 'EDIBLE_ENTITY')}),
    174006: ('Beef, loin, tenderloin roast, separable lean only, boneless, trimmed to 0" fat, select, cooked, roasted', {
        'roast': ('source roast (cooked boneless separable lean only)',
            'kaynak rostodan pişmiş ayrılabilir et (kemiksiz)', 'EDIBLE_ENTITY')}),
    168233: ('Pork, fresh, loin, whole, separable lean only, cooked, roasted', {
        'chop, excluding refuse (yield from 1 raw chop, with refuse, weighing 151 g)':
            ('cooked lean yield from 151 g raw chop with refuse',
             '151 g çiğ pirzoladan pişmiş ayrılabilir et (ayıklama sonrası)', 'YIELD_FINAL_EDIBLE')}),
    168250: ('Pork, fresh, loin, tenderloin, separable lean only, cooked, roasted', {
        'roast': ('source roast (cooked separable lean only)', 'kaynak rostodan pişmiş ayrılabilir et', 'EDIBLE_ENTITY'),
        'piece, cooked, excluding refuse (yield from 1 lb raw meat with refuse)':
            ('cooked edible lean yield from 1 lb raw meat with refuse',
             '1 libre çiğ etten pişmiş ayrılabilir et (ayıklama sonrası)', 'YIELD_FINAL_EDIBLE')}),
    168237: ('Pork, fresh, loin, blade (roasts), bone-in, separable lean only, cooked, roasted', {
        'roast': ('source bone-in roast yield (cooked edible lean only)',
            'kaynak kemikli rostodan pişmiş ayrılabilir et', 'EDIBLE_ENTITY')}),
    169195: ('Pork, fresh, loin, blade (roasts), boneless, separable lean only, cooked, roasted', {
        'roast': ('source boneless roast (cooked separable lean only)',
            'kaynak kemiksiz rostodan pişmiş ayrılabilir et', 'EDIBLE_ENTITY')}),
    173709: ("Fish, tuna, light, canned in water, drained solids (Includes foods for USDA's Food Distribution Program)", {
        'can': ('source can contents (drained)', 'kaynak kutudan süzülmüş içerik', 'SOURCE_PACKAGE'),
        'can (12.5 oz), drained': ('source 12.5 oz can contents (drained)',
            'kaynak 12.5 onsluk kutudan süzülmüş içerik', 'SOURCE_PACKAGE')}),
    171506: ('Turkey, Ground, cooked', {
        'patty (4 oz, raw) (yield after cooking)': ('cooked patty yield from 4 oz raw portion',
            '4 ons çiğ köfteden pişmiş et verimi', 'YIELD_FINAL_EDIBLE'),
        'unit, yield from 1 lb raw': ('cooked meat yield from 1 lb raw portion',
            '1 libre çiğ etten pişmiş et verimi', 'YIELD_FINAL_EDIBLE')}),
    168462: ('Spinach, raw', {
        'package (10 oz)': ('source-specific package (10 oz, raw edible contents)',
            'kaynak 10 onsluk paket (çiğ yenebilir içerik)', 'SOURCE_PACKAGE')}),
    169473: ('Beef, ground, 70% lean meat / 30% fat, crumbles, cooked, pan-browned', {
        'portion (yield from 1/2 lb raw meat )': ('pan-browned crumbles yielded by 1/2 lb raw meat',
            '1/2 libre çiğ etten tavada pişmiş kıyma verimi', 'YIELD_FINAL_EDIBLE')}),
    172544: ('Lamb, ground, cooked, broiled', {
        'unit, cooked (yield from 1 lb raw meat)': ('broiled meat yield from 1 lb raw portion',
            '1 libre çiğ etten ızgara et verimi', 'YIELD_FINAL_EDIBLE')}),
    167822: ('Pork, fresh, loin, blade (chops or roasts), bone-in, separable lean and fat, raw', {
        'chop': ('raw chop yield (edible lean and fat, bone excluded)',
            'çiğ pirzoladan yenebilir et ve yağ (kemik hariç)', 'EDIBLE_ENTITY')}),
    168380: ('Pork, fresh, blade, (chops), boneless, separable lean and fat, cooked, broiled', {
        'chop': ('broiled boneless chop (edible lean and fat)',
            'ızgara kemiksiz pirzola (yenebilir et ve yağ)', 'EDIBLE_ENTITY')}),
    167904: ('Pork, fresh, loin, tenderloin, separable lean and fat, cooked, broiled', {
        'chop, excluding refuse (yield from 1 raw chop, with refuse, weighing 113 g)':
            ('broiled edible lean/fat yield from 113 g raw chop with refuse',
             '113 g çiğ pirzoladan ızgara et ve yağ (ayıklama sonrası)', 'YIELD_FINAL_EDIBLE')}),
    175139: ('Fish, sardine, Atlantic, canned in oil, drained solids with bone', {
        'can (3.75 oz)': ('source 3.75 oz can contents (drained, including bone)',
            'kaynak 3.75 onsluk kutu (süzülmüş, kılçığı dahil)', 'SOURCE_PACKAGE'),
        'sardines': ('sardines (drained, including bone)', 'sardalya (süzülmüş, kılçığı dahil)', 'EDIBLE_ENTITY'),
        'cubic inch': ('cubic inch source measure (drained, including bone)',
            'kübik inçlik kaynak ölçüsü (süzülmüş, kılçığı dahil)', 'SOLID_GEOMETRY')}),
    173420: ('Cheese, feta', {
        'cubic inch': ('cubic inch source measure', 'kübik inçlik kaynak ölçüsü', 'SOLID_GEOMETRY')}),
    169209: ('Asparagus, frozen, cooked, boiled, drained, without salt', {
        'package (10 oz) yields': ('boiled drained yield from source 10 oz frozen package',
            'kaynak 10 ons donmuş paketten haşlanmış süzülmüş verim', 'YIELD_FINAL_EDIBLE')}),
    170380: ('Broccoli, frozen, spears, cooked, boiled, drained, without salt', {
        'package (10 oz) yields': ('boiled drained yield from source 10 oz frozen package',
            'kaynak 10 ons donmuş paketten haşlanmış süzülmüş verim', 'YIELD_FINAL_EDIBLE')}),
    169288: ('Spinach, frozen, chopped or leaf, cooked, boiled, drained, without salt', {
        'package (10 oz) yields': ('boiled drained yield from source 10 oz frozen package',
            'kaynak 10 ons donmuş paketten haşlanmış süzülmüş verim', 'YIELD_FINAL_EDIBLE')}),
    167755: ('Raspberries, raw', {
        'pint as purchased, yields': ('edible yield from source purchased pint (not ml)',
            'satın alınan kaynak pint ölçüsünden yenebilir verim', 'YIELD_FINAL_EDIBLE')}),
    167762: ('Strawberries, raw', {
        'pint as purchased, yields': ('edible yield from source purchased pint (not ml)',
            'satın alınan kaynak pint ölçüsünden yenebilir verim', 'YIELD_FINAL_EDIBLE')}),
    170904: ('Kefir, lowfat, plain, LIFEWAY', {
        'bottle': ('source-specific bottle contents (mass only)',
            'kaynak şişe içeriği (yalnızca gram)', 'SOURCE_PACKAGE')}),
    173708: ('Fish, tuna, light, canned in oil, drained solids', {
        'can': ('source can contents (drained)', 'kaynak kutudan süzülmüş içerik', 'SOURCE_PACKAGE'),
        'can (12.5 oz), drained': ('source 12.5 oz can contents (drained)',
            'kaynak 12.5 onsluk kutudan süzülmüş içerik', 'SOURCE_PACKAGE')}),
}


def digest(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()).hexdigest()


def interpretation(portion, food):
    if not isinstance(food, dict) or type(food.get('fdcId')) is not int or food.get('dataType') != 'SR Legacy':
        return None
    rule = RULES.get(food['fdcId'])
    if rule is None or food.get('description') != rule[0]:
        return None
    unit = portion.get('measureUnit', {})
    modifier, description = portion.get('modifier'), portion.get('portionDescription')
    if (not isinstance(unit, dict) or unit.get('name') not in (None, '', 'undetermined')
            or any(value is not None and not isinstance(value, str) for value in (modifier, description))
            or (modifier and description and modifier.strip() != description.strip())
            or portion.get('footnote') not in (None, '')):
        return None
    raw = (modifier or description or '').strip().lower()
    labels = rule[1].get(raw)
    measures = food.get('foodPortions')
    if (labels is None or not isinstance(measures, list)
            or sum(isinstance(p, dict) and p.get('id') == portion.get('id') for p in measures) != 1
            or not any(isinstance(p, dict) and digest(p) == digest(portion) for p in measures)):
        return None
    en, tr, family = labels
    return {'en': en, 'tr': tr, 'sourceInterpretationFamily': family,
            'sourceInterpretationContract': CONTRACT, 'sourceWeightMeaning': 'FINAL_EDIBLE_SOURCE_GRAMS',
            'sourceDocumentation': DOCUMENTS, 'sourceContextFingerprint': digest(food),
            'sourceContextFdcId': food['fdcId'], 'sourceContextDescription': food['description']}


def reference_disposition(portion):
    """Product policy for generated suggestions, not a claim that the source is invalid."""
    unit = portion.get('measureUnit', {})
    modifier, description = portion.get('modifier'), portion.get('portionDescription')
    if (type(portion.get('id')) is not int or portion['id'] <= 0 or not isinstance(unit, dict)
            or any(type(portion.get(k)) not in (int, float) or not math.isfinite(portion[k]) or portion[k] <= 0
                   for k in ('amount', 'gramWeight'))
            or any(v is not None and not isinstance(v, str) for v in (unit.get('name'), modifier, description))
            or (modifier and description and modifier.strip() != description.strip())
            or portion.get('footnote') not in (None, '')):
        return None
    name = (unit.get('name') or '').strip().lower()
    raw = (modifier or description or '').strip().lower()
    descriptor = raw if name in ('', 'undetermined') else name + (' ' + raw if raw else '')
    if descriptor not in ('racc', 'nlea serving'):
        return None
    return {'policy': 'NO_AUTOMATIC_REGULATORY_REFERENCE_HOUSEHOLD_SUGGESTIONS_V1',
            'disposition': 'REFERENCE_ONLY_KEEP_SOURCE_NOT_A_NEW_HOUSEHOLD_OPTION',
            'householdSuggestionAllowed': False, 'deleteSourceMeasure': False,
            'sourcePortionFingerprint': digest(portion), 'sourceDescriptor': descriptor,
            'reason': 'Retain the labelled source reference; do not silently rename it as a cup, piece or recommended portion',
            'contextUrl': 'https://www.fda.gov/food/nutrition-facts-label/serving-size-nutrition-facts-label'}

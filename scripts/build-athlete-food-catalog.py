#!/usr/bin/env python3
"""Build a bilingual athlete-staples catalog from the official USDA SR Legacy CSV release."""

import argparse
import csv
import json
import re
from collections import Counter
from pathlib import Path


NUTRIENTS = {
    "1008": "calories", "1003": "protein", "1004": "fat", "1005": "carbs",
    "1079": "fiber", "2000": "sugar", "1093": "sodium", "1092": "potassium",
    "1253": "cholesterol", "1087": "calcium", "1089": "iron", "1090": "magnesium",
    "1095": "zinc", "1106": "vitamin_a", "1162": "vitamin_c", "1114": "vitamin_d",
    "1109": "vitamin_e", "1178": "vitamin_b12",
}

# Quotas deliberately emphasize protein sources and training-friendly carbohydrate staples.
RULES = [
    ("EGGS_DAIRY", "Dairy and Egg Products", 15,
     r"^(Egg|Eggs|Milk|Yogurt|Cheese, (cottage|ricotta|mozzarella|feta)|Kefir|Buttermilk)",
     r"infant|dessert|ice cream|sandwich|substitute|salad dressing|imitation|strawberry|chocolate|vanilla|shake|eggnog|frozen|sugared|duck|goose|quail|LIFEWAY|DANNON|CHOBANI|Food Distribution"),
    ("POULTRY", "Poultry Products", 20,
     r"^(Chicken|Turkey), .*(breast|thigh|drumstick|meat|ground)",
     r"skin|giblets|liver|heart|neck|back|wing|breaded|battered|fast food|mechanically"),
    ("FISH_SEAFOOD", "Finfish and Shellfish Products", 22,
     r"^(Fish, (salmon|tuna|cod|haddock|trout|tilapia|halibut|mackerel|sardine|pollock|swordfish)|Crustaceans, (shrimp|crab|lobster)|Mollusks, (mussel|clam|oyster))",
     r"breaded|battered|imitation|salad|spread|dip"),
    ("LEAN_MEAT", "Beef Products", 12,
     r"^Beef, .*(ground|top sirloin|tenderloin|top round|eye of round|flank|strip steak)",
     r"separable fat|liver|brain|kidney|heart|tripe|mechanically|corned|cured"),
    ("PORK", "Pork Products", 4, r"^Pork, .*(tenderloin|loin|ground|chop)",
     r"separable fat|cured|ham|bacon|mechanically"),
    ("LEGUMES", "Legumes and Legume Products", 25,
     r"^(Beans|Lentils|Chickpeas|Peas, split|Soybeans|Edamame|Tofu|Tempeh|Hummus)",
     r"babyfood|chips|snack|flour|with meat|soup"),
    ("GRAINS", "Cereal Grains and Pasta", 25,
     r"^(Rice|Quinoa|Bulgur|Oats|Barley|Buckwheat|Millet|Couscous|Pasta|Spaghetti|Macaroni|Noodles|Cornmeal)",
     r"babyfood|dessert|mix|restaurant|with sauce|with meat|Food Distribution"),
    ("NUTS_SEEDS", "Nut and Seed Products", 18,
     r"^(Nuts, (almonds|walnuts|pistachio|cashew|peanuts)|Seeds, (chia|flaxseed|pumpkin|sunflower|sesame)|Peanut butter|Tahini)",
     r"candy|chocolate|coated|sweetened|with salt added"),
    ("VEGETABLES", "Vegetables and Vegetable Products", 34,
     r"^(Broccoli|Spinach|Kale|Cauliflower|Carrots|Asparagus|Peppers|Tomatoes|Cucumber|Zucchini|Squash|Sweet potato|Potatoes|Beets|Brussels sprouts|Green beans|Mushrooms)",
     r"babyfood|soup|sauce|chips|with meat|au gratin|salad with"),
    ("FRUITS", "Fruits and Fruit Juices", 25,
     r"^(Bananas|Apples|Oranges|Tangerines|Grapefruit|Strawberries|Blueberries|Raspberries|Blackberries|Pineapple|Mango|Kiwifruit|Pears|Peaches|Plums|Cherries|Watermelon|Melons|Dates|Raisins|Avocados)",
     r"juice|nectar|pie|syrup|babyfood|canned, heavy syrup|cooked|microwave|Food Distribution|ZESPRI"),
]

TR_WORDS = [
    (r"\bEggs?\b", "Yumurta"), (r"\bwhite\b", "beyazı"), (r"\byolk\b", "sarısı"),
    (r"\bwhole\b", "tam"), (r"\bMilk\b", "Süt"), (r"\bYogurt\b", "Yoğurt"),
    (r"\bCheese\b", "Peynir"), (r"\bcottage\b", "lor"), (r"\bChicken\b", "Tavuk"),
    (r"\bTurkey\b", "Hindi"), (r"\bbreast\b", "göğüs"), (r"\bthigh\b", "but"),
    (r"\bmeat\b", "et"), (r"\bground\b", "kıyma"), (r"\bBeef\b", "Dana eti"),
    (r"\bPork\b", "Domuz eti"), (r"\bSalmon\b", "Somon"), (r"\bTuna\b", "Ton balığı"),
    (r"\bCod\b", "Morina"), (r"\bTrout\b", "Alabalık"), (r"\bShrimp\b", "Karides"),
    (r"\bSardines?\b", "Sardalya"), (r"\bBeans\b", "Fasulye"),
    (r"\bLentils\b", "Mercimek"), (r"\bChickpeas\b", "Nohut"), (r"\bSoybeans\b", "Soya fasulyesi"),
    (r"\bRice\b", "Pirinç"), (r"\bOats\b", "Yulaf"), (r"\bBarley\b", "Arpa"),
    (r"\bPasta\b", "Makarna"), (r"\bAlmonds\b", "Badem"), (r"\bWalnuts\b", "Ceviz"),
    (r"\bPeanuts\b", "Yer fıstığı"), (r"\braw\b", "çiğ"), (r"\bcooked\b", "pişmiş"),
    (r"\bboiled\b", "haşlanmış"), (r"\broasted\b", "kavrulmuş"), (r"\bgrilled\b", "ızgara"),
    (r"\bwithout salt\b", "tuzsuz"), (r"\bwith salt\b", "tuzlu"),
    (r"\bfresh\b", "taze"), (r"\bfried\b", "kızartılmış"), (r"\bpoached\b", "poşe"),
    (r"\bdried\b", "kurutulmuş"), (r"\bfrozen\b", "dondurulmuş"),
    (r"\bpasteurized\b", "pastörize"), (r"\bGreek\b", "süzme"),
    (r"\blowfat\b", "az yağlı"), (r"\bnonfat\b", "yağsız"),
    (r"\bskinless\b", "derisiz"), (r"\bskin\b", "deri"), (r"\bflesh\b", "iç kısım"),
    (r"\bPotatoes\b", "Patates"), (r"\bSweet potato\b", "Tatlı patates"),
    (r"\bBroccoli\b", "Brokoli"), (r"\bSpinach\b", "Ispanak"),
    (r"\bCauliflower\b", "Karnabahar"), (r"\bCarrots\b", "Havuç"),
    (r"\bTomatoes\b", "Domates"), (r"\bApples\b", "Elma"),
    (r"\bBananas\b", "Muz"), (r"\bOranges\b", "Portakal"),
    (r"\bStrawberries\b", "Çilek"), (r"\bBlueberries\b", "Yaban mersini"),
    (r"\bPears\b", "Armut"), (r"\bPeaches\b", "Şeftali"),
    (r"\bTurkey\b", "Hindi"), (r"\bFish\b", "Balık"), (r"\bCrustaceans\b", "Kabuklu deniz ürünü"),
    (r"\bMollusks\b", "Yumuşakça"), (r"\bPoultry\b", "Kümes hayvanı"),
    (r"\bdark meat\b", "koyu et"), (r"\blight meat\b", "beyaz et"),
    (r"\bmeat only\b", "yalnız et"), (r"\bdry heat\b", "kuru ısıda"),
    (r"\bwith added solution\b", "marine edilmiş"), (r"\bwith added salt\b", "tuzlu"),
    (r"\bwithout added salt\b", "tuzsuz"), (r"\bdrained\b", "süzülmüş"),
    (r"\bsolids\b", "katı kısım"), (r"\bincludes skin\b", "kabuklu"),
    (r"\bmade with\b", "ile yapılmış"), (r"\bmade without\b", "olmadan yapılmış"),
    (r"\benriched\b", "zenginleştirilmiş"), (r"\bunenriched\b", "zenginleştirilmemiş"),
    (r"\bprotein-fortified\b", "proteinle zenginleştirilmiş"),
    (r"\bgluten-free\b", "glütensiz"), (r"\bhomemade\b", "ev yapımı"),
    (r"\bmedium-grain\b", "orta taneli"), (r"\blong-grain\b", "uzun taneli"),
    (r"\bwhite\b", "beyaz"), (r"\bbrown\b", "esmer"), (r"\bonly\b", "yalnız"),
    (r"\bwithout\b", "olmadan"), (r"\bwith\b", "ile"), (r"\band\b", "ve"),
    (r"\bincludes\b", "içerir"), (r"\bsummer\b", "yaz"), (r"\bwinter\b", "kış"),
    (r"\bSquash\b", "Kabak"), (r"\bzucchini\b", "sakız kabağı"),
    (r"\bAsparagus\b", "Kuşkonmaz"), (r"\bBeets\b", "Pancar"),
    (r"\bMushrooms\b", "Mantar"), (r"\bPeppers\b", "Biber"),
    (r"\bBrussels sprouts\b", "Brüksel lahanası"), (r"\bKale\b", "Kara lahana"),
    (r"\bMangos\b", "Mango"), (r"\bRaspberries\b", "Ahududu"),
    (r"\bMelons\b", "Kavun"), (r"\bAvocados\b", "Avokado"),
    (r"\bPineapple\b", "Ananas"), (r"\bTangerines\b", "Mandalina"),
    (r"\bBuckwheat groats\b", "Karabuğday tanesi"), (r"\bMillet\b", "Darı"),
    (r"\bRice noodles\b", "Pirinç eriştesi"), (r"\bNoodles\b", "Erişte"),
    (r"\bSpaghetti\b", "Spagetti"), (r"\bMacaroni\b", "Makarna"),
    (r"\bSoybeans\b", "Soya fasulyesi"), (r"\bPeas\b", "Bezelye"),
    (r"\bnavy\b", "kuru beyaz"), (r"\bpinto\b", "barbunya tipi"),
    (r"\bkidney\b", "kırmızı fasulye"), (r"\bblack\b", "siyah"),
    (r"\badzuki\b", "adzuki"), (r"\bmature seeds?\b", "kuru tane"),
    (r"\bNuts\b", "Kuruyemiş"), (r"\bcashew nuts\b", "kaju"),
    (r"\bpistachio nuts\b", "Antep fıstığı"), (r"\bSeeds\b", "Tohum"),
    (r"\bsunflower seed\b", "ay çekirdeği"), (r"\bsesame\b", "susam"),
    (r"\bpumpkin\b", "kabak"), (r"\btoasted\b", "kavrulmuş"),
    (r"\bdry roasted\b", "kuru kavrulmuş"), (r"\boil roasted\b", "yağda kavrulmuş"),
    (r"\bloin\b", "sırt"), (r"\btenderloin\b", "bonfile"),
    (r"\btop sirloin\b", "kontrfile"), (r"\btop round\b", "kontrnuar"),
    (r"\beye of round\b", "nuar"), (r"\bsteak\b", "biftek"),
    (r"\broast\b", "rosto"), (r"\bblade\b", "kol"), (r"\bbreast\b", "göğüs"),
    (r"\bdrumstick\b", "baget"), (r"\bleg\b", "but"), (r"\bretail parts\b", "parça"),
    (r"\bchoice\b", "üst kalite"), (r"\bselect\b", "seçme kalite"),
    (r"\ball grades\b", "tüm kalite sınıfları"),
    (r"\bsheep\b", "koyun"), (r"\bfluid\b", "sıvı"), (r"\bplain\b", "sade"),
    (r"\blow fat\b", "az yağlı"), (r"\blow sodium\b", "düşük sodyumlu"),
    (r"\bbuttermilk\b", "yayık altı sütü"), (r"\bdark\b", "koyu"), (r"\blight\b", "beyaz"),
    (r"\broasting\b", "fırınlık"), (r"\ball classes\b", "tüm sınıflar"),
    (r"\bbroilers or fryers\b", "etlik"), (r"\bcornish game hens\b", "Cornish tavuk"),
    (r"\bboneless\b", "kemiksiz"), (r"\bbone-in\b", "kemikli"),
    (r"\badded solution\b", "marine edilmiş"), (r"\bdry\b", "kuru"),
    (r"\bkernels\b", "iç"), (r"\bvarieties\b", "çeşitler"), (r"\bvariety\b", "çeşit"),
    (r"\bparboiled\b", "ön haşlanmış"), (r"\brefrigerated\b", "soğutulmuş"),
    (r"\bflour\b", "un"), (r"\bvegetable\b", "sebzeli"), (r"\bmashed\b", "püre"),
    (r"\bchopped\b", "doğranmış"), (r"\bspears\b", "dallar"),
    (r"\bleaf\b", "yaprak"), (r"\bleaves\b", "yaprakları"),
    (r"\bsteamed\b", "buharda pişmiş"), (r"\bbaked\b", "fırınlanmış"),
    (r"\bpeel\b", "kabuk"), (r"\bcommercial\b", "ticari"),
    (r"\bextra sweet\b", "çok tatlı"), (r"\btraditional\b", "geleneksel"),
    (r"\bmixed species\b", "karışık tür"), (r"\ball types\b", "tüm türler"),
    (r"\bseed kernels\b", "tohum içi"), (r"\bseed\b", "tohum"),
    (r"\bunblanched\b", "kabuklu"), (r"\blightly salted\b", "az tuzlu"),
    (r"\bhoney roasted\b", "ballı kavrulmuş"), (r"\bfrom\b", "üretim"),
    (r"\bstone ground\b", "taş değirmende öğütülmüş"), (r"\bdecorticated\b", "kabuksuz"),
    (r"\bcashew\b", "kaju"), (r"\bpistachio\b", "Antep fıstığı"),
    (r"\bbutter\b", "ezme"), (r"\boil\b", "yağda"), (r"\badded\b", ""),
]


def state(description: str) -> str:
    value = description.lower()
    for needle, result in (("raw", "RAW"), ("boiled", "BOILED"), ("grilled", "GRILLED"),
                           ("roasted", "ROASTED"), ("baked", "BAKED"), ("cooked", "COOKED"),
                           ("canned", "PREPARED")):
        if needle in value:
            return result
    return "UNSPECIFIED"


def tr_name(description: str) -> str:
    result = description
    for pattern, replacement in TR_WORDS:
        result = re.sub(pattern, replacement, result, flags=re.IGNORECASE)
    return result.replace(",", " -")


def clean_english(description: str) -> str:
    value = re.sub(r"\s*\(Includes foods for USDA's Food Distribution Program\)", "", description, flags=re.I)
    value = re.sub(r",?\s*(choice|select|all grades)$", "", value, flags=re.I)
    value = re.sub(r",?\s*separable lean only", "", value, flags=re.I)
    value = re.sub(r",?\s*trimmed to [^,]+ fat", "", value, flags=re.I)
    value = re.sub(r"\bboneless\b", "boneless", value, flags=re.I)
    value = re.sub(r"\bbone-in\b", "bone-in", value, flags=re.I)
    value = re.sub(r"\s+", " ", value).strip(" ,")
    return value


def serving_options(cohort: str, english: str) -> list[dict]:
    lower = english.lower()
    if "egg, white" in lower:
        specs = [("PIECE", 1, 33, "1 large egg white", "1 büyük yumurta beyazı"),
                 ("PIECE", 3, 99, "3 large egg whites", "3 büyük yumurta beyazı")]
    elif "egg, yolk" in lower:
        specs = [("PIECE", 1, 17, "1 large egg yolk", "1 büyük yumurta sarısı")]
    elif lower.startswith("egg,"):
        specs = [("PIECE", 1, 50, "1 large egg", "1 büyük yumurta"),
                 ("PIECE", 2, 100, "2 large eggs", "2 büyük yumurta")]
    elif cohort == "EGGS_DAIRY":
        specs = [("CUP", 1, 200, "1 cup", "1 su bardağı"),
                 ("PORTION", 1, 150, "1 portion", "1 porsiyon")]
    elif cohort in {"POULTRY", "FISH_SEAFOOD", "LEAN_MEAT", "PORK"}:
        specs = [("PORTION", 1, 150, "1 portion", "1 porsiyon"),
                 ("PORTION", 0.5, 75, "1/2 portion", "1/2 porsiyon")]
    elif cohort == "LEGUMES":
        specs = [("CUP", 1, 170, "1 cup cooked", "1 su bardağı pişmiş"),
                 ("PORTION", 1, 150, "1 portion", "1 porsiyon")]
    elif cohort == "GRAINS":
        cooked = any(x in lower for x in ("cooked", "noodles", "pasta", "spaghetti", "macaroni"))
        grams = 150 if cooked else 50
        specs = [("PORTION", 1, grams, "1 portion", "1 porsiyon"),
                 ("CUP", 1, 160 if cooked else 90, "1 cup", "1 su bardağı")]
    elif cohort == "NUTS_SEEDS":
        specs = [("PORTION", 1, 30, "1 handful", "1 avuç"),
                 ("TABLESPOON", 1, 16, "1 tablespoon", "1 yemek kaşığı")]
    elif cohort == "VEGETABLES":
        specs = [("PORTION", 1, 150, "1 portion", "1 porsiyon"),
                 ("CUP", 1, 100, "1 cup", "1 su bardağı")]
    else:
        piece_weights = {"banana": 118, "apple": 182, "orange": 140, "pear": 178,
                         "peach": 150, "kiwifruit": 69, "tangerine": 88, "avocado": 150}
        grams = next((weight for key, weight in piece_weights.items() if key in lower), 150)
        specs = [("PORTION", 1, 150, "1 portion", "1 porsiyon")]
        if any(key in lower for key in piece_weights):
            specs.insert(0, ("PIECE", 1, grams, "1 medium piece", "1 orta boy adet"))
        else:
            specs.append(("CUP", 1, 150, "1 cup", "1 su bardağı"))
    return [{"label": en, "unitType": unit, "quantity": quantity, "gramWeight": grams,
             "mlVolume": None, "defaultOption": index == 0, "labels": {"EN": en, "TR": tr}}
            for index, (unit, quantity, grams, en, tr) in enumerate(specs)]


def rank(description: str) -> tuple:
    lower = description.lower()
    preferred = sum(term in lower for term in ("raw", "cooked", "boiled", "roasted", "grilled", "without salt", "lean only"))
    penalties = lower.count(",") + len(description) / 100
    return (-preferred, penalties, description)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--usda-dir", required=True, type=Path)
    parser.add_argument("--existing", type=Path, default=Path("outputs/tr-generic-catalog-v2.csv"))
    parser.add_argument("--output", type=Path, default=Path("outputs/athlete-food-catalog-v1.csv"))
    parser.add_argument("--report", type=Path, default=Path("outputs/athlete-food-catalog-v1.json"))
    args = parser.parse_args()

    def locate(name: str) -> Path:
        matches = list(args.usda_dir.rglob(name))
        if len(matches) != 1:
            raise SystemExit(f"Expected one {name}, found {len(matches)}")
        return matches[0]

    existing = set()
    if args.existing.exists():
        with args.existing.open(encoding="utf-8-sig", newline="") as handle:
            existing = {row["source_key"] for row in csv.DictReader(handle)}

    with locate("food_category.csv").open(encoding="utf-8-sig", newline="") as handle:
        categories = {row["id"]: row["description"] for row in csv.DictReader(handle)}
    with locate("food.csv").open(encoding="utf-8-sig", newline="") as handle:
        foods = list(csv.DictReader(handle))

    selected = []
    used = set()
    counts = Counter()
    for cohort, category, quota, include, exclude in RULES:
        candidates = [f for f in foods if categories.get(f["food_category_id"]) == category
                      and re.search(include, f["description"], re.I)
                      and not re.search(exclude, f["description"], re.I)
                      and f'USDA_FOODDATA:fdc:{f["fdc_id"]}' not in existing]
        candidates.sort(key=lambda f: rank(f["description"]))
        for food in candidates:
            if food["fdc_id"] in used:
                continue
            selected.append((cohort, food))
            used.add(food["fdc_id"])
            counts[cohort] += 1
            if counts[cohort] == quota:
                break
        if counts[cohort] != quota:
            raise SystemExit(f"{cohort}: expected {quota}, found {counts[cohort]}")

    wanted_ids = used
    nutrition = {fdc_id: {} for fdc_id in wanted_ids}
    with locate("food_nutrient.csv").open(encoding="utf-8-sig", newline="") as handle:
        for row in csv.DictReader(handle):
            if row["fdc_id"] in wanted_ids and row["nutrient_id"] in NUTRIENTS:
                nutrition[row["fdc_id"]][NUTRIENTS[row["nutrient_id"]]] = row["amount"]

    fields = ["catalog_type", "data_source", "source_key", "name", "display_name", "short_display_name",
              "calories", "protein", "fat", "carbs", "fiber", "sugar", "sodium", "potassium",
              "cholesterol", "calcium", "iron", "magnesium", "zinc", "vitamin_a", "vitamin_c",
              "vitamin_d", "vitamin_e", "vitamin_b12", "serving_size_grams", "serving_unit",
              "market_region", "preparation_state", "nutrition_basis", "alias_en", "alias_tr", "source_note",
              "display_name_en", "short_display_name_en", "display_name_tr", "short_display_name_tr",
              "serving_options_json"]
    rows = []
    for cohort, food in selected:
        values = nutrition[food["fdc_id"]]
        missing_core = [name for name in ("calories", "protein", "fat", "carbs") if name not in values]
        if missing_core:
            raise SystemExit(f'{food["fdc_id"]} missing core nutrients: {missing_core}')
        english = clean_english(food["description"])
        turkish = tr_name(english)
        row = {field: values.get(field, "") for field in fields}
        row.update({"catalog_type": "GENERIC_INGREDIENT", "data_source": "USDA_FOODDATA",
                    "source_key": f'USDA_FOODDATA:fdc:{food["fdc_id"]}', "name": english,
                    "display_name": english, "short_display_name": english, "serving_size_grams": "100",
                    "serving_unit": "g", "market_region": "GLOBAL", "preparation_state": state(english),
                    "nutrition_basis": "SOURCE_REPORTED", "alias_en": english.lower(), "alias_tr": turkish.lower(),
                    "source_note": f"USDA FoodData Central; dataType=SR Legacy; release=2018-04; athleteCohort={cohort}",
                    "display_name_en": english, "short_display_name_en": english,
                    "display_name_tr": turkish, "short_display_name_tr": turkish,
                    "serving_options_json": json.dumps(serving_options(cohort, english), ensure_ascii=False)})
        rows.append(row)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields, quoting=csv.QUOTE_ALL)
        writer.writeheader(); writer.writerows(rows)
    residual_english = re.compile(r"\b(raw|fresh|cooked|fried|poached|dried|frozen|pasteurized|Greek|lowfat|nonfat|only|with|without|and|includes|drained|solids|heat|boneless|bone-in|added|broilers|fryers|kernels|varieties|plain|roasting|fluid|classes|refrigerated|flour|mashed|chopped|spears|leaf|leaves|baked|steamed|peel|commercial|unblanched|salted|decorticated)\b", re.I)
    forbidden_product = re.compile(r"sweetened|sugared|strawberry|chocolate|vanilla|shake|eggnog|LIFEWAY|DANNON|CHOBANI", re.I)
    localization_review = sum(bool(residual_english.search(r["display_name_tr"])) for r in rows)
    forbidden_rows = sum(bool(forbidden_product.search(r["display_name_en"])) for r in rows)
    duplicate_en = len(rows) - len({r["display_name_en"].casefold() for r in rows})
    duplicate_tr = len(rows) - len({r["display_name_tr"].casefold() for r in rows})
    serving_invalid = 0
    allowed_units = {"SERVING", "PIECE", "SLICE", "SCOOP", "CUP", "TABLESPOON", "TEASPOON",
                     "CAN", "BOTTLE", "PACKAGE", "BAR", "BOWL", "PLATE", "PORTION"}
    for row in rows:
        options = json.loads(row["serving_options_json"])
        if (not options or sum(bool(o.get("defaultOption")) for o in options) != 1
                or any(o.get("unitType") not in allowed_units or float(o.get("gramWeight") or 0) <= 0 for o in options)):
            serving_invalid += 1
    approved = forbidden_rows == 0 and localization_review == 0 and duplicate_en == 0 and duplicate_tr == 0 and serving_invalid == 0
    report = {"version": "athlete-food-catalog-v1", "sourceRelease": "USDA SR Legacy 2018-04",
              "rowCount": len(rows), "netNewAgainstExisting": len(rows), "categoryCounts": dict(counts),
              "uniqueSourceKeys": len({r["source_key"] for r in rows}), "coreNutritionComplete": True,
              "forbiddenProductRows": forbidden_rows, "turkishLocalizationReviewRows": localization_review,
              "duplicateEnglishDisplayNames": duplicate_en, "duplicateTurkishDisplayNames": duplicate_tr,
              "invalidServingOptionRows": serving_invalid,
              "releaseStatus": "APPROVED" if approved else "CANDIDATE_REVIEW_REQUIRED"}
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()

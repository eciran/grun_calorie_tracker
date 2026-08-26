#!/usr/bin/env python3
"""Curate athlete catalog display names and concrete serving options.

The USDA description remains available in aliases/provenance. User-facing names
are concise catalog labels; generic 100 g / "one portion" options are omitted
because gram entry is already always available in the product UI.
"""

import argparse
import csv
import json
import re
from collections import Counter
from pathlib import Path


FISH = {
    "haddock": ("Haddock", "Mezgit"), "tilapia": ("Tilapia", "Tilapya"),
    "swordfish": ("Swordfish", "Kılıç Balığı"), "shrimp": ("Shrimp", "Karides"),
    "salmon": ("Salmon", "Somon"), "cod": ("Cod", "Morina"),
    "mackerel": ("Mackerel", "Uskumru"), "pollock": ("Pollock", "Kömür Balığı"),
    "mussel": ("Mussel", "Midye"), "crab": ("Crab", "Yengeç"),
    "halibut": ("Halibut", "Pisi Balığı"), "oyster": ("Oyster", "İstiridye"),
    "trout": ("Trout", "Alabalık"),
}

PRODUCE = {
    "potatoes": ("Potato", "Patates"), "sweet potato leaves": ("Sweet Potato Leaves", "Tatlı Patates Yaprağı"),
    "sweet potato": ("Sweet Potato", "Tatlı Patates"), "kale": ("Kale", "Kara Lahana"),
    "carrots": ("Carrot", "Havuç"), "asparagus": ("Asparagus", "Kuşkonmaz"),
    "cauliflower": ("Cauliflower", "Karnabahar"), "brussels sprouts": ("Brussels Sprouts", "Brüksel Lahanası"),
    "peppers": ("Pepper", "Biber"), "squash": ("Squash", "Kabak"),
    "broccoli": ("Broccoli", "Brokoli"), "spinach": ("Spinach", "Ispanak"),
    "beets": ("Beet", "Pancar"), "mushrooms": ("Mushroom", "Mantar"),
    "pears": ("Pear", "Armut"), "mangos": ("Mango", "Mango"), "bananas": ("Banana", "Muz"),
    "blueberries": ("Blueberries", "Yaban Mersini"), "raspberries": ("Raspberries", "Ahududu"),
    "strawberries": ("Strawberries", "Çilek"), "melons": ("Melon", "Kavun"),
    "peaches": ("Peach", "Şeftali"), "kiwifruit": ("Kiwi", "Kivi"),
    "oranges": ("Orange", "Portakal"), "avocados": ("Avocado", "Avokado"),
    "apples": ("Apple", "Elma"), "pineapple": ("Pineapple", "Ananas"),
    "tangerines": ("Mandarin", "Mandalina"), "cherries": ("Sour Cherry", "Vişne"),
    "grapefruit": ("Grapefruit", "Greyfurt"),
}


def cohort(row: dict) -> str:
    match = re.search(r"athleteCohort=([^;]+)", row.get("source_note", ""))
    return match.group(1) if match else ""


def prep(desc: str) -> tuple[str, str]:
    if "fried" in desc: return "Fried", "Sahanda"
    if "omelet" in desc: return "Omelet", "Omlet"
    if "poached" in desc: return "Poached", "Poşe"
    if "grilled" in desc: return "Grilled", "Izgara"
    if any(x in desc for x in ("roasted", "baked", "dry heat")): return "Roasted", "Fırında"
    if "boiled" in desc: return "Boiled", "Haşlanmış"
    if "cooked" in desc: return "Cooked", "Pişmiş"
    if "dried" in desc or ", dry" in desc: return "Dried", "Kurutulmuş"
    if "raw" in desc: return "Raw", "Çiğ"
    return "", ""


def egg_names(desc: str) -> tuple[str, str]:
    if "egg, white" in desc: base = ("Egg White", "Yumurta Beyazı")
    elif "egg, yolk" in desc: base = ("Egg Yolk", "Yumurta Sarısı")
    elif "egg, turkey" in desc: base = ("Turkey Egg", "Hindi Yumurtası")
    else: base = ("Whole Egg", "Tam Yumurta")
    en_state, tr_state = prep(desc)
    if en_state == "Fried": return "Fried Egg", "Sahanda Yumurta"
    if en_state == "Omelet": return "Omelet", "Omlet"
    return (f"{en_state} {base[0]}".strip(), f"{tr_state} {base[1]}".strip())


def poultry_names(desc: str) -> tuple[str, str]:
    turkey = desc.startswith("turkey")
    animal = ("Turkey", "Hindi") if turkey else ("Chicken", "Tavuk")
    if "breast" in desc or "light meat" in desc: cut = ("Breast", "Göğsü")
    elif "drumstick" in desc: cut = ("Drumstick", "Baget")
    elif "thigh" in desc: cut = ("Thigh", "But")
    elif "leg" in desc: cut = ("Leg", "But")
    elif "dark meat" in desc: cut = ("Dark Meat", "Koyu Et")
    elif "ground" in desc: return (f"Raw Ground {animal[0]}", f"Çiğ {animal[1]} Kıyma")
    else: cut = ("Meat", "Eti")
    marine = (" Marinated", " Marine") if "added solution" in desc else ("", "")
    return (f"Roasted {animal[0]} {cut[0]}{marine[0]}", f"Fırında {animal[1]} {cut[1]}{marine[1]}")


def fish_names(desc: str) -> tuple[str, str]:
    species = next((names for key, names in FISH.items() if key in desc), ("Seafood", "Deniz Ürünü"))
    en_state, tr_state = prep(desc)
    variant_map = {
        "atlantic": ("Atlantic", "Atlantik"), "pacific": ("Pacific", "Pasifik"),
        "alaska": ("Alaska", "Alaska"), "spanish": ("Spanish", "İspanyol"),
        "greenland": ("Greenland", "Grönland"), "sockeye": ("Sockeye", "Sockeye"),
        "chinook": ("Chinook", "Chinook"), "pink": ("Pink", "Pembe"),
        "chum": ("Chum", "Keta"), "king": ("King", "Kral"), "blue": ("Blue", "Mavi"),
        "queen": ("Queen", "Kraliçe"),
    }
    variant = next((names for key, names in variant_map.items() if re.search(rf"\b{key}\b", desc)), None)
    en_base = f"{variant[0]} {species[0]}" if variant else species[0]
    tr_base = f"{variant[1]} {species[1]}" if variant else species[1]
    return f"{en_state} {en_base}".strip(), f"{tr_state} {tr_base}".strip()


def meat_names(desc: str, pork: bool = False) -> tuple[str, str]:
    if pork:
        if "tenderloin" in desc: cut = ("Pork Tenderloin", "Domuz Bonfile")
        elif "blade" in desc: cut = ("Pork Shoulder", "Domuz Omuz")
        else: cut = ("Pork Loin", "Domuz Sırt Eti")
    else:
        if "top sirloin" in desc: cut = ("Beef Sirloin", "Dana Kontrfile")
        elif "tenderloin" in desc: cut = ("Beef Tenderloin", "Dana Bonfile")
        elif "top round" in desc: cut = ("Beef Top Round", "Dana Kontrnuar")
        else: cut = ("Beef Eye of Round", "Dana Nuar")
    en_state, tr_state = prep(desc)
    grade = " (Choice)" if "choice" in desc else " (Select)" if "select" in desc else ""
    return f"{en_state} {cut[0]}{grade}", f"{tr_state} {cut[1]}{grade}"


def legume_names(desc: str) -> tuple[str, str]:
    kinds = {
        "soybeans": ("Soybeans", "Soya Fasulyesi"), "lentils": ("Lentils", "Mercimek"),
        "peas, split": ("Split Peas", "Kırık Bezelye"), "navy": ("Navy Beans", "Kuru Fasulye"),
        "pink": ("Pink Beans", "Pembe Fasulye"), "black": ("Black Beans", "Siyah Fasulye"),
        "pinto": ("Pinto Beans", "Barbunya Tipi Fasulye"), "white": ("White Beans", "Beyaz Fasulye"),
        "adzuki": ("Adzuki Beans", "Adzuki Fasulyesi"), "french": ("French Beans", "Fransız Fasulyesi"),
        "yellow": ("Yellow Beans", "Sarı Fasulye"), "great northern": ("Great Northern Beans", "İri Beyaz Fasulye"),
        "cranberry": ("Cranberry Beans", "Barbunya"), "royal red": ("Royal Red Kidney Beans", "Koyu Kırmızı Fasulye"),
        "california red": ("California Red Kidney Beans", "Kaliforniya Kırmızı Fasulyesi"),
        "kidney": ("Kidney Beans", "Kırmızı Fasulye"),
    }
    base = next((names for key, names in kinds.items() if key in desc), ("Beans", "Fasulye"))
    salted = (" with Salt", " (Tuzlu)") if "with salt" in desc and "without salt" not in desc else ("", "")
    return f"Boiled {base[0]}{salted[0]}", f"Haşlanmış {base[1]}{salted[1]}"


def grain_names(desc: str) -> tuple[str, str]:
    kinds = {
        "buckwheat": ("Buckwheat", "Karabuğday"), "millet": ("Millet", "Darı"),
        "rice noodles": ("Rice Noodles", "Pirinç Eriştesi"), "spaghetti": ("Spaghetti", "Spagetti"),
        "soba": ("Soba Noodles", "Soba Eriştesi"), "somen": ("Somen Noodles", "Somen Eriştesi"),
        "noodles, egg": ("Egg Noodles", "Yumurtalı Erişte"), "macaroni": ("Macaroni", "Makarna"),
        "pasta": ("Pasta", "Makarna"), "rice": ("Rice", "Pirinç"), "noodles": ("Noodles", "Erişte"),
    }
    base = next((names for key, names in kinds.items() if key in desc), ("Grain", "Tahıl"))
    details = []
    if "brown" in desc: details.append(("Brown", "Esmer"))
    elif "white" in desc: details.append(("White", "Beyaz"))
    if "spinach" in desc: details.append(("Spinach", "Ispanaklı"))
    if "gluten-free" in desc: details.append(("Gluten-Free", "Glütensiz"))
    if "protein-fortified" in desc: details.append(("High-Protein", "Yüksek Proteinli"))
    en_state, tr_state = prep(desc)
    en = " ".join([en_state] + [x[0] for x in details] + [base[0]]).strip()
    tr = " ".join([tr_state] + [x[1] for x in details] + [base[1]]).strip()
    return en, tr


def nut_names(desc: str) -> tuple[str, str]:
    kinds = {
        "almonds": ("Almonds", "Badem"), "cashew butter": ("Cashew Butter", "Kaju Ezmesi"),
        "cashew": ("Cashews", "Kaju"), "pistachio": ("Pistachios", "Antep Fıstığı"),
        "sunflower seed butter": ("Sunflower Seed Butter", "Ay Çekirdeği Ezmesi"),
        "sunflower": ("Sunflower Seeds", "Ay Çekirdeği"), "pumpkin": ("Pumpkin Seeds", "Kabak Çekirdeği"),
        "tahini": ("Tahini", "Tahin"), "sesame": ("Sesame Seeds", "Susam"),
    }
    base = next((names for key, names in kinds.items() if key in desc), ("Nuts", "Kuruyemiş"))
    if "honey roasted" in desc: state = ("Honey-Roasted", "Ballı Kavrulmuş")
    elif "oil roasted" in desc: state = ("Oil-Roasted", "Yağda Kavrulmuş")
    elif "roasted" in desc or "toasted" in desc: state = ("Roasted", "Kavrulmuş")
    elif "raw" in desc: state = ("Raw", "Çiğ")
    else: state = ("", "")
    salt = (" Lightly Salted", " Az Tuzlu") if "lightly salted" in desc else ""
    return f"{state[0]} {base[0]}{salt if isinstance(salt, str) else ''}".strip(), f"{state[1]} {base[1]}{salt if isinstance(salt, str) else ''}".strip()


def produce_names(desc: str) -> tuple[str, str]:
    base = next((names for key, names in PRODUCE.items() if desc.startswith(key)), ("Produce", "Sebze/Meyve"))
    en_state, tr_state = prep(desc)
    details = []
    for key, names in (("red", ("Red", "Kırmızı")), ("green", ("Green", "Yeşil")),
                       ("yellow", ("Yellow", "Sarı")), ("without skin", ("Peeled", "Kabuksuz")),
                       ("with peel", ("With Peel", "Kabuklu")), ("shiitake", ("Shiitake", "Şitake"))):
        if key in desc: details.append(names)
    return " ".join([en_state] + [x[0] for x in details] + [base[0]]).strip(), " ".join([tr_state] + [x[1] for x in details] + [base[1]]).strip()


def professional_names(row: dict) -> tuple[str, str]:
    desc = row["display_name_en"].lower()
    group = cohort(row)
    if desc.startswith("egg,"): return egg_names(desc)
    if desc.startswith("cheese, feta"): return "Feta Cheese", "Beyaz Peynir (Feta)"
    if desc.startswith("milk, sheep"): return "Sheep Milk", "Koyun Sütü"
    if desc.startswith("yogurt"): return "Plain Low-Fat Yogurt", "Sade Az Yağlı Yoğurt"
    if "buttermilk, dried" in desc: return "Buttermilk Powder", "Yayık Altı Sütü Tozu"
    if desc.startswith("milk, low sodium"): return "Low-Sodium Milk", "Düşük Sodyumlu Süt"
    if group == "POULTRY": return poultry_names(desc)
    if group == "FISH_SEAFOOD": return fish_names(desc)
    if group == "LEAN_MEAT": return meat_names(desc)
    if group == "PORK": return meat_names(desc, True)
    if group == "LEGUMES": return legume_names(desc)
    if group == "GRAINS": return grain_names(desc)
    if group == "NUTS_SEEDS": return nut_names(desc)
    return produce_names(desc)


def option(unit, quantity, grams, en, tr, default=True):
    return {"label": en, "unitType": unit, "quantity": quantity, "gramWeight": grams,
            "mlVolume": None, "defaultOption": default, "labels": {"EN": en, "TR": tr}}


def concrete_options(row: dict) -> list[dict]:
    desc = row["display_name_en"].lower()
    group = cohort(row)
    if "egg, white" in desc and "dried" not in desc: return [option("PIECE", 1, 33, "1 large egg white", "1 büyük yumurta beyazı")]
    if "egg, yolk" in desc and "dried" not in desc: return [option("PIECE", 1, 17, "1 large egg yolk", "1 büyük yumurta sarısı")]
    if desc.startswith("egg,") and "dried" not in desc: return [option("PIECE", 1, 50, "1 large egg", "1 büyük yumurta")]
    if desc.startswith("egg,") and "dried" in desc: return []
    if "buttermilk, dried" in desc: return [option("TABLESPOON", 1, 8, "1 tablespoon", "1 yemek kaşığı")]
    if desc.startswith("milk,"): return [option("CUP", 1, 200, "1 cup", "1 su bardağı")]
    if desc.startswith("yogurt"): return [option("BOWL", 1, 200, "1 bowl", "1 kase")]
    if desc.startswith("cheese, feta"): return [option("SLICE", 1, 30, "1 slice", "1 dilim")]
    if group in {"POULTRY", "FISH_SEAFOOD", "LEAN_MEAT", "PORK"}: return []
    if group == "LEGUMES": return [option("CUP", 1, 170, "1 cup cooked", "1 su bardağı pişmiş")]
    if group == "GRAINS":
        cooked = "cooked" in desc
        return [option("CUP", 1, 160 if cooked else 90, "1 cup", "1 su bardağı")]
    if group == "NUTS_SEEDS":
        if "butter" in desc or "tahini" in desc: return [option("TABLESPOON", 1, 16, "1 tablespoon", "1 yemek kaşığı")]
        return [option("PORTION", 1, 30, "1 handful", "1 avuç")]
    piece = {"banana": 118, "apple": 182, "orange": 140, "pear": 178, "peach": 150,
             "kiwifruit": 69, "tangerine": 88, "avocado": 150, "potato": 173, "sweet potato": 130}
    for key, grams in piece.items():
        if key in desc: return [option("PIECE", 1, grams, "1 medium", "1 orta boy")]
    if "pineapple" in desc: return [option("SLICE", 1, 84, "1 slice", "1 dilim")]
    return [option("CUP", 1, 150 if group == "FRUITS" else 100, "1 cup", "1 su bardağı")]


def duplicate_qualifier(desc: str) -> tuple[str, str]:
    """Return a concise meaningful qualifier for otherwise identical labels."""
    rules = [
        ("cornish game hens", "Cornish", "Cornish"), ("broilers or fryers", "Broiler", "Etlik"),
        ("roasting", "Roasting", "Fırınlık"), ("bone-in", "Bone-In", "Kemikli"),
        ("boneless", "Boneless", "Kemiksiz"), ("frozen", "Frozen", "Dondurulmuş"),
        ("all classes", "All Classes", "Tüm Sınıflar"), ("whole, breast", "Whole Breast", "Bütün Göğüs"),
        ("dry roasted", "Dry-Roasted", "Kuru Kavrulmuş"), ("toasted", "Toasted", "Tostlanmış"),
        ("with added salt", "Salted", "Tuzlu"), ("without added salt", "Unsalted", "Tuzsuz"),
        ("with salt", "Salted", "Tuzlu"), ("without salt", "Unsalted", "Tuzsuz"),
        ("extra sweet", "Extra Sweet", "Ekstra Tatlı"), ("traditional", "Traditional", "Geleneksel"),
        ("florida", "Florida", "Florida"), ("california", "California", "Kaliforniya"),
        ("casaba", "Casaba", "Casaba"), ("honeydew", "Honeydew", "Honeydew"),
        ("cantaloupe", "Cantaloupe", "Kantalup"), ("acorn", "Acorn", "Palamut"),
        ("hubbard", "Hubbard", "Hubbard"), ("scallop", "Pattypan", "Patisson"),
        ("butternut", "Butternut", "Butternut"), ("zucchini", "Zucchini", "Sakız Kabağı"),
        ("crookneck", "Crookneck", "Sarı Yaz Kabağı"), ("spaghetti", "Spaghetti", "Spagetti"),
        ("cooked in skin, skin", "Skin", "Kabuk"), ("cooked in skin, flesh", "Flesh", "İç Kısım"),
        ("spears", "Spears", "Dallar"), ("chopped", "Chopped", "Doğranmış"),
        ("vegetable", "Vegetable", "Sebzeli"), ("made with egg", "With Egg", "Yumurtalı"),
        ("made without egg", "Egg-Free", "Yumurtasız"), ("fresh-refrigerated", "Fresh", "Taze"),
        ("corn and rice flour", "Corn & Rice", "Mısır ve Pirinç"), ("corn, cooked", "Corn", "Mısır"),
        ("enriched", "Enriched", "Zenginleştirilmiş"), ("unenriched", "Unenriched", "Zenginleştirilmemiş"),
        ("lightly salted", "Lightly Salted", "Az Tuzlu"), ("decorticated", "Hulled", "Kabuksuz"),
        (", dry", "Dry", "Kuru"), (", cooked", "Cooked", "Pişmiş"),
    ]
    matches = []
    for needle, en, tr in rules:
        if needle not in desc:
            continue
        if needle == "enriched" and "unenriched" in desc:
            continue
        matches.append((en, tr))
    # Preparation alone is not useful when every colliding row has it.
    specific = [value for value in matches if value not in {("Cooked", "Pişmiş"), ("Dry", "Kuru")}]
    selected = (specific or matches)[:3]
    if not selected:
        return "Standard", "Standart"
    return ", ".join(x[0] for x in selected), ", ".join(x[1] for x in selected)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=Path("outputs/athlete-food-catalog-v1.csv"))
    parser.add_argument("--output", type=Path, default=Path("outputs/athlete-food-catalog-v2.csv"))
    parser.add_argument("--report", type=Path, default=Path("outputs/athlete-food-catalog-v2.json"))
    parser.add_argument("--chunk-dir", type=Path, default=Path("outputs/athlete-food-catalog-v2-db-import"))
    args = parser.parse_args()
    with args.input.open(encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle); fields = reader.fieldnames; rows = list(reader)
    proposed = [professional_names(row) for row in rows]
    en_counts = Counter(en.casefold() for en, _ in proposed)
    tr_counts = Counter(tr.casefold() for _, tr in proposed)
    for row, (en, tr) in zip(rows, proposed):
        original = row["display_name_en"].lower()
        if en_counts[en.casefold()] > 1 or tr_counts[tr.casefold()] > 1:
            en_q, tr_q = duplicate_qualifier(original)
            en, tr = f"{en} ({en_q})", f"{tr} ({tr_q})"
        row.update({"name": en, "display_name": en, "short_display_name": en,
                    "display_name_en": en, "short_display_name_en": en,
                    "display_name_tr": tr, "short_display_name_tr": tr,
                    "alias_en": f'{row["alias_en"]}; {en.lower()}',
                    "alias_tr": f'{row["alias_tr"]}; {tr.lower()}',
                    "serving_options_json": json.dumps(concrete_options(row), ensure_ascii=False, separators=(",", ":"))})
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields, quoting=csv.QUOTE_ALL); writer.writeheader(); writer.writerows(rows)
    args.chunk_dir.mkdir(parents=True, exist_ok=True)
    chunks = []
    for start in range(0, len(rows), 50):
        chunk_rows = rows[start:start + 50]
        chunk_path = args.chunk_dir / f"athlete-food-catalog-v2-part-{start // 50 + 1:03d}.csv"
        with chunk_path.open("w", encoding="utf-8-sig", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=fields, quoting=csv.QUOTE_ALL); writer.writeheader(); writer.writerows(chunk_rows)
        chunks.append({"file": chunk_path.name, "rows": len(chunk_rows)})
    duplicate_en = len(rows) - len({r["display_name_en"].casefold() for r in rows})
    duplicate_tr = len(rows) - len({r["display_name_tr"].casefold() for r in rows})
    generic_options = sum(any(o["label"].casefold() in {"100 g", "100g", "1 portion", "1 porsiyon", "1/2 portion", "1/2 porsiyon"}
                              for o in json.loads(r["serving_options_json"])) for r in rows)
    report = {"version": "athlete-food-catalog-v2", "rowCount": len(rows),
              "categoryCounts": dict(Counter(cohort(r) for r in rows)),
              "duplicateEnglishDisplayNames": duplicate_en, "duplicateTurkishDisplayNames": duplicate_tr,
              "rowsWithGenericReadyPortions": generic_options,
              "rowsWithoutReadyPortions": sum(not json.loads(r["serving_options_json"]) for r in rows),
              "importChunks": chunks,
              "namingPattern": "Preparation + Food + meaningful qualifier; no USDA comma/dash chains",
              "releaseStatus": "APPROVED" if generic_options == 0 and duplicate_en == 0 and duplicate_tr == 0 else "REVIEW_REQUIRED"}
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()

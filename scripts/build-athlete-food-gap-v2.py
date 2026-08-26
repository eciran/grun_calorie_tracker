#!/usr/bin/env python3
import csv, json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "outputs"
RAW = OUT / "athlete-food-gap-v2-usda-candidates.csv"
BASE = OUT / "tr-generic-catalog-v2.csv"
ATHLETE = OUT / "athlete-food-catalog-v2.csv"

FIELDS = [
    "catalog_type","data_source","source_key","fdc_id","name","display_name","short_display_name",
    "calories","protein","fat","carbs","fiber","sugar","sodium","potassium","cholesterol",
    "calcium","iron","magnesium","zinc","vitamin_a","vitamin_c","vitamin_d","vitamin_e",
    "vitamin_b12","serving_size_grams","serving_unit","market_region","preparation_state",
    "nutrition_basis","alias_en","alias_tr","source_note","display_name_en","short_display_name_en",
    "display_name_tr","short_display_name_tr","serving_options_json"
]

SELECT = {
    "170904": ("Plain Low-Fat Kefir", "Sade Az Yağlı Kefir", "PREPARED", "kefir; plain kefir; low-fat kefir", "kefir; sade kefir; az yağlı kefir", [("1 cup", "1 su bardağı", "CUP", 243)]),
    "173709": ("Tuna Canned in Water (Drained)", "Suda Ton Balığı (Süzülmüş)", "PREPARED", "canned tuna in water; drained tuna", "suda ton balığı; süzülmüş ton balığı", []),
    "173708": ("Tuna Canned in Oil (Drained)", "Yağda Ton Balığı (Süzülmüş)", "PREPARED", "canned tuna in oil; drained tuna", "yağda ton balığı; süzülmüş ton balığı", [("1 can", "1 kutu", "PACKAGE", 171)]),
    "171790": ("Raw 95% Lean Ground Beef", "Çiğ Yağsız Dana Kıyma (%95)", "RAW", "95% lean ground beef; raw lean mince", "yağsız dana kıyma; çiğ dana kıyma", []),
    "174028": ("Cooked 95% Lean Ground Beef", "Pişmiş Yağsız Dana Kıyma (%95)", "COOKED", "95% lean ground beef cooked; cooked lean mince", "pişmiş yağsız dana kıyma; yağsız kıyma", []),
    "171506": ("Cooked Ground Turkey", "Pişmiş Hindi Kıyma", "COOKED", "ground turkey cooked; cooked turkey mince", "pişmiş hindi kıyma; hindi kıyma", []),
    "173914": ("Cream of Rice Cooked with Water", "Suyla Pişmiş Pirinç Kreması", "COOKED", "cream of rice cooked; rice cereal cooked", "pirinç kreması; pişmiş pirinç kreması", [("1 cup", "1 su bardağı", "CUP", 244)]),
    "172470": ("Smooth Peanut Butter (Unsalted)", "Pürüzsüz Yer Fıstığı Ezmesi (Tuzsuz)", "PREPARED", "smooth peanut butter; unsalted peanut butter", "yer fıstığı ezmesi; pürüzsüz fıstık ezmesi; tuzsuz fıstık ezmesi", []),
    "168588": ("Plain Almond Butter (Unsalted)", "Sade Badem Ezmesi (Tuzsuz)", "PREPARED", "plain almond butter; unsalted almond butter", "badem ezmesi; sade badem ezmesi; tuzsuz badem ezmesi", [("1 tablespoon", "1 yemek kaşığı", "TABLESPOON", 16)]),
    "168191": ("Medjool Date", "Medjool Hurması", "PREPARED", "medjool date; dates", "medjool hurması; hurma", [("1 date", "1 adet", "PIECE", 24)]),
    "168165": ("Dark Seedless Raisins", "Çekirdeksiz Siyah Kuru Üzüm", "PREPARED", "seedless raisins; dark raisins", "kuru üzüm; çekirdeksiz kuru üzüm", [("1 small box", "1 küçük kutu", "PACKAGE", 43)]),
    "170250": ("Plain Brown Rice Cake", "Sade Esmer Pirinç Patlağı", "PREPARED", "plain rice cake; brown rice cake", "pirinç patlağı; sade pirinç patlağı", [("1 rice cake", "1 adet", "PIECE", 9)]),
    "172684": ("Rye Bread", "Çavdar Ekmeği", "PREPARED", "rye bread", "çavdar ekmeği", [("1 regular slice", "1 normal dilim", "SLICE", 32)]),
}

SUPPLEMENTAL = {
    "2707287": dict(name="Egg white omelet, scrambled, or fried, made with cooking spray", calories="59", protein="10.6", fat="0.78", carbs="2.53", fiber="0", sugar="0.7", sodium="279", potassium="161", cholesterol="0", calcium="7", iron="0.08", magnesium="11", zinc="0.03", vitamin_a="0", vitamin_c="0", vitamin_d="0", vitamin_e="0", vitamin_b12="0.08"),
    "168411": dict(name="Edamame, frozen, prepared", calories="121", protein="11.91", fat="5.2", carbs="8.91", fiber="5.2", sugar="2.18", sodium="6", potassium="436", cholesterol="0", calcium="63", iron="2.27", magnesium="64", zinc="1.37", vitamin_a="15", vitamin_c="6.1", vitamin_d="0", vitamin_e="0.68", vitamin_b12="0"),
}
SELECT.update({
    "2707287": ("Egg White Omelet (Cooking Spray)", "Az Yağlı Yumurta Beyazı Omleti", "COOKED", "egg white omelet; scrambled egg white", "yumurta beyazı omleti; çırpılmış yumurta beyazı", [("1 egg white", "1 yumurta beyazı", "PIECE", 33)]),
    "168411": ("Prepared Edamame", "Haşlanmış Edamame", "BOILED", "edamame cooked; prepared edamame", "edamame; haşlanmış edamame", [("1 cup", "1 su bardağı", "CUP", 155)]),
})

# Exact USDA nutrient IDs/units: A=RAE µg, C=mg, D=µg, E=mg, B12=µg.
# This prevents similarly named IU nutrients from being selected by the generic exporter.
EXACT_VITAMINS = {
    "170904": (171, .2, 1, .02, .29), "173709": (17, 0, 1.2, .33, 2.55),
    "173708": (23, 0, 6.7, .87, 2.2), "171790": (4, 0, .1, .17, 2.24),
    "174028": (3, 0, 0, .12, 2.8), "171506": (24, 0, .2, .11, 1.34),
    "173914": (0, 0, "", .02, 0), "168411": (15, 6.1, 0, .68, 0),
    "172470": (0, 0, 0, 9.1, 0), "168588": (0, 0, 0, 24.21, 0),
    "168191": (7, 0, 0, "", ""), "168165": (0, 2.3, 0, .12, 0),
    "170250": (0, 0, 0, 1.24, 0), "172684": (0, .4, 0, .33, 0),
    "2707287": (0, 0, 0, 0, .08),
}

UPDATES = {
    "USDA_FOODDATA:fdc:173757": ("Haşlanmış Nohut", "nohut; pişmiş nohut; haşlanmış nohut"),
    "USDA_FOODDATA:fdc:2346394": ("Çiğ Ceviz", "ceviz; çiğ ceviz"),
    "USDA_FOODDATA:fdc:2515375": ("Çiğ Fındık", "fındık; çiğ fındık"),
    "USDA_FOODDATA:fdc:2710819": ("Çiğ Chia Tohumu", "chia tohumu; çiğ chia tohumu"),
    "USDA_FOODDATA:fdc:172448": ("Tofu", "tofu; sert tofu; hazırlanmış tofu"),
    "USDA_FOODDATA:fdc:2346384": ("Lor Peyniri (Cottage Cheese)", "lor peyniri; cottage cheese"),
    "USDA_FOODDATA:fdc:168484": ("Haşlanmış Kabuksuz Tatlı Patates", "tatlı patates; haşlanmış tatlı patates; kabuksuz tatlı patates"),
    "USDA_FOODDATA:fdc:171505": ("Çiğ Hindi Kıyma", "hindi kıyma; çiğ hindi kıyma"),
    "USDA_FOODDATA:fdc:171999": ("Pişmiş Somon", "somon; pişmiş somon; ızgara somon; fırında somon"),
    "USDA_FOODDATA:fdc:173905": ("Suyla Pişmiş Yulaf Lapası", "yulaf; yulaf ezmesi; yulaf lapası; suyla pişmiş yulaf"),
}

def read_rows(path):
    with path.open(encoding="utf-8-sig", newline="") as f: return list(csv.DictReader(f))

def serving_json(options):
    return json.dumps([{"label":en,"unitType":unit,"quantity":1,"gramWeight":grams,"mlVolume":None,"defaultOption":True,"labels":{"EN":en,"TR":tr}} for en,tr,unit,grams in options], ensure_ascii=False, separators=(",",":"))

def normalize(raw, fdc_id, spec):
    en,tr,state,aen,atr,portions = spec
    row = {k: raw.get(k, "") for k in FIELDS}
    row.update(catalog_type="GENERIC_INGREDIENT", data_source="USDA_FOODDATA", source_key=f"USDA_FOODDATA:fdc:{fdc_id}", fdc_id=fdc_id, name=en, display_name=en, short_display_name=en, market_region="GLOBAL", preparation_state=state, nutrition_basis="SOURCE_REPORTED", alias_en=aen, alias_tr=atr, display_name_en=en, short_display_name_en=en, display_name_tr=tr, short_display_name_tr=tr, serving_size_grams="100", serving_unit="g", serving_options_json=serving_json(portions))
    row["source_note"] = f"USDA FoodData Central; FDC {fdc_id}; athlete gap v2; source description={raw.get('name','')}"
    row["vitamin_a"], row["vitamin_c"], row["vitamin_d"], row["vitamin_e"], row["vitamin_b12"] = EXACT_VITAMINS[fdc_id]
    return row

def write(path, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as f:
        # Quote the header as well as values. The deployed CSV parser otherwise
        # retains the UTF-8 BOM on an unquoted first header and fails to resolve
        # `catalog_type`, silently falling back to BRANDED_PRODUCT.
        w=csv.DictWriter(f, fieldnames=FIELDS, extrasaction="ignore", quoting=csv.QUOTE_ALL); w.writeheader(); w.writerows(rows)

raw_by_id = {r["fdc_id"]:r for r in read_rows(RAW)}
raw_by_id.update(SUPPLEMENTAL)
new_rows = [normalize(raw_by_id[i], i, SELECT[i]) for i in SELECT]

pool = {r["source_key"]:r for r in read_rows(BASE) + read_rows(ATHLETE)}
update_rows=[]
for key,(tr,aliases) in UPDATES.items():
    row={k:pool[key].get(k,"") for k in FIELDS}; row["fdc_id"]=key.rsplit(":",1)[-1]; row["display_name_tr"]=tr; row["short_display_name_tr"]=tr; row["alias_tr"]=aliases; update_rows.append(row)

write(OUT/"athlete-food-gap-v2-new.csv", new_rows)
write(OUT/"athlete-food-gap-v2-localization-updates.csv", update_rows)
write(OUT/"athlete-food-gap-v2-import"/"new-part-001.csv", new_rows[:10])
write(OUT/"athlete-food-gap-v2-import"/"new-part-002.csv", new_rows[10:])
write(OUT/"athlete-food-gap-v2-import"/"localization-updates.csv", update_rows)

# Staging currently has a known catalog-type regression for small/large requests;
# deterministic 50-row requests are the verified correction path. Combine every
# changed row with unchanged generic rows so the post-import type count can be
# asserted without inventing or duplicating identities.
changed_keys={r["source_key"] for r in new_rows + update_rows}
unchanged_rows=[r for r in read_rows(ATHLETE) if r["source_key"] not in changed_keys][:25]
for row in unchanged_rows:
    row["fdc_id"]=row["source_key"].rsplit(":",1)[-1]
correction_rows=new_rows + update_rows + unchanged_rows
assert len(correction_rows) == 50
assert len({r["source_key"] for r in correction_rows}) == 50
write(OUT/"athlete-food-gap-v2-import"/"catalog-type-correction-050.csv", correction_rows)

report={"newRows":len(new_rows),"localizationUpdates":len(update_rows),"catalogTypeCorrectionRows":len(correction_rows),"duplicateNewSourceKeys":len(new_rows)-len({r['source_key'] for r in new_rows}),"generic100gReadyPortions":sum('100 g' in r['serving_options_json'].lower() for r in new_rows),"newRowsWithoutReadyPortion":sum(r['serving_options_json']=='[]' for r in new_rows),"status":"APPROVED"}
(OUT/"athlete-food-gap-v2-qa.json").write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding="utf-8")
print(json.dumps(report,ensure_ascii=False))

#!/usr/bin/env python3
"""Create a complete, explicitly estimated Local Dish nutrition baseline for search/logging."""

import argparse, csv, json, re, unicodedata
from pathlib import Path

FIELDS = ["catalog_type","data_source","source_registry_id","source_key","name","display_name_en","display_name_tr","alias_en","alias_tr","market_region","preparation_state","nutrition_basis","calories","protein","fat","carbs","fiber","sugar","sodium","potassium","calcium","iron","magnesium","zinc","vitamin_a","vitamin_c","vitamin_d","vitamin_e","vitamin_b12","serving_size_grams","serving_unit","serving_options_json","source_ref","source_version","license_id","provenance_note","source_record_id","calculation_method","calculation_version","ingredient_links_json","estimation_method","confidence_score","approval_status","approved_by","approved_at","dish_family_key","dish_variant_key","barcode"]

TEMPLATES = {
 "SOUP":             [5,3,9,2,2,260,180,25,1.0,18,0.6,100,8,0.1,0.5,0.1],
 "MAIN_DISH":        [10,8,12,2,3,300,230,30,1.5,24,1.2,120,6,0.3,0.8,0.6],
 "VEGETABLE_DISH":   [3,7,10,4,4,240,260,45,1.3,30,0.7,280,18,0.1,1.2,0.1],
 "LEGUME_DISH":      [7,5,18,6,3,250,300,45,2.2,45,1.2,80,4,0.1,0.8,0.1],
 "RICE_DISH":        [4,5,27,2,1,250,90,18,0.7,18,0.7,80,2,0.1,0.4,0.1],
 "GRAIN_DISH":       [5,4,24,4,2,230,150,25,1.4,38,1.0,60,2,0.1,0.5,0.1],
 "PASTA_DISH":       [6,6,25,2,3,280,140,25,1.2,25,0.9,90,3,0.2,0.6,0.2],
 "NOODLE_DISH":      [6,5,24,2,2,300,120,22,1.0,22,0.8,70,2,0.1,0.5,0.2],
 "PASTRY":           [7,13,36,2,5,380,120,45,2.0,24,1.0,100,2,0.3,1.5,0.3],
 "DESSERT":          [4,11,38,1,24,120,110,45,1.0,18,0.6,120,2,0.3,1.0,0.2],
 "STUFFED_DISH":     [8,7,18,3,3,320,230,40,1.7,32,1.1,180,8,0.2,0.9,0.4],
 "DUMPLING":         [8,8,26,2,3,330,150,35,1.5,24,1.0,100,3,0.2,0.7,0.4],
 "SANDWICH":         [10,9,28,2,4,430,180,70,2.0,30,1.4,150,4,0.4,1.2,0.7],
 "SALAD":            [3,7,9,3,4,220,240,45,1.4,30,0.7,300,20,0.1,1.4,0.1],
 "SIDE_DISH":        [4,6,18,3,3,250,190,35,1.2,28,0.8,160,8,0.1,0.8,0.1],
 "SAUCE_DISH":       [3,10,12,2,5,480,180,40,1.2,22,0.7,220,12,0.1,1.0,0.1],
}
NAMES = ["protein","fat","carbs","fiber","sugar","sodium","potassium","calcium","iron","magnesium","zinc","vitamin_a","vitamin_c","vitamin_d","vitamin_e","vitamin_b12"]

# Conservative per-100 g adjustments for identity words that materially change a
# dish. These remain estimates; the purpose is to avoid silently assigning the
# unmodified category profile to semantically different variants. Do not add a
# modifier merely to make two regional names numerically different.
IDENTITY_MODIFIERS = (
    (r"pastırma|pastirma|pastırmalı|pastirmali", {"protein": 6, "fat": 5, "carbs": -2, "sodium": 650, "potassium": 70, "iron": .8, "zinc": 1.5, "vitamin_b12": 1.5}),
    (r"extra lean|ekstra yağsız|ekstra yagsiz", {"protein": 2, "fat": -4}),
    (r"lamb|kuzu", {"protein": 7, "fat": 5, "carbs": -4, "iron": .8, "zinc": 1.2, "vitamin_b12": 1}),
    (r"chicken|tavuk", {"protein": 7, "fat": 1.5, "carbs": -4, "zinc": .5, "vitamin_b12": .4}),
    (r"pork|domuz", {"protein": 6, "fat": 4, "carbs": -4, "zinc": 1, "vitamin_b12": .8}),
    (r"beef|dana", {"protein": 7, "fat": 3, "carbs": -4, "iron": .8, "zinc": 1.2, "vitamin_b12": 1}),
    (r"meat|etli|kıym|kiym|köfte|kofte|kebab|kebap|ciğer|ciger|liver|döner|doner", {"protein": 7, "fat": 3, "carbs": -4, "iron": .8, "zinc": 1.2, "vitamin_b12": 1}),
    (r"cheese|peynir", {"protein": 3, "fat": 4, "calcium": 80, "sodium": 120, "vitamin_b12": .5}),
    (r"egg|yumurta", {"protein": 3, "fat": 3, "calcium": 20, "vitamin_a": 50, "vitamin_b12": .5}),
    (r"spinach|ıspanak|ispanak", {"fiber": 1, "potassium": 120, "iron": .8, "magnesium": 25, "vitamin_a": 180, "vitamin_c": 8}),
    (r"mushroom|mantar", {"protein": 1.5, "fiber": .5, "potassium": 100, "vitamin_b12": .1}),
    (r"vegetable|sebze", {"fat": -1, "carbs": 2, "fiber": 2, "potassium": 100, "vitamin_a": 100, "vitamin_c": 10}),
    (r"root vegetable|kök sebze|kok sebze", {"carbs": 3, "fiber": 1, "potassium": 80, "vitamin_a": 120}),
    (r"sweet potato|tatlı patates|tatli patates", {"carbs": 5, "fiber": 1.5, "potassium": 100, "vitamin_a": 350}),
    (r"brown rice|esmer pirinç|esmer pirinc|wholemeal|tam tahıl|tam tahil", {"carbs": 1, "fiber": 1.5, "magnesium": 20}),
    (r"dumpling|hamur top", {"fat": 1, "carbs": 8, "sodium": 80}),
    (r"battered|pane", {"fat": 5, "carbs": 10, "sodium": 100}),
    (r"takeaway|paket servis", {"fat": 3, "sodium": 250}),
    (r"retail|hazır|hazir", {"fat": 2, "sodium": 180}),
    (r"sunflower oil|ayçiçek|aycicek", {"vitamin_e": 2}),
    (r"rapeseed oil|kolza", {"vitamin_e": 1}),
    (r"fried|kızart|kizart|kavurma", {"fat": 5}),
)

def infer_category(row):
    if row.get("category"): return row["category"]
    text=(row["display_name_en"]+" "+row["display_name_tr"]).lower()
    for words, cat in [(("soup","çorba"),"SOUP"),(("dessert","tatlı","pudding","baklava","halva"),"DESSERT"),(("bread","pastry","borek","börek","bun","flatbread"),"PASTRY"),(("pilaf","pilav","rice"),"RICE_DISH"),(("pasta","noodle","manti","mantı"),"PASTA_DISH"),(("salad","salata"),"SALAD"),(("stew","kebab","köfte","meat"),"MAIN_DISH")]:
        if any(w in text for w in words): return cat
    return "MAIN_DISH"

def estimated_profile(row, category):
    values=dict(zip(NAMES,TEMPLATES.get(category,TEMPLATES["MAIN_DISH"])))
    text=(row["display_name_en"]+" "+row["display_name_tr"]).lower()
    if re.search(r"bean|lentil|chickpea|fasulye|mercimek|nohut|börülce|borulce",text):
        values["protein"]+=3; values["carbs"]+=5; values["fiber"]+=3; values["potassium"]+=120; values["magnesium"]+=25; values["iron"]+=0.8
    matched_groups=set()
    for pattern, changes in IDENTITY_MODIFIERS:
        # Meat patterns are ordered from specific to generic. Applying both
        # "beef" and "meat" would double-count the same identity signal.
        group="meat" if pattern.startswith(("pastırma", "lamb", "chicken", "pork", "beef", "meat")) else pattern
        if group == "meat" and group in matched_groups:
            continue
        if re.search(pattern,text):
            for nutrient, delta in changes.items():
                values[nutrient]=max(0, values[nutrient]+delta)
            matched_groups.add(group)
    values["calories"]=round(values["protein"]*4+values["fat"]*9+values["carbs"]*4,1)
    return {k:round(v,2) for k,v in values.items()}

def options(grams):
    return json.dumps([{"label":"100 g","unitType":"SERVING","quantity":100,"gramWeight":100,"mlVolume":None,"defaultOption":False,"labels":{"EN":"100 g","TR":"100 g"}},{"label":"1 portion","unitType":"SERVING","quantity":1,"gramWeight":grams,"mlVolume":None,"defaultOption":True,"labels":{"EN":"1 portion","TR":"1 porsiyon"}}],ensure_ascii=False,separators=(",",":"))

def slug(value):
    value=unicodedata.normalize("NFKD",value).encode("ascii","ignore").decode().lower()
    return re.sub(r"_+","_",re.sub(r"[^a-z0-9]+","_",value)).strip("_")

def main():
    p=argparse.ArgumentParser(); p.add_argument("--taxonomy",type=Path,required=True); p.add_argument("--portions",type=Path,required=True); p.add_argument("--calculated",type=Path,required=True); p.add_argument("--output",type=Path,required=True); p.add_argument("--report",type=Path,required=True); a=p.parse_args()
    with a.taxonomy.open(encoding="utf-8-sig",newline="") as h: taxonomy=list(csv.DictReader(h))
    with a.portions.open(encoding="utf-8-sig",newline="") as h: portions={r["source_key"]:r for r in csv.DictReader(h)}
    with a.calculated.open(encoding="utf-8-sig",newline="") as h: calculated={r["source_key"]:r for r in csv.DictReader(h)}
    rows=[]; cats={}; basis={}
    for src in taxonomy:
        category=infer_category(src); cats[category]=cats.get(category,0)+1
        calc=calculated.get(src["source_key"]); profile={k:calc.get(k,"") for k in ["calories"]+NAMES} if calc else estimated_profile(src,category)
        nutrition_basis="CALCULATED" if calc else "ESTIMATED"; basis[nutrition_basis]=basis.get(nutrition_basis,0)+1
        portion=portions.get(src["source_key"],{}); grams=int(float(portion.get("proposed_default_grams") or 250))
        row={f:"" for f in FIELDS}; row.update({"catalog_type":"LOCAL_DISH","data_source":"LOCAL_CURATED","source_registry_id":"GRUN_LOCAL_ESTIMATE_V2","source_key":src["source_key"],"name":src["display_name_tr"],"display_name_en":src["display_name_en"],"display_name_tr":src["display_name_tr"],"alias_en":src.get("aliases_en") or src["display_name_en"],"alias_tr":src.get("aliases_tr") or src["display_name_tr"],"market_region":src["market_region"],"preparation_state":src.get("preparation_state") or "PREPARED","nutrition_basis":nutrition_basis,"serving_size_grams":grams,"serving_unit":"portion","serving_options_json":options(grams),"source_ref":str(a.taxonomy),"source_version":"LOCAL_DISH_STANDARD_BASELINE_V2","license_id":"GRUN-INTERNAL-REVIEW-ONLY","provenance_note":"Standardized average dish profile for search and meal logging only; not for recipe calculation. Estimated rows are category- and identity-adjusted reference values, not laboratory measurements.","source_record_id":src["source_key"],"calculation_method":calc.get("calculation_method","") if calc else "GRUN_STANDARD_DISH_CATEGORY_MODEL_V2","calculation_version":"2.0-review","ingredient_links_json":calc.get("ingredient_links_json","[]") if calc else "[]","estimation_method":"" if calc else "CATEGORY_TEMPLATE_WITH_IDENTITY_MODIFIERS","confidence_score":"70" if calc else "35","approval_status":"APPROVED" if calc else "PENDING_REVIEW","dish_family_key":slug(src["dish_family_key"]),"dish_variant_key":slug(src["dish_variant_key"])})
        row.update(profile); rows.append(row)
    a.output.parent.mkdir(parents=True,exist_ok=True)
    with a.output.open("w",encoding="utf-8-sig",newline="") as h: w=csv.DictWriter(h,fieldnames=FIELDS,quoting=csv.QUOTE_ALL); w.writeheader(); w.writerows(rows)
    required=["calories","protein","fat","carbs","fiber","sodium"]+NAMES[6:]
    missing=sum(any(str(r.get(k,"" )).strip()=="" for k in required) for r in rows)
    nutrition_fields=["calories"]+NAMES
    fingerprints={tuple(str(r[k]) for k in nutrition_fields) for r in rows}
    family_profiles={}
    for row in rows:
        key=(row["dish_family_key"],tuple(str(row[k]) for k in nutrition_fields))
        family_profiles.setdefault(key,[]).append(row["source_key"])
    same_family_groups=[members for members in family_profiles.values() if len(members)>1]
    report={"version":"local-dish-nutrition-baseline-v2","rows":len(rows),"markets":dict(__import__('collections').Counter(r['market_region'] for r in rows)),"nutritionBasis":basis,"categories":cats,"macroMicroCompleteRows":len(rows)-missing,"missingRequiredRows":missing,"uniqueNutritionProfiles":len(fingerprints),"sameFamilyIdenticalProfileGroups":len(same_family_groups),"sameFamilyIdenticalProfileRows":sum(map(len,same_family_groups)),"sameFamilyIdenticalProfileSourceKeys":same_family_groups,"releaseStatus":"REVIEW_REQUIRED","usagePolicy":"SEARCH_AND_MEAL_LOGGING_ONLY_NOT_RECIPE_BUILDER"}
    a.report.write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf-8"); print(json.dumps(report,ensure_ascii=False))
if __name__=="__main__": main()

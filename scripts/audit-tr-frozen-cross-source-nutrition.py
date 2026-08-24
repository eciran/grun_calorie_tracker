import argparse, hashlib, json
from pathlib import Path

CORE = ("calories", "protein", "fat", "carbs")

def rows(obj):
    if isinstance(obj, list): return obj
    if isinstance(obj, dict):
        for k in ("products", "items", "data"):
            if isinstance(obj.get(k), list): return obj[k]
    return []

def barcode(row):
    for k in ("barcode", "gtin", "ean", "ean13", "productBarcode"):
        v=row.get(k)
        if v is not None and str(v).isdigit(): return str(v)
    return ""

def nutrition(row):
    pools=[row]
    for k in ("nutrition", "nutriments", "nutritionFacts"):
        if isinstance(row.get(k), dict): pools.append(row[k])
    aliases={"calories":("calories","kcal","energyKcal","energy-kcal_100g"),"protein":("protein","proteins","proteins_100g"),"fat":("fat","totalFat","fat_100g"),"carbs":("carbs","carbohydrates","carbohydrates_100g")}
    out={}
    for key,names in aliases.items():
        for pool in pools:
            for name in names:
                if pool.get(name) not in (None, ""):
                    try: out[key]=float(pool[name]); break
                    except (TypeError,ValueError): pass
            if key in out: break
    basis=" ".join(str(row.get(k,"")) for k in ("nutritionBasis","basis","servingSize","nutritionUnit")).lower()
    explicit100 = any(x in basis for x in ("100 g","100g","100 ml","100ml","per 100")) or any("_100g" in n for n in aliases["calories"] if any(n in p for p in pools))
    plausible = len(out)==4 and 0<=out["calories"]<=1000 and all(0<=out[k]<=100 for k in CORE[1:])
    return out if explicit100 and plausible else None

def main():
    ap=argparse.ArgumentParser(); ap.add_argument("--queue",required=True); ap.add_argument("--out",required=True); a=ap.parse_args()
    root=Path.cwd(); targets=json.loads(Path(a.queue).read_text(encoding="utf-8-sig")); out=Path(a.out); out.mkdir(parents=True,exist_ok=True)
    cache={}; candidates=[]; exact=0; missing=[]
    refs=0
    for t in targets:
        if t.get("alreadyInV9"): continue
        found=False
        for ref in t.get("alternateReferences",[]):
            refs+=1; p=root/ref["sourceFile"]
            if p not in cache:
                raw=p.read_bytes(); obj=json.loads(raw.decode("utf-8-sig")); cache[p]=(rows(obj),hashlib.sha256(raw).hexdigest())
            rs,sha=cache[p]
            for row in rs:
                if barcode(row)==t["gtin"]:
                    exact+=1; n=nutrition(row)
                    if n:
                        candidates.append({"gtin":t["gtin"],"catalog":t["catalog"],"nutrition":n,"sourceUrl":ref["sourceUrl"],"sourceFile":ref["sourceFile"],"sourceFileSha256":sha,"matchMethod":"EXACT_GTIN","basis":"EXPLICIT_PER_100"}); found=True
                    break
            if found: break
        if not found: missing.append({"gtin":t["gtin"],"reason":"NO_EXPLICIT_COMPLETE_PER_100_IN_FROZEN_EXACT_GTIN_SOURCES"})
    report={"targets":sum(not t.get("alreadyInV9") for t in targets),"referencedFiles":len(cache),"referencesScanned":refs,"exactGtinRows":exact,"nutritionComplete":len(candidates),"netNewCandidates":len({x['gtin'] for x in candidates}),"rejectedOrMissing":len(missing)}
    for name,obj in (("candidates.json",candidates),("review.json",missing),("report.json",report)):
        (out/name).write_text(json.dumps(obj,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(json.dumps(report,indent=2))
if __name__=="__main__": main()

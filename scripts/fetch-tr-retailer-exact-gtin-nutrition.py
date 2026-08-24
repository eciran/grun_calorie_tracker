#!/usr/bin/env python3
"""Checkpointed, bounded exact-GTIN nutrition fetch for public retailer pages."""
import argparse, concurrent.futures, datetime, hashlib, html, json, re, time
from pathlib import Path
from urllib.request import Request, urlopen

FIELDS = {
    "calories": r"Enerji.{0,400}?(\d+(?:[.,]\d+)?)\s*kcal",
    "fat": r"(?:Toplam\s+)?Ya[ğg].{0,250}?(\d+(?:[.,]\d+)?)\s*g",
    "carbs": r"Karbonhidrat.{0,250}?(\d+(?:[.,]\d+)?)\s*g",
    "protein": r"Protein.{0,250}?(\d+(?:[.,]\d+)?)\s*g",
}

def decode_page(raw):
    text = raw.decode("utf-8", "replace")
    for a, b in ((r"\u003C", "<"), (r"\u003E", ">"), (r"\u002F", "/"), (r'\"', '"')):
        text = text.replace(a, b).replace(a.lower(), b)
    return html.unescape(text)

def parse_nutrition(text):
    starts = [m.start() for m in re.finditer(r"Besin\s+De[ğg]erleri", text, re.I)]
    for start in starts:
        segment = re.sub(r"<[^>]+>", " ", text[start:start + 8000])
        segment = re.sub(r"\s+", " ", segment)
        if not re.search(r"100\s*(?:g|gr|ml)\b", segment, re.I):
            continue
        values = {}
        for key, pattern in FIELDS.items():
            m = re.search(pattern, segment, re.I | re.S)
            if m:
                values[key] = float(m.group(1).replace(",", "."))
        if set(values) == set(FIELDS) and values["calories"] <= 1000 and all(0 <= values[k] <= 100 for k in ("fat", "carbs", "protein")) and sum(values[k] for k in ("fat", "carbs", "protein")) <= 115:
            return values, segment[:2000]
    return None, None

def fetch(row, timeout, retries):
    last = None
    for attempt in range(retries + 1):
        try:
            req = Request(row["sourceUrl"], headers={"User-Agent": "GRUNCatalogResearch/1.0 (+bounded public-page validation)"})
            with urlopen(req, timeout=timeout) as response:
                raw = response.read()
                status = response.status
            text = decode_page(raw)
            nutrition, evidence = parse_nutrition(text)
            title_m = re.search(r"<title[^>]*>(.*?)</title>", text, re.I | re.S)
            return {**row, "httpStatus": status, "bytes": len(raw), "rawSha256": hashlib.sha256(raw).hexdigest(), "title": re.sub(r"\s+", " ", html.unescape(title_m.group(1))).strip() if title_m else None, "nutrition": nutrition, "evidenceExcerpt": evidence, "error": None}
        except Exception as exc:
            last = f"{type(exc).__name__}: {exc}"
            time.sleep(min(2 ** attempt, 4))
    return {**row, "httpStatus": None, "bytes": 0, "rawSha256": None, "title": None, "nutrition": None, "evidenceExcerpt": None, "error": last}

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--candidates", required=True)
    ap.add_argument("--source-index", required=True)
    ap.add_argument("--output-dir", required=True)
    ap.add_argument("--source-family", default="ozdilekteyim")
    ap.add_argument("--max-pages", type=int, default=300)
    ap.add_argument("--skip", type=int, default=0)
    ap.add_argument("--workers", type=int, default=4)
    ap.add_argument("--timeout", type=int, default=20)
    ap.add_argument("--retries", type=int, default=1)
    args = ap.parse_args()
    out = Path(args.output_dir); out.mkdir(parents=True, exist_ok=True)
    wanted_rows = json.loads(Path(args.candidates).read_text(encoding="utf-8"))
    wanted = {r["catalogId"].split(":", 1)[-1]: r for r in wanted_rows if r.get("status") == "NO_EXACT_GTIN_NUTRITION"}
    index = json.loads(Path(args.source_index).read_text(encoding="utf-8"))["products"]
    selected = {}
    for r in index:
        gtin = str(r.get("barcode") or "")
        if r.get("sourceFamily") == args.source_family and gtin in wanted and r.get("sourceUrl"):
            selected.setdefault(gtin, {**r, "catalogName": wanted[gtin].get("name"), "catalogBrand": wanted[gtin].get("brand")})
    ordered = sorted(selected.values(), key=lambda r: (r["barcode"], r["sourceUrl"]))
    targets = ordered[args.skip:args.skip + args.max_pages]
    fetched_at = datetime.datetime.now(datetime.timezone.utc).isoformat()
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        results = list(pool.map(lambda r: fetch(r, args.timeout, args.retries), targets))
    candidates = [{**r, "fetchedAt": fetched_at, "nutritionBasis": "PER_100", "matchMethod": "EXACT_GTIN"} for r in results if r["nutrition"]]
    summary = {"sourceFamily": args.source_family, "availableExactTargets": len(selected), "skip": args.skip, "scannedPages": len(results), "fetchedPages": sum(r["httpStatus"] == 200 for r in results), "fetchErrors": sum(bool(r["error"]) for r in results), "nutritionComplete": len(candidates), "parseRejects": sum(r["httpStatus"] == 200 and not r["nutrition"] for r in results), "exactJoins": len(results), "validGtins": len({r["barcode"] for r in results}), "netNewCandidates": len({r["barcode"] for r in candidates})}
    (out / "pages.json").write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (out / "candidates.json").write_text(json.dumps(candidates, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (out / "report.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False))

if __name__ == "__main__": main()

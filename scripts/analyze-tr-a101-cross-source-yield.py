#!/usr/bin/env python3
"""Measure exact-GTIN alternate-source URLs for A101 nutrition-missing targets."""
import argparse,csv,hashlib,json
from collections import Counter,defaultdict
from pathlib import Path
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest().upper()
def main():
 p=argparse.ArgumentParser();p.add_argument('--queue',type=Path,required=True);p.add_argument('--source-index',type=Path,required=True);p.add_argument('--current-rich',type=Path,required=True);p.add_argument('--output-dir',type=Path,required=True);a=p.parse_args()
 q=json.loads(a.queue.read_text(encoding='utf-8-sig')); idx=json.loads(a.source_index.read_text(encoding='utf-8-sig'))['products']
 wanted={str(x['catalogId']).split(':',1)[1]:x for x in q if x.get('status')=='NO_EXACT_GTIN_NUTRITION' and str(x.get('catalogId','')).startswith('GTIN:')}
 by=defaultdict(list)
 for r in idx:
  g=str(r.get('barcode') or '')
  if g in wanted: by[g].append(r)
 a101={g for g,rows in by.items() if any(r.get('sourceFamily')=='a101' for r in rows)}
 with a.current_rich.open(encoding='utf-8-sig',newline='') as f: rich={r['barcode'] for r in csv.DictReader(f)}
 out=[]; fam=Counter()
 for g in sorted(a101):
  alternatives=[r for r in by[g] if r.get('sourceFamily')!='a101' and r.get('sourceUrl')]
  for r in alternatives:fam[r.get('sourceFamily')]+=1
  if alternatives: out.append({'gtin':g,'catalog':wanted[g],'alreadyInV9':g in rich,'a101References':[r for r in by[g] if r.get('sourceFamily')=='a101'],'alternateReferences':alternatives})
 a.output_dir.mkdir(parents=True,exist_ok=True);(a.output_dir/'cross-source-targets.json').write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
 report={'status':'PASS','a101ExactGtinTargets':len(a101),'withAlternateSource':len(out),'withoutAlternateSource':len(a101)-len(out),'alreadyInV9':sum(x['alreadyInV9'] for x in out),'netNewAlternateTargets':sum(not x['alreadyInV9'] for x in out),'alternateReferenceCounts':dict(sorted(fam.items())),'queueSha256':sha(a.queue),'sourceIndexSha256':sha(a.source_index),'currentRichSha256':sha(a.current_rich)}
 (a.output_dir/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8');print(json.dumps(report))
if __name__=='__main__':main()

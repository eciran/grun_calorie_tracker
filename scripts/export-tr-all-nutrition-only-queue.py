import argparse,csv,json
from pathlib import Path

def valid_gtin(v):
    v=str(v or '').strip()
    if not v.isdigit() or len(v) not in (8,12,13,14): return False
    return (10-sum(int(x)*(3 if i%2==0 else 1) for i,x in enumerate(v[-2::-1]))%10)%10==int(v[-1])
def num(v):
    try:return float(v)
    except:return None
def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--catalog',required=True); ap.add_argument('--out',required=True); a=ap.parse_args()
    doc=json.loads(Path(a.catalog).read_text(encoding='utf-8-sig')); rows=doc.get('products',doc); out=[]
    for r in rows:
        n=r.get('nutrition') or {}; vals=[num(n.get('energyKcal')),num(n.get('protein')),num(n.get('fat')),num(n.get('carbohydrate'))]
        nutrition=len(vals)==4 and all(v is not None for v in vals) and 0<=vals[0]<=1000 and all(0<=v<=100 for v in vals[1:])
        ids=[r.get('primaryBarcode'),r.get('barcode'),r.get('catalogId','').split(':')[-1]]
        if nutrition and not any(valid_gtin(x) for x in ids): out.append({k:r.get(k,'') for k in ('catalogId','name','brand','packageAmount','packageUnit')})
    p=Path(a.out);p.parent.mkdir(parents=True,exist_ok=True)
    with p.open('w',encoding='utf-8-sig',newline='') as f:
        w=csv.DictWriter(f,fieldnames=('catalogId','name','brand','packageAmount','packageUnit'),delimiter='\t');w.writeheader();w.writerows(out)
    print(json.dumps({'catalogRows':len(rows),'nutritionOnlyRows':len(out)}))
if __name__=='__main__':main()

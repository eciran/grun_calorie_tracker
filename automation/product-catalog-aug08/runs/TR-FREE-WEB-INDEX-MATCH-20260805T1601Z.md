# TR free web/index growth — 2026-08-05

- Run ID: `TR-FREE-WEB-INDEX-MATCH-20260805T1601Z`
- Stage: `TR-FREE-WEB-GROWTH`
- Result: `PASS_EXACT_CANDIDATE_EXPANSION`
- Catalog/database/production mutation: none

## Commands

```powershell
$py='C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
& $py .\scripts\match-tr-nutrition-ready-to-free-barcode-index.py --queue .\outputs\product-catalog-aug08\tr-barcode-evidence-priority-v1-20260803\tr-nutrition-ready-barcode-evidence-priority.tsv --source-index .\outputs\TR_Products\bulk-barcode-reconciliation\source-index.json --output-dir .\outputs\product-catalog-aug08\tr-free-index-barcode-match-v1-20260805
Get-FileHash -Algorithm SHA256 -LiteralPath .\scripts\match-tr-nutrition-ready-to-free-barcode-index.py
Get-FileHash -Algorithm SHA256 -LiteralPath .\outputs\product-catalog-aug08\tr-free-index-barcode-match-v1-20260805\report.json
Get-FileHash -Algorithm SHA256 -LiteralPath .\outputs\product-catalog-aug08\tr-free-index-barcode-match-v1-20260805\exact-free-index-barcode-candidates.json
```

The matcher was executed twice. Output hashes remained identical.

## Counts

- Nutrition-ready/barcode-missing input: 1,925 rows
- Frozen free-index rows: 30,150
- Exact single-GTIN rows: 142
- Exact single unique GTINs: 135
- Exact collision rows: 30
- No exact match: 1,753
- Against private rich v2: 123 exact rows / 118 unique GTINs are net-new candidates; 19 rows already resolve to a v2 GTIN.
- Current achieved private rich v2: 2,251 unique products
- Candidate-only projected total after one-row-per-GTIN review: 2,369
- Remaining to 10,000 after projected exact batch: 7,631

Evidence rows came from the already-frozen free index: A101 37, BakkalAbla 2,
Gürmar 62, İYAŞ 20, OFF 3, Özdilekteyim 5, Sarıyer 97 and ToptanTR 31.

## Web-search pilot

The first seven P0 products were searched by exact brand, product name and
package. Confirmed candidates found:

- Ülker Sürmix Peynirli Ballı Sürme 180 g — GTIN `8691375603402`, manufacturer product page: `https://www.besler.com.tr/tr/urunlerimiz/ulker-surmix-peynirli-balli-surme-180g`.
- Ülker Sürmix Peynirli Çikolatalı Sürme 180 g — GTIN `8691375603341`, indexed barcode/product page: `https://glutenalarmi.com/urun/8691375603341-ulker-surmix-peynirli-cikolatali-surme-180g-glutensiz-mi`; nutrition independently visible at CarrefourSA/Migros.
- Sana Bitkisel Yağlı Krema 200 ml — GTIN `8719200307377`, independently present at Hakmar Express and Gürmar; nutrition visible at Migros.
- Schweppes Mandalina 1 L — GTIN `5449000089380`, exact frozen OFF match and included in the 142 exact-single rows.

Kinder Pingui 30 g was not promoted because the candidate is regional and the
queue package unit is ambiguous. The Eti Gong and Çizmeci Time pilot searches
confirmed exact products/nutrition but did not expose a reliable GTIN yet.

## Hashes

- Script: `9BB1D057CF3B6EDB30C6AF9493C3CC5CB4B902840CA7B02699B2E0CE7526CAA9`
- Queue: `88E68826295A6EBA6A7C20FFBBBC3AE4CB596F90DF5B59EEB3E14DE4AF5A90C5`
- Source index: `0A05CC636EE14390D72B7DD7E68C8AE5147E71EA98451A2C55AC8F876AAFC926`
- Report: `BF0068EF3F5D5BEE4002DEF4EFA175169AD1A4C6E7ED85494E3523781F199F65`
- Candidates: `B28318BE0C5D0E7BBA5BCF0797752221446C5A2FFC5E7BD630D6F3AE33B00217`

## Next action

Build a versioned PRIVATE_TEST_ONLY v3 from v2 using only reviewed one-row-per-
GTIN exact candidates, preserve the 30 collisions and duplicate target GTINs in
review, add the four P0 web evidence records, and validate identity/nutrition.
Then run the same free exact-index/web process against the 4,585 barcode-ready/
nutrition-missing rows. The existing automation was updated to keep this TR 10k
growth objective ahead of D5 owner-contract work.

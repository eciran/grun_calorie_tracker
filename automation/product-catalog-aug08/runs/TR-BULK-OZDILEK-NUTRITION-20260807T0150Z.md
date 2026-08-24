# TR-BULK-OZDILEK-NUTRITION-20260807T0150Z

## Result

`PASS_BULK_CANDIDATES`

300 public Özdilekteyim product pages were scanned. 299 returned HTTP 200; 248 supplied explicit per-100 core nutrition passing plausibility checks. The 248 candidates have distinct GTINs; 57 overlap PRIVATE_TEST_ONLY v6, leaving 191 net-new candidates. Validated total remains 2,416 until the overlay build (projected 2,607).

## Commands

```powershell
C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe -m py_compile scripts/fetch-tr-retailer-exact-gtin-nutrition.py
C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe scripts/fetch-tr-retailer-exact-gtin-nutrition.py --candidates outputs/product-catalog-aug08/tr-free-nutrition-index-match-v1-20260805/exact-gtin-free-nutrition-candidates.json --source-index outputs/TR_Products/bulk-barcode-reconciliation/source-index.json --output-dir outputs/product-catalog-aug08/tr-bulk-ozdilek-nutrition-v1-20260807 --max-pages 300 --workers 4 --timeout 20 --retries 1
```

## Counts

- Available targets: 622
- Scanned / exact joins / valid GTINs: 300 / 300 / 300
- HTTP 200 / errors: 299 / 1
- Nutrition complete / parse rejects: 248 / 51
- Duplicate candidate GTINs / v6 overlaps / net-new: 0 / 57 / 191
- Plausibility rejects: 0

## SHA-256

- Script: `1D1517C8A2FEDF4C51137F2580600677D40AF6A0D4552ED657264DFEDC84C58E`
- Report: `704624847F2C46528F6420306E1A03F8807685B0644AA62076785CC4E8F77DDA`
- Candidates: `F595BF113A0B3899566190F390ED8C95ECB9695CD5C8F1D462B230A8C5A1F4CE`
- Pages: `7AC9C057AB359127CB4A89D5973F0BA39293DA21CA8DE96A41F653B0CF169F1C`

## Next

Build immutable PRIVATE_TEST_ONLY v7 from v6 plus four pending ETİ rows and 191 net-new rows, validate it, then resume the remaining 322 exact-GTIN pages.

# TR-BULK-OZDILEK-REMAINDER-20260807T0352Z

## Result

`PASS_BULK_CANDIDATES`

Completed the remaining exact-GTIN Özdilekteyim slice using deterministic offset 300. All 322 pages returned HTTP 200; 259 contained explicit plausible per-100 core nutrition. Against validated PRIVATE_TEST_ONLY v7, 27 already exist and 232 are net-new candidates. Validated total remains 2,608 until the next immutable overlay; projected total is 2,840.

## Commands

```powershell
C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe -m py_compile scripts/fetch-tr-retailer-exact-gtin-nutrition.py
C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe scripts/fetch-tr-retailer-exact-gtin-nutrition.py --candidates outputs/product-catalog-aug08/tr-free-nutrition-index-match-v1-20260805/exact-gtin-free-nutrition-candidates.json --source-index outputs/TR_Products/bulk-barcode-reconciliation/source-index.json --output-dir outputs/product-catalog-aug08/tr-bulk-ozdilek-nutrition-v2-remainder-20260807 --skip 300 --max-pages 322 --workers 4 --timeout 20 --retries 1
```

## Counts

- Available exact targets / skipped checkpoint / scanned: 622 / 300 / 322
- HTTP 200 / errors: 322 / 0
- Exact joins / valid GTINs: 322 / 322
- Nutrition complete / parse rejects: 259 / 63
- Candidate collisions / v7 overlaps / net-new: 0 / 27 / 232
- Achieved validated / projected / remaining after promotion: 2,608 / 2,840 / 7,160

## SHA-256

- Fetcher: `FED356EB927547A06FABDA2B55721E0DB7A9C9BDDCFD4E659B5372D961918642`
- Report: `9776B6871E96144154F202C290044EF23411B334CC1AC44DD173222453BB509D`
- Candidates: `891FEF549D408BCD7A45AA39E2D53E75F66C490C855967BA305B67D7D09C0912`
- Pages: `579AF8E622C70FE43D1B02A901B6184D5E1F19E05A85536ED0E621D9A96BB04C`

## Next

Build and validate immutable PRIVATE_TEST_ONLY v8 from v7 plus the 232 net-new candidates, then move bulk discovery to Cepte Şok or Ofix.

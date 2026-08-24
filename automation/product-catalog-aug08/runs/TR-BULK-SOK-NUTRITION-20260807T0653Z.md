# TR-BULK-SOK-NUTRITION-20260807T0653Z

## Result

`PASS_BULK_CANDIDATES_PARTIAL_FETCH`

Fetched the 71 exact full-name/package Cepte Şok targets with four workers and bounded retry. 59 pages returned HTTP 200, 12 remained fetch errors, 34 exposed complete plausible structured per-100 nutrition, and 25 successful pages lacked usable complete core nutrition. The 34 candidates have unique GTINs; 8 overlap v8, leaving 26 net-new candidates. Validated total remains 2,835 until a later overlay; projected total is 2,861.

## Commands

```powershell
C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe -m py_compile scripts/fetch-tr-sok-exact-nutrition.py
C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe scripts/fetch-tr-sok-exact-nutrition.py --matches outputs/product-catalog-aug08/tr-bulk-sok-intersect-v1-20260807/exact-matches.json --output-dir outputs/product-catalog-aug08/tr-bulk-sok-nutrition-v1-20260807 --workers 4 --timeout 20 --retries 1
```

## Counts

- Scanned URLs / exact joins / valid GTINs: 71 / 71 / 71
- HTTP 200 / fetch errors: 59 / 12
- Nutrition complete / parse rejects: 34 / 25
- Candidate collisions / v8 overlaps / net-new: 0 / 8 / 26
- Achieved validated / projected / remaining after promotion: 2,835 / 2,861 / 7,139

## SHA-256

- Script: `D0D09AA4B8C0F0225CA4ECEFAF13E34A2E7BCCC6BE7522BFB74ECAC5341040EF`
- Report: `2C78067DD30FEAC0F5185B9CF0FE16AE063239DF32180ED2BE31E4B0F3F0D68F`
- Candidates: `979A1BF0BBDF1EC9B7373BFBCEC6418A614006697236F71F5B72083B6D525B64`
- Pages: `6EEF45B3AD87CC7263BA44D0240F40CCFB7D8F2BEF95150E13C88B043FB5A96E`

## Next

Retry only the 12 failed exact targets in a checkpointed bounded slice; then merge all successful Cepte Şok candidates into one immutable PRIVATE_TEST_ONLY overlay and validate it.

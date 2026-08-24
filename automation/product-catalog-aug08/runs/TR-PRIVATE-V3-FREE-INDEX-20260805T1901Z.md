# TR PRIVATE_TEST_ONLY v3 free-index/web build — 2026-08-05

- Run ID: `TR-PRIVATE-V3-FREE-INDEX-20260805T1901Z`
- Stage: `TR-FREE-WEB-GROWTH`
- Result: `PASS_PRIVATE_TEST_V3`
- Production/database/network mutation: none

## Commands

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\product-catalog-aug08-automation-lock.ps1 -Action Acquire -RunId TR-PRIVATE-V3-FREE-INDEX-20260805T1901Z
$py='C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
& $py .\scripts\build-tr-retailer-private-v3-free-index.py --v2-rich .\outputs\product-catalog-aug08\tr-retailer-test-v2-off-nutrition-20260803\tr-retailer-test-rich-v2-import.csv --catalog .\outputs\TR_Products\merged-a101-migros-iyas-deduplicated\products.json --candidates .\outputs\product-catalog-aug08\tr-free-index-barcode-match-v1-20260805\exact-free-index-barcode-candidates.json --manual-evidence .\sample-data\manifests\product-catalog-aug08-tr-free-web-p0-v1.json --output-dir .\outputs\product-catalog-aug08\tr-retailer-test-v3-free-index-web-20260805
Import-Csv .\outputs\product-catalog-aug08\tr-retailer-test-v3-free-index-web-20260805\tr-retailer-test-rich-v3-import.csv
Get-FileHash -Algorithm SHA256 <builder/report/csv/provenance/review>
```

The build command was executed twice; hashes remained identical.

## Result

- v2 base: 2,251 rows
- Exact candidate pairs including four P0 web records: 145
- Added: 116 net-new, checksum-valid, one-row-per-GTIN rich products
- v3 achieved: 2,367 rows / 2,367 unique GTINs
- Duplicate output groups: 0
- Required-field/core-nutrition failures: 0
- Five candidate GTIN groups pointed to two target products and were excluded to review.
- Remaining to 10,000: 7,633

The four P0 records are present exactly once: Ülker Sürmix Ballı
`8691375603402`, Ülker Sürmix Çikolatalı `8691375603341`, Sana Krema
`8719200307377`, and Schweppes Mandalina `5449000089380`. Kinder Pingui stayed
excluded because its regional/package identity remains ambiguous.

## Hashes

- Builder: `1791513148184AC3238AF4995ECF8AC1C9A03AFD69598B7E41B268BC54C9A5A0`
- v2 input: `211B879B9E37BEB98422293FD164401B3033AC2059B93EE0DFA7A6E205916085`
- Catalog input: `56B9F149953FC8B38FD302AC024150F46214DF99F2B588AB4DCA37AD71563933`
- Candidate input: `B28318BE0C5D0E7BBA5BCF0797752221446C5A2FFC5E7BD630D6F3AE33B00217`
- Manual evidence: `7F005BE74476D7D0EBACBA081318D91CE510455E58B087CA3F8F4053D822A850`
- v3 CSV: `0820BB9CF9331F8CE11F9E86FAD8CF26607FD5FC1E091EF221F5C6E19E60640D`
- Provenance: `B88025AC6F9ECC6A5B3B4DA6C4C4E92C9C0C48195FE173051BE1F5B99A15C07E`
- Duplicate-target review: `2429C06C982E0A44D051C651DB15FF57E9A1B43F244686EEE3A1FC1BFF9B132C`
- Report: `4BB1F13911F257933101EE74EAA2016CB75A069D7CE285992FD861F1B6A96342`

## Next action

Run exact GTIN nutrition completion against the 4,585 barcode-ready/nutrition-
missing records using the frozen OFF dump and free source index, then publish a
versioned v4. Continue free web brand batches for the 1,753 nutrition-ready rows
with no exact local match.

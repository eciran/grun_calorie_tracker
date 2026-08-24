# TR-PRIVATE-V4-NUTRITION-20260806T0302Z

- Stage: `TR-FREE-WEB-GROWTH`
- Result: `PASS_PRIVATE_TEST_V4`
- Scope: versioned `PRIVATE_TEST_ONLY` v4; v3 unchanged; no database or production mutation.

## Commands

```powershell
$py='C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
$env:PYTHONDONTWRITEBYTECODE='1'
& $py -m py_compile .\scripts\build-tr-retailer-private-v4-nutrition.py
& $py .\scripts\build-tr-retailer-private-v4-nutrition.py --v3-rich .\outputs\product-catalog-aug08\tr-retailer-test-v3-free-index-web-20260805\tr-retailer-test-rich-v3-import.csv --candidates .\outputs\product-catalog-aug08\tr-free-nutrition-index-match-v1-20260805\exact-gtin-free-nutrition-candidates.json --output-dir .\outputs\product-catalog-aug08\tr-retailer-test-v4-nutrition-20260806
& $py .\scripts\build-tr-retailer-private-v4-nutrition.py --v3-rich .\outputs\product-catalog-aug08\tr-retailer-test-v3-free-index-web-20260805\tr-retailer-test-rich-v3-import.csv --candidates .\outputs\product-catalog-aug08\tr-free-nutrition-index-match-v1-20260805\exact-gtin-free-nutrition-candidates.json --output-dir .\outputs\product-catalog-aug08\tr-retailer-test-v4-nutrition-20260806
Get-FileHash .\scripts\build-tr-retailer-private-v4-nutrition.py -Algorithm SHA256
Get-FileHash .\outputs\product-catalog-aug08\tr-retailer-test-v4-nutrition-20260806\* -Algorithm SHA256
```

## Result and validation

- Base v3: 2,367 rows.
- Exact-single input: 766 rows / 722 unique GTINs.
- Excluded duplicate-target identities: 44 GTIN groups / 88 rows.
- Already present in v3: 634 GTINs.
- Net-new exact-GTIN nutrition rows: 44.
- v4: 2,411 rows / 2,411 unique checksum-valid GTINs.
- Required name, brand and core per-100 calories/protein/fat/carbs failures: 0.
- Provenance: 44 rows; missing source URL/file: 0/0.
- Deterministic rebuild: PASS.
- Remaining to 10,000: 7,589.

The builder performs the strict overlay validator during both runs: unique GTIN, valid check digit, required identity and plausible complete core nutrition. Conflicts and duplicate target identities were not added.

## Hashes

- v3 input CSV: `0820BB9CF9331F8CE11F9E86FAD8CF26607FD5FC1E091EF221F5C6E19E60640D`
- Candidate input: `ACA919EDDF0DC670758AECE846B0F8FCBE23A7342EADAE9F78333B2012434C88`
- Builder: `DF48EEC48720E337A0DE3C3D785337DD41E8A8D445E3C14DEF06B69CB73A5D3B`
- v4 CSV: `F0BFFB73F403B1ADEAD19ABFEB82D762B26923A6AC85A58EE0231CB86E56977D`
- Provenance: `E4D8496EA02DAD624464617EFEE5615D7F9E9C24A27FE7DFDA6E60D2E2E37B89`
- Review: `897B4C5820770EAC789BA50A88A05CFB931F7A3228F51E25D61473848FD64A57`
- Report: `D2E6D61346F21CF28AAEF1A9B327492C6AB63C352A52257762DFE8B2C208EBAB`

## Next

Use the 3,682 exact-GTIN nutrition no-match rows to create high-yield brand batches for free manufacturer/distributor/indexed web nutrition research, while continuing exact barcode research for the 1,753 unmatched nutrition-ready identities. Publish additions only in a new version.

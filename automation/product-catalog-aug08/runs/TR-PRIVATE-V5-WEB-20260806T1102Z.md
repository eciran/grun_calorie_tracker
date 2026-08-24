# TR-PRIVATE-V5-WEB-20260806T1102Z

- Stage: `TR-FREE-WEB-GROWTH`
- Result: `PASS_PRIVATE_TEST_V5`
- Scope: versioned `PRIVATE_TEST_ONLY` v5; v4 unchanged; no database or production mutation.

## Commands

```powershell
$py='C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
$env:PYTHONDONTWRITEBYTECODE='1'
& $py -m py_compile .\scripts\build-tr-retailer-private-v5-web.py
& $py .\scripts\build-tr-retailer-private-v5-web.py --v4-rich .\outputs\product-catalog-aug08\tr-retailer-test-v4-nutrition-20260806\tr-retailer-test-rich-v4-import.csv --candidates .\outputs\product-catalog-aug08\tr-free-web-brand-batch-ulker-v1-20260806\candidates.json --output-dir .\outputs\product-catalog-aug08\tr-retailer-test-v5-web-20260806
& $py .\scripts\build-tr-retailer-private-v5-web.py --v4-rich .\outputs\product-catalog-aug08\tr-retailer-test-v4-nutrition-20260806\tr-retailer-test-rich-v4-import.csv --candidates .\outputs\product-catalog-aug08\tr-free-web-brand-batch-ulker-v1-20260806\candidates.json --output-dir .\outputs\product-catalog-aug08\tr-retailer-test-v5-web-20260806
Get-FileHash .\scripts\build-tr-retailer-private-v5-web.py -Algorithm SHA256
Get-FileHash .\outputs\product-catalog-aug08\tr-retailer-test-v5-web-20260806\* -Algorithm SHA256
```

## Result and validation

- Base v4: 2,411 rows.
- Exact web candidates: 2; additions: 2; builder review rejects: 0.
- Added once each: Metro 36 g `8690504035909`, Çokomilk 24 g `8690504050124`.
- v5: 2,413 rows / 2,413 unique checksum-valid GTINs.
- Missing required core nutrition: 0.
- Evidence checksum validation is part of the builder; all four source evidence checksums passed.
- Deterministic rebuild: PASS.
- Remaining to 10,000: 7,587.

## Hashes

- v4 input: `F0BFFB73F403B1ADEAD19ABFEB82D762B26923A6AC85A58EE0231CB86E56977D`
- Candidate input: `6C2C9E0A7C93F9A6D0E43ADD7CBB24917F45B873E2C4F1BEED994CB9163A3F09`
- Builder: `22DE52A24CC96781B14D347DBCF1B047CA0514AA72EE98EEB7137873BADE582B`
- v5 CSV: `707AD0BDF6F394E70BD86072769696AC9ADA98BDF05F4A98C6ED5F4D25CC67F6`
- Provenance: `382437406F6A94A7864D27A4A1473D93ACD14331A7EB2C2EE55F7CF52E8253BB`
- Review: `A5338D955B09046EC0B16F3A9625B7955C763AAE07DC722E474E6078745F932F`
- Report: `7FF2ADAFBEA5377FE027516BA872596268D4F0B5C5949849FAD4A6B2C70C3767`

## Next

Continue the highest-yield Ülker exact web batch and accumulate only exact identity/package nutrition candidates for a later version; keep basis-conflicted rows in review.

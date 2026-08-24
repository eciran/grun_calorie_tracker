# TR-PRIVATE-V6-TORKU-20260806T1903Z

- Stage: `TR-FREE-WEB-GROWTH`
- Result: `PASS_PRIVATE_TEST_V6`
- Scope: versioned `PRIVATE_TEST_ONLY` v6; v5 unchanged; no database or production mutation.

## Commands

```powershell
$py='C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
$env:PYTHONDONTWRITEBYTECODE='1'
& $py -m py_compile .\scripts\build-tr-retailer-private-v6-torku.py
& $py .\scripts\build-tr-retailer-private-v6-torku.py --v5-rich .\outputs\product-catalog-aug08\tr-retailer-test-v5-web-20260806\tr-retailer-test-rich-v5-import.csv --candidates .\outputs\product-catalog-aug08\tr-free-web-brand-batch-torku-v1-20260806\candidates.json --output-dir .\outputs\product-catalog-aug08\tr-retailer-test-v6-torku-20260806
& $py .\scripts\build-tr-retailer-private-v6-torku.py --v5-rich .\outputs\product-catalog-aug08\tr-retailer-test-v5-web-20260806\tr-retailer-test-rich-v5-import.csv --candidates .\outputs\product-catalog-aug08\tr-free-web-brand-batch-torku-v1-20260806\candidates.json --output-dir .\outputs\product-catalog-aug08\tr-retailer-test-v6-torku-20260806
Get-FileHash .\scripts\build-tr-retailer-private-v6-torku.py -Algorithm SHA256
Get-FileHash .\outputs\product-catalog-aug08\tr-retailer-test-v6-torku-20260806\* -Algorithm SHA256
```

## Result and validation

- Base v5: 2,413 rows.
- Candidates/additions: 3/3; builder review rejects: 0.
- v6: 2,416 rows / 2,416 unique checksum-valid GTINs.
- All three selected Torku GTINs occur once; missing core nutrition: 0.
- Evidence checksum validation and strict GTIN/plausibility checks passed.
- Deterministic rebuild: PASS.
- Remaining to 10,000: 7,584.

The wrapper reuses the validated v5 free-web builder. Its inherited JSON key is named `v4Sha256`, but in this run that value is the v5 input hash shown below.

## Hashes

- v5 input: `707AD0BDF6F394E70BD86072769696AC9ADA98BDF05F4A98C6ED5F4D25CC67F6`
- Candidate input: `8714177F7B6547D1BC766FFD7DBB89C6877266261E91844F334D97F383134D18`
- Wrapper: `1F6046E1899E71A853A5B401F34ADF1E754363568F47761C9ADE21191E729DCC`
- v6 CSV: `C66D56797F123AB8DF387414B2D6238A72B24218C52B86CBE1DFCE0372771B2F`
- Provenance: `D74A1DE1A86A07EBFD64B5F3DD358E6FF3CFB07D6E7D1480423A6517811F54FF`
- Review: `A5338D955B09046EC0B16F3A9625B7955C763AAE07DC722E474E6078745F932F`
- Report: `3125019F15BE39267D77FC4BCF5B7FD7F4AD2A570241AE3EB4EFF4752F1206FC`

## Next

Continue exact web research against the remaining high-yield Torku/Çerezya/ETİ brand batches; accumulate verified candidates for a later version.

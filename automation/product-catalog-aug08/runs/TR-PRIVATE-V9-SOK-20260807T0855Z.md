# TR-PRIVATE-V9-SOK-20260807T0855Z

## Result

`PASS_PRIVATE_TEST_V9`

Built immutable PRIVATE_TEST_ONLY v9 from validated v8 and both Cepte Şok candidate slices. The combined input had 41 distinct exact identity candidates; 32 were net-new and nine already existed in v8 and were routed to review. Output contains 2,867 rows, 2,867 unique checksum-valid GTINs, zero duplicates and zero missing core-nutrition fields. Remaining to 10,000: 7,133.

## Commands

```powershell
C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe -m py_compile scripts/build-tr-retailer-private-v9-sok.py
C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe scripts/build-tr-retailer-private-v9-sok.py --v8-rich outputs/product-catalog-aug08/tr-retailer-test-v8-ozdilek-complete-20260807/tr-retailer-test-rich-v8-import.csv --candidate-files outputs/product-catalog-aug08/tr-bulk-sok-nutrition-v1-20260807/candidates.json outputs/product-catalog-aug08/tr-bulk-sok-nutrition-v2-retry-20260807/candidates.json --output-dir outputs/product-catalog-aug08/tr-retailer-test-v9-sok-20260807
```

## Counts

- Candidate rows / exact joins: 41 / 41
- Review overlaps / net-new: 9 / 32
- Output / unique valid GTIN / nutrition complete: 2,867 / 2,867 / 2,867
- Collisions / missing core: 0 / 0
- Remaining to 10,000: 7,133

## SHA-256

- Builder: `049B53042DBFB0EA8DFB13B3DA889EDCAD9A25F808369573CAE6E6D64601E972`
- CSV: `D6F31FC10DD3AAEDA09443BE9720AE1C714C97BF402721A38DAB7704C0B9995E`
- Report: `5907F8011B59EA65F8DFBAAE2DC88F8C88BDD1B221F8C91816714CE222BEF1D5`
- Provenance: `21F9E9A0F40BB05BFAC352B50A8840F0D8D1104E48B7F28EC2C240D0CD668C87`
- Review: `A14469616C228AECBCEBA07AF1F78E6D97512557206B41DCD5EA1B88ABFE3AF7`

## Next

Measure Ofix robots/sitemap/catalog access and exact-GTIN yield; if unavailable, use the existing frozen A101 identity index and search-index nutrition evidence without bypassing access controls.

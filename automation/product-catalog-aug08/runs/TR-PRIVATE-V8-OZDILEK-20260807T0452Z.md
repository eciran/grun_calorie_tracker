# TR-PRIVATE-V8-OZDILEK-20260807T0452Z

## Result

`PASS_PRIVATE_TEST_V8`

Built immutable PRIVATE_TEST_ONLY v8 from validated v7 and the remaining Özdilekteyim bulk candidate slice. Of 263 deduplicated candidate records, 227 were net-new and 36 were routed to review (existing identity or failed builder gate). The output contains 2,835 rows with 2,835 unique checksum-valid GTINs, no duplicate identities and no missing core nutrition. Remaining to 10,000: 7,165.

## Commands

```powershell
C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe scripts/build-tr-retailer-private-v7-bulk.py --v6-rich outputs/product-catalog-aug08/tr-retailer-test-v7-bulk-ozdilek-20260807/tr-retailer-test-rich-v7-import.csv --eti-candidates outputs/product-catalog-aug08/tr-free-web-brand-batch-eti-v1-20260806/candidates.json --bulk-candidates outputs/product-catalog-aug08/tr-bulk-ozdilek-nutrition-v2-remainder-20260807/candidates.json --output-dir outputs/product-catalog-aug08/tr-retailer-test-v8-ozdilek-complete-20260807
Move-Item -LiteralPath outputs/product-catalog-aug08/tr-retailer-test-v8-ozdilek-complete-20260807/tr-retailer-test-rich-v7-import.csv -Destination outputs/product-catalog-aug08/tr-retailer-test-v8-ozdilek-complete-20260807/tr-retailer-test-rich-v8-import.csv
```

## Counts

- Candidate records / exact identity inputs: 263 / 263
- Builder review/rejects / net-new: 36 / 227
- Output rows / valid unique GTINs / nutrition complete: 2,835 / 2,835 / 2,835
- Output collisions / missing core: 0 / 0
- Remaining to 10,000: 7,165

## SHA-256

- CSV: `0A91A9D0D7A8816CA861F434983869D01EEAF05355E093ED01C11598ECCC5B7B`
- Report: `4F066897E48489053352A502B197AFCEB8DD402A900700C4F9A3B7E499D41A9A`
- Provenance: `A8ADC9CFE7993C165A5C7E4F565E4FDE4060D86F7FB53142C2BBCACF25F889B0`
- Review: `B9627F3E8B76285ED10AAD0E68F8BDD10CE8C607A37532BC3422F8770CB4CC8C`

## Next

Use the bulk source inventory to intersect Cepte Şok and Ofix URLs/identities with the barcode-ready nutrition-missing queue, then implement a bounded source parser for the highest exact-GTIN yield.

# TR-PRIVATE-V7-BULK-OZDILEK-20260807T0250Z

## Result

`PASS_PRIVATE_TEST_V7`

Built immutable PRIVATE_TEST_ONLY v7 from v6 plus the ETİ and first bounded Özdilekteyim bulk candidates. The builder processed 251 deduplicated candidates, promoted 192 net-new rows, and routed 59 existing/invalid additions to review. Output has 2,608 rows, 2,608 unique checksum-valid GTINs and zero missing core-nutrition fields. Remaining to 10,000: 7,392.

## Commands

```powershell
C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe -m py_compile scripts/build-tr-retailer-private-v7-bulk.py
C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe scripts/build-tr-retailer-private-v7-bulk.py --v6-rich outputs/product-catalog-aug08/tr-retailer-test-v6-torku-20260806/tr-retailer-test-rich-v6-import.csv --eti-candidates outputs/product-catalog-aug08/tr-free-web-brand-batch-eti-v1-20260806/candidates.json --bulk-candidates outputs/product-catalog-aug08/tr-bulk-ozdilek-nutrition-v1-20260807/candidates.json --output-dir outputs/product-catalog-aug08/tr-retailer-test-v7-bulk-ozdilek-20260807
```

## Counts

- Scanned candidate records: 251
- Exact joins / valid output GTINs / nutrition-complete output: 251 / 2,608 / 2,608
- Collisions in output / missing core / builder rejects-review: 0 / 0 / 59
- Net-new: 192
- Achieved rich unique total / remaining: 2,608 / 7,392

## SHA-256

- Builder: `D85E93B8C6C7E03182A44821143DB756F5504AC625B19C5309962C77944E02B6`
- CSV: `58017F8B71AE24917C52376470E8AD49C84881370B1B6E6E6AD2B0AA0F9CB0A7`
- Report: `52107B7146FBA8725B4A743B97AB310D84C47D113276969E3B835009B4EC9555`
- Provenance: `F7BA6CED1DD9C295FFC56BCA22213F55C1BA4324C8B9E05F5C0EF2364EB613CE`
- Review: `DF59EEABDDD7D8841F677335D5869C06068F1DD831827A7E70D18A05158DAE21`

## Next

Resume the remaining 322 exact-GTIN Özdilekteyim pages using the bounded bulk fetcher, deduplicate against v7, and publish a later immutable overlay only after validation.

# TR-BULK-A101-CROSSSOURCE-20260807T1058Z

## Result

`PASS_BULK_CROSS_SOURCE_QUEUE`

Analyzed all 2,432 A101 exact-GTIN nutrition-missing targets against the full frozen free-source index. 515 have at least one non-A101 exact-GTIN reference; 296 are already present in validated v9, leaving 219 net-new targets with alternate-source evidence. No fuzzy join was used. The largest alternate-reference families are Özdilekteyim 304, IYAS 205, Barkod Bankası 183, Çağrı 82, Gürmar 64 and Sarıyer 59 (references overlap by GTIN). This stage creates the next bulk page/frozen-payload queue; v9 remains 2,867.

## Commands

```powershell
C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe -m py_compile scripts/analyze-tr-a101-cross-source-yield.py
C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe scripts/analyze-tr-a101-cross-source-yield.py --queue outputs/product-catalog-aug08/tr-free-nutrition-index-match-v1-20260805/exact-gtin-free-nutrition-candidates.json --source-index outputs/TR_Products/bulk-barcode-reconciliation/source-index.json --current-rich outputs/product-catalog-aug08/tr-retailer-test-v9-sok-20260807/tr-retailer-test-rich-v9-import.csv --output-dir outputs/product-catalog-aug08/tr-bulk-a101-cross-source-v1-20260807
```

## Counts

- A101 exact-GTIN targets scanned: 2,432
- With / without alternate source: 515 / 1,917
- Already in v9 / net-new alternate targets: 296 / 219
- Exact joins represented: 515 target groups
- Nutrition complete / promoted this stage: 0 / 0
- Collisions: retained as multi-reference groups; no identity collision promoted
- Achieved / remaining: 2,867 / 7,133

## SHA-256

- Script: `D8C147DC83E62BB52BDEEBFC89165670B4BD8524F83AD3392CF0A376EB9CB87B`
- Report: `48B43E985F11657F905EEAB620936EB958E138A4E7F66FBFC01F2867151526D1`
- Target queue: `450A2555C3EC826F012ED9BBF8462D00323A6602B524DB23FB036E90C0D3AA28`
- Input queue: `ACA919EDDF0DC670758AECE846B0F8FCBE23A7342EADAE9F78333B2012434C88`
- Source index: `0A05CC636EE14390D72B7DD7E68C8AE5147E71EA98451A2C55AC8F876AAFC926`
- Current v9: `D6F31FC10DD3AAEDA09443BE9720AE1C714C97BF402721A38DAB7704C0B9995E`

## Next

Inspect frozen IYAS/Çağrı/Gürmar/Sarıyer source payloads for explicit per-100 nutrition across the 219 net-new targets; if absent, use their existing public URLs in bounded source-specific page parsers.

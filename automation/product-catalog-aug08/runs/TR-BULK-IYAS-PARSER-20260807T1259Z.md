# TR-BULK-IYAS-PARSER-20260807T1259Z

Result: `PASS_BOUNDED_LOW_YIELD_DEFER`

The generic exact-GTIN page parser was applied to two public retailer families. IYAS: 1,297 available exact-GTIN targets, first 100 pages fetched HTTP 200, 0 errors, 0 explicit complete per-100 nutrition, 100 parse rejects. HTML inspection of an example found no Besin, kcal or Karbonhidrat fields. Per instruction to switch sources in the same run, Cagri was then tested: 125 available targets, first 100 pages HTTP 200, 0 errors, 0 explicit complete per-100 nutrition, 100 parse rejects. No fuzzy joins, candidates, collisions or promotions. v9 remains 2,867; 7,133 short.

Commands:
- sandbox attempt (timed out before artifact write): `python ... fetch-tr-retailer-exact-gtin-nutrition.py --source-family iyas --max-pages 300 --workers 3 --timeout 20 --retries 1`
- completed IYAS: `C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe scripts/fetch-tr-retailer-exact-gtin-nutrition.py --candidates outputs/product-catalog-aug08/tr-free-nutrition-index-match-v1-20260805/exact-gtin-free-nutrition-candidates.json --source-index outputs/TR_Products/bulk-barcode-reconciliation/source-index.json --output-dir outputs/product-catalog-aug08/tr-bulk-iyas-nutrition-v1-20260807 --source-family iyas --max-pages 100 --workers 4 --timeout 12 --retries 0`
- completed Cagri: same inputs with `--output-dir outputs/product-catalog-aug08/tr-bulk-cagri-nutrition-v1-20260807 --source-family cagri --max-pages 100 --workers 4 --timeout 12 --retries 0`.

Hashes: parser `FED356EB927547A06FABDA2B55721E0DB7A9C9BDDCFD4E659B5372D961918642`; IYAS pages `A95F053D0D4788F27761C580E6B6F26F868310CECDFC47931962FA7498A5ACC1`, report `8029313E628438C9944FC4180C5CCA4F1F44148AB9ADEDAEC511F9BBF8135C75`; Cagri pages `0941A125F0E4C849B2627CA795272C9D998460F0786C094C1F23525A37F772D3`, report `6346FFF9B96B5C9CD98DF5CFFE1AD8CFDFDE988DDE0642EC9AECF4AB134269E9`.

Next: bounded Gurmar/Sariyer parser sample; if still zero, stop spending fetch budget on these retailer templates and bulk-join manufacturer/OFF indexed nutrition by exact GTIN.

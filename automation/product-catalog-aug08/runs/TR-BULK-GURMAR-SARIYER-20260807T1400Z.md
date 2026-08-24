# TR-BULK-GURMAR-SARIYER-20260807T1400Z

Result: `PASS_RETAILER_TEMPLATE_EXHAUSTED_ZERO_YIELD`

Continued the prior low-yield audit with two additional exact-GTIN retailer families. Gurmar exposed 133 exact targets; the first 100 pages all returned HTTP 200, but none exposed explicit complete per-100 kcal/protein/fat/carbs. Sariyer had 94 targets; 89 returned 200, 5 failed, and none of the 89 parsable pages exposed complete per-100 core nutrition. Across this slice: 194 pages, 189 fetched, 5 errors, 0 nutrition-complete, 189 parse rejects, 194 exact joins, 194 valid GTINs, 0 collisions and 0 net-new. No promotion; v9 remains 2,867 and 7,133 short.

Commands: `C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe scripts/fetch-tr-retailer-exact-gtin-nutrition.py --candidates outputs/product-catalog-aug08/tr-free-nutrition-index-match-v1-20260805/exact-gtin-free-nutrition-candidates.json --source-index outputs/TR_Products/bulk-barcode-reconciliation/source-index.json --output-dir <family-output> --source-family <gurmar|sariyer> --max-pages 100 --workers 4 --timeout 12 --retries 0`.

Hashes: Gurmar pages `04A134FA373719B80126B8A25E42872E9CB393F55D7AE89BA2596F93071B1165`, report `3B20917D84EB3856C1DB2E25E2351815E199A9A81496392A2997E7D96074CFB4`; Sariyer pages `9CD2BC880082F2210033D8AF8A45E1E8A132A0F02919B6416F865C6FC392874D`, report `C8B00EE2AA868F190D3F60F2391B001CE20F58AF33549EC2A98680E69A8005FF`.

Decision: stop spending fetch budget on IYAS/Cagri/Gurmar/Sariyer templates after 394 sampled pages yielded zero nutrition. Next: bulk manufacturer/brand-owner URL discovery and exact-GTIN nutrition join for the highest-count barcode-ready brands; frozen OFF has already been exhausted.

# TR-BULK-SOK-OFIX-INTERSECT-20260807T0553Z

## Result

`PASS_BULK_EXACT_INTERSECTION`

Downloaded the robots-allowed official Cepte Şok market-product sitemap once and performed an offline exact normalized full-name/package URL-slug join against the 4,463 barcode-ready nutrition queue. Of 20,665 product URLs, 71 matched exactly and uniquely to 71 GTIN queue rows. No fuzzy identity was accepted. This run produced the bounded page-fetch queue; it did not mutate v8, so achieved rich total remains 2,835.

## Commands

```powershell
curl.exe -L --max-time 30 -A "GRUNCatalogResearch/1.0" https://www.sokmarket.com.tr/sitemap/market-product_1.xml -o outputs/product-catalog-aug08/tr-bulk-sok-intersect-v1-20260807-sitemap.xml
C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe -m py_compile scripts/intersect-tr-sok-sitemap.py
C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe scripts/intersect-tr-sok-sitemap.py --sitemap outputs/product-catalog-aug08/tr-bulk-sok-intersect-v1-20260807-sitemap.xml --queue outputs/product-catalog-aug08/tr-free-nutrition-index-match-v1-20260805/exact-gtin-free-nutrition-candidates.json --output-dir outputs/product-catalog-aug08/tr-bulk-sok-intersect-v1-20260807
```

## Counts

- Scanned sitemap URLs: 20,665
- Parsed product identities: 20,665
- Queue rows: 4,463
- Exact joins / unique GTINs: 71 / 71
- Collisions / fuzzy accepts / rejects as matches: 0 / 0 / 20,594
- Nutrition complete / net-new promoted: 0 / 0 (page parsing is the next stage)
- Achieved / remaining: 2,835 / 7,165

## SHA-256

- Script: `60D4B8B7008BC6E5A811DCAE06A1D72FD4872EDF8E973F511901BD1DA87EA438`
- Sitemap: `FF48BFFC1D71B0ED9F038EF742152334194FF54E65397D89E74CBBB57C43938C`
- Report: `C8FBC448C43878F7A759DC571336A4BB3D23FA35D391FDA7220CC8E27F02659C`
- Exact matches: `49D2CF765CEA43EE7B6C835CD7425BAA6BA50A3E2FF1BC5B661AB57A954EA143`

## Next

Fetch the 71 exact-match Cepte Şok pages with bounded concurrency, parse only explicit per-100 core nutrition, preserve raw hashes, and promote successful rows in a later immutable overlay.

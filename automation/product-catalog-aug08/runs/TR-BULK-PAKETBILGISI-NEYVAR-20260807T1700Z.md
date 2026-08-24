# TR-BULK-PAKETBILGISI-NEYVAR-20260807T1700Z

Result: `PASS_INDEX_DISCOVERY_REFERENCE_ONLY`

Paketbilgisi robots returned 200 empty, sitemap 404; homepage returned 200 / 52,473 bytes with one `/urunler` link, but that directory returned HTTP 500, so the source was deferred without retry loops. neyvar robots returned 200 and explicitly allows search/reference while disallowing GPTBot and AI training; therefore only the permitted sitemap/search-index layer was used and no detail pages were fetched. The 1,772,489-byte sitemap contained 10,005 product URLs. Exact normalized full-name+package slug intersection against 4,463 barcode-ready nutrition gaps produced 11 exact URL references / 11 checksum-valid GTINs, 0 collisions. Since sitemap URLs do not provide nutrition, 0 products were promoted.

Commands: read-only robots/sitemap/homepage checks with `Invoke-WebRequest`; non-empty `.part` sitemap download then rename; `python scripts/intersect-tr-neyvar-sitemap.py --queue outputs/product-catalog-aug08/tr-free-nutrition-index-match-v1-20260805/exact-gtin-free-nutrition-candidates.json --sitemap outputs/product-catalog-aug08/tr-bulk-neyvar-index-v1-20260807/neyvar.sitemap.xml --out outputs/product-catalog-aug08/tr-bulk-neyvar-index-v1-20260807`.

Counts: neyvar product URLs 10,005; queue rows 4,463; exact joins 11; valid GTINs 11; collisions 0; invalid-GTIN rejects 71; detail pages scanned 0; nutrition-complete/net-new 0. Paketbilgisi homepage pages scanned 1, parsed product-directory links 1, directory failures 1. Achieved v9 2,867; 7,133 remaining.

Hashes: matcher `460C03CB0A26B28C80C35BC50A04965E579231EAA1F0207E90334BF73170B8B9`; sitemap `768A8C5E210D90B7749C35B3EEF1B101552F680190827BD267382BC161FAC7CE`; exact index `356ABE3620D2C6D57AE3247BC2E3095CD8990D9BB61E1A935F73B2EADAE6F8C2`; report `E73BC3F345C717EEA95AE7527D5D7E420BFA92E80A650C7C168FF86125516739`.

Next: retain the 11 neyvar URLs as identity references only; pivot to another source that permits detail-page extraction and exposes explicit per-100 fields, prioritizing frozen/indexed OFF-compatible datasets or brand-owner feeds.

# TR-BULK-OFIX-DISCOVERY-20260807T0957Z

## Result

`PASS_LOW_YIELD_DEFER`

Ofix robots returned 200 and allows public paths; its declared CDN sitemap root and product sitemap returned 200. The product sitemap contains 88,849 URLs. Offline exact normalized full-name/package joining against 4,463 nutrition-missing queue rows yielded only two exact page identities; one has a usable GTIN, while the other queue row carries only a MIGROS source identifier. Both pages returned successfully but neither exposed explicit complete per-100 core nutrition. Ofix is therefore deferred as low-yield; v9 remains 2,867.

## Commands

```powershell
curl.exe -L --max-time 30 -A "GRUNCatalogResearch/1.0" https://www.ofix.com/robots.txt -o outputs/product-catalog-aug08/tr-bulk-ofix-discovery-v1-20260807/robots.txt
curl.exe -L --max-time 30 -A "GRUNCatalogResearch/1.0" https://cdn.ofix.com/sitemaps/sitemap.xml -o outputs/product-catalog-aug08/tr-bulk-ofix-discovery-v1-20260807/sitemap-root.xml
curl.exe -L --max-time 45 -A "GRUNCatalogResearch/1.0" https://cdn.ofix.com/sitemaps/product-sitemap.xml -o outputs/product-catalog-aug08/tr-bulk-ofix-discovery-v1-20260807/product-sitemap.xml
C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe scripts/intersect-tr-sok-sitemap.py --source OFIX --sitemap outputs/product-catalog-aug08/tr-bulk-ofix-discovery-v1-20260807/product-sitemap.xml --queue outputs/product-catalog-aug08/tr-free-nutrition-index-match-v1-20260805/exact-gtin-free-nutrition-candidates.json --output-dir outputs/product-catalog-aug08/tr-bulk-ofix-intersect-v1-20260807
```

## Counts

- Scanned/parsed sitemap URLs: 88,849 / 88,849
- Queue rows / exact joins / usable valid GTINs: 4,463 / 2 / 1
- Page fetches / nutrition complete / parse rejects: 2 / 0 / 2
- Collisions / net-new: 0 / 0
- Achieved / remaining: 2,867 / 7,133

## SHA-256

- Robots: `7BAC954D1DEDEB2C8530DA7067D9BE659D909A778492D952396C62F656C8B30E`
- Sitemap root: `ADE2C2DB45F3F029BD249676E0C8666CDD2C16F4BD349DA2F9B330B1B02F11DE`
- Product sitemap: `CEA29FF548E13B8609A4F9CB45B842A8CC156811FC1613F0FFCB6F4085188738`
- Intersection script: `00150193A41DB78F0C476576D8A18E2FF03B29C50A4EA5F4D999477F0A2FA302`
- Report: `D3054AACCAE7DD3433BEDD2F5080162A439DF977FEE5EB4283DDCF076DBD1AB8`
- Matches: `9032563037710C08F03EA8E5A8F90761498D41C53ED8E1F5B841E086ECCE36D6`

## Next

Switch to the 2,432 exact-GTIN A101 target URLs already identified in the frozen index. Use only existing index/search evidence first; direct HTML previously showed no embedded nutrition.

# TR-BULK-MANUFACTURER-DISCOVERY-20260807T1500Z

Result: `PASS_DISCOVERY_LOW_EXACT_YIELD`

Read-only robots/sitemap discovery covered four official manufacturers. Ulker returned 403 for robots/sitemap and was not bypassed. Torku robots and sitemap returned 200; ETI robots returned 200 but sitemap 404; Sutas robots and sitemap returned 200. Torku and Sutas snapshots contain 1,673 URLs total and were frozen with SHA-256. An initial matcher audit exposed an empty-root-slug false-positive condition; it was fixed before accepting any target. The corrected exact normalized full-name/package slug join against 4,463 barcode-ready nutrition-gap rows produced 1 unique exact target, 1 collision and 1 valid GTIN. The one exact Sutas Labne 700 g page returned HTTP 200 but did not expose explicit complete per-100 core nutrition. Net-new 0; v9 remains 2,867 and 7,133 short.

Commands: official robots/sitemap `Invoke-WebRequest` checks; dated `.part` downloads renamed after non-empty verification; `python scripts/intersect-tr-manufacturer-sitemaps.py --queue outputs/product-catalog-aug08/tr-free-nutrition-index-match-v1-20260805/exact-gtin-free-nutrition-candidates.json --output outputs/product-catalog-aug08/tr-bulk-manufacturer-discovery-v1-20260807 --sources torku=.../torku.sitemap.xml sutas=.../sutas.sitemap.xml`; exact page fetch via `fetch-tr-retailer-exact-gtin-nutrition.py --source-family sutas --max-pages 10 --workers 1 --timeout 15 --retries 1`.

Counts: sitemap URLs 1,673; queue rows 4,463; accepted exact targets 1; collisions 1; pages scanned/fetched 1/1; valid GTIN 1; nutrition complete 0; rejects 1; net-new 0.

Hashes: matcher `059B76EDD0425BB3A16ADBB4CFB0485DA33A6F626ECAE93E64994A4231C396BB`; Torku sitemap `675DC238191C29158661E23CBF1BC0B32D417D54ABD7ADFB59ABE35235E964FC`; Sutas sitemap `27E1CCB4CC8FB179FAD0CB1E4C63CCAAE872F4E189BDBCD236F86B5B7C95FB85`; source index `965A12ED6A67488750B88A4729D4A48875366A49262C93FC0DBA3FD7458A9771`; discovery report `8EB9C0C7D0F74571C37D1B378D6F60CAC05D4CE36C84B13BC0845B55E42620BD`; page report `208A17EAC81832382E70B0870B6BB208C3799198268549D44497DC4549631FE1`.

Next: use Torku's official domestic product catalog PDF as a bounded structured source audit; only exact GTIN plus explicit per-100 rows may promote.

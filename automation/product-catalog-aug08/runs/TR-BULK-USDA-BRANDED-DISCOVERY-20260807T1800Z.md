# TR-BULK-USDA-BRANDED-DISCOVERY-20260807T1800Z

Result: `PASS_BULK_SOURCE_ZERO_OVERLAP`

Discovered the current official USDA FoodData Central branded-food bulk CSV (2026-04-30). HEAD returned 200, 448,767,220 bytes, Last-Modified 2026-04-29 and ETag `69f28dc6-1abfa4f4`. Downloaded to `.part`, verified the exact Content-Length, renamed, and SHA-256 locked. A streaming matcher scanned all 1,999,950 branded rows and exact-joined normalized UPC/GTIN variants against the 3,682 valid-GTIN nutrition-gap queue. It found zero exact GTIN overlap, so the large US-centric source cannot improve this TR queue. No fuzzy/name joins and no promotion.

Commands: official FoodData Central download-page read; HEAD and bounded download of `https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_branded_food_csv_2026-04-30.zip`; `python scripts/match-tr-barcode-ready-to-usda-branded.py --queue outputs/product-catalog-aug08/tr-free-nutrition-index-match-v1-20260805/exact-gtin-free-nutrition-candidates.json --zip outputs/product-catalog-aug08/tr-usda-branded-20260430/FoodData_Central_branded_food_csv_2026-04-30.zip --out outputs/product-catalog-aug08/tr-usda-branded-match-v1-20260807`.

Counts: source bytes 448,767,220; branded rows scanned 1,999,950; queue GTINs 3,682; exact GTIN groups/rows 0/0; nutrition-complete 0; collisions/rejects 0; net-new 0. Achieved v9 2,867; 7,133 remaining.

Hashes: source ZIP `26050A5D03197469813754743A21EE0FAD4CCF22B6AAC2A995846A987719FC49`; matcher `5B05DBB90A77BD6E125635E6B17D6020200A2D694940ABCF30C739FF4143B4BD`; report `281A3F830295282419AD2522E60638DC8867D55D23361B7AF587CA5D08215072`.

Next: stop USDA for this TR queue; prioritize European/Turkish GTIN coverage in another downloadable/open bulk source, or switch to final August 8 verification if the date gate is reached.

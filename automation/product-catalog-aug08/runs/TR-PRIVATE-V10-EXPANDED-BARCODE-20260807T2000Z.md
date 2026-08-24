# TR-PRIVATE-V10-EXPANDED-BARCODE-20260807T2000Z

Result: `PASS_PRIVATE_TEST_V10`

Built immutable PRIVATE_TEST_ONLY v10 from v9 and the expanded nutrition-only exact-barcode candidates. Policy prefilter processed 289 GTIN groups: 13 multi-catalog-target groups and 61 manual/policy exclusions went to review, leaving 215 safe candidates. The versioned builder routed 129 existing-v9 overlaps to review and added 86 net-new exact-GTIN products. Output validation passed at 2,953 rows / 2,953 unique checksum-valid GTINs, with 0 missing name/brand and 0 missing core per-100 nutrition. Licensed-default was not modified or mixed.

Command: `python scripts/build-tr-retailer-private-v10-expanded-barcode.py --v9-rich outputs/product-catalog-aug08/tr-retailer-test-v9-sok-20260807/tr-retailer-test-rich-v9-import.csv --matches outputs/product-catalog-aug08/tr-all-nutrition-only-v1-20260807/exact-free-index-barcode-candidates.json --catalog outputs/TR_Products/merged-a101-migros-iyas-deduplicated/products.json --excluded outputs/TR_Products/barcode-enrichment/manual-excluded-products.json --output-dir outputs/product-catalog-aug08/tr-retailer-test-v10-expanded-barcode-20260807`; independent PowerShell CSV uniqueness/completeness audit.

Counts: base 2,867; GTIN groups 289; duplicate-target review 13; manual/policy excluded 61; candidate rows 215; v9 overlaps/builder review 129; net-new 86; output 2,953; unique GTIN 2,953; missing core 0; missing identity 0; remaining 7,047.

Hashes: builder `A1E3938257A68E0CF908A0DF5B57F0401C540D2F1E38D1421A52F6E208FD7211`; CSV `11A1276169637B26BC74029C75384354E77A687FA9FA5FFBF02D2DF0945E34DB`; report `4C39665C6676616A901DDE766372033B821B145BAE720D39AA95031A5376BAFA`; prefilter review `BF38CFEE401E349D1A4BBEB0A59227A34B3343D3D4DE8F2866BA743EEEB07799`; provenance `FEC961EEE72D19EAFAB26EC9DAE47E85A93E5070EA19159046F904395D6FDDED`.

Next: at the next run, perform final pre-August-8 bundle/hash monitoring or one last exact-source batch if time remains; on/after August 8 execute final read-only verification and handoff classification.

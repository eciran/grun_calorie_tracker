# TR-BULK-BARCODE-QUEUE-EXPANSION-20260807T1900Z

Result: `PASS_EXPANDED_EXACT_CANDIDATES`

Expanded barcode completion beyond the old 1,925 active priority queue by deterministically exporting every nutrition-complete row in the 13,173-row merged catalog that lacks a checksum-valid GTIN. This broader audit found 3,500 rows (the published 3,039 capacity number applies additional active/exclusion policy). Exact brand/name/package and exact name/package matching against all 30,150 frozen free-source identities produced 304 exact-single rows / 289 GTIN groups, 112 collision-review rows and 3,084 no-matches. After one-row-per-GTIN and v9 dedupe: 276 unique-target GTINs, 13 duplicate-target groups, 131 v9 overlaps and 145 projected net-new review candidates. They are not yet promoted because the next immutable builder must reapply manual exclusions, multipack and active-row policy.

Commands: `python scripts/export-tr-all-nutrition-only-queue.py --catalog outputs/TR_Products/merged-a101-migros-iyas-deduplicated/products.json --out outputs/product-catalog-aug08/tr-all-nutrition-only-v1-20260807/queue.tsv`; `python scripts/match-tr-nutrition-ready-to-free-barcode-index.py --queue .../queue.tsv --source-index outputs/TR_Products/bulk-barcode-reconciliation/source-index.json --output-dir .../tr-all-nutrition-only-v1-20260807`; PowerShell one-row-per-GTIN/v9 comparison.

Counts: catalog 13,173; broad nutrition-only 3,500; source rows 30,150; exact-single 304; unique GTIN 289; collision rows 112; no-match 3,084; unique one-row GTIN 276; duplicate-target groups 13; v9 overlaps 131; projected pre-policy net-new 145; projected ceiling 3,012; achieved remains 2,867 / 7,133 short.

Hashes: exporter `4CB34CB8E86807D2D9AF76D7A3CB596C5ED0805332FB3B7158A4E13AFD276164`; queue `E522E4EE3FB4FED2BD9D9077520D3ABC507CE952C58F30BD303483A456438819`; candidates `91F34B0D2658B9D5EA95650385C146C97488C567F20F6210CD51C697FBF376F7`; report `BD5CADB40EA12F199A5BCD200658CA1E2E22978F396B4E8575837BB58B0D3F3B`.

Next: immutable PRIVATE_TEST_ONLY v10 builder from only the 145 candidates that also pass active/manual-exclusion/multipack policy, with collisions and overlaps routed to review; validate exact counts and hashes.

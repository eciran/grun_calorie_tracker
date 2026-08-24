# Run BRANDED-TR-BARCODE-PRIORITY-20260803T0653Z

- Stage: `TR-GROWTH-ENRICHMENT`
- Result: `PASS_ACQUISITION_QUEUE`
- Branch: `feature/unified-product-intake-review`
- Catalog/database/network mutation: none
- Barcode assignments: 0

## Build command

```powershell
$env:PYTHONDONTWRITEBYTECODE='1'
$py='C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
& $py .\scripts\prioritize-tr-barcode-evidence-queue.py --queue .\outputs\TR_Products\barcode-enrichment\primary-catalog-barcode-queue.json --catalog .\outputs\TR_Products\merged-a101-migros-iyas-deduplicated\products.json --excluded .\outputs\TR_Products\barcode-enrichment\manual-excluded-products.json --enrichment .\outputs\TR_Products\barcode-enrichment\migros-barcode-enrichment.json --confirmed .\outputs\TR_Products\barcode-enrichment\migros-barcode-confirmed.json --external-verified .\outputs\TR_Products\barcode-enrichment\external-verified-barcodes.json --manual-review .\outputs\TR_Products\barcode-enrichment\manual-barcode-review-results.json --web-search-state .\outputs\TR_Products\barcode-enrichment\web-search-state.json --source-registry .\sample-data\manifests\tr-food-source-registry-v1.json --output-dir .\outputs\product-catalog-aug08\tr-barcode-evidence-priority-v1-20260803 --expected-queue-sha256 CBD4C78F1C166CFB1B0089297695269A0107C717F28D5CBD42F24AF89AC32EC2 --expected-catalog-sha256 56B9F149953FC8B38FD302AC024150F46214DF99F2B588AB4DCA37AD71563933 --expected-excluded-sha256 C1E6F63D1405D76510BC3D811F9834AEC32C64CB36F4328CF75E2699FEB7893A --expected-enrichment-sha256 73C14E3A340AE156D450DEDBE10C10E6D1D5F18E7E6C0B2162D562C6FDD92914 --expected-confirmed-sha256 828397C61A37B6A31B638BE6B36F6F6D6D43EEE29CE6AE88A989D296C5CAF51A --expected-external-sha256 2ABAE61B01A5C84862DAD6824041F8DAE1AD4F1ED998E86CA33FF606A4FD262C --expected-manual-sha256 2273B857A8F4B82152D8DC2193AD9EDFAD73FB1A439A1DA11D96595E5AE9B2F2 --expected-web-state-sha256 CBD319469FEEB27D11F78FF87E17F9224D6F7D96B08C5D1442105144508C2114 --expected-registry-sha256 887B3B56AF4A3ABAECB809277744D7299E5CA965175749885890263B9E9B7896 --generated-at 2026-08-03T05:53:08.526Z
```

The same command was executed a second time with the fixed timestamp; all five
artifact hashes were identical.

## Independent validation command

```powershell
$q=Import-Csv .\outputs\product-catalog-aug08\tr-barcode-evidence-priority-v1-20260803\tr-nutrition-ready-barcode-evidence-priority.tsv -Delimiter "`t"
$e=Import-Csv .\outputs\product-catalog-aug08\tr-barcode-evidence-priority-v1-20260803\tr-existing-barcode-evidence-review.tsv -Delimiter "`t"
$b=Import-Csv .\outputs\product-catalog-aug08\tr-barcode-evidence-priority-v1-20260803\tr-brand-evidence-acquisition-plan.tsv -Delimiter "`t"
```

The independent gate required exactly 1,925 rows, zero assigned barcodes, zero
promotable/fuzzy-promoted rows, seven checksum-valid review-only candidate
sets, and deterministic hashes.

## Evidence

- Required-barcode queue: 2,581; complete plausible nutrition: 1,925.
- All 1,925 nutrition-ready rows are currently Migros-source catalog records.
- Existing checksum-valid GTIN candidates: 7 rows, all review-only.
- Authorized evidence with content checksum/admin approval: 0.
- P0 review candidate rows: 7; P1 brand/GS1 batches: 345; P2 brand/supplier: 994; P3 supplier/user-label: 579.
- Brand acquisition batches: 442. Highest-yield: Migros 121, Eti 37, Golf 27, Polonez 27, Pinar 25, Reyondan 24, Torku 23.
- Assigned barcodes: 0; promotable: 0; blocked: 1,925.
- Product priority SHA-256: `88E68826295A6EBA6A7C20FFBBBC3AE4CB596F90DF5B59EEB3E14DE4AF5A90C5`
- Evidence history SHA-256: `8FF080942D1768376C8EC0908F6570605581CCBB69E3944A1D058CA2B4C37A9B`
- Brand plan SHA-256: `29377AA799C85DFBAAF8381CF743BD3BA913B6434E54E8AA2FE4C8A0CBEB5590`
- Report SHA-256: `AF0799A044A35FF5CD12AAA71C73BAEE8AE960C001BA5FE59F908841B2C06B39`
- Manifest SHA-256: `981460263D14FBCC6CAA8C96F8AC80B940B1EC5428EC32C22CE856DE86C4CFEF`

## Stage decision and next action

The unattended, file-only TR enrichment scope is complete. Exact market
references remain blocked by rights/admin decisions; OFF nutrition v2 is built;
the 1,925 identity gap is prioritized; and the 20,699 supplemental GTINs remain
research-only. The next automation run may enter `D5-BUILD-GATES` and isolate
one evidenced pre-existing full-suite root without touching dirty files.

# TR-FREE-NUTRITION-INDEX-MATCH-20260805T2302Z

- Stage: `TR-FREE-WEB-GROWTH`
- Result: `PASS_EXACT_NUTRITION_CANDIDATE_EXPANSION`
- Scope: read-only exact-GTIN nutrition matching; no catalog/database mutation.

## Commands

```powershell
$py='C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
$env:PYTHONDONTWRITEBYTECODE='1'
& $py .\scripts\match-tr-barcode-ready-to-free-nutrition-index.py --queue .\outputs\TR_Products\nutrition-enrichment\queue.json --source-index .\outputs\TR_Products\bulk-barcode-reconciliation\source-index.json --workspace . --output-dir .\outputs\product-catalog-aug08\tr-free-nutrition-index-match-v1-20260805
& $py .\scripts\match-tr-barcode-ready-to-free-nutrition-index.py --queue .\outputs\TR_Products\nutrition-enrichment\queue.json --source-index .\outputs\TR_Products\bulk-barcode-reconciliation\source-index.json --workspace . --output-dir .\outputs\product-catalog-aug08\tr-free-nutrition-index-match-v1-20260805
Get-FileHash .\scripts\match-tr-barcode-ready-to-free-nutrition-index.py -Algorithm SHA256
Get-FileHash .\outputs\product-catalog-aug08\tr-free-nutrition-index-match-v1-20260805\* -Algorithm SHA256
```

The initial development pass scanned JSON sources only and reported zero matches plus one unreadable TSV. The matcher was corrected to parse the frozen OFF TSV directly; the two recorded final runs are deterministic and have zero missing/unreadable files.

## Counts

- Queue: 4,463 barcode-ready/nutrition-missing rows; 5,852 target GTINs.
- Frozen sources: 27 relevant files; 69,624 source products/rows scanned.
- Exact single nutrition: 766 rows / 722 unique GTINs.
- Nutrition conflicts: 15 rows; quarantined from automatic use.
- No exact nutrition: 3,682 rows.
- Duplicate target identity: 44 GTIN groups / 88 rows; review-only.
- One-row-per-GTIN candidates: 678.
- Already present in v3: 634.
- Projected safe net-new for versioned v4: 44.
- Achieved rich unique total remains 2,367 until v4 is built and validated; projected v4 total 2,411, leaving 7,589 to 10,000.

## Hashes

- Queue: `60A8F1EFACE2BF824319EAD17DA2434214BBE2A9195B53AB0D69D921EABC82FF`
- Source index: `0A05CC636EE14390D72B7DD7E68C8AE5147E71EA98451A2C55AC8F876AAFC926`
- Matcher: `272A9AD1DAE204AA72CA9B0F0ABAC539970534E0D8898F03BC6960C22E885C52`
- Candidates: `ACA919EDDF0DC670758AECE846B0F8FCBE23A7342EADAE9F78333B2012434C88`
- Report: `521A447C8C7AD28A4B3F65038528A9EA13552AC1C175BC60BE299B575D0E7043`

## Next

Build a new `PRIVATE_TEST_ONLY` v4 from only the 44 projected net-new, one-row-per-GTIN exact candidates; exclude the 15 conflicts and 44 duplicate-target GTIN groups, retain provenance, and run the strict validator. Do not modify v3.

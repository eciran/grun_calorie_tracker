# Run BRANDED-TR-NUTRITION-V2-20260803T0252Z

- Stage: `TR-GROWTH-ENRICHMENT`
- Result: `PASS_PRIVATE_TEST_V2`
- Branch: `feature/unified-product-intake-review`
- Database/network mutation: none
- v1 mutation: none; all three v1 hashes reverified

## Build command

```powershell
$env:PYTHONDONTWRITEBYTECODE='1'
$py='C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
& $py .\scripts\build-tr-retailer-private-v2.py --v1-manifest .\outputs\product-catalog-aug08\tr-retailer-test-v1\manifest.json --v1-rich .\outputs\product-catalog-aug08\tr-retailer-test-v1\tr-retailer-test-rich-import.csv --v1-max .\outputs\product-catalog-aug08\tr-retailer-test-v1\tr-retailer-test-max-import.csv --candidates .\outputs\product-catalog-aug08\tr-off-nutrition-match-20260802\tr-retailer-off-nutrition-candidates.json --output-dir .\outputs\product-catalog-aug08\tr-retailer-test-v2-off-nutrition-20260803 --expected-v1-manifest-sha256 C9EB84C56B53780EA55DC849FFB41DAB719331B3D7F8DFAAAF92DCA46CCEDA36 --expected-v1-rich-sha256 C23A7984CA0F8771600D78085F4972830FC5DD8239A081D708258FBDD1AD6611 --expected-v1-max-sha256 70173ED40B13BD34028958912C3FF0DE7B687AFABA1397F5DE30FB6209C247FF --expected-candidates-sha256 0CA40B870A05D26E7CB0892E9D998D113BA1557637211AEC456FE9CDF9969ECA --generated-at 2026-08-03T01:52:34.861Z
```

The same command was executed a second time with the fixed timestamp. All six
artifact hashes remained identical.

## Independent validation command

```powershell
$v1=Import-Csv .\outputs\product-catalog-aug08\tr-retailer-test-v1\tr-retailer-test-rich-import.csv
$v2=Import-Csv .\outputs\product-catalog-aug08\tr-retailer-test-v2-off-nutrition-20260803\tr-retailer-test-rich-v2-import.csv
$prov=Import-Csv .\outputs\product-catalog-aug08\tr-retailer-test-v2-off-nutrition-20260803\tr-off-nutrition-field-provenance.tsv -Delimiter "`t"
$dup=Import-Csv .\outputs\product-catalog-aug08\tr-retailer-test-v2-off-nutrition-20260803\tr-off-nutrition-duplicate-gtin-review.tsv -Delimiter "`t"
$identity=Import-Csv .\outputs\product-catalog-aug08\tr-retailer-test-v2-off-nutrition-20260803\tr-off-nutrition-primary-identity-review.tsv -Delimiter "`t"
```

Validation additionally required unique checksum-valid GTINs, complete
plausible core nutrition, OFF provider plus source URL on every added row,
one provenance row per addition, and exact source/input hashes.

## Evidence

- Candidate rows: 843; unique candidate GTINs: 797.
- Duplicate groups: 46 / 92 rows, all excluded to duplicate review.
- Selected-primary identity conflicts: 53 rows, all excluded to identity review.
- Already-rich rows retained unchanged: 52.
- v1 rich: 1,605; safe net-new: 646; v2 rich: 2,251.
- OFF strict plus v2 rich private union: 3,335.
- Production import authorized: false.
- Manifest: `102AE0EF94ED218A8EB29EA0EEBF9F4121DE5A6C98AD32A779EEDA705B5B02B6`
- v2 CSV: `211B879B9E37BEB98422293FD164401B3033AC2059B93EE0DFA7A6E205916085`
- provenance: `7D453367E08B2B1307BA9FBB40950FB74579E1DFB87C2AE44D025E971C100993`
- duplicate review: `F5609F7F5C9B779B288B487F65CAFA2C778FB0291C61699A9C842E4DD43A34F8`
- identity review: `D42372642A214A21877DADCFB21AA3C37A8A808DDD9E3352ABB7AD74E351CC29`
- validation: `C81DA29BF91BEB06A4CA7144B190CD3AA45A4989C6EF817C4013C4DB4AA0175A`

## Next action

Prioritize the 1,925 nutrition-complete rows in the 2,581 required-barcode
queue using only GS1, brand-owner, authorized supplier, or user-label evidence.
Fuzzy matching remains review-only and cannot create a GTIN.

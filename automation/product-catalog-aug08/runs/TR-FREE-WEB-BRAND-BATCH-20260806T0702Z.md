# TR-FREE-WEB-BRAND-BATCH-20260806T0702Z

- Stage: `TR-FREE-WEB-GROWTH`
- Result: `PASS_FREE_WEB_CANDIDATES`
- Scope: highest-yield Ülker nutrition no-match brand batch; research/candidate evidence only; no catalog or database mutation.

## Commands and searches

```powershell
$d=Get-Content .\outputs\product-catalog-aug08\tr-free-nutrition-index-match-v1-20260805\exact-gtin-free-nutrition-candidates.json -Raw|ConvertFrom-Json
$d|Where-Object status -eq 'NO_EXACT_GTIN_NUTRITION'|Group-Object brand|Sort-Object Count -Descending|Select-Object -First 30 Count,Name
```

Search queries executed through the web index:

- `8690504142959 besin değerleri`
- `8690504157649 besin değerleri`
- `8683508151959 besin değerleri`
- `8690766149871 besin değerleri`
- `"Ülker Sütlü Çikolata 60 G" besin değerleri`
- `"Ülker Antep Fıstıklı Çikolata 65 G" besin değerleri`
- `"Ülker Caramio Duo" 32 g besin değerleri`
- `"Ülker Metro Çikolata Nugalı Bar 36 G" besin değerleri`
- `"Ülker Albeni Beyaz" 36 G besin değerleri`
- `"Ülker Coco Star Crisp" 20 G besin değerleri`
- `"Ülker Çokomilk" 24 G besin değerleri`
- `"Ülker Rodeo" 45 G besin değerleri`

Direct `Invoke-WebRequest` attempts for the four selected evidence pages failed at TLS receive in this environment. Therefore each record preserves the indexed URL, title, accessed time, exact extracted evidence string, and SHA-256 of that evidence string; it does not claim a raw-page checksum.

## Counts and decisions

- Nutrition no-match brand leader: Ülker, 239 rows.
- Bounded products researched: 8.
- Exact candidate products: 2; valid GTINs: 2; v4 overlap: 0; projected net-new: 2.
- Evidence sources: 4; evidence checksums verified: 4.
- Review/reject: 6.
- Metro 36 g `8690504035909`: two exact identity/package sources agree on 441 kcal, 17 g fat and 67 g carbs per 100 g; conservative protein 3.7 g selected.
- Çokomilk 24 g `8690504050124`: exact retailer identity/package source supplies complete per-100 core nutrition; second indexed source agrees on kcal/fat/carbs.
- Caramio Duo and Albeni Viva were rejected because package-sized values were mislabeled as per-100 values.
- Coco Star Crisp lacked exact-package nutrition proof; Rodeo nutrition was for a different 25 g pack; two other products had no complete exact source.
- Achieved rich unique total remains 2,411 pending a versioned v5 build; projected total 2,413, leaving 7,587 to 10,000.

## Hash

- Candidate evidence: `6C2C9E0A7C93F9A6D0E43ADD7CBB24917F45B873E2C4F1BEED994CB9163A3F09`

## Next

Build and validate PRIVATE_TEST_ONLY v5 from the two exact candidates without modifying v4, then continue the Ülker batch with products whose exact package identity and nutrition basis can be proven.

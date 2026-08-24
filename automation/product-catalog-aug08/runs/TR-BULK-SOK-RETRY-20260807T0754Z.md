# TR-BULK-SOK-RETRY-20260807T0754Z

## Result

`PASS_RETRY_COMPLETE`

Retried only the 12 failed exact Cepte Şok targets with two workers, a 30-second timeout and at most two retries. All 12 returned HTTP 200; seven exposed complete plausible structured per-100 core nutrition and five were explicit parse rejects. Across the original and retry slices there are now 41 unique nutrition-complete GTIN candidates, nine overlapping v8 and 32 net-new. Validated total remains 2,835 until the immutable overlay stage; projected total is 2,867.

## Commands

```powershell
$pages=Get-Content -Raw outputs/product-catalog-aug08/tr-bulk-sok-nutrition-v1-20260807/pages.json|ConvertFrom-Json
@($pages|Where-Object{$_.error})|ConvertTo-Json -Depth 20|Set-Content -Encoding utf8 outputs/product-catalog-aug08/tr-bulk-sok-nutrition-v1-20260807/retry-input.json
C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe scripts/fetch-tr-sok-exact-nutrition.py --matches outputs/product-catalog-aug08/tr-bulk-sok-nutrition-v1-20260807/retry-input.json --output-dir outputs/product-catalog-aug08/tr-bulk-sok-nutrition-v2-retry-20260807 --workers 2 --timeout 30 --retries 2
```

## Counts

- Retry URLs / exact joins / valid GTINs: 12 / 12 / 12
- HTTP 200 / remaining fetch errors: 12 / 0
- Retry nutrition complete / parse rejects: 7 / 5
- Combined candidates / unique GTINs: 41 / 41
- Combined v8 overlaps / net-new: 9 / 32
- Achieved validated / projected / projected remaining: 2,835 / 2,867 / 7,133

## SHA-256

- Retry input: `4F9F66AFE1D3E136ABE4E45939E4EB368EB2C6988D205E7258F679C2097242EF`
- Retry report: `EA60EF9B05E48753E84BA2B3745A3104854CD716E3F9BCFE8ED6545AC70F89B6`
- Retry candidates: `E286206365921943B5EF3C0B03BD0911197A2666CD3F60982ED7D4D830DAFB9E`
- Retry pages: `C3B201892FC8B5BD2323DB9412BF140AA8A42EE18699454D28CEFAEE203671DE`

## Next

Combine the 34 original and seven retry candidates, preserve both page-evidence hashes, build immutable PRIVATE_TEST_ONLY v9 from v8, and run the output validator.

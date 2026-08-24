# D5 product regression monitor — 2026-08-05 14:01 Europe/Dublin

- Run ID: `D5-PRODUCT-REGRESSION-MONITOR-20260805T1501Z`
- Branch: `feature/unified-product-intake-review`
- Stage: `D5-BUILD-GATES`
- Result: `PASS_PRODUCT_REGRESSION_D5_STILL_BLOCKED`

## Command

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\product-catalog-aug08-automation-lock.ps1 -Action Acquire -RunId D5-PRODUCT-REGRESSION-MONITOR-20260805T1501Z
.\mvnw.cmd '-Dtest=FoodItemControllerTest,FoodProductImportServiceImplTest,GenericFoodManifestGateTest,FoodProductCanonicalKeyRulesTest,FoodProductNormalizationRulesTest' test
powershell -ExecutionPolicy Bypass -File .\scripts\product-catalog-aug08-automation-lock.ps1 -Action Release -RunId D5-PRODUCT-REGRESSION-MONITOR-20260805T1501Z
Get-FileHash -Algorithm SHA256 -LiteralPath target/surefire-reports/TEST-<class>.xml
git diff --check
```

## Evidence

- Maven build: PASS
- Tests: 54
- Failures: 0
- Errors: 0
- Skipped: 0
- Maven elapsed time: 27.500 seconds
- `FoodItemControllerTest`: 15 tests; XML SHA-256 `82BABA76D4683C6D0AB12D61AD8765C6153A0CDFAE192F43D5292B3CD95B409C`
- `FoodProductImportServiceImplTest`: 31 tests; XML SHA-256 `96C2878DF750A8FD4B33B5473FF1B103E6BF62171EB22B3CB6523CAC5C0FAC9A`
- `GenericFoodManifestGateTest`: 1 test; XML SHA-256 `7AF6BBA77B1DCA327814921F8834B7502F82C65921AE4C54AD9EE3A9777725CA`
- `FoodProductCanonicalKeyRulesTest`: 4 tests; XML SHA-256 `BFD242DC026CA84494089CC13B05CF44CFE27D86AEB5DA30D6C5CDA1C5EB8514`
- `FoodProductNormalizationRulesTest`: 3 tests; XML SHA-256 `C4070D4AB28EBF9BB3043755DCB9BAF0F96FE27F5C5B4A040DB0BFA4D4BD9DA7`
- `git diff --check`: PASS (line-ending warnings only)
- Application, permissions, fixtures, catalog, database, and unrelated dirty files were unchanged.

## Blocker and next action

The product regression remains green. D5 still requires owner decisions for
template-applied optional nutrition snapshot semantics and the 17 explicit
`denyAll()` admin mutation contracts. After those decisions, implement only the
approved contract and run focused tests, the full Maven suite, product regression,
admin UI build, and final release-candidate manifest.

# Local Food Catalog August 8 Status

**Branch:** `feature/unified-product-intake-review`
**Window:** through 2026-08-08 (Europe/Dublin)
**PROGRAM_STATUS:** PAUSED
**Active stage:** D2 — generic expansion and normalization

## Baseline and release metrics

| Partition | Approved baseline | D1 contract fixture | Release-ready now | Candidate/review |
|---|---:|---:|---:|---:|
| GENERIC_INGREDIENT | 146 USDA rows | 1 synthetic smoke row | 113 production-preflight PASS | 33 baseline + 5 new USDA pilot rows in review/quarantine |
| LOCAL_DISH | 0 | 4 synthetic independent variants | 0 | 267 taxonomy; 5 calculated-review; 5 curator-pending |
| RESTAURANT_CHAIN | separate review-only exception | 0 | 0 | 30 Burger King TR official candidates |

D1 fixture rows are synthetic test evidence and are excluded from every release metric.

## Work queue

- [x] `D1-CONTRACT-MODEL-GATES` — additive local-dish identity model, import mapping, source registry, review/production preflight, negative controls, focused tests.
- [ ] `D2-GENERIC-EXPANSION` — baseline review complete. Official DEMO_KEY pilot fetched 5 new review-only rows (3 nutrition-complete, 0 baseline duplicates); full-volume expansion remains BLOCKED pending scoped `USDA_FOODDATA_API_KEY`.
- [ ] `D3-LOCAL-DISH-TAXONOMY` — create 100–250 canonical Turkish dish candidates with explicit family/variant identity and source basis; keep unapproved estimates separate.
- [ ] `D4-QUALITY-SEARCH-QA` — validate ranges, macro-energy, completeness, duplicates, servings, aliases, search behavior, and deterministic rebuilds.
- [ ] `D5-RELEASE-REHEARSAL` — immutable partitioned bundle, checksums, two isolated imports, idempotency, rollback, and test-build handoff.
- [ ] `AUG08-FINAL-AUDIT` — on or after 2026-08-08, final read-only verification and readiness classification.

## Run log

### D5 test-build DB package (2026-08-09)

- Run `LOCAL-FOOD-D5-TESTBUILD-PACKAGE-20260809T20260809T1159Z`: **PASS_PACKAGE_WITH_BLOCKED_PARTITIONS**.
- Created a unified, hash-addressed test-build manifest and a guarded import runner. No DB, AWS, production, deploy, commit, push, reset, revert, or stash operation occurred.
- Import-ready default: licensed catalog 176,631 input rows -> 174,581 expected canonical products; existing PostgreSQL two-pass rehearsal PASS.
- Explicit private-test opt-in: TR v10 overlay 2,953 rows; 625 licensed-barcode updates and 2,328 expected net-new canonical products; expected combined canonical count 176,909.
- Review-only/excluded: enhanced generic 113 (replacement reconciliation required), Local Dish 267 taxonomy / 5 nutrition review / 0 import, restaurant chain 44 REVIEW-pass / 0 import.
- Verification: unified builder deterministic 2/2; unified static preflight with overlay PASS 2/2; licensed preflight PASS; generic production preflight PASS 113/113 with 0 errors/warnings; restaurant REVIEW preflight PASS 44/44 with 0 errors.
- SHA-256: unified manifest `06A0D00B3C0EBDD86C9BF9CFD2D51FAD63F9215B21748EAB6E5E4AAD80BCCE4E`; licensed manifest `150B70D622473FF4C1406427678B3CB7939B33FA3795383451F6451BA5AB4C4D`; overlay `11A1276169637B26BC74029C75384354E77A687FA9FA5FFBF02D2DF0945E34DB`; generic validation `5BE754746E8B1DE12974D2A4DC658C095522E89BA4CDCD793ADF4249CEEC8171`; restaurant validation `43C2AE1B74B9725D22DC587CF91E9699FBAD369E7F32D706CF9CDC5D53ED784F`.
- Commands: `build-test-build-product-package.ps1` twice; `import-test-build-product-package.ps1` default and overlay preflight twice; licensed bundle preflight; local-food PRODUCTION validation; restaurant REVIEW validation; PowerShell parser checks; SHA-256 audit.
- Next: operator imports licensed-default plus explicit TR overlay into the isolated test-build DB and records two DB passes. Local Dish and restaurant-chain promotion continue separately; AWS remains a later explicitly approved step.

| Time (Europe/Dublin) | Run ID | Stage | Result | Evidence / next action |
|---|---|---|---|---|
| 2026-08-02 20:54 | `LOCAL-FOOD-D1-20260802-019fc3fa35f0` | D1-CONTRACT-MODEL-GATES | PASS | Branch/diff audited; 146 approved USDA baseline confirmed. Review preflight: 5 fixture rows, 1 generic + 4 independent local-dish variants, 0 errors, 0 warnings. Production negative control: expected FAIL with 8 blockers for 4 pending estimates. Python: 4/4 tests pass. Maven: 36/36 focused tests pass. Fixture SHA-256 `F150A823BD3757F52951DE9BE7C0F58759AD7C64BA4D1712D92A0871B870CA57`; contract `692BAF182FFC9496BB4BBE446FEAF20E3CB5C3B4C07713D7435598062B412204`; registry `75E584D82C2D84CB90A9FF1476BAA3C07116B0213A67D40076E295DA7BB99C71`; PASS report `8CEE992E1F03AFD085672EB515435831D028E486545069EEE2BFDA766C6F5FCB`; expected-FAIL report `924842F6D6F00CEF1B89F8E23E56BE209F71FCDB0FC34710B9527846FD1A12E6`. Next: D2 revalidate/normalize the 146 baseline before expansion. |
| 2026-08-03 00:38 | `LOCAL-FOOD-D2-20260803-c5409090e1a5` | D2-BASELINE-NORMALIZATION | PASS | 146/146 USDA rows normalized with CC0 provenance, stable keys, g-per-100g macro and mg-per-100g sodium/mineral policy. All 146 remain review-only for reviewed TR aliases; findings: 21 preparation states, 25 missing fiber, 1 missing sodium, 1 insufficient micronutrients (overlaps possible). Release-ready: 0. Python: 2/2 pass. Input SHA-256 `9BB603D44C5B2767C4774E53CB8B75D04D4EA142F5B9ECFCD18209C6AE3AEC28`; staging `35B102F9B0BE3C42A35C5E02E78D160F45381F681052935C2A02433467269B7D`; empty release `9F0A5C52512F26E4B0F0A7D77A39BD31C92D34A5C32E24586EC7519E88CCFD2F`; report `A9B584C8D96E6FE608184BFD2857D09433C7349BAA44244EBEF7D6143C9E9273`. Next: reviewed TR alias map and preparation/nutrient remediation; do not expand until gates pass. |
| 2026-08-03 04:40 | `LOCAL-FOOD-D2-ALIASES-20260803-4ddac8f2aaf2` | D2-TR-ALIAS-COHORT-01 | PASS | Explicit row-level TR alias review added for 15 fruit records; 11/15 have complete nutrition and pass production preflight with 0 errors/warnings, while 4 remain review-only and no values were imputed. The gate caught and fixed the normalizer registry ID mismatch (`USDA_FOODDATA_PUBLIC`). Overall staging 146; release-ready 11; remaining review-only 135; missing reviewed TR alias 131, missing fiber 25, unspecified preparation 21, missing sodium 1, insufficient micronutrients 1 (overlaps possible). Python 3/3 PASS. Alias review SHA-256 `448380EE61B8F7FE8D2A7181D881988DB9AE7D94866E2AE64443B2833AC6C1B8`; staging `D0FF831765485EDD182F02CA1406C430739C12A562FF5646BA1B3FA353B8383B`; release `B13E5CE200DE3F94F526A873CAEE6CE7A5B15FEB4FA33A22BA7455EA3CE1367A`; report `0986DA498BAA414E219731C6D569B1A5779F5ED3981243F0391957B17FE79259`; validation `AA1794594D0597461A14FFE714E6A886C32AF5A671C08824C7C056C81330397B`. Next: D2 alias cohort 02 with semantic mismatch quarantine before any count expansion. |
| 2026-08-03 08:41 | `LOCAL-FOOD-D2-ALIASES02-20260803-6fa6b88b282e` | D2-TR-ALIAS-COHORT-02 | PASS | Reviewed rows increased from 15 to 50: 49 alias approvals and 1 explicit quarantine (`fdc_id=169303`, alias said potato while source is sweet-potato leaves). Release-ready increased 11→42; all 42 pass production preflight with 0 errors/warnings. Overall staging 146; remaining review/quarantine 104; missing TR alias 96, missing fiber 25, unspecified preparation 21, missing sodium 1, insufficient micronutrients 1, identity mismatch 1 (overlaps possible). Python 3/3 PASS. Alias review SHA-256 `C4BD245D784B83515EA313B0A39F242586C227D5584E516E20B1F59BF11E60DE`; staging `67EA2F95B26A37F495174A5181E2C0469A520B0F71AD1D64F97B1325FDEB482A`; release `22528493CDC65B5288BDED5FDB8F8647D62F31F049411716F79C531976FDE91E`; report `A48F34B6509EEACC09E19168D31A9522B61EC580FDFCEE72EB4921DDFB22A949`; validation `92ADA7503082293EA8E011FC67F679CBAA3CC8767472908533D0FF449A8C1F45`. Next: D2 alias cohort 03 for proteins, preserving semantic quarantine. |
| 2026-08-03 12:42 | `LOCAL-FOOD-D2-ALIASES03-20260803-b1fb1bf094a8` | D2-TR-ALIAS-COHORT-03 | PASS | Added explicit TR review for 20 mushroom/protein records (20 approved, no new quarantine). Cumulative reviewed 70: 69 approved, 1 quarantined. Release-ready increased 42→57; all 57 pass production preflight with 0 errors/warnings. Overall staging 146; remaining review/quarantine 89; missing TR alias 76, missing fiber 25, unspecified preparation 21, missing sodium 1, insufficient micronutrients 1, identity mismatch 1 (overlaps possible). Python 3/3 PASS. Alias review SHA-256 `96CDFA5A30B1EE8D9C257075AEA94CA094643D7DA8161CCD6E013824BFD460A5`; staging `BD291B268E72753D2EFDAD226FDB5EC77BA97445D2715C79EFAC46E686A6E30B`; release `5C9A0617B572102DBD3ADE50622238AC6C14D14C5251A5F79C1DC7C2BB014A59`; report `4F74683AE446C21A6C38826CB2868A3D8EC31D00ED39BCF040080E2119C59584`; validation `BCFAD9FBB34208F8B79D4B1D5028EB60E56F9A282BF0FAAA2D6E280766B89D8C`. Next: D2 cohort 04 fish/egg/dairy with explicit preparation-state handling kept fail-closed. |
| 2026-08-03 16:43 | `LOCAL-FOOD-D2-ALIASES04-20260803-0046710c8cf8` | D2-TR-ALIAS-COHORT-04 | PASS | Added explicit TR review for 22 fish/seafood/egg/dairy records. Cumulative reviewed 92: 91 approved, 1 quarantined. Release-ready increased 57→69; all 69 pass production preflight with 0 errors/warnings. Alias approval did not bypass preparation/nutrition gates: dairy rows with `UNSPECIFIED` remain review-only. Overall staging 146; remaining review/quarantine 77; missing TR alias 54, missing fiber 25, unspecified preparation 21, missing sodium 1, insufficient micronutrients 1, identity mismatch 1 (overlaps possible). Python 3/3 PASS. Alias review SHA-256 `515A9BC8233D54868D12041E254BBFDAC5A4E82F4D94A866C05B6C7FF9C90762`; staging `65BA10CCA7EAAC4EA31C22A3A1408B6C2897C97034EA980F271911730497AB03`; release `06CBE205D6F15E9D00C1C3643C43778AB7462961AE77CC139CDA63532FF5D639`; report `EF814AEA3AE174A79218D00FC180E496D1036468DD2DBF470371B5C2ED019F42`; validation `AC927F38B882F169AE4582D009F88F3140BAE4327E52F4465A4CEE430DC47F72`. Next: D2 cohort 05 grain/legume/oil aliases with source-identity mismatch quarantine. |
| 2026-08-03 20:44 | `LOCAL-FOOD-D2-ALIASES05-20260803-82b1f4c065dc` | D2-TR-ALIAS-COHORT-05 | PASS | Added explicit review for 32 grain/legume rows: 29 approved and 3 new quarantines (branded QUAKER oats, branded UNCLE BEN'S rice, and mature-soy alias vs green-soy source). Cumulative reviewed 124: 120 approved, 4 quarantined. Release-ready increased 69→94; all 94 pass production preflight with 0 errors/warnings. Overall staging 146; remaining review/quarantine 52; missing TR alias 22, missing fiber 25, unspecified preparation 21, missing sodium 1, insufficient micronutrients 1, identity mismatch 4 (overlaps possible). Python 3/3 PASS. Alias review SHA-256 `AC1BDEB76949C3F6B12A0F410A489B2039FA012D40A5E1622EC285AC316DEEB5`; staging `27C22C66D4481B727F11E09836388595ECA5B45B290203F3AA7264F59114BA9B`; release `097748DCECB5F2872C3890671EED9AA59B4BFB4B3835BF0CFBEED8569CE73FB6`; report `0721DD4828E148812709AE4629F800D5D13E495D6D1AA0A1EC09B3301E77B275`; validation `9298FC8CDCFB9460DAF6CF6DDFEA7523B9FED42595D8264D00CEFB05A3412510`. Next: D2 cohort 06 remaining tofu/oil/nut/seed/beverage aliases with semantic quarantine. |
| 2026-08-04 00:45 | `LOCAL-FOOD-D2-ALIASES06-20260804-d701458128ba` | D2-TR-ALIAS-COHORT-06 | PASS | Completed row-level alias/identity review for all 146 baseline rows. Cohort 06 reviewed 22 tofu/oil/nut/seed/beverage rows: 19 approved and 3 quarantined (olive-oil alias vs mayonnaise, sunflower-oil alias vs roasted kernels, sunflower-seed alias vs seed butter). Cumulative: 139 approved, 7 quarantined. Release-ready increased 94→103; all 103 pass production preflight with 0 errors/warnings. Remaining review/quarantine 43; missing fiber 25, unspecified preparation 21, missing sodium 1, insufficient micronutrients 1, identity mismatch 7 (overlaps possible); missing TR alias is now 0. Python 3/3 PASS. Alias review SHA-256 `A88F16588DB567742C4E334ED900688F98731C7ABEC735D88253100180D1F82D`; staging `E59405B284A00C7F2C9116EDFF4F6F78844C0AE4BA18BF8EC42744C9A4BE64D5`; release `38DC50C50DBA516D4D49DA1070C1EC803B079E836DE414B874B77CD572AF2FB2`; report `DD96272CF5C66EA9A3C07143226D41BDEA8EEBD714BDAD716E184CCA7FD708CA`; validation `B85DC8BAF7523EF241442A0C24BC567BE13B8F33A1096BE124643293DEBCC0EF`. Next: D2 preparation-state remediation with explicit evidence; do not impute missing nutrients. |
| 2026-08-04 04:46 | `LOCAL-FOOD-D2-PREP-20260804-7a1db71a7ed0` | D2-PREPARATION-REMEDIATION | PASS | Reviewed all 21 `UNSPECIFIED` preparation states with row-level evidence; 21/21 received explicit RAW or PREPARED classifications. Quarantined identities stayed quarantined and no nutrients were imputed. Release-ready increased 103→113; all 113 pass production preflight with 0 errors/warnings. Remaining review/quarantine 33; missing fiber 25, missing sodium 1, insufficient micronutrients 1, identity mismatch 7 (overlaps possible); unspecified preparation is now 0. Python 4/4 PASS. Preparation review SHA-256 `46388A9C7A7E3E3E4247129F7F5A97CBC0018D14F7C97EF1930B80007A15C8B8`; staging `CA59E0392C5DFF55DE732866FB91AA614BB8FD6519B9E7EEA4C73FCF3C288497`; release `B35281CFC0A1999447ABC98E6AA9274A338014C617BE41F19D0B3229947B78BC`; report `25460C2E166F44CF5F6C08292B241ED7AB90365CF94AEDE8A3A08834E398DAD0`; validation `A910C64D11EB89D1094597914EFD89C9FACEB8388DB2EB530B26376DB8F466C4`. Next: D2 nutrient remediation/replacement queue; never infer missing source nutrients. |
| 2026-08-04 08:46 | `LOCAL-FOOD-D2-NUTRIENTQ-20260804-c2096b9c4193` | D2-NUTRIENT-REMEDIATION-QUEUE | PASS | Deterministically extracted 26 unique nutrient-incomplete rows from the 146-row normalized staging partition: missing fiber 25, missing sodium 1, insufficient micronutrients 1 (overlap present). Every row is `PENDING_SOURCE_REQUERY` with primary action `REQUERY_USDA_SAME_FDC`, fallback `REPLACE_WITH_COMPLETE_RIGHTS_CLEARED_USDA_RECORD`, and `value_imputation_allowed=false`; imputed values 0. Release-ready remains 113 and quarantine remains 7. Queue SHA-256 `9F4614A787BDC7B63C069A4C912DE2956DFA6D8926C683DCD4AB17F21A520DFD`; report `BB39AED641E4987B2C12695C179736C2C8694C181656E077BEC128B82289B09B`. Result PASS. Next: D2 rights-cleared USDA expansion/requery slice; preserve review gates. |
| 2026-08-04 12:47 | `LOCAL-FOOD-D2-USDA-CAPABILITY-20260804-a8df31fc6727` | D2-USDA-EXPANSION-CAPABILITY | BLOCKED | Existing official USDA exporter passed 16/16 rule tests, including preparation matching, semantic exclusions, relevance, and Atwater energy fallback. Environment variable `USDA_FOODDATA_API_KEY` is absent, so 0 rows fetched and 0 writes/imports performed; no unverified data was generated. Capability report SHA-256 `F6C8506475939BCAEDDC9CE115778850CBA7C25128DBC4A619F1E3AD201B8019`; exporter script `4BAA68A28FA7B60AB392C4ECC5AC10CE09C9F835A6F0F59FAE50C9CC64E378B5`. Next: provide scoped USDA FoodData Central API key, then run expansion/requery; otherwise D2 volume target remains blocked. |
| 2026-08-04 16:48 | `LOCAL-FOOD-D2-USDA-DEMO-20260804-91b78eed3cee` | D2-USDA-DEMO-EXPANSION-PILOT | PASS | Official USDA docs confirm DEMO_KEY for initial exploration (30/hour, 50/day). Five bounded Foundation/SR Legacy queries yielded 5 unique review-only rows from 125 candidates; 106 descriptions were rejected by semantic rules, 5/5 queries returned a row, 0 baseline FDC duplicates. Required nutrition complete: 3/5; reviewed TR aliases: 0; release-ready: 0; production writes: 0. Raw SHA-256 `B342FD178B3CE1799FBF3C972F73F055DAF4F44C2A8D8209A1EC574C0953EF8C`; report `53E38E35BAC5CFDB6DF1BA5089D77FA68206D7762C1A796B2027ECF138F6E2EC`. Next: semantic/TR review of these five; obtain scoped API key before volume expansion. |

## D1 commands

```powershell
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" -m unittest -v test_validate_local_food_catalog.py
.\mvnw.cmd '-Dtest=LocalDishIdentityMigrationContractTest,FoodProductCanonicalKeyRulesTest,FoodProductImportServiceImplTest' test
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" .\scripts\validate-local-food-catalog.py --input .\automation\local-food-catalog-aug08\fixtures\d1-contract-smoke.csv --release-class REVIEW --report .\outputs\local-food-catalog-aug08\review\d1-contract-smoke-report.json
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" .\scripts\validate-local-food-catalog.py --input .\automation\local-food-catalog-aug08\fixtures\d1-contract-smoke.csv --release-class PRODUCTION --report .\outputs\local-food-catalog-aug08\review\d1-production-negative-control-report.json
```

## Current blockers

- No D1 code/test blocker.
- TürKomp remains `BLOCKED_CONTRACT_REQUIRED`; it is not ingestible.
- The sodium/mineral unit contract is explicitly mg/100 g and the normalization audit passes; promotion remains blocked by reviewed TR aliases and row-level preparation/nutrient findings.
- No production-ready local dishes exist yet; synthetic D1 fixtures are never promotion candidates.

## D2 baseline normalization commands

```powershell
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" -m unittest scripts\test_build_local_food_generic_baseline.py
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" scripts\build-local-food-generic-baseline.py --input outputs\usda-generic-manifest-v1-approved-candidates.csv --staging outputs\local-food-catalog-aug08\staging\d2-usda-generic-baseline-normalized.csv --release outputs\local-food-catalog-aug08\release\d2-usda-generic-release-ready.csv --report outputs\local-food-catalog-aug08\review\d2-usda-generic-baseline-report.json
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" scripts\validate-local-food-catalog.py --input outputs\local-food-catalog-aug08\release\d2-usda-generic-release-ready.csv --release-class PRODUCTION --report outputs\local-food-catalog-aug08\review\d2-usda-generic-release-validation.json
```

## D2 TR alias cohort 01 commands

```powershell
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" -m unittest scripts\test_build_local_food_generic_baseline.py
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" scripts\build-local-food-generic-baseline.py --input outputs\usda-generic-manifest-v1-approved-candidates.csv --alias-review automation\local-food-catalog-aug08\generic-tr-alias-review-v1.csv --staging outputs\local-food-catalog-aug08\staging\d2-usda-generic-baseline-normalized.csv --release outputs\local-food-catalog-aug08\release\d2-usda-generic-release-ready.csv --report outputs\local-food-catalog-aug08\review\d2-usda-generic-alias-cohort-report.json
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" scripts\validate-local-food-catalog.py --input outputs\local-food-catalog-aug08\release\d2-usda-generic-release-ready.csv --release-class PRODUCTION --report outputs\local-food-catalog-aug08\review\d2-usda-generic-alias-cohort-validation.json
```


## D2 TR alias cohort 02 commands

```powershell
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" -m unittest scripts\test_build_local_food_generic_baseline.py
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" scripts\build-local-food-generic-baseline.py --input outputs\usda-generic-manifest-v1-approved-candidates.csv --alias-review automation\local-food-catalog-aug08\generic-tr-alias-review-v1.csv --staging outputs\local-food-catalog-aug08\staging\d2-usda-generic-baseline-normalized.csv --release outputs\local-food-catalog-aug08\release\d2-usda-generic-release-ready.csv --report outputs\local-food-catalog-aug08\review\d2-usda-generic-alias-cohort-02-report.json
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" scripts\validate-local-food-catalog.py --input outputs\local-food-catalog-aug08\release\d2-usda-generic-release-ready.csv --release-class PRODUCTION --report outputs\local-food-catalog-aug08\review\d2-usda-generic-alias-cohort-02-validation.json
```

## D2 TR alias cohort 03 commands

```powershell
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" -m unittest scripts\test_build_local_food_generic_baseline.py
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" scripts\build-local-food-generic-baseline.py --input outputs\usda-generic-manifest-v1-approved-candidates.csv --alias-review automation\local-food-catalog-aug08\generic-tr-alias-review-v1.csv --staging outputs\local-food-catalog-aug08\staging\d2-usda-generic-baseline-normalized.csv --release outputs\local-food-catalog-aug08\release\d2-usda-generic-release-ready.csv --report outputs\local-food-catalog-aug08\review\d2-usda-generic-alias-cohort-03-report.json
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" scripts\validate-local-food-catalog.py --input outputs\local-food-catalog-aug08\release\d2-usda-generic-release-ready.csv --release-class PRODUCTION --report outputs\local-food-catalog-aug08\review\d2-usda-generic-alias-cohort-03-validation.json
```

## D2 TR alias cohort 04 commands

```powershell
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" -m unittest scripts\test_build_local_food_generic_baseline.py
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" scripts\build-local-food-generic-baseline.py --input outputs\usda-generic-manifest-v1-approved-candidates.csv --alias-review automation\local-food-catalog-aug08\generic-tr-alias-review-v1.csv --staging outputs\local-food-catalog-aug08\staging\d2-usda-generic-baseline-normalized.csv --release outputs\local-food-catalog-aug08\release\d2-usda-generic-release-ready.csv --report outputs\local-food-catalog-aug08\review\d2-usda-generic-alias-cohort-04-report.json
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" scripts\validate-local-food-catalog.py --input outputs\local-food-catalog-aug08\release\d2-usda-generic-release-ready.csv --release-class PRODUCTION --report outputs\local-food-catalog-aug08\review\d2-usda-generic-alias-cohort-04-validation.json
```

## D2 TR alias cohort 05 commands

```powershell
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" -m unittest scripts\test_build_local_food_generic_baseline.py
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" scripts\build-local-food-generic-baseline.py --input outputs\usda-generic-manifest-v1-approved-candidates.csv --alias-review automation\local-food-catalog-aug08\generic-tr-alias-review-v1.csv --staging outputs\local-food-catalog-aug08\staging\d2-usda-generic-baseline-normalized.csv --release outputs\local-food-catalog-aug08\release\d2-usda-generic-release-ready.csv --report outputs\local-food-catalog-aug08\review\d2-usda-generic-alias-cohort-05-report.json
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" scripts\validate-local-food-catalog.py --input outputs\local-food-catalog-aug08\release\d2-usda-generic-release-ready.csv --release-class PRODUCTION --report outputs\local-food-catalog-aug08\review\d2-usda-generic-alias-cohort-05-validation.json
```

## D2 TR alias cohort 06 commands

```powershell
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" -m unittest scripts\test_build_local_food_generic_baseline.py
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" scripts\build-local-food-generic-baseline.py --input outputs\usda-generic-manifest-v1-approved-candidates.csv --alias-review automation\local-food-catalog-aug08\generic-tr-alias-review-v1.csv --staging outputs\local-food-catalog-aug08\staging\d2-usda-generic-baseline-normalized.csv --release outputs\local-food-catalog-aug08\release\d2-usda-generic-release-ready.csv --report outputs\local-food-catalog-aug08\review\d2-usda-generic-alias-cohort-06-report.json
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" scripts\validate-local-food-catalog.py --input outputs\local-food-catalog-aug08\release\d2-usda-generic-release-ready.csv --release-class PRODUCTION --report outputs\local-food-catalog-aug08\review\d2-usda-generic-alias-cohort-06-validation.json
```

## D2 preparation remediation commands

```powershell
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" -m unittest scripts\test_build_local_food_generic_baseline.py
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" scripts\build-local-food-generic-baseline.py --input outputs\usda-generic-manifest-v1-approved-candidates.csv --alias-review automation\local-food-catalog-aug08\generic-tr-alias-review-v1.csv --preparation-review automation\local-food-catalog-aug08\generic-preparation-review-v1.csv --staging outputs\local-food-catalog-aug08\staging\d2-usda-generic-baseline-normalized.csv --release outputs\local-food-catalog-aug08\release\d2-usda-generic-release-ready.csv --report outputs\local-food-catalog-aug08\review\d2-usda-generic-preparation-report.json
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" scripts\validate-local-food-catalog.py --input outputs\local-food-catalog-aug08\release\d2-usda-generic-release-ready.csv --release-class PRODUCTION --report outputs\local-food-catalog-aug08\review\d2-usda-generic-preparation-validation.json
```

## D2 nutrient remediation queue command

```powershell
$rows = Import-Csv outputs\local-food-catalog-aug08\staging\d2-usda-generic-baseline-normalized.csv
$nutrientCodes = @('MISSING_REQUIRED_FIBER','MISSING_REQUIRED_SODIUM','INSUFFICIENT_MICRONUTRIENTS')
# Select rows containing any nutrient code; emit PENDING_SOURCE_REQUERY with no imputation.
$items | Sort-Object {[int64]$_.fdc_id} | Export-Csv outputs\local-food-catalog-aug08\review\d2-usda-nutrient-remediation-queue.csv -NoTypeInformation -Encoding utf8
$doc | ConvertTo-Json -Depth 5 | Set-Content outputs\local-food-catalog-aug08\review\d2-usda-nutrient-remediation-report.json -Encoding utf8
```

## D2 USDA expansion capability commands

```powershell
& .\scripts\export-usda-fooddata-generic-products.ps1 -RunRuleTests
if ($env:USDA_FOODDATA_API_KEY) { 'USDA_FOODDATA_API_KEY_PRESENT' } else { 'USDA_FOODDATA_API_KEY_MISSING' }
# Capability report records 16/16 rule PASS, zero fetched rows, zero production writes, and the missing-key blocker.
Get-FileHash -Algorithm SHA256 outputs\local-food-catalog-aug08\review\d2-usda-expansion-capability.json,scripts\export-usda-fooddata-generic-products.ps1
```

## D2 USDA DEMO_KEY pilot command

```powershell
& .\scripts\export-usda-fooddata-generic-products.ps1 -ApiKey DEMO_KEY -Queries @('apricot raw','cherries raw','asparagus raw','green beans raw','avocado raw') -OutputPath .\outputs\local-food-catalog-aug08\raw\d2-usda-demo-expansion-pilot.csv -MarketRegion GLOBAL -PageSize 25 -MaxRowsPerQuery 1 -RequestDelayMs 500
# Review-only audit: required nutrients, micronutrients, baseline FDC duplicates, TR alias readiness, and zero production writes.
Get-FileHash -Algorithm SHA256 outputs\local-food-catalog-aug08\raw\d2-usda-demo-expansion-pilot.csv,outputs\local-food-catalog-aug08\review\d2-usda-demo-expansion-pilot-report.json
```

## D2 pilot semantic/TR review update (2026-08-04 20:49)

- Current GENERIC_INGREDIENT release-ready: **116** (113 baseline + 3 isolated pilot); review/quarantine: 33 baseline + 2 pilot nutrient-incomplete rows.
- Run `LOCAL-FOOD-D2-PILOTREVIEW-20260804T194945Z` / `D2-USDA-PILOT-SEMANTIC-TR-REVIEW`: **PASS**. All 5 identities, RAW states, and Turkish aliases reviewed and approved; 3 nutrition-complete rows pass PRODUCTION validation with 0 errors/warnings. Dark-red sweet cherries and Hass avocado remain fail-closed because source fiber is absent. Python 4/4 PASS; deterministic rebuild hashes unchanged; no DB or production writes.
- SHA-256: alias `B37D0ACFB821CB2EA0A9A8ED024B7C749A88D7B5D5DB9A06280DC6D151200172`; staging `6D7D8294F4BFCD6B61AC3346C9DDAD31930023006AF0AFAA1DF980DD05FB32D1`; release `2F1E1341986241A15447E249ACAA3D2CD1EAC9F03CDB860E2B8B1E2A934017E4`; report `B700AE9DD16F82197916F69C4B0D3F98F4FEEB133C0C8465C12310F4D3AA9A88`; validation `76A707626C65EB7CD38739233D0029BF5BE15BFAB0EE6121B44793A53FF7DE2A`; builder `1A435EE8AC3341E14445DD16D7405AF74A86D5F895A30227634E53637571362F`.
- Next: obtain scoped `USDA_FOODDATA_API_KEY` for full-volume D2 expansion; do not impute the two incomplete pilot rows.

```powershell
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" scripts\build-local-food-generic-baseline.py --input outputs\local-food-catalog-aug08\raw\d2-usda-demo-expansion-pilot.csv --alias-review automation\local-food-catalog-aug08\generic-tr-alias-pilot-review-v1.csv --source-version USDA-FDC-DEMO-PILOT-2026-08-04 --staging outputs\local-food-catalog-aug08\staging\d2-usda-demo-pilot-normalized.csv --release outputs\local-food-catalog-aug08\release\d2-usda-demo-pilot-release-ready.csv --report outputs\local-food-catalog-aug08\review\d2-usda-demo-pilot-review-report.json
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" -m unittest scripts\test_build_local_food_generic_baseline.py
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" scripts\validate-local-food-catalog.py --input outputs\local-food-catalog-aug08\release\d2-usda-demo-pilot-release-ready.csv --release-class PRODUCTION --report outputs\local-food-catalog-aug08\review\d2-usda-demo-pilot-validation.json
# Builder command repeated; staging/release/report hashes stayed identical.
Get-FileHash -Algorithm SHA256 automation\local-food-catalog-aug08\generic-tr-alias-pilot-review-v1.csv,outputs\local-food-catalog-aug08\staging\d2-usda-demo-pilot-normalized.csv,outputs\local-food-catalog-aug08\release\d2-usda-demo-pilot-release-ready.csv,outputs\local-food-catalog-aug08\review\d2-usda-demo-pilot-review-report.json,outputs\local-food-catalog-aug08\review\d2-usda-demo-pilot-validation.json,scripts\build-local-food-generic-baseline.py
```

## D3 TR + UK/IE transition (2026-08-04)

- **Active stage:** D3 local-dish taxonomy and provenance; D2 full-volume generic expansion remains separately blocked by missing scoped USDA API key.
- **LOCAL_DISH collected:** 40 canonical review candidates: TR 20, UK_IE 20. Identity-ready 40; PREPARED 40; localized TR/EN names and aliases 40; release-ready 0 pending nutrition/provenance.
- TR strategy: 20 `CALCULATED` candidates using a versioned rights-cleared USDA-component method; optional ingredient provenance only.
- UK/IE strategy: 20 `SOURCE_REPORTED` candidates preferring exact licensed CoFID profiles, with OGL attribution and third-party-exception review; calculation fallback stays review-only until method evidence exists.
- Run `LOCAL-FOOD-D3-TR-UKIE-20260804T210000Z` / `D3-TR-UKIE-TAXONOMY`: **PASS**. 40 unique source keys, 40 unique market/family/variant identities, 0 duplicate keys, 0 duplicate identities, 0 non-PREPARED rows, 0 missing localization findings. No nutrition was fabricated and no DB/production write occurred.
- Taxonomy SHA-256 `D3E7D105E956126C2A0C9E534A97EF398582A55FE92908BA5F60E995207E750E`; report `68BFBCABEC8CD963EC51A17E2A6FBC2789D8AFD89C5EC3AEBA60C5332BB230D7`.
- Next: map the first TR and UK/IE cohorts to exact licensed nutrition profiles or versioned calculations, add plausible localized portions, then run REVIEW validation; production promotion remains fail-closed.

```powershell
$rows = Import-Csv automation\local-food-catalog-aug08\local-dish-taxonomy-tr-ukie-v1.csv
# Validate market-qualified source keys, family/variant uniqueness, PREPARED state, localization, and candidate source strategy; emit deterministic review report.
Get-FileHash -Algorithm SHA256 automation\local-food-catalog-aug08\local-dish-taxonomy-tr-ukie-v1.csv,outputs\local-food-catalog-aug08\review\d3-tr-ukie-taxonomy-report.json
```

## D3 profile mapping queue (2026-08-05 00:49)

- Run `LOCAL-FOOD-D3-PROFILEQUEUE-20260804T234919Z` / `D3-PROFILE-MAPPING-QUEUE`: **PASS**.
- Deterministically mapped all 40 canonical local-dish identities into a fail-closed nutrition/provenance acquisition queue: TR 20, UK_IE 20; P0 20 (10 per market), P1 20.
- All 40 remain `PENDING`, 0 release-eligible, and 0 nutrition values were generated or inferred. TR requires a versioned calculation method plus rights-cleared component references; UK/IE requires exact CoFID record/version/license plus third-party-exception review.
- Taxonomy SHA-256 `D3E7D105E956126C2A0C9E534A97EF398582A55FE92908BA5F60E995207E750E`; queue `8FBF37844CFFCE06A30A44A5F593237248411F47E43D50D37BC48DD39B9C634F`; report `85498E6136C82B6B7F8F4797E4D5AD63A482B7FBE4FC245AF3C5798D0752E8FF`.
- Result PASS; production/DB writes 0. Next: resolve the 20 P0 rows against exact licensed CoFID profiles or versioned USDA-component calculation evidence, retaining unmatched rows in review.

```powershell
$rows = Import-Csv automation\local-food-catalog-aug08\local-dish-taxonomy-tr-ukie-v1.csv
# Assign P0 to the first 10 identities per market and P1 to the remainder; emit evidence requirements with blank source/profile fields and release_eligible=false.
Get-FileHash -Algorithm SHA256 outputs\local-food-catalog-aug08\review\d3-tr-ukie-profile-mapping-queue.csv,outputs\local-food-catalog-aug08\review\d3-tr-ukie-profile-mapping-report.json
```

## D3 official CoFID source acquisition (2026-08-05 04:50)

- Run `LOCAL-FOOD-D3-SOURCEAUDIT-20260805T035052Z` / `D3-COFID-SOURCE-ACQUISITION`: **PASS_SOURCE_ACQUIRED**.
- Official GOV.UK CoFID 2021 workbook acquired from Public Health England: 4,629,542 bytes, SHA-256 `436E9445EF2ADB2A75F3D7EDD51302DE3ADAD25385F9795FC94BA58BD030E97D`.
- License policy remains `OGL-3.0_EXCEPT_WHERE_OTHERWISE_STATED`; row-level attribution and third-party-exception review are mandatory. P0 UK/IE rows 10; matched 0; release-eligible 0 until workbook parsing and exact-profile review complete.
- Acquisition report SHA-256 `457525920C67FFBE45A504104BDB10F32F3542EC35F776261E09BAB29BB435DC`. USDA API key remains absent; TR calculation evidence is unchanged. No DB/production writes.
- Result PASS_SOURCE_ACQUIRED. Next: inspect CoFID worksheets, match the 10 UK/IE P0 identities by exact food description/code, normalize documented per-100 g units, and quarantine ambiguous/third-party-exception rows.

```powershell
Invoke-WebRequest -Uri 'https://assets.publishing.service.gov.uk/media/60538b91e90e07527df82ae4/McCance_Widdowsons_Composition_of_Foods_Integrated_Dataset_2021..xlsx' -OutFile 'outputs/local-food-catalog-aug08/raw/cofid-2021.xlsx'
Get-Item outputs/local-food-catalog-aug08/raw/cofid-2021.xlsx
Get-FileHash -Algorithm SHA256 outputs/local-food-catalog-aug08/raw/cofid-2021.xlsx,outputs/local-food-catalog-aug08/review/d3-cofid-source-acquisition-report.json
```

## D3 CoFID P0 exact matching (2026-08-05 08:51)

- Run `LOCAL-FOOD-D3-COFIDMATCH-20260805T075126Z` / `D3-COFID-P0-EXACT-MATCH`: **PASS_REVIEW_ONLY**.
- Parsed the frozen official CoFID 2021 workbook read-only and evaluated 10 UK/IE P0 identities. Exact name/preparation matches: 3 (`19-626` homemade shepherd's pie, `19-575` homemade cottage pie, `19-479` homemade Irish stew); ambiguous/no exact match: 7.
- Extracted source-reported per-100 g protein, fat, carbohydrate, calories, sugar, AOAC fibre, sodium, potassium, calcium, iron and zinc for the 3 exact rows. Values retain CoFID food code, source reference, version and OGL identifier.
- All 3 remain review-only with `third_party_exception_review=PENDING` and `release_eligible=false`; 0 automatic nutrition assignment for the other 7 and 0 production/DB writes.
- Match CSV SHA-256 `BD9E23B2769AFDCEBF590788651E96E8B05B457E9870B259979481F818D4E7F6`; report `2659B0841CB45FB3449DB2DA1095FFD3228AC9F1F085BB24F7DB7051AA72DA04`; source workbook `436E9445EF2ADB2A75F3D7EDD51302DE3ADAD25385F9795FC94BA58BD030E97D`.
- Result PASS_REVIEW_ONLY. Next: perform row-level OGL/third-party-exception review for the 3 exact profiles and add sourced serving options; keep the other 7 in manual/calculate review.

```powershell
& "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" - # standard-library ZIP/XML read of CoFID Proximates and Inorganics sheets; exact-code extraction only
Get-FileHash -Algorithm SHA256 outputs\local-food-catalog-aug08\raw\cofid-2021.xlsx,outputs\local-food-catalog-aug08\review\d3-cofid-p0-exact-match-review.csv,outputs\local-food-catalog-aug08\review\d3-cofid-p0-exact-match-report.json
```

## D3 CoFID rights and serving review (2026-08-05 12:52)

- Run `LOCAL-FOOD-D3-RIGHTSPORTION-20260805T115230Z` / `D3-COFID-RIGHTS-SERVING-REVIEW`: **PASS_REVIEW_ONLY**.
- Row-level rights review passed for the 3 exact CoFID profiles: GOV.UK OGL v3 policy, blank row footnotes, and internal CoFID `Updated 2013/2014` references; no row-specific exception found.
- Added 3 explicit `LOCAL_DISH_PORTION_POLICY_V1_REVIEW_PROPOSAL` defaults (300 g shepherd's pie, 300 g cottage pie, 350 g Irish stew). All remain `PENDING_CURATOR_APPROVAL`; approved 0, release-eligible 0, DB/production writes 0.
- Review CSV SHA-256 `A8474039B787B22A44D3B557A18AD28C9495C9062D5ACEB9AFF482236B7E83CB`; report `455C189384B2C2365CA4E9E491C8DFC438B3ADC72B68BB9732E00C0E6F83240E`. Result PASS_REVIEW_ONLY.
- Next: curator serving decision, contract-compatible review import, and REVIEW validation; the other 7 UK/IE P0 identities remain unmatched/manual.

## D3 UK/IE contract enablement (2026-08-05 16:54)

- Run `LOCAL-FOOD-D3-CONTRACTUKIE-20260805T155405Z` / `D3-UKIE-CONTRACT-ENABLEMENT`: **PASS**.
- Extended the local-food v1 contract from TR-only to explicit `TR` + `UK_IE` local-dish regions and enabled verified CoFID 2021 OGL for `LOCAL_DISH` in the fail-closed source registry. Generic and branded behavior is unchanged.
- Focused Python regression 4/4 PASS. UK/IE REVIEW smoke: 5 rows (4 local dish + 1 generic), 0 errors, 0 warnings, 4 unique UK/IE canonical dish identities.
- Contract SHA-256 `C10DC9E419A9650642062B3F81BB470B8C53C2970424CF1FB904F96DB153EF54`; registry `EAE9FCC1D500B4BD327CA0ED3956F650D5D04919AF3F8EC1A8D231A8D88B760D`; validator `597575B778C177F32A40DBF668D435392B7F1F0913F94AED18975F5CAEC974A8`; smoke report `BCD92C7CEE0191B16339BB349824B31875C4DEE0BA943779FB4A34C9936F39E4`.
- Result PASS; production/DB writes 0. Next: build the three exact CoFID rows in contract-compatible REVIEW format and validate portions/provenance; serving curator approval remains pending.

## Automation direction pivot (2026-08-05 18:06)

- Run `LOCAL-FOOD-AUTOMATION-PIVOT-20260805T180613861`: user-directed priority change recorded; **PASS**.
- D3 accelerated LOCAL_DISH discovery is the authoritative active direction. D2 generic expansion remains tracked but its missing scoped USDA key does not block independent dish taxonomy work.
- Current authoritative taxonomy count: 40 canonical candidates total - TR 20, UK_IE 20. Nutrition-mapped UK_IE profiles: 3. Release-ready LOCAL_DISH records: 0. These metrics are intentionally separate.
- Targets: TR minimum 100 / preferred 150-200; UK_IE minimum 75 / preferred 100-150; combined minimum 175 / preferred 250-350.
- Every successful research run should add 30-50 net-new canonical candidates and perform market-scoped duplicate, family, and variant resolution in the same slice.
- Official/institutional web sources are permitted and preferred for identity, regional, alias, and category discovery. Recipe/editorial sites are secondary cross-validation only; unclear-rights nutrient values remain excluded from production.
- Source/license, nutrition provenance, completeness, portion plausibility, consistency, duplicate, idempotency, and approval gates remain unchanged and fail closed.
- Result PASS; catalog/DB/production writes 0. Next: create the first 30-50 net-new TR cohort from official regional KTB/GoTürkiye and institutional sources, with discovery evidence and dedupe fields; then apply the same workflow to UK_IE.

- Commands: shared lock `Acquire`; three-document zero-context `git apply`; `Get-FileHash -Algorithm SHA256`; scoped `git diff --check`; shared lock `Release` in finally.
- Records changed: taxonomy 0, nutrition-ready 0, release-ready 0; documentation files 3.
- SHA-256: README `0E253C23AEC208D2ABA4013F00EE41D5F4DF4D9B22E56F52700F3547EE81BF72`; design `EE16749B9A41B26A41A65C3CD4C0434DA85CE16D2C20BB950351DA1937326D7E`; STATUS pre-verification-log `E83929F6F4AB353EAF1CA5317566D02BEF23DD6F8E6A4FD504985DA779B607BB`.
- Verification: scoped `git diff --check` PASS.

## D3 accelerated TR taxonomy cohort 02 (2026-08-05 20:55)

- Run `LOCAL-FOOD-D3-TR-EXPAND-20260805T205552944` / `D3-TR-TAXONOMY-COHORT-02`: **PASS_TAXONOMY_ONLY**.
- Added 40 net-new canonical TR identities from official Ministry of Culture and Tourism regional pages: Aydin 12, Amasya 12, Kars 10, Ordu 6. Existing taxonomy was not rewritten.
- Authoritative combined taxonomy: 80 candidates total - TR 60, UK_IE 20. Nutrition-ready added 0; release-ready added 0.
- Cross-cohort gates: duplicate source keys 0, duplicate market/family/variant identities 0, missing required taxonomy/localization/evidence fields 0, non-PREPARED 0, non-official evidence 0, source HTTP failures 0.
- All new rows remain `TAXONOMY_ONLY`; no nutrient values were inferred, copied from recipe sites, or promoted. Preferred profiling remains versioned calculation with rights-cleared USDA components.
- SHA-256: cohort `9600CACFEE763E2E9F0CBC8AAABBA363740C92AD52980193D5BEF544F6E84055`; report `AB332332029EB66FA6B55DBED4E7B7C07C4021F27DB0310A722607076DED665B`.
- Commands: shared lock `Acquire`; official-page `Invoke-WebRequest -UseBasicParsing`; `Import-Csv` cross-cohort count/required-field/source-key/family-variant validation; `Get-FileHash -Algorithm SHA256`; scoped `git diff --check`; shared lock `Release` in finally.
- Result PASS_TAXONOMY_ONLY; catalog rows 40, nutrition rows 0, DB/production writes 0. Next: build a 30-50 net-new UK_IE institutional-source cohort, then run deterministic combined dedupe/family/search QA.

## D3 accelerated UK/IE taxonomy cohort 02 (2026-08-06 00:56)

- Run `LOCAL-FOOD-D3-UKIE-EXPAND-20260806T005635769` / `D3-UKIE-TAXONOMY-COHORT-02`: **PASS_EXACT_PROFILE_CANDIDATES**.
- Added 40 net-new UK/IE prepared-dish identities, each tied to an exact food code in the frozen official CoFID 2021 workbook. Existing taxonomy and TR cohort were not rewritten.
- Authoritative combined taxonomy: 120 candidates total - TR 60, UK_IE 60. Nutrition-ready added 0; release-ready added 0.
- Cross-cohort gates: duplicate source keys 0, duplicate market/family/variant identities 0, duplicate CoFID codes 0, missing required taxonomy/localization/evidence fields 0, non-PREPARED 0. CoFID codes found 40/40; official GOV.UK source HTTP 200.
- All new rows remain `EXACT_PROFILE_PENDING_RIGHTS`; no nutrient values were extracted or promoted. OGL third-party-exception and row-level rights review remain mandatory.
- SHA-256: cohort `993AB9F5EF3BE724D0A9D7A3B1E96D13544820FDF7A656D9A7047A6FBD95AE42`; report `5A44373C1B4A86C9AC854F0DA912AB88E1158613284EDA000FD02B34019F467F`; frozen CoFID workbook `436E9445EF2ADB2A75F3D7EDD51302DE3ADAD25385F9795FC94BA58BD030E97D`.
- Commands: shared lock `Acquire`; read-only `openpyxl` CoFID sheet/code inspection; `Import-Csv` combined count/required-field/source-key/family-variant validation; official-page `Invoke-WebRequest -UseBasicParsing`; `Get-FileHash -Algorithm SHA256`; shared lock `Release` in finally.
- Result PASS_EXACT_PROFILE_CANDIDATES; taxonomy rows 40, nutrition rows 0, DB/production writes 0. Next: add 30-40 further institutional UK_IE identities to exceed the 75 minimum, then extract required nutrients and run row-level rights review.

## D3 accelerated UK/IE taxonomy cohort 03 (2026-08-06 04:56)

- Run `LOCAL-FOOD-D3-UKIE-EXPAND3-20260806T045632362` / `D3-UKIE-TAXONOMY-COHORT-03`: **PASS_UKIE_MINIMUM_EXCEEDED**.
- Added 35 net-new UK/IE identities tied to distinct exact food codes in the frozen official CoFID 2021 workbook. Same-family rows use distinct preparation/retail variants with independent profiles; serving options were not modeled as products.
- Authoritative combined taxonomy: 155 candidates total - TR 60, UK_IE 95. UK_IE minimum 75 exceeded by 20. Nutrition-ready added 0; release-ready added 0.
- Cross-cohort gates: duplicate source keys 0, duplicate market/family/variant identities 0, duplicate CoFID codes 0, missing required taxonomy/localization/evidence fields 0, non-PREPARED 0. CoFID codes found 35/35.
- All new rows remain `EXACT_PROFILE_PENDING_RIGHTS`; OGL third-party-exception review, required nutrient extraction, servings, and release validation remain pending.
- SHA-256: cohort `6E4CE4EC1D9DDB7E3018302F8D02B6B0A1D1ABF8B0BFEE13818D0B0C2C0DDC0C`; report `C1F8C99207822F008AD11351E8BC87A16F379C9202E0FBAA42A31D89D715A4CC`; frozen CoFID workbook `436E9445EF2ADB2A75F3D7EDD51302DE3ADAD25385F9795FC94BA58BD030E97D`.
- Commands: shared lock `Acquire`; read-only `openpyxl` CoFID exact-code verification; four-cohort `Import-Csv` count/source-key/family-variant/required-field checks; `Get-FileHash -Algorithm SHA256`; shared lock `Release` in finally.
- Result PASS_UKIE_MINIMUM_EXCEEDED; taxonomy rows 35, nutrition rows 0, DB/production writes 0. Next: add 40 net-new TR official-source identities to reach TR 100 and combined 195, then begin nutrient/provenance promotion.

## D3 accelerated TR taxonomy cohort 03 (2026-08-06 08:57)

- Run `LOCAL-FOOD-D3-TR-EXPAND3-20260806T085708308` / `D3-TR-TAXONOMY-COHORT-03`: **PASS_ALL_TAXONOMY_MINIMUMS_MET**.
- Added 40 net-new official-source TR identities: Aydin 12, Amasya 20, Kars 8. All three Ministry of Culture and Tourism source pages returned HTTP 200; existing cohorts were not rewritten.
- Authoritative combined taxonomy: 195 candidates total - TR 100, UK_IE 95. TR minimum 100, UK_IE minimum 75, and combined minimum 175 are all met. Nutrition-ready added 0; release-ready added 0.
- Cross-cohort gates: duplicate source keys 0, duplicate market/family/variant identities 0, missing required taxonomy/localization/evidence fields 0, non-PREPARED 0, non-official evidence 0, source HTTP failures 0.
- All new rows remain `TAXONOMY_ONLY`; no recipe-site nutrition, inferred values, ingredient requirement, RecipeEntity dependency, DB import, or production mutation occurred.
- SHA-256: cohort `2F563DB1253F11A99103FAE73B95C6716642A34E4CD9BA524A16DE0AC5AA127B`; report `41C615460D772036540956B5B2B5C0C20F944147F0F70B4F92C9050834C7DC02`.
- Commands: shared lock `Acquire`; three official-page `Invoke-WebRequest -UseBasicParsing`; five-cohort `Import-Csv` combined count/source-key/family-variant/required-field checks; `Get-FileHash -Algorithm SHA256`; shared lock `Release` in finally.
- Result PASS_ALL_TAXONOMY_MINIMUMS_MET; taxonomy rows 40, nutrition rows 0, DB/production writes 0. Next: freeze the 195-row combined taxonomy and run D4 deterministic dedupe, family/variant, alias, and search QA before profile promotion.

## D4 taxonomy freeze, dedupe and search QA (2026-08-06 12:58)

- Run `LOCAL-FOOD-D4-FREEZE-QA-20260806T125815333` / `D4-TAXONOMY-FREEZE-DEDUP-SEARCH-QA`: **PASS_WITH_SEARCH_REVIEW**.
- D3 volume is complete at the agreed minimums. D4 is now the authoritative active stage. Frozen deterministic snapshot: 195 rows - TR 100, UK_IE 95.
- Identity gates: duplicate source keys 0, duplicate market/family/variant identities 0, missing localization 0, non-PREPARED 0.
- Search QA found 5 same-family expected alias collisions and 1 cross-family review collision: normalized `turkish flatbread with minced meat` maps to both `kiymali_pide` and `lahmacun`. Decision is `KEEP_SEPARATE_REVIEW_ALIAS`; no merge was performed.
- Deterministic rebuild PASS: snapshot hash and collision-review hash matched across two complete regenerations.
- Nutrition-mapped remains 3 and release-ready remains 0. Taxonomy QA did not infer nutrients, approve portions, or promote records.
- SHA-256: frozen snapshot `5C11B79F992892F28934A4BACD31B4E6651D92C92781DADD900086EE239B181A`; collision review `D23EEFE951F79E875D0CE8EB210625C83E5AFC611D6E8F2907A09F13785A629A`; QA report `FB448D63AAAE1E1C5038321E23BC2E5818E9AC9DA58A273201923127B3693550`.
- Commands: shared lock `Acquire`; five-cohort normalized `Import-Csv` / sorted `Export-Csv`; Turkish-diacritic search-key normalization; source/family/variant/alias collision QA; two-run SHA-256 comparison; JSON report generation; shared lock `Release` in finally.
- Result PASS_WITH_SEARCH_REVIEW; DB/production writes 0. Next: add explicit search query fixtures for the one cross-family collision and run portion plausibility QA, then promote evidence-complete CoFID rows through rights review.

## D4 search fixtures and portion readiness (2026-08-06 16:59)

- Run `LOCAL-FOOD-D4-SEARCH-PORTION-20260806T165945242` / `D4-SEARCH-FIXTURES-PORTION-READINESS`: **PASS_WITH_PORTION_REVIEW**.
- Added 12 deterministic TR/UK_IE search fixtures covering exact aliases, the lahmacun/kiymali-pide cross-family collision, and multi-variant families (`moussaka`, `lasagne`, `irish_stew`, `bubble_and_squeak`). Final fixture result 12/12 PASS.
- The fixture run caught and corrected one test expectation: `lasagne` has five independent variants, including `vegetable_retail`, not four. No taxonomy merge or deletion occurred.
- Portion readiness queue covers all 195 taxonomy rows: 155 category-policy proposals are `PENDING_CURATOR_APPROVAL`; 40 legacy rows with blank category are `PENDING_MANUAL_CATEGORY`. Approved portions 0, generated serving options 0, release-ready 0.
- Proposal policy is fail-closed: gram ranges/defaults are review suggestions only, `release_eligible=false`, and serving options never create separate products.
- SHA-256: search fixtures `4FF1E65D52E4F83C2A934C65B6AF32141640FF5C609D0071CF92D8A9C784BA7A`; portion queue `AE16ECD70041A93FE2B5DA6683C5734845B1EAA73349F19EA2A28378BF9657B5`; report `3976673B269B622BF238D0E1C4294547F3F4145C2AB4611F829E51CB8333100D`; frozen taxonomy `5C11B79F992892F28934A4BACD31B4E6651D92C92781DADD900086EE239B181A`.
- Commands: shared lock `Acquire`; category-policy queue generation with `Import-Csv`/`Export-Csv`; normalized localized search index; exact/family-variant fixture evaluation; corrected lasagne fixture; JSON report generation; SHA-256 verification; shared lock `Release` in finally.
- Result PASS_WITH_PORTION_REVIEW; taxonomy 195, nutrition-mapped 3, DB/production writes 0. Next: curate the 40 missing legacy categories, then approve/adjust portion proposals and generate localized serving options for plausibility validation.

## D4 legacy category review and full portion-policy coverage (2026-08-06 21:00)

- Run `LOCAL-FOOD-D4-LEGACY-CATEGORY-20260806T210049900` / `D4-LEGACY-CATEGORY-PORTION-COVERAGE`: **PASS_FULL_PORTION_POLICY_COVERAGE**.
- Reviewed and classified all 40 legacy taxonomy rows that lacked category metadata: TR 20 and UK_IE 20. The frozen 195-row taxonomy was not rewritten; category decisions are isolated in a row-level review mapping.
- Portion-policy coverage increased from 155/195 to 195/195. Missing category after review 0; all 195 rows now have min/max/default gram proposals.
- Every proposal remains `PENDING_CURATOR_APPROVAL`, `serving_options_status=NOT_GENERATED`, and `release_eligible=false`. Approved portions 0, nutrition promotions 0, release-ready 0.
- Category review is identity metadata only and does not imply source, nutrition, portion, serving, or production approval.
- SHA-256: legacy category mapping `2A51800AEA0F5ECA05BE67508B23FD9F8384AA8F2B5490216902A22A716D70B4`; rebuilt portion queue `33EC9CDDC953EFC938F04D42195D1D8DA63C7373FB9F4FDD8DE88F75F9C5FF96`; report `3CAAAC48265B7196DBCB77BE8B122FE7EA93E551BE5FD01D38EB32725331B92F`; frozen taxonomy `5C11B79F992892F28934A4BACD31B4E6651D92C92781DADD900086EE239B181A`.
- Commands: shared lock `Acquire`; 40-row identity category mapping; complete policy lookup and fail-closed queue rebuild; missing-policy/category assertions; JSON report and SHA-256 generation; shared lock `Release` in finally.

## D3 accelerated TR taxonomy cohort 04 (2026-08-06 23:52)

- Run `LOCAL-FOOD-D3-TR-EXPAND4-20260806-234936-698` / `D3-TR-TAXONOMY-COHORT-04`: **PASS_TR_PREFERRED_APPROACH**.
- Added 42 net-new official-source TR identities: Tunceli 19, Sivas 15, Elazig 8. Existing cohorts and the immutable 195-row D4 snapshot were not rewritten.
- Current live taxonomy: 237 candidates total - TR 142, UK_IE 95. Nutrition-mapped remains 3; release-ready remains 0.
- Cross-cohort gates: duplicate source keys 0, duplicate market/family/variant identities 0, missing new-cohort required fields 0, missing combined identity fields 0, non-PREPARED 0. The 40 legacy blank categories remain covered by the isolated D4 category mapping.
- All 42 rows remain `TAXONOMY_ONLY`; official cultural evidence establishes identity only. No recipe-site nutrients, inferred nutrition, production import, or DB mutation occurred.
- Restaurant-chain follow-on direction recorded: official McDonald's/Burger King-style menu nutrition may be researched after Local Dish expansion, but only in a separate `BRANDED_PRODUCT` / `RESTAURANT_CHAIN` partition and never in Local Dish metrics.
- SHA-256: cohort `E27307EFD0488B32943F6FC9B6FDFA7BACA1D38C18FA71C8DF1DCB741C262D1A`; report `EB24051448E4662EE33AFCA4BFC7E9ABD37C0965067AD08CFF2948A4239A698D`.
- Commands: shared lock `Acquire`; official-page `Invoke-WebRequest -UseBasicParsing`; six-cohort `Import-Csv` required-field/source-key/family-variant/PREPARED validation; JSON report generation; `Get-FileHash -Algorithm SHA256`; scoped `git diff --check`; shared lock `Release` in finally.
- Result PASS_TR_PREFERRED_APPROACH; taxonomy rows added 42, nutrition rows 0, DB/production writes 0. Next: add at least 8 net-new official-source TR identities to reach the preferred TR floor of 150, then rebuild a versioned combined snapshot and rerun D4 dedupe/search/portion coverage for all rows.

## D3 TR cohort 05 and D4 taxonomy v2 rebuild (2026-08-07 01:02)

- Run `LOCAL-FOOD-D3-TR-EXPAND5-20260807-010052-211` / `D3-TR-COHORT-05-AND-D4-V2-REBUILD`: **PASS_PREFERRED_VOLUME_AND_D4_REBUILD**.
- Added 30 net-new Elazig identities from the official Provincial Directorate of Culture and Tourism cuisine page. Existing cohorts and v1 artifacts were preserved.
- Current taxonomy: 267 total - TR 172, UK_IE 95. Both TR preferred 150-200 and combined preferred 250-350 ranges are now reached. Nutrition-mapped remains 3; release-ready remains 0.
- D4 v2 gates: duplicate source keys 0, duplicate market/family/variant identities 0, missing identity fields 0, non-PREPARED 0, deterministic rebuild PASS, portion-policy identity coverage 267/267.
- Search review has 6 collision groups: 5 expected same-family and 1 retained cross-family review collision. No merge was performed. Portion proposals remain unapproved and release-ineligible.
- SHA-256: cohort `C794E0BF55C2DA6E2D6EE74B6047B0A1C4B4DCB1D1C98ED09768A2E99B71F461`; v2 snapshot `DAA51B2976BA3D5292D26CB44B32BFF231520C3083CF94AB81319516E1688F9E`; collision review `C10CB5861F5B6A930EE9BB1D2B435D8BCADF976064F086CCFC404DA9CE2BAB26`; portion queue `A751783CCD81CB85764BAFF865F3EED201121D28277A01C7951CB00D5ADBEF4C`; report `AD93B0DA6472160BD84F4223E8768D2994BCF57F504AF0680D91A390D1DEBC92`.
- Commands: startup `git status`/branch and full README/STATUS/design reads; shared lock `Acquire`; official-source web research; cohort `Export-Csv`; seven-cohort identity/dedup/search/portion validation; two deterministic snapshot builds; JSON report and SHA-256 generation; scoped `git diff --check`; shared lock `Release` in finally.
- Records: taxonomy +30; nutrition +0; release +0; DB/production writes 0.
- Result PASS_PREFERRED_VOLUME_AND_D4_REBUILD. Next: prioritize nutrition provenance and curator-approved serving options for a bounded TR cohort; keep restaurant-chain branded research separate until Local Dish promotion advances.

## D4 TR nutrition and portion promotion cohort 01 (2026-08-07 05:01)

- Run `LOCAL-FOOD-D4-TR-PROMOTION-20260807-050100-566` / `D4-TR-NUTRITION-PORTION-PROMOTION-COHORT-01`: **PASS_REVIEW_PACKET**.
- Prepared a bounded 20-row TR promotion packet covering common independent dishes and variants, including three kuru-fasulye variants, soups, pilafs, vegetable dishes, pastries, doner variants, kebab, and dessert.
- Linked 21 distinct rights-cleared USDA generic component records from the 113-row release-ready generic partition; invalid component references 0. Ingredient links remain optional provenance and no RecipeEntity dependency was introduced.
- Fail-closed outcome: fully component-resolved 0, per-100 g calculations 0, portion proposals 20, portion approvals 0, serving options generated 0, release-ready 0. No nutrient values were inferred.
- Explicit blockers: rights-cleared component gaps, versioned recipe weights, measured final cooked yield, curator portion approval, and per-100 g validation.
- SHA-256: promotion queue `B50234F079D09D8BCE7534805BB0F4B9C6152CFDA9CC2E2DC2CFD9B594216E34`; report `B1D967809A3A6503A74EADAD2D29A89500A2AEB040C8A04A52317FC9F2642B55`.
- Commands: startup branch/status and full README/STATUS/design reads; shared lock `Acquire`; v2 portion queue and 113-row generic release inspection; 20-row component/provenance mapping; component-key integrity validation; JSON report and SHA-256 generation; scoped `git diff --check`; shared lock `Release` in finally.
- Records: taxonomy +0; promotion-review +20; nutrition +0; release +0; DB/production writes 0.
- Result PASS_REVIEW_PACKET. Next: acquire/approve the missing rights-cleared generic components and versioned recipe/yield specifications for a smaller 5-row pilot, then calculate and validate per-100 g profiles; curator approval is required before serving-option generation.

## D4 TR calculated nutrition pilot 01 (2026-08-07 09:01)

- Run `LOCAL-FOOD-D4-TR-CALC5-20260807-090112-481` / `D4-TR-CALCULATED-NUTRITION-PILOT-01`: **PASS_CALCULATED_REVIEW**.
- Built versioned `GRUN_WEIGHTED_RECIPE_YIELD_V1` draft specifications and calculated independent per-100 g profiles for 5 TR dishes: mercimek corbasi, nohut yemegi, bulgur pilavi, menemen, and pirinc pilavi.
- Inputs: 28 recipe-component rows; all USDA component references resolve to the 113-row release-ready generic partition. Salt sodium uses a documented 393.4 mg/g method constant pending method review.
- Gates: localized profiles 5/5, required nutrient completeness 5/5, macro-energy hard failures 0, invalid component references 0. Calculated profiles remain `REVIEW` and `release_eligible=false`.
- Approval state: recipe/yield approved 0, portion approved 0, serving options generated 0, release-ready 0. These values must not be presented as approved production nutrition.
- SHA-256: recipe specification `D8D740B1312B2CF5CCEA07DA51DA9435E0426519A1AFF352FD8B2103B37BFE8F`; calculated profiles `0840EFA2B2E921C90B2E0B2E019AAA206FF27042E6BBF6DBC158518EC6BF7589`; report `4A0149192E6A3C8DA2A60A4A40A8555498774D5B589C92D76662B4B454CD9549`.
- Commands: startup branch/status and full README/STATUS/design reads; shared lock `Acquire`; release-ready USDA component indexing; weighted recipe/yield calculation; localization repair; required-nutrient and macro-energy gates; JSON report and SHA-256 generation; scoped `git diff --check`; shared lock `Release` in finally.
- Records: taxonomy +0; calculated-review nutrition +5; approved nutrition +0; release +0; DB/production writes 0.
- Result PASS_CALCULATED_REVIEW. Next: curator review of the 5 recipe weights, final yields, salt method, and proposed portions; after approval generate serving options and run the local-food validator in REVIEW then PRODUCTION preflight without importing.

## D4 TR calculated preflight pilot 01 (2026-08-07 13:04)

- Run `LOCAL-FOOD-D4-TR-PREFLIGHT5-20260807-130431-269` / `D4-TR-CALCULATED-PREFLIGHT-PILOT-01`: **PASS_EXPECTED_BLOCKERS**.
- Converted the 5 calculated-review profiles into the local-food v1 contract shape without claiming approval or generating serving options.
- REVIEW preflight: expected FAIL, 5 errors, all `MISSING_SERVING_OPTIONS`. PRODUCTION preflight: expected FAIL, 10 errors - 5 `MISSING_SERVING_OPTIONS` and 5 `SOURCE_NOT_PRODUCTION_ALLOWED` pending explicit record approval.
- Unexpected error codes 0; unique source keys 5; unique canonical local dishes 5. Nutrition, identity, source-key, required-nutrient, and macro gates introduced no additional errors.
- Serving options generated 0, record approvals 0, release-ready 0. Fail-closed behavior is preserved.
- SHA-256: preflight CSV `D90E97A1D50A3A310EF1F47E85F4D5E715F44D01FFD460C0CFEF0498192D7891`; REVIEW report `7CA6CFFFEA6EAD69554624F10155252ED29C78A89B24152FC95D1C7310EFD4DA`; PRODUCTION expected-fail report `32CF65C3FA02234927A933EF3BC8B351C44B71588021A7C07875BA4A91245BC9`; blocker summary `C62B4F164ECBDF3476907F532958F04DABB206714327C26B45DC5C4786C982D4`.
- Commands: startup branch/status and full README/STATUS/design reads; shared lock `Acquire`; contract CSV transformation; validator REVIEW and PRODUCTION preflights; error-code assertion and blocker summary; SHA-256 generation; scoped `git diff --check`; shared lock `Release` in finally.
- Records: preflight rows 5; approval 0; serving options 0; release 0; DB/production writes 0.
- Result PASS_EXPECTED_BLOCKERS. Next: obtain curator recipe/yield, portion, salt-method, and explicit record approval; only then generate serving options and rerun REVIEW/PRODUCTION preflight.

## D4 TR calculated curator packet 01 (2026-08-07 17:05)

- Run `LOCAL-FOOD-D4-CURATOR-PACKET-20260807-170535-771` / `D4-TR-CALCULATED-CURATOR-PACKET-01`: **PASS_DECISION_PACKET**.
- Produced one 5-row curator decision CSV and a human-readable review guide covering recipe component weights, final cooked yield, salt constant, calculated per-100 g values, proposed portions, and explicit record approval.
- All decisions remain fail-closed: recipe weight pending 5, final yield pending 5, salt method pending 5, portion pending 5, record approval pending 5.
- Serving options generated 0, approved records 0, release-ready 0. The packet does not grant or imply approval.
- SHA-256: decision CSV `3C2AED5DD965F014B55DDCEF53CB05A7819FF658E6FEB2FB41E3659CBD5A14E5`; curator guide `FB1DD476C131936560E03B5B761A581F5B33BA7A03A3410CD3A926BC525D0609`; report `70FE71EE2FCB5B102BDD01C0BB1C59B4198D2B0CFB20138FF0DA31F4B07CDDF0`.
- Commands: startup branch/status and full README/STATUS/design reads; shared lock `Acquire`; recipe/profile/preflight join; decision CSV and Markdown guide generation; pending-state assertions; JSON report and SHA-256 generation; scoped `git diff --check`; shared lock `Release` in finally.
- Records: decision rows 5; approvals 0; serving options 0; release 0; DB/production writes 0.
- Result PASS_DECISION_PACKET. Next: an authorized curator must complete the five decision fields per row; automation can then validate the signed decisions, generate localized serving options, and rerun REVIEW/PRODUCTION preflight.

## Restaurant-chain Burger King TR pilot 01 (2026-08-07 18:26)

- Run `LOCAL-FOOD-CHAIN-PILOT-20260807-182606-728` / `RESTAURANT-CHAIN-BURGER-KING-TR-PILOT-01`: **PASS_CANDIDATE_QUEUE_WITH_SOURCE_QA**.
- User-authorized transition started after Local Dish preferred taxonomy volume was reached. Added 30 official Burger King Turkey burger candidates to a separate `BRANDED_PRODUCT / RESTAURANT_CHAIN` review partition; Local Dish and Generic counts changed by 0.
- Official source fields available per menu-item serving: calories, protein, carbohydrate, fat, and sodium. Stable market/brand/menu keys are unique; duplicate source keys 0.
- Source QA: macro-energy PASS 9, warning 8, hard-review 13. These findings preserve the official values but block automatic promotion pending source clarification.
- Per-100 g ready 0, rights-ready 0, release-ready 0. Blocking gates: missing verified serving weights, prohibited per-100 g conversion without weight, pending terms/rights review, and macro-energy source review.
- SHA-256: candidate CSV `CB9FF198851669D1B7B4D81A9785698265EF220A6C07459D02A9CD7E06605EC5`; report `F3E1474FA90A6A04F7412ECCC2F72EB074B1100A5AD3F326F0EE66CE4F03CBEF`.
- Commands: startup branch/status and full README/STATUS/design reads; shared lock `Acquire`; official Burger King TR and McDonald's TR read-only fetch; official-table extraction; identity/dedup/macro-energy/source QA; JSON report and SHA-256 generation; scoped `git diff --check`; shared lock `Release` in finally.
- Records: restaurant-chain candidates +30; Local Dish +0; Generic +0; release +0; DB/production writes 0.
- Result PASS_CANDIDATE_QUEUE_WITH_SOURCE_QA. Next: research official serving weights or product-specific weight evidence and terms for Burger King TR; in parallel create a separate McDonald's TR/UK_IE official-source pilot without mixing market versions.

## Dual-lane automation integration (2026-08-07 18:31)

- Run `LOCAL-FOOD-AUTOMATION-DUAL-LANE-20260807-183157-083` / `AUTOMATION-DUAL-LANE-INTEGRATION`: **PASS_AUTOMATION_INTEGRATION**.
- Added machine-readable `work-lanes-v1.json` with three strictly isolated metric partitions and restartable lanes: Local Dish promotion, restaurant-chain discovery, and generic remediation.
- Scheduling is alternating, not concurrent project writes: exactly one lane may write per shared-lock acquisition; BUSY/BUSY_RACE remains report-only and every lock releases in finally.
- Current metrics: Generic release-ready 113; Local Dish taxonomy 267 (TR 172, UK_IE 95), calculated-review 5, release-ready 0; Restaurant Chain candidates 30, release-ready 0.
- Local lane remains active through official recipe/yield evidence and curator-decision monitoring. Restaurant lane next tasks are Burger King TR weight/rights QA and separate McDonald's TR then UK_IE pilots.
- Ownership boundary: restaurant artifacts remain under local review scope and may reach general branded automation only via an immutable signed D5 partition manifest; direct branded import mutation and double counting are prohibited.
- SHA-256: scheduler config `3C5FB029721E89210808CFA2C779752D4E8AE8561A1DABC7D5D0AEB34DEC0978`; integration report `AA521C5AABE542063B14ADE60D6345926A6F4642F669C098D16BEF95C98539F2`.
- Commands: startup branch/status and full README/STATUS/design reads; shared lock `Acquire`; parallel read-only automation boundary audit; scheduler JSON generation; lock/partition/handoff invariant validation; scoped `git diff --check`; shared lock `Release` in finally.
- Records: scheduler lanes 3; metric partitions 3; catalog records changed 0; DB/production writes 0.

## RC2 Burger King TR weight and rights QA (2026-08-07 21:07)

- Run `LOCAL-FOOD-RC-BKTR-WEIGHT-RIGHTS-20260807-210740-774` / `RC2-BURGER-KING-TR-WEIGHT-RIGHTS-QA`: **PASS_FAIL_CLOSED_QUARANTINE**.
- Scheduler first checked Local Dish curator decisions: 5/5 still pending, so the next ready restaurant-chain slice ran under the shared lock.
- Reviewed all 30 Burger King TR candidates against official product, nutrition, and legal pages; all three source classes returned HTTP 200.
- Verified serving weights 0. Official pages provide relative sizes and patty counts but no gram weight, so per-100 g conversion remains prohibited and per-100 g ready is 0.
- Rights review: the official legal page prohibits copying/use of site content without TAB Gida written permission. Rights-ready 0; written-permission-required 30; all 30 rows quarantined.
- Macro QA remains PASS 9, warning 8, hard-review 13. Source-reported values are preserved as evidence only; release-ready remains 0.
- SHA-256: row-level review `863487AFBD4DCB2505A1FBFEE6956817E5E6FEB98A6861A776D4CF8664B120DF`; report `6C2089EFAF0BC8FFB6F26D1971F2846946BBD34E843F09A3EB234D1D75418A7B`.
- Commands: startup branch/status and full README/STATUS/design reads; curator-pending scheduler check; shared lock `Acquire`; official product/weight search; official legal-page fetch and terms review; 30-row fail-closed weight/rights QA; JSON report and SHA-256 generation; scoped `git diff --check`; shared lock `Release` in finally.
- Records: reviewed 30; quarantined 30; per-100 g 0; rights-ready 0; release 0; Local Dish/Generic changes 0; DB/production writes 0.

## August 8 final audit and pause (2026-08-08 01:09)

- Run `LOCAL-FOOD-AUG08-FINAL-AUDIT-20260808-010819-261` / `AUG08-FINAL-AUDIT`: **NOT_READY_FOR_TEST_BUILD**.
- Final evidence-only audit completed on/after 2026-08-08. Automation status is now **PAUSED**; all three scheduler lanes are `PAUSED_FINAL_AUDIT`.
- Current metrics: Generic release-ready 113 / target minimum 400; Local Dish taxonomy 267 (TR 172, UK_IE 95); Local Dish calculated-review 5; Local Dish release-ready 0; Restaurant Chain candidates/quarantine 30; Restaurant Chain release-ready 0.
- Passed: TR taxonomy volume, deterministic 267-row snapshot, duplicate identity gates, 113 generic production-preflight rows, 5/5 calculated nutrient completeness, 0 macro hard failures in the TR pilot, strict metric partitioning, and 0 production/AWS/DB writes.
- Exact blockers: `GENERIC_VOLUME_BELOW_TARGET`; `LOCAL_DISH_RELEASE_PARTITION_EMPTY`; `LOCAL_DISH_CURATOR_APPROVAL_PENDING`; `IMMUTABLE_IMPORT_MANIFEST_MISSING`; `IDEMPOTENT_REHEARSALS_MISSING`.
- D5 evidence: immutable test-build manifest 0; isolated rehearsal 0/2. No import was attempted because required release partitions are not ready.
- Burger King TR quarantine is non-blocking for Local Dish/Generic readiness but remains excluded from release due written-permission and serving-weight requirements.
- SHA-256: final audit JSON `26F74DC2E7AA6744126654B750884471C67881652CBFE80E38A07E469625CA10`; human audit `47777C9371AD03251F4E338B0FCF4686519AC3259000D96E3302153A408C7140`; paused scheduler `60C01F1ED729F87247E72DEB81A299BAC59AEA7A82EE33F6D7CEB41A2B9B00AC`.
- Commands: startup branch/status and full README/STATUS/design reads; shared lock `Acquire`; read-only release/taxonomy/approval/quarantine/manifest/rehearsal evidence audit; final JSON/Markdown status recording; scheduler pause; scoped `git diff --check`; shared lock `Release` in finally.
- Records imported 0; DB/production/AWS writes 0; deploy/commit/push/reset/stash 0.

## RC3 multichain official pilot 01 (2026-08-08)

- Run `LOCAL-FOOD-RC-MULTICHAIN-PILOT-20260808T0015Z` / `RC3-MULTICHAIN-OFFICIAL-5-TO-7-PRODUCT-PILOT`: **PASS_REVIEW_CANDIDATE_QUEUE**.
- Explicit user resume superseded the historical final-audit pause for safe review-only work. Scheduler is `RESUMED_USER_DIRECTED`; restaurant-chain discovery is active while Local Dish remains approval-dependent.
- Added 17 official-source restaurant-chain candidates in a separate branded review partition: McDonald's TR 5, Popeyes TR 5, KFC UK_IE 7. Existing Burger King TR 30 plus this cohort gives 47 restaurant-chain candidates across four brands.
- KFC Turkey official nutrition data was not located; no cross-market substitution was made. KFC nutrition uses the official UK table and all KFC rows remain `UK_IE`.
- Source-reported per-100 g available 5/17 (McDonald's TR); missing verified serving weight 12/17. Rights review required 17/17; release-ready 0. No derived per-100 g values, imports, DB, production, AWS, deploy, commit, push, reset, revert, or stash.
- Validation: rows 17; brand counts 5/5/7; duplicate source keys 0; invalid nutrition ranges 0; Local Dish delta 0; Generic delta 0.
- SHA-256: candidate CSV `F54CE066183A9E27E8678CAA795B663C5B88EF22A5A5620FDFECE7F505EA626C`; report `C2E39A2369216A68776F4EA92EED9132A5094FBB1E56121BA92396D3B5F85196`.
- Commands: startup `git status --short` and `git branch --show-current`; full README/STATUS/design reads; shared lock `Acquire`; official-source web search; isolated CSV/report generation; `Import-Csv` count/dedup/range/release assertions; SHA-256; scoped `git diff --check`; shared lock `Release` in finally.
- Result `PASS_REVIEW_CANDIDATE_QUEUE`. Next: run product-level macro-energy QA and official terms/serving-weight review for McDonald's, Popeyes, and KFC; keep all records quarantined until rights and import-contract gates pass.

## RC4 multichain rights, weight and macro QA 01 (2026-08-08)

- Run `LOCAL-FOOD-RC-MULTICHAIN-QA-20260808T0100Z` / `RC4-MULTICHAIN-RIGHTS-WEIGHT-MACRO-QA-01`: **PASS_FAIL_CLOSED_QA**.
- Reviewed 17 official-source candidates: McDonald's TR 5, Popeyes TR 5, KFC UK_IE 7. Duplicate source keys 0; release-ready 0.
- Macro-energy gates: PASS 14, WARN 0, HARD_REVIEW 3. Popeyes Sundae Sade, Sundae Cikolatali, and Sundae Karamelli were hard-quarantined because source-reported carbohydrate and calories are mutually inconsistent.
- Rights: McDonald's online terms prohibit commercial copying/republication; KFC UK requires prior written permission; Popeyes states all site content is legally protected. Rights-ready 0/17 and all remain quarantined.
- Serving evidence: McDonald's 5 rows have source-reported serving and per-100 g values but no explicit gram weight; Popeyes/KFC 12 rows lack verified weights, so no per-100 g derivation occurred. McDonald's rows also retain a missing-sodium-source-field review flag.
- SHA-256: QA CSV `863C60567123C1CB1F38AA77C87DD3B6A01DBE3A3FE1AE0D78F7E31DDD7F8BEA`; report `B301D21138548D764B4D47C37DBA0FB60A2EDF5852914E06B997EB0601B9D01C`.
- Commands: startup branch/status and full README/STATUS/design reads; shared lock `Acquire`; official terms and product-source research; macro-energy calculation; nutrient/weight/rights gates; CSV/JSON generation; duplicate/release assertions; SHA-256; scoped `git diff --check`; shared lock `Release` in finally.
- Records: reviewed 17; hard quarantine 3; rights-ready 0; release 0; Local Dish/Generic delta 0; DB/production/AWS writes 0.
- Result `PASS_FAIL_CLOSED_QA`. Next: create separate official-source Subway and Domino's 5–7 item pilots, then run the same rights/weight/macro gates without mixing markets.

## RC5 core-main scope correction 01 (2026-08-08)

- Run `LOCAL-FOOD-RC-CORE-MAINS-20260808T0130Z` / `RC5-RESTAURANT-CHAIN-CORE-MAIN-SCOPE`: **PASS_CORE_MAIN_SCOPE**.
- User direction narrowed restaurant-chain discovery to the most popular representative main foods only. Allowed: burger, sandwich, wrap, pizza, and core chicken. Excluded: dessert, ice cream, beverage, sauce, and side.
- Superseded the three Popeyes Sundae candidates with Tavukburger, Smoky XL Sandwich, and 10-piece Nuggets from official Popeyes pages. The earlier evidence remains immutable review history but is excluded from the active candidate cohort.
- Active core-main cohort remains 17: McDonald's TR 5, Popeyes TR 5, KFC UK_IE 7. Category gate 17/17 PASS; macro-energy PASS 17, WARN 0, HARD_REVIEW 0; duplicate source keys 0; release-ready 0 pending rights/import gates.
- Added machine-readable `restaurant-chain-core-scope-v1.json`; future brand pilots must select 5-7 core main products and reject non-core categories.
- SHA-256: cohort `1CA78A6EA5D60A959C446B0707E619592F5841E8D00ACC5D2C5345C09EA3DD54`; QA `2E5A5700BE25ADE153258C2D6A2BAAA2044BF11BE1B845E8216A09EF3ABC55B4`; report `A21BF44DB90AA7C26DC4F7C88CAF3A4B87A4A5964756010A77745D5B23422707`; scope policy `0482D99B534DEFEFBF9697FCBBE04ECAB496B57832A845E0072B00B4F001FDEC`.
- Commands: startup branch/status and full README/STATUS/design reads; shared lock `Acquire`; official Popeyes main-product research; active-cohort rebuild; core-category and macro gates; duplicate assertion; policy/report generation; SHA-256; scoped `git diff --check`; shared lock `Release` in finally.
- Records: active restaurant-chain candidates unchanged at 47 total including Burger King 30; active multichain core cohort 17; Local Dish/Generic delta 0; DB/production/AWS writes 0.
- Result `PASS_CORE_MAIN_SCOPE`. Next: Subway and Domino's official-source pilots with 5-7 popular main products each; no desserts, ice cream, beverages, sauces, or sides.

## RC6 Subway and Domino''s core-main pilot 01 (2026-08-08)

- Run `LOCAL-FOOD-RC-SUBWAY-DOMINOS-20260808T0200Z` / `RC6-SUBWAY-DOMINOS-CORE-MAIN-PILOT-01`: **PASS_REVIEW_CANDIDATE_QUEUE**.
- Added 10 official-source UK_IE core-main candidates: Subway 5 standard 6-inch sandwiches and Domino''s 5 Personal Classic Crust whole pizzas. Desserts, ice cream, beverages, sauces, and sides were excluded.
- Subway: Italian B.M.T., Meatball Marinara, Tuna Mayonnaise, Veggie Delite, and Ham. Domino''s: BBQ Chicken and Bacon, Cheese and Tomato, Pepperoni, Sausage and Bacon, and Vegi Classic.
- Gates: core-main 10/10 PASS; macro-energy PASS 10, WARN 0, HARD_REVIEW 0; duplicate source keys 0. Subway serving grams verified 5; Domino''s explicit gram weights missing 5, so no derived per-100 g values.
- Rights-ready 0 and release-ready 0. Subway terms prohibit copying without written permission; Domino''s rights/import-contract review remains pending. Official values stay review-only provenance evidence.
- Active restaurant-chain candidate total is now 57 across Burger King, McDonald''s, Popeyes, KFC, Subway, and Domino''s. Metrics remain isolated from Local Dish and Generic Ingredient.
- SHA-256: cohort `B79F9C105126C6E3B47180CAD2BB4CEC36CD809CEE949ACF174A37F8E2028734`; QA `6158D78C9ED8AA9F42ED15A59874803661660EF2D0AC75509E283B232F73F090`; report `C9224199D348302F433D7997B87A8C8F0279D63AFE9F6DA24376B6D741325FB1`.
- Commands: startup branch/status and full README/STATUS/design reads; shared lock `Acquire`; official Subway and Domino''s research; official Domino''s PDF table visual check; 10-row cohort build; core category, macro, weight, duplicate, and release gates; SHA-256; scoped `git diff --check`; shared lock `Release` in finally.
- Records: restaurant candidates +10; active restaurant total 57; release 0; Local Dish/Generic delta 0; DB/production/AWS writes 0.
- Result `PASS_REVIEW_CANDIDATE_QUEUE`. Next: complete Subway/Domino''s terms and import-contract review, then build a consolidated six-brand core-main review manifest without importing.

## RC7 six-brand review manifest and import-contract audit (2026-08-08)

- Run `LOCAL-FOOD-RC-SIX-BRAND-MANIFEST-20260808T0230Z` / `RC7-SIX-BRAND-REVIEW-MANIFEST-CONTRACT-AUDIT`: **PASS_REVIEW_MANIFEST_BLOCKED_IMPORT_CONTRACT**.
- Consolidated 57 core-main candidates across six brands into one review-only manifest: Burger King 30, McDonald''s 5, Popeyes 5, KFC 7, Subway 5, and Domino''s 5. Markets: TR 40, UK_IE 17; duplicate source keys 0.
- Import-contract audit found an exact blocker: the current `BRANDED_PRODUCT` batch validator requires an 8-14 digit barcode and uses barcode identity. Restaurant menu items are naturally barcodeless, so contract-ready 0/57 and no import/rehearsal was attempted.
- Nutrition status: source-reported per-100 g 5; remaining rows need a supported serving-only contract or verified weight conversion. Burger King macro hard-review rows 13 remain flagged. Rights-ready 0.
- Subway terms explicitly prohibit copying without written permission. Domino''s official nutrition is publicly provided for consumer transparency, but no explicit reuse license was found; permission review remains required.
- Added `LOCAL_FOOD_CATALOG_AUG08_RESTAURANT_CHAIN_CONTRACT_GAP.md` defining a proposed identity `restaurant:{market_region}:{brand_key}:{menu_item_key}`, subtype isolation, idempotent upsert, serving, rights, macro, and duplicate gates. No migration or importer mutation occurred.
- SHA-256: manifest `472047BA9AC5ED5DA53BA1162EA961B60B5A7EB4789B3BC1154834F9A8633BF2`; report `ED7F7E7B842EFCE506008366380A1D62C77CE767098A6344371112ED98D83741`; contract-gap design `53343480603A40168C49F0E1BD1E3E95981E9A01665CBCD6A1AE0709887ED51B`.
- Commands: startup branch/status and full README/STATUS/design reads; shared lock `Acquire`; branded import standard/validator/importer audit; official Subway/Domino''s terms research; six-brand normalization; count/dedup/contract/release assertions; design note and SHA-256; scoped `git diff --check`; shared lock `Release` in finally.
- Records: review manifest 57; contract-ready 0; release 0; Local Dish/Generic delta 0; DB/production/AWS writes 0.
- Result `PASS_REVIEW_MANIFEST_BLOCKED_IMPORT_CONTRACT`. Next: design an isolated barcodeless restaurant-chain import contract and focused idempotency/identity tests before any schema or importer change.

## RC8 barcodeless restaurant import contract v1 (2026-08-08)

- Run `LOCAL-FOOD-RC-CONTRACT-V1-20260808T0300Z` / `RC8-BARCODELESS-RESTAURANT-CONTRACT-V1`: **PASS_CONTRACT_TESTS_EXPECTED_DATA_BLOCKERS**.
- Added an isolated restaurant-chain validator and focused test harness without changing the packaged branded importer, application schema, or database.
- Contract identity is `restaurant:{market}:{brand}:{menu_item}`. The 57-row contract cohort has 57 unique identities, empty barcodes by design, strict TR/UK_IE markets, and core-main categories only.
- Focused tests PASS 4/4: valid REVIEW, valid PRODUCTION, duplicate restaurant identity rejection, and deterministic two-pass idempotent upsert simulation.
- Actual REVIEW preflight expected FAIL: 13 errors, all Burger King `MACRO_HARD_REVIEW`. Actual PRODUCTION preflight expected FAIL: 174 errors = macro hard 13, missing per-100/verified weight 47, record approval pending 57, rights approval pending 57.
- No data was imported. The validator proves the identity and fail-closed rules but is not wired into the production endpoint.
- SHA-256: validator `A3B42A5A63EF30780C198F3B6B733A0E6AE253105B6CEFB24CB88E23B28C4DB4`; tests `71D8FBC10DCCED872DB2FE3DBDFB8933C27EA77809CB6FB2431415E2E43DBEDD`; contract CSV `7D22F4039A6321B5C9E9FD7CC9978285A8DA9EDA4412B4C31775A3456854ACD1`; REVIEW report `D4E2D53B13318F34A351BF34075F9F3E83F18AF5B38169D4EF9E17E38B30CE7D`; PRODUCTION report `1D3FAA52F086513332BB4271DE1272DF63AFB6DB9F5FB1364054BD003910E58A`; summary `7C8D9EBCA6CDA5C570705E0172183B5A91D5F4F2713C9B32322738E116D6C058`.
- Commands: startup branch/status and full README/STATUS/design reads; shared lock `Acquire`; current branded contract audit; isolated validator/test implementation; 57-row identity transformation; parser/focused tests; REVIEW and PRODUCTION expected-fail preflights; SHA-256; scoped `git diff --check`; shared lock `Release` in finally.
- Records: contract rows 57; review-pass rows 44; production-ready 0; DB/production/AWS writes 0.
- Result `PASS_CONTRACT_TESTS_EXPECTED_DATA_BLOCKERS`. Next: create a 44-row REVIEW-passing cohort excluding the 13 macro-hard Burger King rows and run two deterministic no-DB rehearsal passes; rights/approval blockers remain unchanged.

## RC9 review-passing 44 cohort and two no-DB rehearsals (2026-08-08)

- Run `LOCAL-FOOD-RC-REHEARSAL44-20260808T0330Z` / `RC9-REVIEW-PASSING-44-TWO-REHEARSALS`: **PASS_IDEMPOTENT_REHEARSALS**.
- Excluded the 13 Burger King macro-hard rows from the active rehearsal cohort. The resulting 44-row, six-brand cohort passes the barcodeless restaurant REVIEW contract with 44 unique restaurant identities and 0 errors.
- Added a deterministic no-DB rehearsal runner. Two independent rehearsals each produced: pass 1 inserted 44 / state 44; pass 2 inserted 0, updated 44 / state 44. Both runs are idempotent and deterministic; final snapshot hashes match.
- PRODUCTION expected-fail preflight on the 44 rows produced 122 blockers: rights approval 44, record approval 44, and missing per-100 g or verified weight 34. Macro hard failures are 0 in this cohort.
- No database, API, production, or AWS mutation occurred.
- SHA-256: runner `EA8ACE2B698DDE373F38E4B05E211A3D9F902975B978A6009842FE6D79FEDE59`; cohort `8257D177D1DC502F3607BAE1FE66D4EE9CE80C1C56F99A8498AEC7D570893212`; REVIEW validation `43C2AE1B74B9725D22DC587CF91E9699FBAD369E7F32D706CF9CDC5D53ED784F`; PRODUCTION expected-fail `EBF3BA0F9F8BD9CF40CA39D04D76030C3987B0D81E8689A2CFFA953E2F084A13`; rehearsal reports 01/02 both `1C20BBE4752C7CCD7AE45F7B7E2F3ABB71D39A506D7C866CC000E6993720007A`.
- Commands: startup branch/status and full README/STATUS/design reads; shared lock `Acquire`; 44-row filter; REVIEW fail-on-error validation; two independent no-DB two-pass rehearsals; snapshot/hash comparison; PRODUCTION expected-fail preflight; SHA-256; scoped `git diff --check`; shared lock `Release` in finally.
- Records: review-pass 44; rehearsal 2/2 PASS; production-ready 0; DB/production/AWS writes 0.
- Result `PASS_IDEMPOTENT_REHEARSALS`. Next: generate a row-level rights, weight/per-100, and explicit record-approval decision packet for the 44 products; no promotion before all required decisions pass.

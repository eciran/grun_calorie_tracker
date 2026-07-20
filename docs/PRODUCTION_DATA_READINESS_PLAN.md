# Production Data Readiness Plan


Date: 2026-07-13
Scope: Food catalog production readiness before AWS/live DB import.

## Goal

Prepare a production-safe food catalog pipeline that can grow from local pilot data to live AWS database without polluting the catalog with low-quality, duplicate, badly named, or misleading nutrition records.

This plan deliberately separates three product classes:

- `BRANDED_PRODUCT`: barcode/package products. Primary source: Open Food Facts regional exports and barcode fallback.
- `GENERIC_INGREDIENT`: stable base foods such as rice, chicken breast, egg, banana, broccoli. Primary source: curated USDA/FoodData or manual verified seed.
- `LOCAL_DISH`: regional cooked dishes such as Turkish and UK/EU common meals. Primary source: curated recipe/yield calculation, not random product pages.

## Non-Negotiable Rules

1. Live DB import is blocked until local readiness gate passes.
2. Import row count is not success; search relevance and nutrition trust are success.
3. Open Food Facts must not be used as the source of truth for generic ingredients.
4. USDA search output must be filtered and reviewed; some cooked staples need curated/manual rows because USDA search can return flour, pasta, soup, or raw-only records.
5. Generic ingredients must have explicit `preparation_state` when raw/cooked nutrition can differ.
6. Mobile display names must be user-friendly and localized; raw USDA names are not acceptable as primary display names.
7. Turkish/English search should use aliases/localizations instead of duplicate food records.
8. Frontend must receive allowed serving options from backend; UI must not invent gram/ml/slice options.

## Target Architecture

```text
External data sources
  -> OFF regional branded export/import
  -> USDA filtered generic export
  -> Curated generic staples seed
  -> Curated local dish seed

Import services
  -> normalize barcode/source key
  -> classify catalog type
  -> validate nutrition fields
  -> set display/short names
  -> set aliases/localizations
  -> set preparation state
  -> create quality warnings

Local DB readiness
  -> report-food-import-pilot
  -> search smoke terms
  -> readiness gate
  -> targeted tests

AWS/live DB
  -> only after local readiness passes
  -> same scripts, same gate, stricter thresholds
```

## Phase 1 - Curated Generic Staples

Purpose: guarantee that common searches return correct base foods before branded products.

Initial seed must include at minimum:

- Raw Banana
- Raw Apple
- Raw Broccoli
- Boiled Egg
- Raw Egg
- Raw Chicken Breast
- Cooked Chicken Breast
- Raw White Rice
- Cooked White Rice
- Raw Potato
- Boiled Potato
- Rolled Oats
- Olive Oil
- Whole Milk
- Plain Yogurt

Each row must include:

- `catalog_type=GENERIC_INGREDIENT`
- `data_source=LOCAL_CURATED` or reviewed `USDA_FOODDATA`
- stable `source_key`
- canonical `name`
- clean `display_name`
- concise `short_display_name`
- calories/protein/fat/carbs per 100g
- nutrition values normalized per `100 g` or `100 ml`
- product-specific default serving conversion and `serving_options_json`
- explicit `nutrition_basis` (`SOURCE_REPORTED`, `CALCULATED`, or `ESTIMATED`)
- `market_region=GLOBAL`
- explicit `preparation_state`
- `alias_en`
- `alias_tr`

## Phase 2 - USDA Export Guardrails

`export-usda-fooddata-generic-products.ps1` is allowed only for generic ingredient candidate generation.

Required controls:

- query-level candidate scoring
- query-specific exclusion terms
- max rows per query
- no direct runtime USDA calls from mobile flow
- manual review for cooked staples when USDA search is unreliable

Known risk: USDA search for `rice white cooked` may return flour/raw rice instead of cooked rice. Cooked rice should be provided by curated seed or a verified direct source record.

## Phase 3 - Branded Product Expansion

Source: Open Food Facts regional data.

Initial production strategy:

1. Start with UK_IE and EU/UK high quality branded products.
2. Add TR branded products after Turkish naming/alias rules are ready.
3. Prefer records with barcode, calories, macros, brand, serving size.
4. Reject or queue products with suspicious calories/macros/missing names.
5. Do not import product images as required product UX dependency.

## Phase 4 - Local Dishes

Source must be curated recipe/yield, not scraped product pages.

Examples:

- Pilav
- Tavuk pilav
- Mercimek corbasi
- Menemen
- Bulgur pilavi
- Chicken tikka style dish only if recipe/yield is controlled

Local dishes must include:

- `catalog_type=LOCAL_DISH`
- `data_source=LOCAL_CURATED`
- `preparation_state=PREPARED`
- recipe/yield note in source note where supported
- localized display names and aliases

## Phase 5 - Readiness Gate

Before any AWS/live import, run local pipeline and require:

- `duplicateBarcodeGroups = 0`
- POTENTIAL_GENERIC_DUPLICATE = 0 after canonical duplicate review; source records must not be auto-merged.
- Canonical duplicate candidates are reviewed through `GET /api/v1/admin/products/duplicates/canonical`; each group exposes `resolved`, `primaryProductId`, `resolvedBy`, and `resolvedAt` for admin continuity.
- Admin selects the user-search primary through `POST /api/v1/admin/products/duplicates/canonical/resolve`.
- Resolution is non-destructive: all source records, nutrition values, source keys, and provenance remain stored.
- Until a group is resolved, all candidates remain searchable; after resolution, only the selected primary is returned to users.
- Admin can filter the duplicate queue with `resolved=true|false`; filtering and pagination totals are calculated in the database.
- Re-selecting a primary replaces the decision and writes a `CANONICAL_PRIMARY_CHANGE` product audit record.
- `DELETE /api/v1/admin/products/duplicates/canonical/resolution?canonicalFoodKey=...` clears a decision, restores all candidates to user search, preserves every food record, and writes an audit entry.
- Canonical resolution is allowed only for groups containing at least two `GENERIC_INGREDIENT` records; it is not a branded-product merge mechanism.
- A canonical primary cannot be `REJECTED`, carry an active search-blocking quality issue, or fail critical calorie/macro validation.
- If a resolved primary later becomes rejected, gains a blocking issue, or fails critical nutrition checks, its resolution stops suppressing eligible alternatives in user search.
- `missingCalories = 0`
- `missingMacros = 0`
- `GENERIC_MISSING_PREPARATION_STATE = 0`
- `SUSPICIOUS_DISPLAY_NAME = 0` for curated generic staples
- search smoke returns expected first results for core terms

Core search smoke terms:

- banana -> Raw Banana / Banana
- broccoli -> Raw Broccoli / Broccoli
- rice -> Cooked White Rice or Raw White Rice before rice flour/pasta/cakes
- chicken -> Chicken Breast before broth/soup/skin/wing products
- egg -> Boiled Egg or Raw Egg
- milk -> Milk before chocolate/desserts

### Golden search quality gate

The versioned fixture src/test/resources/golden-food-search-v1.json is the mandatory ranking gate. It contains 230 EN/TR cases covering preparation state, common spelling errors, Turkish and ASCII Turkish terms, serving labels, localized display names, and GLOBAL/UK_IE/EU/TR market behavior.

Run the focused gate with:

    .\mvnw.cmd "-Dtest=GoldenFoodSearchQualityGateTest" test

The gate writes target/reports/golden-food-search-v1-report.json and fails when Top-1 is below 95%, Recall@3 is below 99%, a critical query has no result, a rejected/blocking product is visible, a resolved generic duplicate is visible, localization/serving assertions fail, or fixture p95 exceeds 300 ms. scripts/test-product-management-regression.ps1 includes this gate so ranking and import changes cannot bypass it.

The fixture p95 is a deterministic local regression signal. It does not replace the PostgreSQL staging-scale p95 measurement required before production rollout.
### Generic food manifest gate

The versioned S7 contract contains 96 generic identities and 146 preparation variants across fruit, vegetables, meat, fish, dairy, grains, legumes, fats, nuts, seeds and drinks. Every variant has one frozen USDA `fdc_id`, explicit preparation state, EN/TR display and aliases, a positive product-specific serving conversion, localized serving labels and complete calorie/protein/fat/carbohydrate values.

Tracked artifacts:

- `src/test/resources/generic-food-manifest-v1.json`: identity, category, query, localization and serving contract.
- `src/test/resources/generic-food-usda-candidates-v1.csv`: reviewed USDA nutrition/source input.
- `src/test/resources/generic-food-source-selection-v1.json`: one stable source key per variant.
- `src/test/resources/generic-food-approved-seed-v1.csv`: backend-importable approved seed.

Reproducibility and rule checks:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\generate-generic-food-manifest-queries.ps1 -Check
powershell -ExecutionPolicy Bypass -File .\scripts\generate-generic-food-approved-seed.ps1 -Check
powershell -ExecutionPolicy Bypass -File .\scripts\export-usda-fooddata-generic-products.ps1 -RunRuleTests
```

`GenericFoodManifestGateTest` validates category minimums, unique identities/queries/source keys, preparation compatibility, localization, serving conversions and artifact consistency. `FoodItemServiceSearchIntegrationTest` imports all 146 rows through the real import service and verifies zero skipped rows, zero core nutrition/serving/preparation warnings, 292 product localizations, 146 serving options, 292 serving localizations and Turkish search behavior.

USDA API search is candidate discovery only. Production import uses the reviewed, versioned source selection and never depends on live USDA ranking. Nutrition provenance is persisted as `nutrition_basis`; local-dish estimates must be marked `ESTIMATED` and calculated values must be marked `CALCULATED`.

### UK/IE branded growth gate

S8 uses the versioned sample-data/manifests/open-food-facts-uk-ie-v1.json contract and a frozen Open Food Facts source snapshot. The build verifies the source SHA-256 before export, then applies exact UK/IE country matching, barcode/source-key idempotency, required product name and brand, complete core nutrition, plausible nutrition ranges, market/catalog/source checks and explicit SOURCE_REPORTED provenance.

Reproducible local commands:

`powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-open-food-facts-market-batch.ps1 -Stage pilot
powershell -ExecutionPolicy Bypass -File .\scripts\build-open-food-facts-market-batch.ps1 -Stage gate
powershell -ExecutionPolicy Bypass -File .\scripts\test-open-food-facts-market-batch.ps1
`

Verified results:

- Pilot: 5,000/5,000 rows, zero blocking duplicate/barcode/nutrition/name/brand/market/source/provenance issues.
- Gate: 25,000/25,000 rows, zero blocking issues and 7,617 reported brand labels.
- Review-only warnings: 6,467 gate rows lack source serving metadata and 602 names require review; source values are not fabricated.
- Search: real Tesco, Dunnes, Sainsbury and Marks & Spencer examples validate branded intent; broad core-food queries continue to rank generic ingredients first.
- Golden search: Top-1 1.000, Recall@3 1.000, MRR@3 1.000 and fixture p95 14 ms.

Generated batch artifacts stay under ignored outputs/product-data-readiness/. They are evidence and import candidates, not automatic database mutations. Full measurements and checksums are in docs/PRODUCT_MANAGEMENT_S8_UK_IE_REPORT.md.

### TR and EU branded growth gate

S9 uses separate versioned TR and EU manifests against the same frozen Open Food Facts snapshot. The market batch builder accepts UK_IE, TR and EU, verifies the source checksum, applies market-specific country rules and records capacity failures separately from data-quality failures.

EU passed both controlled stages: 5,000/5,000 pilot rows and 25,000/25,000 gate rows, with zero blocking barcode, source-key, nutrition, identity, market or provenance issues. Missing serving metadata remains a visible review warning; unavailable translations are not invented.

The complete snapshot yielded 1,595 TR products that satisfy the strict branded-product contract. Internet-only discovery also found 3,807 nutrition-complete 868/869 candidates, but GS1 prefix is not market proof; these rows are isolated in a non-importable second-source evidence queue. At least 3,405 approvals are needed for the 5k pilot. Even if all candidates are approved, the 25k gate remains short by 19,598 products. This is a source-capacity blocker, not an importer defect.

TR source remediation is intentionally deferred while S10 staging and S11 scale engineering continue with existing approved artifacts. Before S12 production approval, obtain written usage and persistence rights and evaluate sources in this order: GS1 Turkiye/TOBBsenkron GDSN recipient feed; licensed retailer/manufacturer feeds; TurKomp for generic Turkish foods; review-first user barcode/label contributions. FatSecret TR is runtime-only unless a separate contract permits durable storage. Retailer-site scraping is not an approved source. OFF data and private sources must retain separate provenance and receive an ODbL compatibility review before any combined production publication.

Internet pages may supply supporting evidence but never write directly to the canonical catalog. The controlled path is: authorized source evidence -> immutable raw JSON-LD/HTML/label-OCR record with provenance and checksum -> deterministic identity, unit, nutrition, preparation, serving, locale and market validation -> licensed/USDA/OFF/current-catalog comparison -> AI suggestion and explanation -> admin approval with audit -> quality issue and search-readiness recalculation. Source precedence is official label/manufacturer evidence, licensed GS1/manufacturer feed, authorized retailer evidence, OFF, user contribution and finally AI inference. AI inference alone is not a valid nutrition source.

Cross-market identity is modelled as one product with a primary market plus a set of market availabilities. Re-importing the same barcode/source identity for another market unions availability without creating another product row. The 51,600-row overlap gate found 2,041 compatible shared identities and zero source-key or core-nutrition conflicts.

Full measurements, checksums, localization decisions and remaining blockers are recorded in docs/PRODUCT_MANAGEMENT_S9_TR_EU_REPORT.md.
### S10 staging rehearsal status

The staging package contains 51,746 approved input rows split into eight multipart-safe chunks: 25,000 UK_IE, 25,000 EU, 1,600 TR and 146 generic rows. A disposable PostgreSQL staging-equivalent rehearsal imported 49,705 canonical products with 2,041 compatible cross-market updates and zero skipped or failed rows.

The second full import was idempotent: it created zero products, updated the expected 51,746 market inputs and did not grow product, market, evidence, alias, localization or serving tables. Flyway reached V126, the pre-import snapshot restored successfully into an independent clone, and all ten English/Turkish search smoke queries returned relevant first results.

S10 is DONE. The clean 5df9c17 application revision imported the package through authenticated AWS staging with 49,705 inserts and 2,041 updates on the first pass, followed by 0 inserts and 51,746 idempotent updates on the second pass. All ten English/Turkish smoke searches passed; the repeated AWS S11 gate measured p95 104 ms and completed ten concurrent requests in 581 ms.

Encrypted pre/post snapshots were created, the pre-import snapshot restored to an isolated private clone, and the immutable application migrated that clone from Flyway V37 to V131 with zero food items. Runtime resources were then removed: ECS is at 0/0/0, and no temporary ALB or staging RDS instance remains. SHA-256 evidence is archived with the report. S12 remains blocked only by S9's licensed/authorized TR 5k and 25k catalog gates.
### S11 100k+ scale status

S11 is DONE. The optimized PostgreSQL path first selects a bounded indexed candidate set, then preserves the existing quality, canonical, market, localization and relevance rules. A disposable PostgreSQL run passed 25k, 50k, 100k and 200k catalog gates with p95 values of 53 ms, 169 ms, 218 ms and 210 ms respectively, all below the 300 ms budget.

The gate also passed deep pagination, a 10-request concurrent EN/TR batch in 501 ms, all seven required search indexes and an EXPLAIN ANALYZE plan using bitmap index scans. Controlled staging may use `db.t4g.small`; initial production should start at `db.t4g.medium` and scale from measured CPU, connection, memory, read-latency, burst-credit and API p95 pressure. Full evidence and trigger thresholds are in `docs/PRODUCT_MANAGEMENT_S11_SCALE_REPORT.md`.

Detailed evidence is recorded in docs/PRODUCT_MANAGEMENT_S10_STAGING_REHEARSAL_REPORT.md. Generated package and result artifacts remain under ignored outputs/product-data-readiness/staging-rehearsal/.
## Phase 6 - AWS/live DB Procedure

Live DB import is allowed only after local green run.

Procedure:

1. Backup live DB snapshot.
2. Apply migrations.
3. Import curated generic staples first.
4. Run readiness report.
5. Import small branded batch by region.
6. Run readiness report again.
7. Run search smoke against live API.
8. Increase batch size only if gate remains green.

## Blockers That Must Stop Live Import

- Core searches return misleading first results.
- Generic ingredient names look source-like or unreadable.
- Cooked/raw variants are missing or mixed.
- Nutrition basis is unclear.
- Duplicate source keys/barcodes appear.
- Import creates broad review queue without a review plan.

## Current Recommendation

Do not copy the current local DB as production seed. Rebuild production data in controlled layers:

1. curated generic staples
2. small high-quality regional branded batch
3. local dish curated seed
4. larger regional OFF batches
## PostgreSQL Import Performance Gate

Run the isolated local PostgreSQL benchmark before increasing import batches beyond the pilot size:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-food-import-postgres-performance.ps1
```

The runner creates and removes its own `grun-product-benchmark-postgres` container. It does not use or modify the normal local catalog database.

Current 500-row localized generic import budgets:

- saved rows: `500`
- skipped rows: `0`
- prepared SQL statements: at most `3125`
- measured import duration: less than `8 seconds`
- bulk lookup chunk size: at most `500` identifiers per repository call

The PostgreSQL benchmark is opt-in and skipped during the normal H2 test suite. It uses Hibernate `create-drop` inside the disposable database because unrelated in-progress migrations can temporarily prevent a clean Flyway bootstrap. Production migration validation remains a separate mandatory gate.

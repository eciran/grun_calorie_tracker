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
- `serving_size_grams=100`
- `serving_unit=g`
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

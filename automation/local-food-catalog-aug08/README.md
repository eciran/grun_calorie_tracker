# Local Food Catalog August 8 Automation

This directory is the restartable execution contract for a generic ingredient and canonical Turkish local-dish catalog. It shares the workspace and lock with `automation/product-catalog-aug08/` but never mixes its counts or artifacts with branded products.

## Scope

In scope:

- `GENERIC_INGREDIENT` from rights-cleared official composition sources;
- `LOCAL_DISH` as independent, barcode-free `FoodItem` records;
- Turkish and English names and aliases;
- per-100 g nutrition, default serving, structured serving options, provenance, validation, and isolated rehearsal;
- one final manifest whose generic, local-dish, and branded partitions remain explicit.

Out of scope:

- General `BRANDED_PRODUCT` import ownership, GTIN matching, retailer scraping, RecipeEntity ownership, production/AWS writes, deployment, commit, push, reset, stash, or cleanup of unrelated files. The only branded exception is the bounded official-source `RESTAURANT_CHAIN` review pilot defined below; it cannot import, promote, or change general branded metrics.

## Required run protocol

1. Inspect branch, `git status`, this README, `STATUS.md`, the shared August 8 roadmap, and handoff.
2. Acquire `scripts/product-catalog-aug08-automation-lock.ps1` with a unique `LOCAL-FOOD` run ID.
3. If the result is `BUSY` or `BUSY_RACE`, write no project files.
4. Re-check target files for pre-existing diffs; preserve all unrelated work.
5. Complete only the next incomplete stage, within 45 minutes.
6. Record commands, exact counts, SHA-256 hashes, PASS/FAIL/BLOCKED, and the next action in `STATUS.md`.
7. Release the lock in a finally path.

## Stages

- D1 — contract, model, source registry, identity, and quality gates.
- D2 — expand the 146 approved USDA generic baseline; normalize nutrient units, preparation states, and TR aliases.
- D3 — build the canonical Turkish dish taxonomy and rights-cleared/review-only profiles.
- D4 — run validation, deduplication, portions, macro-energy, and search QA.
- D5 — build immutable partitioned release artifacts and run two isolated import rehearsals.
- August 8 or later — final read-only audit and readiness classification.

## D1 preflight

Review-class validation:

```powershell
$python = "C:\Users\emrah\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe"
& $python .\scripts\validate-local-food-catalog.py `
  --input .\automation\local-food-catalog-aug08\fixtures\d1-contract-smoke.csv `
  --release-class REVIEW `
  --report .\outputs\local-food-catalog-aug08\review\d1-contract-smoke-report.json
```

Production preflight is intentionally stricter. Pending `ESTIMATED` profiles fail until a named, dated approval is present. TürKomp rows fail all ingestion until a recorded use/data-sales agreement changes the source registry.

The fixture proves the contract only. It is not counted as release data.

## Restaurant-chain follow-on lane

Local Dish remains the first priority. McDonald's, Burger King, and comparable chain menu items may be researched afterward only from official market-specific nutrition sources and must stay in a separate `BRANDED_PRODUCT` / `RESTAURANT_CHAIN` partition. They are never included in Local Dish or Generic Ingredient counts. Each candidate must retain chain, market, menu-item identity, serving/version date, official source, and nutrition provenance; retailer scraping and cross-market nutrition substitution remain prohibited.

## Current operating direction: accelerated LOCAL_DISH discovery

D3 is now the primary active lane. Work expands Turkish and UK/Irish prepared-dish identities while nutrition provenance and release approval continue as separate, fail-closed lanes.

- Produce 30-50 net-new canonical dish candidates per successful research run, with family/variant and duplicate checks performed in the same run.
- TR target: minimum 100, preferred 150-200 canonical dishes.
- UK_IE target: minimum 75, preferred 100-150 canonical dishes.
- Combined target: minimum 175, preferred 250-350 canonical dishes.
- Track three distinct metrics: taxonomy candidates, nutrition-ready profiles, and release-ready records. A discovered dish is never counted as nutrition-ready or release-ready without the required evidence.
- Prefer official ministry, tourism-board, local-government, university, and institutional cultural sources for name, region, category, and identity discovery. Reputable editorial/recipe sites may provide secondary identity cross-checks only.
- Never import nutrition values from unclear-rights recipe sites. Production nutrition must remain tied to rights-cleared sources such as USDA FoodData Central, license-verified CoFID, or a versioned calculation method using eligible components.
- Record stable source keys, market partition, TR/EN names and aliases, category, region, family/variant keys, discovery URLs, evidence class, and nutrition-profile status for every candidate.
- Volume targets do not relax source, license, provenance, nutrient completeness, serving, plausibility, duplicate, or approval gates.

Near-term run order is: expand TR taxonomy from official regional sources, expand UK_IE taxonomy from institutional sources, resolve duplicates/families, then promote only evidence-complete profiles into nutrition and release review.

## Active restaurant-chain pilot

The Local Dish preferred volume range has been reached, so a bounded restaurant-chain discovery lane may now run under the same shared lock. It writes only to the separate `BRANDED_PRODUCT / RESTAURANT_CHAIN` review partition, uses official market-specific sources, and never changes Local Dish or Generic Ingredient metrics. Per-serving nutrition without a verified serving weight remains review-only and cannot be converted to per 100 g.


## Restartable dual-lane scheduler

`work-lanes-v1.json` is the machine-readable source of truth for the next slice. It alternates independent queues across heartbeat runs while allowing at most one writing lane per acquired shared lock.

- `LOCAL_DISH_PROMOTION`: continue source substantiation and monitor the signed curator packet; never invent approval.
- `RESTAURANT_CHAIN_DISCOVERY`: official market-specific candidate, serving-weight, rights, and macro-source QA under `outputs/local-food-catalog-aug08/review/restaurant-chain/` only.
- `GENERIC_REMEDIATION`: resume expansion when the scoped USDA key is available; existing QA may continue read-only.

Run ID ownership uses `LOCAL-FOOD-LD-*` for Local Dish and `LOCAL-FOOD-RC-*` for restaurant chain work. `BUSY` or `BUSY_RACE` means zero project writes. The restaurant lane hands off only an immutable signed review manifest during D5; it never mutates `automation/product-catalog-aug08/` or branded import artifacts directly.

Current alternating order: Local Dish approval/evidence check, Burger King TR weight/rights QA, McDonald's TR pilot, Local Dish validation if approved, UK/IE chain pilot, then D5 partition manifest.

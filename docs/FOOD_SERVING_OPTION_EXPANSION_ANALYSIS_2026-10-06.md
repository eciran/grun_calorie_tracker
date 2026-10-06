# Food Serving Option Expansion Analysis

Date: 2026-10-06
Scope: Published, non-custom products in the active AWS test catalog.

## Objective

Improve the user experience for products that currently expose only one verified serving option. The goal is not to create arbitrary duplicate options. New options must represent a distinct, useful real-world measure or size and must have a verified gram or milliliter conversion.

## Current Catalog Baseline

| Metric | Count |
| --- | ---: |
| Published non-custom products | 181,749 |
| Products with at least one verified serving option | 501 |
| Verified serving options | 645 |
| Products with exactly one verified option | 414 |
| Products with multiple verified options | 87 |
| Maximum verified options on one product | 4 |

Only `VERIFIED` options are returned to the mobile client. Review-only options are therefore excluded from the user-visible baseline.

### Single-option products by catalog type

| Catalog type | Count |
| --- | ---: |
| `GENERIC_INGREDIENT` | 170 |
| `BRANDED_PRODUCT` | 126 |
| `STANDARD_PREPARED_ITEM` | 118 |

### Current unit distribution for the 414 products

| Unit | Count |
| --- | ---: |
| `CUP` | 150 |
| `SERVING` | 117 |
| `PIECE` | 69 |
| `PORTION` | 56 |
| `TABLESPOON` | 12 |
| `SLICE` | 6 |
| `PACKAGE` | 2 |
| `SHOT` | 1 |
| `BOWL` | 1 |

## Important Finding: Banana

The repository already contains migration `V109__seed_banana_serving_options.sql`, which defines medium, small, large, half and quarter banana options. Its product predicate is:

```sql
lower(coalesce(fi.display_name, fi.name)) = 'banana'
```

The current AWS result is `Raw Banana`, so the migration does not match it. The active record therefore exposes only the source-imported `1 medium Banana` option at 118 g.

Two AWS rows also require identity review before expansion:

- `49555 Raw Banana`: `GENERIC_INGREDIENT`, USDA FoodData, medium 118 g.
- `181161 Raw Banana`: USDA FoodData but classified as `BRANDED_PRODUCT`, medium 118 g.

These must be canonicalized or deduplicated first. Serving options must be attached to the surviving canonical product, not copied independently to both rows.

## Expansion Policy

### What should become multiple stored options

Store multiple options only when each option has a different real-world meaning and a source-backed conversion:

- Countable produce: small, medium and large piece.
- Eggs: size classes supported by the product market/source.
- Bread and bakery products: slice or piece when the source supplies its weight.
- Liquids: glass, mug, bottle or can only when the applicable volume is known.
- Spoonable foods: tablespoon and teaspoon when conversion evidence exists.
- Prepared meals: bowl, plate or named portion only when the recipe/source defines its weight or volume.

### What should not become duplicate database rows

Do not persist simple arithmetic variants such as half cup, two cups, half serving or two servings when the UI can change the quantity of the same option. A single verified `1 cup = 170 g` conversion already supports `0.5 cup`, `1.5 cups` and `2 cups` without additional serving-option identities.

Exceptions are familiar size labels such as small/medium/large banana, because the gram conversion is not a linear multiplier chosen by the user.

### Unsafe automatic expansion

The following groups must not be expanded from product-name keywords or guessed ratios:

- Branded package, bottle and can sizes without barcode/package evidence.
- Prepared dishes with only a synthetic `standard serving` value.
- Products with unresolved duplicate identity or incorrect catalog type.
- Liquids inferred only from words such as `water`, `milk` or `juice` in the name.
- Small/regular/large prepared meals without a source-defined size profile.

## Processing Queues

### Queue A - Identity cleanup first

Priority: highest.

- Merge or separate canonical duplicates before inserting options.
- Correct USDA generic foods incorrectly classified as branded products.
- Resolve records sharing the same food identity but carrying different catalog types.
- Recalculate the candidate count after cleanup.

This queue includes the duplicated/misclassified `Raw Banana` example.

### Queue B - Source-backed size profiles

Priority: high.

Best initial family: the 69 single-option `PIECE` products.

Candidate families include banana, apple, orange/mandarin, pear, kiwi, peach, avocado, potato and egg. For each family:

1. Resolve the canonical product and immutable source key.
2. Read all authoritative portions from the frozen source record.
3. Map only distinct sizes with positive gram conversions.
4. Add EN/TR labels through serving-option localizations.
5. Keep exactly one default option.
6. Reject conflicting or implausible conversions.

### Queue C - Existing option is already sufficient

Priority: medium/low.

Most `CUP`, `TABLESPOON` and `PORTION` records do not need separate half/double rows. Mobile should expose a decimal quantity selector for the verified option. This covers a large part of the 218 `CUP`/`PORTION`/`TABLESPOON` candidates without polluting the database.

### Queue D - Prepared-item review

Priority: after generic foods.

The 118 `STANDARD_PREPARED_ITEM` records mainly contain one fixed `standard serving`. They should receive a second option only when source or recipe evidence defines a bowl, plate, slice, piece or another real portion. Generic labels such as small/large must not be generated with guessed ratios.

### Queue E - Branded packaging evidence

Priority: last.

The 126 branded candidates require product-specific package evidence. Add can, bottle, package or bar options only when barcode/source data confirms the net amount. Different package sizes should remain separate barcode products when nutrition or commercial identity differs.

## Recommended First Release

Do not update all 414 records in one operation. Use this bounded sequence:

1. Produce the canonical duplicate and catalog-type correction report.
2. Fix the banana identity and verify its user-visible search result.
3. Build source-backed profiles for 20-30 high-usage generic `PIECE` foods.
4. Apply with database snapshot, exact expected-row gate and rollback SQL.
5. Verify English and Turkish labels, one default per product, positive conversions and nutrition calculations.
6. Validate search/detail/logging flows on mobile.
7. Expand to remaining generic foods, then prepared items, then branded products.

## Implementation Progress

- [x] Baseline the active AWS test catalog and identify 414 single-option products.
- [x] Identify the legacy banana migration's exact-name matching defect.
- [x] Prepare an idempotent canonical-key migration for small, medium and large raw banana options with EN/TR labels.
- [x] Pass the focused migration contract test (2/2).
- [x] Run the complete PostgreSQL migration suite against a clean database (297 migrations through V302; 8/8 tests passed).
- [x] Verify V302 twice against a canonical `Raw Banana` fixture: 3 options, 1 default, 3 distinct weights and 6 EN/TR localizations with no duplicate options.
- [x] Audit all 69 single-option `PIECE` candidates against their exact USDA FoodData source records.
- [x] Freeze 60 source-backed options across 24 immutable USDA source keys in V304.
- [x] Quarantine the three unsafe source identities (`169303`, `170049`, `2747659`) instead of exposing unsupported portions.
- [x] Leave 42 candidates unchanged where the authoritative source does not publish another meaningful conversion; no guessed sizes were generated.
- [x] Pass the clean PostgreSQL chain through V304 (299 migrations; 10/10 focused tests passed).
- [x] Run V302 and V304 twice against behavioral fixtures with zero duplicate option identities and zero products with multiple defaults.
- [x] Verify representative results: banana 3, peach 5, cooked onion 5 and raw egg 6 verified options, each with one default and complete EN/TR labels.
- [x] Separate authoritative conversion evidence from mobile-facing choices with V305 `user_visible` governance.
- [x] Hide five unnatural produce cup measures while retaining their source conversions for admin review and calculations.
- [x] Verify every hidden option has a visible verified sibling and no hidden option is selected as default.
- [ ] Validate the migration against an AWS dry-run snapshot before deployment.
- [ ] Resolve the second USDA `Raw Banana` record's branded/generic identity.
- [x] Build and review the first source-backed `PIECE` profile batch.
- [ ] Apply the approved batch with exact-count and rollback gates.

## Completed Batch Result

V304 uses exact `source_key` matches only. It does not match by display name, infer portions from keywords or use AI-estimated weights. Existing unsupported options on covered products are moved to `NEEDS_REVIEW`; approved identities are normalized rather than duplicated; missing options receive EN/TR localizations and exactly one verified default.

The remaining 42 audited `PIECE` candidates are intentionally not expanded. Their USDA source record either contains only the current measure, only a regulatory reference amount, or no safe product-specific portion conversion. They remain valid single-option products and continue to support gram entry. This is a closed evidence outcome, not unfinished processing.

V305 keeps source evidence and user experience separate. Cup-based conversions for sliced peach, orange sections, sliced plum, sweet-potato cubes and grape tomatoes remain stored but are not returned in mobile serving selectors. Natural piece/size choices remain visible. Cooking-oriented measures for prepared onions, eggs and similar recipe inputs remain available where they are useful.

## Acceptance Gates

- No serving option is created without a positive gram or milliliter conversion.
- Exactly one default verified option exists per expanded product.
- EN and TR labels point to the same serving-option identity.
- No canonical duplicate receives a parallel option set.
- Quantity scaling works without duplicate half/double option rows.
- Liquid options use ml only when the product classification and evidence support volume.
- Product detail and food-log calculations produce the same nutrition totals for equivalent quantities.
- Every batch has a snapshot, dry-run counts, exact-count apply gate and rollback path.

## Decision

The target is not to turn all 414 products into multi-option products. The target is to expand every product for which an additional option is accurate and useful. The first implementation batch should focus on canonical generic countable foods; the remaining products should stay single-option until reliable serving evidence exists.

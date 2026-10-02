# Standard Prepared Catalog 200 Plan

## Purpose

This plan defines the first 200 barcode-free prepared foods and drinks for the
UK/IE and TR markets. It is a scope manifest, not an import-ready nutrition
dataset. No row may enter production until nutrition, portion and source
evidence have passed review.

Source manifest:

- `sample-data/standard-prepared-catalog-200-v1.csv`

## Catalog Model Decision

The current catalog types do not describe these records precisely:

- `BRANDED_PRODUCT` requires a specific commercial product identity.
- `GENERIC_INGREDIENT` represents a base ingredient rather than a prepared
  consumer item.
- `LOCAL_DISH` represents a regional dish backed by a curated recipe.

Before import, introduce a dedicated type such as
`STANDARD_PREPARED_ITEM`. These records are barcode-free common preparations
such as Americano, latte, porridge, toastie or fries.

## Identity And Localization

- One canonical item is shared between markets when the preparation is
  materially the same.
- `display_name_en` and `display_name_tr` are localizations, not duplicate food
  records.
- A market-specific record is used only when the recipe, serving convention or
  product meaning differs materially. Examples include Turkish coffee, salep,
  full Irish breakfast and raki.
- Proposed canonical source key:
  `GLOBAL:STANDARD_PREPARED_ITEM:PREPARED:{item_key}` or
  `{market}:STANDARD_PREPARED_ITEM:PREPARED:{item_key}` for a market-specific
  preparation.

## Variant Policy

The manifest deliberately separates products from modifiers. Size, milk,
syrup, sugar, cream, extra espresso, dressing and sauce must not create a new
catalog row for every combination.

Examples:

- Latte is one canonical prepared item. Milk type, size, syrup and extra shot
  are calculation modifiers.
- Turkish coffee is one market-specific item. Sugar level and double portion
  are modifiers.
- Gin and tonic is one prepared drink. Gin measure and tonic type are
  modifiers.

Frequently searched named presets such as caramel latte may remain searchable,
but should resolve to a base preparation plus a controlled modifier recipe.

## Portion Profiles

Each `portion_profile` must resolve to reviewed serving options before import.

The controlled profile registry is stored in:

- `sample-data/standard-prepared-portion-profiles-v1.csv`
- `sample-data/standard-prepared-liquid-serving-options-v1.csv`
- `sample-data/standard-prepared-modifier-rules-v1.csv`

Liquid profiles use `PER_100ML` and expose reviewed milliliter options. Solid
profiles use `PER_100G`, but their piece, slice, bowl or plate weight must be
measured per item or calculated from an approved recipe yield. A generic
profile weight must never be copied across unrelated solid foods.

Modifier nutrition is component based. Size may scale the reviewed base
recipe, while milk, sugar, syrup, cream, toppings and similar choices must add
or substitute a linked catalog component. A modifier marked
`REQUIRES_COMPONENT_LINK`, `REQUIRES_PRODUCT_EVIDENCE` or
`REQUIRES_RECIPE_VARIANT` blocks production import until that dependency has
been reviewed.
At minimum:

- Hot/cold drinks: ml plus culturally familiar cup sizes.
- Espresso and spirits: shot/measure plus ml.
- Tea: cup/mug or Turkish tea glass plus ml.
- Bakery: piece or slice plus verified gram weight.
- Bowls and plates: serving plus verified gram weight.
- Alcohol: ml and local serving convention; ABV remains part of the evidence.

UK/IE examples include pint, half-pint, 25 ml/35 ml spirit measures and common
cafe cup sizes. TR examples include Turkish tea glass, Turkish coffee cup,
portion, bowl and glass. Legal serving conventions must be verified before
release and must not be inferred solely from names.

## Nutrition Evidence Pipeline

1. Resolve each item to an authoritative food-composition source or a reviewed
   canonical recipe.
2. Store ingredient quantities, prepared yield and nutrition basis.
3. Calculate and review base nutrition per 100 g or 100 ml.
4. Attach reviewed serving options and modifier deltas.
5. Add EN/TR aliases without duplicating the item.
6. Run duplicate, market, category, preparation-state and portion validation.
7. Import to staging only; compare search ranking and logged-calorie behavior.
8. Promote only approved records to production.

## Release Order

- P0: common coffees, tea, everyday cold drinks, core breakfasts and common
  sides.
- P1: broader cafe, bakery, sandwiches, regional preparations and core alcohol.
- P2: seasonal/niche drinks, desserts and variable cocktails.

Alcohol remains part of the calorie catalog, not a recommendation feature. It
should be searchable and loggable without celebratory language, consumption
prompts or health-benefit claims.

## Acceptance Gates

- Exactly 200 unique `item_key` values.
- No duplicate canonical identity across UK/IE and TR.
- Every row has EN/TR display names, market scope, portion profile and variant
  policy.
- Market-specific rows have an explicit reason.
- No production row has `PLANNED_RESEARCH` status.
- Nutrition and modifier calculations are covered by golden tests before
  production import.

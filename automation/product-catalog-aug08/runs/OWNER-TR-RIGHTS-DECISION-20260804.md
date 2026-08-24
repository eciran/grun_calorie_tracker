# Owner TR source authorization decision

- Run ID: `OWNER-TR-RIGHTS-DECISION-20260804`
- Date: `2026-08-04`
- Result: `PASS_PLAN_REBASE`
- Decision: the product owner confirms the already-collected TR retailer and
  supplemental datasets are authorized for this project's test-build use.

## Planning effect

- Usage rights are no longer a blocker for the August 8 test catalog.
- Source provenance and classifications remain recorded; no source rows are
  mixed into the licensed-default bundle automatically.
- The TR growth objective is now completion yield across the 13,173-row
  deduplicated retailer pool: exact barcode acquisition and plausible per-100
  nutrition completion.
- Current measured starting points: 1,693 rows with identity and nutrition,
  4,585 identity-only, 3,039 nutrition-only, 1,085 needing both after the
  name/brand policy; 10,155 active rows are addressable under existing queue
  policy. The versioned private v2 artifact already contains 2,251 rich rows.
- The 20,699 supplemental net-new GTINs remain a later discovery backlog until
  product identity and nutrition are filled; rights are not their blocker.

## UK/IE confirmation

The uncapped strict artifact contains 151,928 rows. Every accepted row has a
checksum-valid GTIN, name, brand, and plausible core per-100 energy/protein/fat/
carbohydrate fields. Optional completeness is partial: 102,043 images, 98,857
servings, 103,167 fiber, 140,943 sugars, and 115,485 sodium rows.

Evidence:

- `outputs/product-catalog-aug08/uk-ie-off-20260802-capacity/uk-ie-off-capacity-report.json`
- `outputs/product-catalog-aug08/tr-capacity-20260802/tr-capacity-report.json`

No catalog, database, network, production, or AWS mutation was performed.

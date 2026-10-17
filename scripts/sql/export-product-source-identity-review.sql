-- Product-only, version-compatible even before V307; no AWS writes.
-- psql -X -q -v ON_ERROR_STOP=1 -f <file> > catalog.csv
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '90s';
SET LOCAL lock_timeout = '3s';
COPY (
    SELECT id, source_key, name, display_name, catalog_type, data_source,
           preparation_state, nutrition_reference_unit, barcode, normalized_barcode,
           market_region, calories, protein, fat, carbs,
           canonical_food_key, to_jsonb(f)->>'source_data_type' AS source_data_type,
           to_jsonb(f)->>'food_concept_key' AS food_concept_key,
           to_jsonb(f)->>'composition_variant_key' AS composition_variant_key
    FROM food_items f
    WHERE NOT coalesce(is_custom, false)
      AND (data_source = 'USDA_FOODDATA' OR catalog_type IN
           ('GENERIC_INGREDIENT', 'STANDARD_PREPARED_ITEM', 'LOCAL_DISH'))
    ORDER BY id
) TO STDOUT WITH (FORMAT CSV, HEADER TRUE);
COMMIT;

-- Repeatable-read per batch; a multi-batch sweep is not one global snapshot.
-- psql -X -q -v ON_ERROR_STOP=1 -v after_id=0 -v high_water_id=<captured_max>
--      -v batch_size=500 -f <file> > batch.csv
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '60s';
SET LOCAL lock_timeout = '3s';
COPY (
    SELECT f.id, f.source_key, f.name, f.display_name, f.short_display_name,
           f.catalog_type, f.data_source, f.preparation_state, f.nutrition_reference_unit,
           f.canonical_food_key, f.brand, f.brand_id,
           b.canonical_name AS canonical_brand_name, b.verified AS canonical_brand_verified,
           b.status AS canonical_brand_status,
           l.display_name_en, l.short_display_name_en, l.display_name_tr, l.short_display_name_tr,
           to_jsonb(f)->>'source_data_type' AS source_data_type,
           to_jsonb(f)->>'food_concept_key' AS food_concept_key,
           to_jsonb(f)->>'composition_variant_key' AS composition_variant_key
    FROM food_items f
    LEFT JOIN food_brands b ON b.id = f.brand_id
    LEFT JOIN LATERAL (
        SELECT max(display_name) FILTER (WHERE language = 'EN') AS display_name_en,
               max(short_display_name) FILTER (WHERE language = 'EN') AS short_display_name_en,
               max(display_name) FILTER (WHERE language = 'TR') AS display_name_tr,
               max(short_display_name) FILTER (WHERE language = 'TR') AS short_display_name_tr
        FROM food_item_localizations
        WHERE food_item_id = f.id AND active = true
    ) l ON true
    WHERE NOT coalesce(f.is_custom, false)
      AND f.id > :'after_id'::bigint AND f.id <= :'high_water_id'::bigint
    ORDER BY f.id
    LIMIT greatest(1, least(1000, :'batch_size'::integer))
) TO STDOUT WITH (FORMAT CSV, HEADER TRUE);
ROLLBACK;

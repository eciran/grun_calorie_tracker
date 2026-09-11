-- Preserve source-backed food_items.name for traceability. Only clean the
-- user-facing fields when an imported branded product ends in a stray comma.
UPDATE food_items
SET display_name = regexp_replace(display_name, ',[[:space:]]*$', ''),
    short_display_name = CASE
        WHEN short_display_name IS NULL THEN NULL
        ELSE regexp_replace(short_display_name, ',[[:space:]]*$', '')
    END
WHERE catalog_type = 'BRANDED_PRODUCT'
  AND display_name ~ ',[[:space:]]*$';

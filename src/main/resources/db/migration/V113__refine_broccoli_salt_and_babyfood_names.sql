INSERT INTO food_item_localizations(food_item_id, language, display_name, short_display_name, source)
SELECT id, 'TR', 'Tuzlu Haslanmis Brokoli', 'Tuzlu Haslanmis Brokoli', 'core_localization_refine'
FROM food_items
WHERE catalog_type = 'GENERIC_INGREDIENT'
  AND lower(name) LIKE '%broccoli%'
  AND lower(name) LIKE '%with salt%'
ON CONFLICT (food_item_id, language) DO UPDATE
SET display_name = EXCLUDED.display_name,
    short_display_name = EXCLUDED.short_display_name,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO food_item_localizations(food_item_id, language, display_name, short_display_name, source)
SELECT id, 'TR', 'Tuzsuz Haslanmis Brokoli', 'Tuzsuz Haslanmis Brokoli', 'core_localization_refine'
FROM food_items
WHERE catalog_type = 'GENERIC_INGREDIENT'
  AND lower(name) LIKE '%broccoli%'
  AND lower(name) LIKE '%without salt%'
ON CONFLICT (food_item_id, language) DO UPDATE
SET display_name = EXCLUDED.display_name,
    short_display_name = EXCLUDED.short_display_name,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO food_item_localizations(food_item_id, language, display_name, short_display_name, source)
SELECT id, 'TR', 'Brokolili Bebek Mamasi', 'Bebek Mamasi', 'core_localization_refine'
FROM food_items
WHERE catalog_type = 'GENERIC_INGREDIENT'
  AND lower(name) LIKE '%babyfood%'
  AND lower(name) LIKE '%broccoli%'
ON CONFLICT (food_item_id, language) DO UPDATE
SET display_name = EXCLUDED.display_name,
    short_display_name = EXCLUDED.short_display_name,
    updated_at = CURRENT_TIMESTAMP;
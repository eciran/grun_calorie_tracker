INSERT INTO food_item_localizations(food_item_id, language, display_name, short_display_name, source)
SELECT id, 'TR', 'Brokoli Yapraklari', 'Brokoli Yapraklari', 'core_localization_refine'
FROM food_items
WHERE catalog_type = 'GENERIC_INGREDIENT'
  AND lower(name) LIKE '%broccoli%'
  AND lower(name) LIKE '%leaves%'
ON CONFLICT (food_item_id, language) DO UPDATE
SET display_name = EXCLUDED.display_name,
    short_display_name = EXCLUDED.short_display_name,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO food_item_localizations(food_item_id, language, display_name, short_display_name, source)
SELECT id, 'TR', 'Brokoli Saplari', 'Brokoli Saplari', 'core_localization_refine'
FROM food_items
WHERE catalog_type = 'GENERIC_INGREDIENT'
  AND lower(name) LIKE '%broccoli%'
  AND lower(name) LIKE '%stalk%'
ON CONFLICT (food_item_id, language) DO UPDATE
SET display_name = EXCLUDED.display_name,
    short_display_name = EXCLUDED.short_display_name,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO food_item_localizations(food_item_id, language, display_name, short_display_name, source)
SELECT id, 'TR', 'Brokoli Cicekleri', 'Brokoli Cicekleri', 'core_localization_refine'
FROM food_items
WHERE catalog_type = 'GENERIC_INGREDIENT'
  AND lower(name) LIKE '%broccoli%'
  AND lower(name) LIKE '%flower clusters%'
ON CONFLICT (food_item_id, language) DO UPDATE
SET display_name = EXCLUDED.display_name,
    short_display_name = EXCLUDED.short_display_name,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO food_item_localizations(food_item_id, language, display_name, short_display_name, source)
SELECT id, 'TR', 'Dondurulmus Dogranmis Brokoli', 'Dondurulmus Brokoli', 'core_localization_refine'
FROM food_items
WHERE catalog_type = 'GENERIC_INGREDIENT'
  AND lower(name) LIKE '%broccoli%'
  AND lower(name) LIKE '%frozen%'
ON CONFLICT (food_item_id, language) DO UPDATE
SET display_name = EXCLUDED.display_name,
    short_display_name = EXCLUDED.short_display_name,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO food_item_localizations(food_item_id, language, display_name, short_display_name, source)
SELECT id, 'TR', 'Haslanmis Brokoli', 'Haslanmis Brokoli', 'core_localization_refine'
FROM food_items
WHERE catalog_type = 'GENERIC_INGREDIENT'
  AND lower(name) LIKE '%broccoli%'
  AND (lower(name) LIKE '%cooked%' OR lower(name) LIKE '%boiled%')
ON CONFLICT (food_item_id, language) DO UPDATE
SET display_name = EXCLUDED.display_name,
    short_display_name = EXCLUDED.short_display_name,
    updated_at = CURRENT_TIMESTAMP;
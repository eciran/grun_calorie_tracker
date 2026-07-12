UPDATE food_item_localizations loc
SET display_name = 'Cok Olgun Muz',
    short_display_name = 'Cok Olgun Muz',
    updated_at = CURRENT_TIMESTAMP
FROM food_items item
WHERE loc.food_item_id = item.id
  AND loc.language = 'TR'
  AND item.catalog_type = 'GENERIC_INGREDIENT'
  AND lower(item.name) LIKE '%banana%'
  AND lower(item.name) LIKE '%overripe%';

UPDATE food_item_localizations loc
SET display_name = 'Muz',
    short_display_name = 'Muz',
    updated_at = CURRENT_TIMESTAMP
FROM food_items item
WHERE loc.food_item_id = item.id
  AND loc.language = 'TR'
  AND item.catalog_type = 'GENERIC_INGREDIENT'
  AND lower(item.name) LIKE '%banana%'
  AND lower(item.name) LIKE '%ripe and slightly ripe%';

UPDATE food_item_localizations loc
SET display_name = 'Cin Brokolisi',
    short_display_name = 'Cin Brokolisi',
    updated_at = CURRENT_TIMESTAMP
FROM food_items item
WHERE loc.food_item_id = item.id
  AND loc.language = 'TR'
  AND item.catalog_type = 'GENERIC_INGREDIENT'
  AND lower(item.name) LIKE '%broccoli%'
  AND lower(item.name) LIKE '%chinese%';

UPDATE food_item_localizations loc
SET display_name = 'Brokoli Yapraklari',
    short_display_name = 'Brokoli Yapraklari',
    updated_at = CURRENT_TIMESTAMP
FROM food_items item
WHERE loc.food_item_id = item.id
  AND loc.language = 'TR'
  AND item.catalog_type = 'GENERIC_INGREDIENT'
  AND lower(item.name) LIKE '%broccoli%'
  AND lower(item.name) LIKE '%leaves%';

UPDATE food_item_localizations loc
SET display_name = 'Brokoli Saplari',
    short_display_name = 'Brokoli Saplari',
    updated_at = CURRENT_TIMESTAMP
FROM food_items item
WHERE loc.food_item_id = item.id
  AND loc.language = 'TR'
  AND item.catalog_type = 'GENERIC_INGREDIENT'
  AND lower(item.name) LIKE '%broccoli%'
  AND lower(item.name) LIKE '%stalk%';

INSERT INTO food_item_localizations(food_item_id, language, display_name, short_display_name, source)
SELECT id, 'TR', 'Brokoli Raab', 'Brokoli Raab', 'core_localization_refine'
FROM food_items
WHERE catalog_type = 'GENERIC_INGREDIENT'
  AND lower(name) LIKE '%broccoli%'
  AND (lower(name) LIKE '%raab%' OR lower(name) LIKE '%rabe%')
ON CONFLICT (food_item_id, language) DO UPDATE
SET display_name = EXCLUDED.display_name,
    short_display_name = EXCLUDED.short_display_name,
    updated_at = CURRENT_TIMESTAMP;
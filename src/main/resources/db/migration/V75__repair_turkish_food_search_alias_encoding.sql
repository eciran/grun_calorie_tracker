UPDATE food_item_search_aliases
SET alias = 'süt'
WHERE language = 'TR'
  AND source = 'migration_seed'
  AND normalized_alias = 'sut';

UPDATE food_item_search_aliases
SET alias = 'yoğurt'
WHERE language = 'TR'
  AND source = 'migration_seed'
  AND normalized_alias = 'yogurt';

UPDATE food_item_search_aliases
SET alias = 'tavuk göğsü'
WHERE language = 'TR'
  AND source = 'migration_seed'
  AND normalized_alias = 'tavuk gogsu';

UPDATE food_item_search_aliases
SET alias = 'pirinç'
WHERE language = 'TR'
  AND source = 'migration_seed'
  AND normalized_alias = 'pirinc';

UPDATE food_item_search_aliases
SET alias = 'tereyağı'
WHERE language = 'TR'
  AND source = 'migration_seed'
  AND normalized_alias = 'tereyagi';

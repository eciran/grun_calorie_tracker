UPDATE food_item_search_aliases alias
SET active = FALSE
FROM food_items food
WHERE alias.food_item_id = food.id
  AND alias.source = 'migration_seed'
  AND alias.language = 'TR'
  AND alias.normalized_alias = 'sut'
  AND (
      lower(food.name) NOT LIKE '%milk%'
      OR lower(food.name) LIKE '%chocolate%'
      OR lower(food.name) LIKE '%cocoa%'
      OR lower(food.name) LIKE '%spread%'
      OR lower(food.name) LIKE '%raisin%'
      OR lower(food.name) LIKE '%biscuit%'
      OR lower(food.name) LIKE '%cookie%'
      OR lower(food.name) LIKE '%bar%'
      OR lower(food.name) LIKE '%coin%'
      OR lower(food.name) LIKE '%flavour%'
      OR lower(food.name) LIKE '%flavor%'
      OR lower(food.name) LIKE '%powder%'
      OR lower(food.name) LIKE '%shake%'
      OR coalesce(food.calories, 0) > 150
  );
ALTER TABLE food_items
    ADD COLUMN nutrition_basis VARCHAR(32) NOT NULL DEFAULT 'SOURCE_REPORTED';

ALTER TABLE food_items
    ADD CONSTRAINT chk_food_items_nutrition_basis
        CHECK (nutrition_basis IN ('SOURCE_REPORTED', 'CALCULATED', 'ESTIMATED'));

UPDATE food_items
SET nutrition_basis = 'ESTIMATED'
WHERE catalog_type = 'LOCAL_DISH'
  AND data_source IN ('MANUAL', 'ADMIN_IMPORT');
ALTER TABLE food_items
    ADD COLUMN nutrition_reference_unit VARCHAR(32) NOT NULL DEFAULT 'PER_100G';

ALTER TABLE food_items
    ADD CONSTRAINT chk_food_items_nutrition_reference_unit
        CHECK (nutrition_reference_unit IN ('PER_100G', 'PER_100ML'));

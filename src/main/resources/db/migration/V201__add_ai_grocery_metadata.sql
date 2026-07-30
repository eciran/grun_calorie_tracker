ALTER TABLE meal_plan_items
    ADD COLUMN grocery_name VARCHAR(160),
    ADD COLUMN preparation_method VARCHAR(30);

ALTER TABLE meal_plan_items
    ADD CONSTRAINT chk_meal_plan_items_preparation_method
        CHECK (preparation_method IS NULL OR preparation_method IN (
            'UNSPECIFIED', 'RAW', 'COOKED', 'BOILED', 'GRILLED',
            'FRIED', 'BAKED', 'ROASTED', 'STEAMED', 'PREPARED'
        ));
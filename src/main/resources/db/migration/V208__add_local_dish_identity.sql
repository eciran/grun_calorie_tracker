ALTER TABLE food_items
    ADD COLUMN IF NOT EXISTS dish_family_key VARCHAR(160);

ALTER TABLE food_items
    ADD COLUMN IF NOT EXISTS dish_variant_key VARCHAR(160);

CREATE INDEX IF NOT EXISTS idx_food_items_local_dish_family_variant
    ON food_items (market_region, dish_family_key, dish_variant_key)
    WHERE catalog_type = 'LOCAL_DISH';

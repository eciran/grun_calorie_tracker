ALTER TABLE food_items
    ADD COLUMN IF NOT EXISTS canonical_food_key VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_food_items_canonical_food_key
    ON food_items (canonical_food_key)
    WHERE canonical_food_key IS NOT NULL;

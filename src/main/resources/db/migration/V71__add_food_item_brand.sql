ALTER TABLE food_items
    ADD COLUMN IF NOT EXISTS brand VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_food_items_brand
    ON food_items (brand);

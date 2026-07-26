ALTER TABLE food_search_telemetry
    ADD COLUMN IF NOT EXISTS result_food_item_ids VARCHAR(2000) NOT NULL DEFAULT '';

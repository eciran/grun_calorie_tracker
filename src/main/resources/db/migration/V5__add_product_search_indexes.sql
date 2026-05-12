CREATE INDEX IF NOT EXISTS idx_food_items_name ON food_items (name);

CREATE INDEX IF NOT EXISTS idx_food_items_search_quality
    ON food_items (quality_score DESC, usage_count DESC, name ASC);

CREATE INDEX IF NOT EXISTS idx_food_items_nutri_score ON food_items (nutri_score);

CREATE TABLE IF NOT EXISTS recipe_categories (
    recipe_id BIGINT NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    category VARCHAR(60) NOT NULL,
    PRIMARY KEY (recipe_id, category)
);

CREATE INDEX IF NOT EXISTS idx_recipe_categories_category
    ON recipe_categories(category);

CREATE INDEX IF NOT EXISTS idx_recipes_public_discovery
    ON recipes(visibility, verification_status, archived, market_region, meal_type, updated_at DESC);

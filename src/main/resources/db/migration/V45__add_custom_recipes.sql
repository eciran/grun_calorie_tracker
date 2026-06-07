CREATE TABLE IF NOT EXISTS recipes (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT REFERENCES users(id),
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000),
    meal_type VARCHAR(40),
    visibility VARCHAR(40) NOT NULL DEFAULT 'PRIVATE',
    verification_status VARCHAR(40),
    market_region VARCHAR(40),
    language VARCHAR(12),
    image_url VARCHAR(1024),
    total_yield_grams DOUBLE PRECISION,
    default_serving_grams DOUBLE PRECISION,
    serving_count INTEGER,
    snapshot_calories DOUBLE PRECISION,
    snapshot_protein DOUBLE PRECISION,
    snapshot_carbs DOUBLE PRECISION,
    snapshot_fat DOUBLE PRECISION,
    snapshot_fiber DOUBLE PRECISION,
    snapshot_sugar DOUBLE PRECISION,
    snapshot_sodium DOUBLE PRECISION,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS recipe_ingredients (
    id BIGSERIAL PRIMARY KEY,
    recipe_id BIGINT NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    food_item_id BIGINT NOT NULL REFERENCES food_items(id),
    portion_size DOUBLE PRECISION,
    portion_unit VARCHAR(40),
    normalized_portion_grams DOUBLE PRECISION,
    item_order INTEGER
);

CREATE INDEX IF NOT EXISTS idx_recipes_owner_archived_updated
    ON recipes(owner_user_id, archived, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_recipes_visibility_archived_updated
    ON recipes(visibility, archived, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_recipe_ingredients_recipe_order
    ON recipe_ingredients(recipe_id, item_order, id);

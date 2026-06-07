CREATE TABLE IF NOT EXISTS recipe_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    recipe_id BIGINT NOT NULL REFERENCES recipes(id),
    serving_grams DOUBLE PRECISION,
    serving_count DOUBLE PRECISION,
    meal_type VARCHAR(40),
    log_date TIMESTAMP NOT NULL,
    snapshot_calories DOUBLE PRECISION,
    snapshot_protein DOUBLE PRECISION,
    snapshot_carbs DOUBLE PRECISION,
    snapshot_fat DOUBLE PRECISION,
    snapshot_fiber DOUBLE PRECISION,
    snapshot_sugar DOUBLE PRECISION,
    snapshot_sodium DOUBLE PRECISION,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_recipe_logs_user_date
    ON recipe_logs(user_id, log_date);

CREATE INDEX IF NOT EXISTS idx_recipe_logs_user_meal_date
    ON recipe_logs(user_id, meal_type, log_date);

CREATE INDEX IF NOT EXISTS idx_recipe_logs_recipe
    ON recipe_logs(recipe_id);

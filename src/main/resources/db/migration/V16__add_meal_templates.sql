CREATE TABLE IF NOT EXISTS meal_templates (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(255) NOT NULL,
    meal_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_meal_templates_user_created
    ON meal_templates (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS meal_template_items (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL REFERENCES meal_templates(id) ON DELETE CASCADE,
    food_item_id BIGINT NOT NULL REFERENCES food_items(id),
    portion_size DOUBLE PRECISION,
    portion_unit VARCHAR(50),
    normalized_portion_grams DOUBLE PRECISION,
    log_time TIME,
    item_order INTEGER
);

CREATE INDEX IF NOT EXISTS idx_meal_template_items_template_order
    ON meal_template_items (template_id, item_order, id);

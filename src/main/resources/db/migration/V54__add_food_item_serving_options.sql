CREATE TABLE food_item_serving_options (
    id BIGSERIAL PRIMARY KEY,
    food_item_id BIGINT NOT NULL REFERENCES food_items(id) ON DELETE CASCADE,
    label VARCHAR(120) NOT NULL,
    unit_type VARCHAR(40) NOT NULL,
    quantity DOUBLE PRECISION NOT NULL,
    gram_weight DOUBLE PRECISION,
    ml_volume DOUBLE PRECISION,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    source VARCHAR(40) NOT NULL DEFAULT 'ADMIN',
    quality_status VARCHAR(40) NOT NULL DEFAULT 'NEEDS_REVIEW',
    CONSTRAINT chk_food_item_serving_options_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_food_item_serving_options_weight_or_volume CHECK (
        (gram_weight IS NOT NULL AND gram_weight > 0)
        OR (ml_volume IS NOT NULL AND ml_volume > 0)
    )
);

CREATE INDEX idx_food_item_serving_options_food_item
    ON food_item_serving_options(food_item_id);

ALTER TABLE food_logs
    ADD COLUMN serving_option_id BIGINT NULL;

ALTER TABLE food_logs
    ADD CONSTRAINT fk_food_logs_serving_option
    FOREIGN KEY (serving_option_id)
    REFERENCES food_item_serving_options(id)
    ON DELETE SET NULL;

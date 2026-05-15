CREATE TABLE IF NOT EXISTS food_product_review_audits (
    id BIGSERIAL PRIMARY KEY,
    food_item_id BIGINT NOT NULL,
    reviewed_by VARCHAR(255),
    action_type VARCHAR(50) NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_food_product_review_audits_food_item
        FOREIGN KEY (food_item_id)
        REFERENCES food_items (id)
);

CREATE INDEX IF NOT EXISTS idx_food_product_review_audits_food_item_created_at
    ON food_product_review_audits (food_item_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_food_product_review_audits_reviewed_by
    ON food_product_review_audits (reviewed_by);

CREATE INDEX IF NOT EXISTS idx_food_product_review_audits_action_type
    ON food_product_review_audits (action_type);

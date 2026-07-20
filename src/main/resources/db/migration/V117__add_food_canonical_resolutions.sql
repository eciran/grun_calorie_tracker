CREATE TABLE IF NOT EXISTS food_canonical_resolutions (
    canonical_food_key VARCHAR(255) PRIMARY KEY,
    primary_food_item_id BIGINT NOT NULL,
    resolved_by VARCHAR(255),
    resolved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_food_canonical_resolutions_primary
        FOREIGN KEY (primary_food_item_id)
        REFERENCES food_items (id)
);

CREATE INDEX IF NOT EXISTS idx_food_canonical_resolutions_primary
    ON food_canonical_resolutions (primary_food_item_id);
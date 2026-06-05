CREATE TABLE IF NOT EXISTS food_product_quality_issues (
    id BIGSERIAL PRIMARY KEY,
    food_item_id BIGINT NOT NULL,
    issue_type VARCHAR(255) NOT NULL,
    identifier VARCHAR(255),
    reason VARCHAR(500),
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    first_detected_at TIMESTAMP,
    last_detected_at TIMESTAMP,
    resolved_at TIMESTAMP,
    resolved_by VARCHAR(255),
    CONSTRAINT fk_food_product_quality_issues_food_item
        FOREIGN KEY (food_item_id) REFERENCES food_items (id) ON DELETE CASCADE,
    CONSTRAINT chk_food_product_quality_issues_type
        CHECK (issue_type IN (
            'LOW_QUALITY',
            'MISSING_IMAGE',
            'MISSING_CALORIES',
            'MISSING_MACROS',
            'MISSING_SERVING_SIZE',
            'MISSING_REGION',
            'UNSUPPORTED_REGION',
            'MISSING_BARCODE',
            'INVALID_BARCODE_FORMAT'
        ))
);

CREATE INDEX IF NOT EXISTS idx_food_product_quality_issues_food_item_resolved
    ON food_product_quality_issues (food_item_id, resolved);

CREATE INDEX IF NOT EXISTS idx_food_product_quality_issues_type_resolved
    ON food_product_quality_issues (issue_type, resolved);

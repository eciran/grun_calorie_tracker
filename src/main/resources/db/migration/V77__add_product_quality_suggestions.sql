CREATE TABLE IF NOT EXISTS product_quality_suggestions (
    id BIGSERIAL PRIMARY KEY,
    food_item_id BIGINT NOT NULL,
    suggestion_type VARCHAR(50) NOT NULL,
    source VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    confidence_score INTEGER,
    current_value VARCHAR(1000),
    suggested_value VARCHAR(1000),
    reason VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP,
    reviewed_by VARCHAR(255),
    CONSTRAINT fk_product_quality_suggestions_food_item
        FOREIGN KEY (food_item_id) REFERENCES food_items (id) ON DELETE CASCADE,
    CONSTRAINT chk_product_quality_suggestions_type
        CHECK (suggestion_type IN ('NAME_CLEANUP', 'SEARCH_ALIAS')),
    CONSTRAINT chk_product_quality_suggestions_source
        CHECK (source IN ('RULE_BASED', 'AI_ASSISTED')),
    CONSTRAINT chk_product_quality_suggestions_status
        CHECK (status IN ('OPEN', 'ACCEPTED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_product_quality_suggestions_status_created
    ON product_quality_suggestions (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_product_quality_suggestions_food_item_status
    ON product_quality_suggestions (food_item_id, status);

CREATE UNIQUE INDEX IF NOT EXISTS ux_product_quality_suggestions_open_dedupe
    ON product_quality_suggestions (food_item_id, suggestion_type, suggested_value)
    WHERE status = 'OPEN';

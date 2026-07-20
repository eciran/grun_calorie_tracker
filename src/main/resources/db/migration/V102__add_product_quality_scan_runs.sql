ALTER TABLE food_items
    ADD COLUMN IF NOT EXISTS quality_validated_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS quality_validated_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS quality_validation_source VARCHAR(50),
    ADD COLUMN IF NOT EXISTS quality_validation_notes VARCHAR(1000);

CREATE TABLE IF NOT EXISTS product_quality_scan_runs (
    id BIGSERIAL PRIMARY KEY,
    source VARCHAR(50) NOT NULL,
    trigger_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    market_region VARCHAR(50),
    requested_limit INTEGER NOT NULL,
    effective_limit INTEGER NOT NULL,
    force_rescan BOOLEAN NOT NULL DEFAULT FALSE,
    scanned_products INTEGER NOT NULL DEFAULT 0,
    created_suggestions INTEGER NOT NULL DEFAULT 0,
    skipped_existing_suggestions INTEGER NOT NULL DEFAULT 0,
    skipped_previously_validated_products INTEGER NOT NULL DEFAULT 0,
    validated_products INTEGER NOT NULL DEFAULT 0,
    triggered_by VARCHAR(255),
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    error_message VARCHAR(1000),
    CONSTRAINT chk_product_quality_scan_runs_source
        CHECK (source IN ('RULE_BASED', 'AI_ASSISTED')),
    CONSTRAINT chk_product_quality_scan_runs_trigger_type
        CHECK (trigger_type IN ('MANUAL', 'SCHEDULED')),
    CONSTRAINT chk_product_quality_scan_runs_status
        CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_food_items_quality_validation
    ON food_items (quality_validated_at, verification_status, market_region);

CREATE INDEX IF NOT EXISTS idx_product_quality_scan_runs_started
    ON product_quality_scan_runs (started_at DESC);

CREATE INDEX IF NOT EXISTS idx_product_quality_scan_runs_status
    ON product_quality_scan_runs (status, started_at DESC);

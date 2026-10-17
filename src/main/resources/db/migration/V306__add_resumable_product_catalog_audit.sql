CREATE TABLE product_catalog_audit_state (
    id BIGINT PRIMARY KEY CHECK (id = 1),
    scan_run_id BIGINT REFERENCES product_quality_scan_runs(id),
    status VARCHAR(24) NOT NULL DEFAULT 'READY'
        CHECK (status IN ('READY', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    ruleset_version VARCHAR(80),
    cursor_id BIGINT NOT NULL DEFAULT 0 CHECK (cursor_id >= 0),
    high_water_id BIGINT NOT NULL DEFAULT 0 CHECK (high_water_id >= cursor_id),
    lease_owner VARCHAR(64),
    lease_until TIMESTAMP,
    last_completed_at TIMESTAMP,
    CHECK ((lease_owner IS NULL) = (lease_until IS NULL))
);
INSERT INTO product_catalog_audit_state(id) VALUES (1);

-- One current scan record per product, not a new 181k-row history on every sweep.
CREATE TABLE product_catalog_audit_items (
    product_id BIGINT PRIMARY KEY REFERENCES food_items(id) ON DELETE CASCADE,
    scan_run_id BIGINT NOT NULL REFERENCES product_quality_scan_runs(id),
    status VARCHAR(24) NOT NULL CHECK (status IN ('RULES_PASSED', 'REVIEW_REQUIRED', 'RETRY_PENDING', 'DEAD_LETTER', 'EXCLUDED')),
    ruleset_version VARCHAR(80) NOT NULL,
    input_fingerprint VARCHAR(64),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    last_scanned_at TIMESTAMP,
    next_attempt_at TIMESTAMP,
    findings_json TEXT,
    last_error_code VARCHAR(255)
);
CREATE INDEX idx_product_catalog_audit_retry ON product_catalog_audit_items(scan_run_id, status, next_attempt_at, product_id);

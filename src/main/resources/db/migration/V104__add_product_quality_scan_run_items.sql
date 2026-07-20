CREATE TABLE IF NOT EXISTS product_quality_scan_run_items (
    id BIGSERIAL PRIMARY KEY,
    scan_run_id BIGINT NOT NULL REFERENCES product_quality_scan_runs(id) ON DELETE CASCADE,
    food_item_id BIGINT NOT NULL REFERENCES food_items(id) ON DELETE CASCADE,
    status VARCHAR(40) NOT NULL,
    suggestion_type VARCHAR(80),
    field_name VARCHAR(100),
    suggested_value VARCHAR(1000),
    reason VARCHAR(1000),
    confidence_score INTEGER,
    product_name_snapshot VARCHAR(255),
    brand_snapshot VARCHAR(255),
    note VARCHAR(1000)
);

CREATE INDEX IF NOT EXISTS idx_product_quality_scan_run_items_run_id
    ON product_quality_scan_run_items(scan_run_id);

CREATE INDEX IF NOT EXISTS idx_product_quality_scan_run_items_food_item_id
    ON product_quality_scan_run_items(food_item_id);

CREATE INDEX IF NOT EXISTS idx_product_quality_scan_run_items_status
    ON product_quality_scan_run_items(status);
ALTER TABLE food_logs
    ADD COLUMN source VARCHAR(30) NOT NULL DEFAULT 'MANUAL';

CREATE TABLE failed_barcode_scans (
    id BIGSERIAL PRIMARY KEY,
    barcode VARCHAR(80) NOT NULL,
    market_region VARCHAR(40),
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    scan_count BIGINT NOT NULL DEFAULT 1,
    first_scanned_at TIMESTAMP NOT NULL,
    last_scanned_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_failed_barcode_scans_lookup
    ON failed_barcode_scans(barcode, market_region, user_id);

CREATE TABLE product_correction_suggestions (
    id BIGSERIAL PRIMARY KEY,
    food_item_id BIGINT NOT NULL REFERENCES food_items(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    suggested_calories DOUBLE PRECISION,
    suggested_protein DOUBLE PRECISION,
    suggested_carbs DOUBLE PRECISION,
    suggested_fat DOUBLE PRECISION,
    note VARCHAR(500),
    image_url VARCHAR(500),
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_product_correction_suggestions_food_status
    ON product_correction_suggestions(food_item_id, status, created_at DESC);

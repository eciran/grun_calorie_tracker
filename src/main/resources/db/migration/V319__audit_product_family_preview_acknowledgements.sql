ALTER TABLE food_product_families
    ADD COLUMN IF NOT EXISTS preview_fingerprint VARCHAR(64),
    ADD COLUMN IF NOT EXISTS acknowledged_warnings VARCHAR(4000);

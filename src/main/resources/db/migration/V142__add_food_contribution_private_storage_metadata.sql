ALTER TABLE food_product_contributions
    ALTER COLUMN evidence_url DROP NOT NULL,
    ADD COLUMN evidence_storage_key VARCHAR(1024),
    ADD COLUMN evidence_content_type VARCHAR(80),
    ADD COLUMN evidence_size_bytes BIGINT;

ALTER TABLE food_product_contributions
    ADD CONSTRAINT chk_food_product_contribution_evidence_size
        CHECK (evidence_size_bytes IS NULL OR evidence_size_bytes > 0);

CREATE UNIQUE INDEX uq_food_product_contribution_storage_key
    ON food_product_contributions (evidence_storage_key)
    WHERE evidence_storage_key IS NOT NULL;

CREATE TABLE food_product_source_evidence (
    id BIGSERIAL PRIMARY KEY,
    food_item_id BIGINT NOT NULL,
    provider VARCHAR(40) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    field_name VARCHAR(50) NOT NULL,
    numeric_value DOUBLE PRECISION NOT NULL,
    basis VARCHAR(30) NOT NULL,
    confidence_score INTEGER NOT NULL,
    observed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fingerprint VARCHAR(64) NOT NULL UNIQUE,
    source_version VARCHAR(120),
    CONSTRAINT fk_food_source_evidence_item
        FOREIGN KEY (food_item_id) REFERENCES food_items(id),
    CONSTRAINT chk_food_source_evidence_provider
        CHECK (provider IN ('MANUAL', 'OPEN_FOOD_FACTS', 'USDA_FOODDATA', 'EDAMAM', 'NUTRITIONIX', 'LOCAL_CURATED', 'ADMIN_IMPORT')),
    CONSTRAINT chk_food_source_evidence_basis
        CHECK (basis IN ('PER_100_G', 'PER_100_ML', 'SERVING', 'LABEL')),
    CONSTRAINT chk_food_source_evidence_confidence
        CHECK (confidence_score BETWEEN 0 AND 100),
    CONSTRAINT chk_food_source_evidence_value
        CHECK (numeric_value >= 0)
);

CREATE INDEX idx_food_source_evidence_item_field_observed
    ON food_product_source_evidence (food_item_id, field_name, observed_at DESC);

CREATE INDEX idx_food_source_evidence_provider_external
    ON food_product_source_evidence (provider, external_id);

CREATE OR REPLACE FUNCTION reject_food_source_evidence_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'food_product_source_evidence rows are immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_food_source_evidence_immutable
    BEFORE UPDATE OR DELETE ON food_product_source_evidence
    FOR EACH ROW EXECUTE FUNCTION reject_food_source_evidence_mutation();
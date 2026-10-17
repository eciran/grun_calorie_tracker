-- New attestations never relabel or replace legacy scalar source evidence.
CREATE TABLE food_typed_nutrient_evidence (
    id BIGSERIAL PRIMARY KEY,
    food_item_id BIGINT NOT NULL REFERENCES food_items(id),
    provider VARCHAR(40) NOT NULL CHECK (provider = 'USDA_FOODDATA'),
    external_id VARCHAR(255) NOT NULL,
    field_name VARCHAR(30) NOT NULL,
    nutrient_id INTEGER NOT NULL,
    nutrient_number VARCHAR(10) NOT NULL,
    source_unit VARCHAR(10) NOT NULL,
    semantic VARCHAR(50) NOT NULL,
    canonical_unit VARCHAR(20) NOT NULL,
    basis VARCHAR(20) NOT NULL CHECK (basis = 'PER_100_G'),
    source_amount NUMERIC NOT NULL CHECK (source_amount >= 0 AND source_amount <> 'NaN'::numeric
        AND source_amount <> 'Infinity'::numeric AND source_amount <> '-Infinity'::numeric),
    source_json TEXT NOT NULL,
    source_sha256 VARCHAR(64) NOT NULL CHECK (source_sha256 ~ '^[a-f0-9]{64}$'),
    observed_at TIMESTAMPTZ NOT NULL,
    before_json TEXT NOT NULL,
    before_sha256 VARCHAR(64) NOT NULL CHECK (before_sha256 ~ '^[a-f0-9]{64}$'),
    fingerprint VARCHAR(64) NOT NULL UNIQUE CHECK (fingerprint ~ '^[a-f0-9]{64}$'),
    reviewer_identity VARCHAR(255) NOT NULL CHECK (length(trim(reviewer_identity)) > 0),
    approval_reason VARCHAR(1000) NOT NULL CHECK (length(trim(approval_reason)) >= 10),
    trust_state VARCHAR(60) NOT NULL DEFAULT 'ADMIN_REVIEWED_NOT_PROVIDER_FETCH_VERIFIED'
        CHECK (trust_state = 'ADMIN_REVIEWED_NOT_PROVIDER_FETCH_VERIFIED'),
    legacy_unit_attested BOOLEAN NOT NULL DEFAULT FALSE CHECK (legacy_unit_attested = FALSE),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_typed_nutrient_contract CHECK (
        (field_name = 'vitamin_a' AND nutrient_id = 1106 AND nutrient_number = '320'
            AND source_unit = 'ug' AND semantic = 'VITAMIN_A_RAE' AND canonical_unit = 'MCG_RAE') OR
        (field_name = 'vitamin_d' AND nutrient_id = 1114 AND nutrient_number = '328'
            AND source_unit = 'ug' AND semantic = 'VITAMIN_D_D2_D3' AND canonical_unit = 'MCG') OR
        (field_name = 'sugar' AND nutrient_id = 2000 AND nutrient_number = '269'
            AND source_unit = 'g' AND semantic = 'USDA_TOTAL_SUGARS_2000' AND canonical_unit = 'G')
    )
);
CREATE INDEX idx_typed_nutrient_product_field ON food_typed_nutrient_evidence(food_item_id, field_name, id DESC);

CREATE FUNCTION reject_typed_nutrient_evidence_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Typed nutrient evidence is append-only; retain history and append a new reviewed observation';
END;
$$;
CREATE TRIGGER typed_nutrient_evidence_immutable
    BEFORE UPDATE OR DELETE ON food_typed_nutrient_evidence
    FOR EACH ROW EXECUTE FUNCTION reject_typed_nutrient_evidence_mutation();
CREATE TRIGGER typed_nutrient_evidence_no_truncate
    BEFORE TRUNCATE ON food_typed_nutrient_evidence
    FOR EACH STATEMENT EXECUTE FUNCTION reject_typed_nutrient_evidence_mutation();

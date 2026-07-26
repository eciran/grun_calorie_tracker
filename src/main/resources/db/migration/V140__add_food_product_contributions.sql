CREATE TABLE food_product_contributions (
    id BIGSERIAL PRIMARY KEY,
    submitted_by_user_id BIGINT NOT NULL REFERENCES users(id),
    barcode VARCHAR(14) NOT NULL,
    normalized_barcode VARCHAR(14) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    brand VARCHAR(160) NOT NULL,
    market_region VARCHAR(20) NOT NULL,
    calories DOUBLE PRECISION NOT NULL,
    protein DOUBLE PRECISION NOT NULL,
    fat DOUBLE PRECISION NOT NULL,
    carbs DOUBLE PRECISION NOT NULL,
    fiber DOUBLE PRECISION,
    sugar DOUBLE PRECISION,
    sodium DOUBLE PRECISION,
    serving_size_grams DOUBLE PRECISION,
    serving_unit VARCHAR(40),
    evidence_url VARCHAR(2048) NOT NULL,
    evidence_checksum VARCHAR(64) NOT NULL,
    evidence_retrieved_at TIMESTAMP NOT NULL,
    commercial_use_allowed BOOLEAN NOT NULL,
    persistent_storage_allowed BOOLEAN NOT NULL,
    status VARCHAR(30) NOT NULL,
    reviewer_identity VARCHAR(255),
    review_note VARCHAR(1000),
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_food_product_contribution_market CHECK (market_region IN ('GLOBAL', 'TR', 'UK_IE', 'EU')),
    CONSTRAINT chk_food_product_contribution_status CHECK (status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_food_product_contribution_nutrition CHECK (
        calories BETWEEN 0 AND 1000 AND protein BETWEEN 0 AND 100 AND
        fat BETWEEN 0 AND 100 AND carbs BETWEEN 0 AND 100 AND protein + fat + carbs <= 110
    ),
    CONSTRAINT chk_food_product_contribution_consent CHECK (commercial_use_allowed AND persistent_storage_allowed),
    CONSTRAINT uq_food_product_contribution_submission UNIQUE (submitted_by_user_id, normalized_barcode, evidence_checksum)
);

CREATE INDEX idx_food_product_contribution_review
    ON food_product_contributions (status, market_region, created_at, id);

CREATE INDEX idx_food_product_contribution_barcode
    ON food_product_contributions (normalized_barcode);

CREATE UNIQUE INDEX uq_food_product_contribution_approved_barcode
    ON food_product_contributions (normalized_barcode)
    WHERE status = 'APPROVED';

CREATE TABLE food_product_upload_sessions (
    id VARCHAR(64) PRIMARY KEY,
    created_by_user_id BIGINT NOT NULL REFERENCES users(id),
    idempotency_key VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    finalized_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_food_product_upload_session_status CHECK (
        status IN ('CREATED', 'UPLOADING', 'UPLOADED', 'FINALIZED', 'EXPIRED', 'CLEANED')
    ),
    CONSTRAINT uq_food_product_upload_session_user_key
        UNIQUE (created_by_user_id, idempotency_key)
);

CREATE TABLE food_product_review_cases (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    source VARCHAR(30) NOT NULL,
    submitted_by_user_id BIGINT REFERENCES users(id),
    source_reference VARCHAR(100),
    normalized_barcode VARCHAR(14),
    original_barcode VARCHAR(64),
    market_region VARCHAR(20) NOT NULL,
    food_item_id BIGINT REFERENCES food_items(id),
    resolution_mode VARCHAR(30) NOT NULL,
    status VARCHAR(40) NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 1,
    submitted_values_json TEXT NOT NULL,
    field_confidence_json TEXT,
    correction_summary_json TEXT,
    nutrition_basis VARCHAR(30),
    consent_version VARCHAR(30),
    temporary_evidence_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    public_media_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    review_note VARCHAR(1000),
    reviewed_by VARCHAR(255),
    reviewed_at TIMESTAMP,
    applied_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_food_product_review_case_source CHECK (
        source IN ('USER_OCR', 'USER_CORRECTION', 'ADMIN_MANUAL', 'LEGACY_CONTRIBUTION', 'LEGACY_CORRECTION')
    ),
    CONSTRAINT chk_food_product_review_case_resolution CHECK (
        resolution_mode IN ('NEW_CANDIDATE', 'UPDATE_EXISTING')
    ),
    CONSTRAINT chk_food_product_review_case_status CHECK (
        status IN ('SUBMITTED', 'IN_REVIEW', 'NEEDS_SUBMITTER_ACTION', 'APPROVED',
                   'APPLIED', 'REJECTED', 'WITHDRAWN', 'EXPIRED')
    ),
    CONSTRAINT chk_food_product_review_case_risk CHECK (
        risk_level IN ('LOW', 'MEDIUM', 'HIGH')
    )
);

CREATE UNIQUE INDEX uq_food_product_review_case_source_reference
    ON food_product_review_cases (source, source_reference)
    WHERE source_reference IS NOT NULL;

CREATE INDEX idx_food_product_review_case_queue
    ON food_product_review_cases (status, market_region, risk_level, created_at);

CREATE INDEX idx_food_product_review_case_barcode
    ON food_product_review_cases (normalized_barcode, market_region);

CREATE TABLE food_product_review_case_assets (
    id BIGSERIAL PRIMARY KEY,
    review_case_id BIGINT REFERENCES food_product_review_cases(id),
    upload_session_id VARCHAR(64) NOT NULL REFERENCES food_product_upload_sessions(id),
    asset_type VARCHAR(30) NOT NULL,
    storage_key VARCHAR(1024) NOT NULL UNIQUE,
    content_type VARCHAR(80) NOT NULL,
    size_bytes BIGINT NOT NULL,
    width INTEGER,
    height INTEGER,
    sha256 VARCHAR(64) NOT NULL,
    upload_state VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    deletion_state VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted_at TIMESTAMP,
    deletion_attempt_count INTEGER NOT NULL DEFAULT 0,
    last_deletion_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_food_product_review_asset_type CHECK (
        asset_type IN ('FRONT_PACKAGE', 'NUTRITION_LABEL', 'BARCODE_PANEL', 'INGREDIENTS_LABEL')
    ),
    CONSTRAINT chk_food_product_review_asset_upload_state CHECK (
        upload_state IN ('RESERVED', 'UPLOADING', 'UPLOADED', 'VERIFIED', 'REJECTED')
    ),
    CONSTRAINT chk_food_product_review_asset_deletion_state CHECK (
        deletion_state IN ('ACTIVE', 'PENDING', 'DELETED', 'FAILED')
    ),
    CONSTRAINT chk_food_product_review_asset_size CHECK (size_bytes > 0),
    CONSTRAINT chk_food_product_review_asset_sha CHECK (sha256 ~ '^[0-9a-f]{64}$')
);

CREATE UNIQUE INDEX uq_food_product_review_asset_slot_checksum
    ON food_product_review_case_assets (upload_session_id, asset_type, sha256);

CREATE TABLE food_product_review_case_extractions (
    id BIGSERIAL PRIMARY KEY,
    review_case_id BIGINT NOT NULL UNIQUE REFERENCES food_product_review_cases(id),
    engine VARCHAR(80) NOT NULL,
    engine_version VARCHAR(40),
    parser_version VARCHAR(40) NOT NULL,
    locale VARCHAR(20),
    recognized_lines_json TEXT NOT NULL,
    parsed_values_json TEXT NOT NULL,
    parser_warnings_json TEXT,
    raw_payload_expires_at TIMESTAMP,
    raw_payload_deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE food_product_contributions
    ADD COLUMN review_case_id BIGINT REFERENCES food_product_review_cases(id);

ALTER TABLE product_correction_suggestions
    ADD COLUMN review_case_id BIGINT REFERENCES food_product_review_cases(id);

INSERT INTO food_product_review_cases (
    idempotency_key, source, submitted_by_user_id, source_reference,
    normalized_barcode, original_barcode, market_region, resolution_mode,
    status, risk_level, schema_version, submitted_values_json,
    consent_version, temporary_evidence_allowed, public_media_allowed,
    review_note, reviewed_by, reviewed_at, created_at, updated_at
)
SELECT
    'legacy-contribution:' || contribution.id,
    'LEGACY_CONTRIBUTION',
    contribution.submitted_by_user_id,
    contribution.id::TEXT,
    contribution.normalized_barcode,
    contribution.barcode,
    contribution.market_region,
    'NEW_CANDIDATE',
    CASE contribution.status
        WHEN 'APPROVED' THEN 'APPROVED'
        WHEN 'REJECTED' THEN 'REJECTED'
        ELSE 'SUBMITTED'
    END,
    'MEDIUM',
    1,
    jsonb_build_object(
        'productName', contribution.product_name,
        'brand', contribution.brand,
        'calories', contribution.calories,
        'protein', contribution.protein,
        'fat', contribution.fat,
        'carbs', contribution.carbs,
        'fiber', contribution.fiber,
        'sugar', contribution.sugar,
        'sodium', contribution.sodium,
        'servingSizeGrams', contribution.serving_size_grams,
        'servingUnit', contribution.serving_unit
    )::TEXT,
    'legacy-v1',
    TRUE,
    contribution.commercial_use_allowed,
    contribution.review_note,
    contribution.reviewer_identity,
    contribution.reviewed_at,
    contribution.created_at,
    contribution.updated_at
FROM food_product_contributions contribution
ON CONFLICT (idempotency_key) DO NOTHING;

UPDATE food_product_contributions contribution
SET review_case_id = review_case.id
FROM food_product_review_cases review_case
WHERE review_case.source = 'LEGACY_CONTRIBUTION'
  AND review_case.source_reference = contribution.id::TEXT
  AND contribution.review_case_id IS NULL;

INSERT INTO food_product_review_cases (
    idempotency_key, source, submitted_by_user_id, source_reference,
    normalized_barcode, original_barcode, market_region, food_item_id,
    resolution_mode, status, risk_level, schema_version,
    submitted_values_json, correction_summary_json, consent_version,
    temporary_evidence_allowed, public_media_allowed, created_at, updated_at
)
SELECT
    'legacy-correction:' || correction.id,
    'LEGACY_CORRECTION',
    correction.user_id,
    correction.id::TEXT,
    COALESCE(food.normalized_barcode, food.barcode),
    food.barcode,
    COALESCE(food.market_region, 'GLOBAL'),
    correction.food_item_id,
    'UPDATE_EXISTING',
    CASE correction.status
        WHEN 'ACCEPTED' THEN 'APPROVED'
        WHEN 'REJECTED' THEN 'REJECTED'
        ELSE 'SUBMITTED'
    END,
    'MEDIUM',
    1,
    jsonb_build_object(
        'calories', correction.suggested_calories,
        'protein', correction.suggested_protein,
        'carbs', correction.suggested_carbs,
        'fat', correction.suggested_fat,
        'note', correction.note
    )::TEXT,
    jsonb_build_object('legacyCorrectionId', correction.id)::TEXT,
    'legacy-v1',
    correction.image_url IS NOT NULL,
    FALSE,
    correction.created_at,
    correction.created_at
FROM product_correction_suggestions correction
JOIN food_items food ON food.id = correction.food_item_id
ON CONFLICT (idempotency_key) DO NOTHING;

UPDATE product_correction_suggestions correction
SET review_case_id = review_case.id
FROM food_product_review_cases review_case
WHERE review_case.source = 'LEGACY_CORRECTION'
  AND review_case.source_reference = correction.id::TEXT
  AND correction.review_case_id IS NULL;

CREATE UNIQUE INDEX uq_food_product_contribution_review_case
    ON food_product_contributions (review_case_id)
    WHERE review_case_id IS NOT NULL;

CREATE UNIQUE INDEX uq_product_correction_review_case
    ON product_correction_suggestions (review_case_id)
    WHERE review_case_id IS NOT NULL;

DROP INDEX IF EXISTS uq_food_product_contribution_approved_barcode;

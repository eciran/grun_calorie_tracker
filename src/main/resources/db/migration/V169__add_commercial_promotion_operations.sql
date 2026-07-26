ALTER TABLE promo_codes
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS name VARCHAR(160),
    ADD COLUMN IF NOT EXISTS description VARCHAR(600),
    ADD COLUMN IF NOT EXISTS status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN IF NOT EXISTS promo_type VARCHAR(24) NOT NULL DEFAULT 'CAMPAIGN',
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS start_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS end_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS target_plan VARCHAR(24),
    ADD COLUMN IF NOT EXISTS target_product_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS target_store VARCHAR(24) NOT NULL DEFAULT 'ALL',
    ADD COLUMN IF NOT EXISTS target_region VARCHAR(24),
    ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'EUR',
    ADD COLUMN IF NOT EXISTS eligibility_rule VARCHAR(80),
    ADD COLUMN IF NOT EXISTS per_user_limit INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS global_limit INTEGER,
    ADD COLUMN IF NOT EXISTS campaign_key VARCHAR(120),
    ADD COLUMN IF NOT EXISTS provider_offer_id VARCHAR(160),
    ADD COLUMN IF NOT EXISTS provider_product_id VARCHAR(160),
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255) NOT NULL DEFAULT 'SYSTEM_MIGRATION',
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255) NOT NULL DEFAULT 'SYSTEM_MIGRATION',
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deactivated_reason VARCHAR(500);

UPDATE promo_codes SET name = code WHERE name IS NULL;
UPDATE promo_codes SET discount_percent = 0 WHERE discount_percent IS NULL;
UPDATE promo_codes SET used_count = 0 WHERE used_count IS NULL;
ALTER TABLE promo_codes ALTER COLUMN name SET NOT NULL;
ALTER TABLE promo_codes ALTER COLUMN discount_percent SET NOT NULL;
ALTER TABLE promo_codes ALTER COLUMN used_count SET NOT NULL;

ALTER TABLE promo_codes DROP CONSTRAINT IF EXISTS chk_promo_codes_status;
ALTER TABLE promo_codes ADD CONSTRAINT chk_promo_codes_status
    CHECK (status IN ('DRAFT', 'ACTIVE', 'DEACTIVATED', 'EXPIRED'));
ALTER TABLE promo_codes DROP CONSTRAINT IF EXISTS chk_promo_codes_type;
ALTER TABLE promo_codes ADD CONSTRAINT chk_promo_codes_type
    CHECK (promo_type IN ('CAMPAIGN', 'INTRO_OFFER', 'WIN_BACK', 'SUPPORT_GRANT'));
ALTER TABLE promo_codes DROP CONSTRAINT IF EXISTS chk_promo_codes_store;
ALTER TABLE promo_codes ADD CONSTRAINT chk_promo_codes_store
    CHECK (target_store IN ('ALL', 'REVENUECAT', 'APPLE_APP_STORE', 'GOOGLE_PLAY'));
ALTER TABLE promo_codes DROP CONSTRAINT IF EXISTS chk_promo_codes_limits;
ALTER TABLE promo_codes ADD CONSTRAINT chk_promo_codes_limits CHECK (
    discount_percent >= 0 AND discount_percent <= 100
    AND per_user_limit > 0
    AND (global_limit IS NULL OR global_limit > 0)
);

ALTER TABLE applied_promos
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(120),
    ADD COLUMN IF NOT EXISTS provider_event_id VARCHAR(160),
    ADD COLUMN IF NOT EXISTS status VARCHAR(24) NOT NULL DEFAULT 'CONVERTED',
    ADD COLUMN IF NOT EXISTS amount_minor BIGINT,
    ADD COLUMN IF NOT EXISTS currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS converted_at TIMESTAMP;

UPDATE applied_promos SET idempotency_key = 'legacy-' || id WHERE idempotency_key IS NULL;
UPDATE applied_promos SET converted_at = applied_at WHERE converted_at IS NULL AND status = 'CONVERTED';
ALTER TABLE applied_promos ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE applied_promos ALTER COLUMN promo_code_id SET NOT NULL;
ALTER TABLE applied_promos ALTER COLUMN applied_at SET NOT NULL;
ALTER TABLE applied_promos ALTER COLUMN idempotency_key SET NOT NULL;
ALTER TABLE applied_promos DROP CONSTRAINT IF EXISTS uq_applied_promos_idempotency;
ALTER TABLE applied_promos ADD CONSTRAINT uq_applied_promos_idempotency UNIQUE (idempotency_key);
ALTER TABLE applied_promos DROP CONSTRAINT IF EXISTS chk_applied_promos_status;
ALTER TABLE applied_promos ADD CONSTRAINT chk_applied_promos_status
    CHECK (status IN ('RESERVED', 'PROVIDER_VERIFIED', 'CONVERTED', 'REJECTED'));

CREATE INDEX IF NOT EXISTS idx_promo_codes_admin_filters
    ON promo_codes(status, promo_type, target_store, target_region, start_at, end_at);
CREATE INDEX IF NOT EXISTS idx_applied_promos_promo_status
    ON applied_promos(promo_code_id, status, applied_at DESC);
CREATE INDEX IF NOT EXISTS idx_applied_promos_user_promo
    ON applied_promos(user_id, promo_code_id);

ALTER TABLE admin_action_audits DROP CONSTRAINT IF EXISTS chk_admin_action_audits_action_type;
ALTER TABLE admin_action_audits ADD CONSTRAINT chk_admin_action_audits_action_type CHECK (action_type IN (
    'SUBSCRIPTION_UPDATE','AI_QUOTA_RESET','AI_QUOTA_ADDON_GRANT','AI_QUOTA_REFUND',
    'AI_REQUEST_INSPECT','AI_CREDIT_PRICING_UPDATE','SUBSCRIPTION_FEATURE_UPDATE',
    'SUBSCRIPTION_ENTITLEMENT_MATRIX_APPLY','RETENTION_POLICY_UPDATE','RECIPE_CREATE',
    'RECIPE_REVIEW_UPDATE','USER_STATUS_UPDATE','USER_SUPPORT_NOTE_CREATE','USER_SESSION_REVOKE',
    'PRODUCT_QUALITY_AI_SETTINGS_UPDATE','NOTIFICATION_CAMPAIGN_CREATE','NOTIFICATION_CAMPAIGN_UPDATE',
    'NOTIFICATION_CAMPAIGN_SCHEDULE','NOTIFICATION_CAMPAIGN_CANCEL','PROMO_CREATE','PROMO_UPDATE',
    'PROMO_ACTIVATE','PROMO_DEACTIVATE','PROMO_RECONCILE','PROMO_REDEMPTION_RECORD'
));
ALTER TABLE admin_action_audits DROP CONSTRAINT IF EXISTS chk_admin_action_audits_target_type;
ALTER TABLE admin_action_audits ADD CONSTRAINT chk_admin_action_audits_target_type CHECK (target_type IN (
    'USER_SUBSCRIPTION','AI_REQUEST','AI_CREDIT_PRICING','SUBSCRIPTION_FEATURE','RETENTION_POLICY',
    'RECIPE','USER_ACCOUNT','PRODUCT_QUALITY_AI_SETTINGS','NOTIFICATION_CAMPAIGN','PROMOTION'
));

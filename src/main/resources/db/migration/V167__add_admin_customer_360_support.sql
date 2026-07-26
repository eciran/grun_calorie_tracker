CREATE TABLE IF NOT EXISTS admin_user_support_notes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    note VARCHAR(1000) NOT NULL,
    tags VARCHAR(500) NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_admin_user_support_notes_user_created
    ON admin_user_support_notes(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_users_admin_filters
    ON users(role, account_enabled, account_locked, email_verified, market_region, last_active_at);

ALTER TABLE admin_action_audits
    DROP CONSTRAINT IF EXISTS chk_admin_action_audits_action_type;

ALTER TABLE admin_action_audits
    ADD CONSTRAINT chk_admin_action_audits_action_type CHECK (action_type IN (
        'SUBSCRIPTION_UPDATE',
        'AI_QUOTA_RESET',
        'AI_QUOTA_ADDON_GRANT',
        'AI_QUOTA_REFUND',
        'AI_REQUEST_INSPECT',
        'AI_CREDIT_PRICING_UPDATE',
        'SUBSCRIPTION_FEATURE_UPDATE',
        'SUBSCRIPTION_ENTITLEMENT_MATRIX_APPLY',
        'RETENTION_POLICY_UPDATE',
        'RECIPE_CREATE',
        'RECIPE_REVIEW_UPDATE',
        'USER_STATUS_UPDATE',
        'USER_SUPPORT_NOTE_CREATE',
        'USER_SESSION_REVOKE',
        'PRODUCT_QUALITY_AI_SETTINGS_UPDATE',
        'NOTIFICATION_CAMPAIGN_CREATE',
        'NOTIFICATION_CAMPAIGN_UPDATE',
        'NOTIFICATION_CAMPAIGN_SCHEDULE',
        'NOTIFICATION_CAMPAIGN_CANCEL'
    ));

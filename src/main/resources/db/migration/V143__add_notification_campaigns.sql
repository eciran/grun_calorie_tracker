ALTER TABLE users
    ADD COLUMN IF NOT EXISTS marketing_notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE notification_campaigns (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    title VARCHAR(120) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    category VARCHAR(32) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    target_route VARCHAR(255),
    target_plan VARCHAR(32),
    target_region VARCHAR(32),
    target_language VARCHAR(16),
    scheduled_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    last_processed_user_id BIGINT NOT NULL DEFAULT 0,
    estimated_audience BIGINT NOT NULL DEFAULT 0,
    processed_count BIGINT NOT NULL DEFAULT 0,
    in_app_count BIGINT NOT NULL DEFAULT 0,
    push_sent_count BIGINT NOT NULL DEFAULT 0,
    push_skipped_count BIGINT NOT NULL DEFAULT 0,
    push_failed_count BIGINT NOT NULL DEFAULT 0,
    failure_message VARCHAR(2048),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_notification_campaign_category CHECK (category IN ('SYSTEM', 'MARKETING')),
    CONSTRAINT chk_notification_campaign_channel CHECK (channel IN ('IN_APP', 'PUSH', 'IN_APP_AND_PUSH')),
    CONSTRAINT chk_notification_campaign_status CHECK (status IN ('DRAFT', 'SCHEDULED', 'PROCESSING', 'COMPLETED', 'CANCELLED', 'FAILED'))
);

CREATE INDEX idx_notification_campaign_status_schedule
    ON notification_campaigns (status, scheduled_at);

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS title VARCHAR(120),
    ADD COLUMN IF NOT EXISTS campaign_id BIGINT REFERENCES notification_campaigns(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS visible_in_app BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX IF NOT EXISTS idx_notifications_campaign
    ON notifications (campaign_id);

CREATE TABLE notification_campaign_recipients (
    id BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL REFERENCES notification_campaigns(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    notification_id BIGINT REFERENCES notifications(id) ON DELETE SET NULL,
    status VARCHAR(32) NOT NULL,
    push_attempted INTEGER NOT NULL DEFAULT 0,
    push_sent INTEGER NOT NULL DEFAULT 0,
    push_skipped INTEGER NOT NULL DEFAULT 0,
    push_failed INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    CONSTRAINT uq_notification_campaign_recipient UNIQUE (campaign_id, user_id),
    CONSTRAINT chk_notification_campaign_recipient_status CHECK (status IN ('CREATED', 'DELIVERED', 'FAILED'))
);

CREATE INDEX idx_notification_campaign_recipient_status
    ON notification_campaign_recipients (campaign_id, status);

ALTER TABLE admin_action_audits
    DROP CONSTRAINT IF EXISTS chk_admin_action_audits_action_type;

ALTER TABLE admin_action_audits
    ADD CONSTRAINT chk_admin_action_audits_action_type CHECK (action_type IN (
        'SUBSCRIPTION_UPDATE',
        'AI_QUOTA_RESET',
        'AI_QUOTA_ADDON_GRANT',
        'AI_QUOTA_REFUND',
        'AI_CREDIT_PRICING_UPDATE',
        'SUBSCRIPTION_FEATURE_UPDATE',
        'SUBSCRIPTION_ENTITLEMENT_MATRIX_APPLY',
        'RETENTION_POLICY_UPDATE',
        'RECIPE_CREATE',
        'RECIPE_REVIEW_UPDATE',
        'USER_STATUS_UPDATE',
        'PRODUCT_QUALITY_AI_SETTINGS_UPDATE',
        'NOTIFICATION_CAMPAIGN_CREATE',
        'NOTIFICATION_CAMPAIGN_UPDATE',
        'NOTIFICATION_CAMPAIGN_SCHEDULE',
        'NOTIFICATION_CAMPAIGN_CANCEL'
    ));

ALTER TABLE admin_action_audits
    DROP CONSTRAINT IF EXISTS chk_admin_action_audits_target_type;

ALTER TABLE admin_action_audits
    ADD CONSTRAINT chk_admin_action_audits_target_type CHECK (target_type IN (
        'USER_SUBSCRIPTION',
        'AI_REQUEST',
        'AI_CREDIT_PRICING',
        'SUBSCRIPTION_FEATURE',
        'RETENTION_POLICY',
        'RECIPE',
        'USER_ACCOUNT',
        'PRODUCT_QUALITY_AI_SETTINGS',
        'NOTIFICATION_CAMPAIGN'
    ));
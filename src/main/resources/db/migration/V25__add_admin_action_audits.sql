CREATE TABLE IF NOT EXISTS admin_action_audits (
    id BIGSERIAL PRIMARY KEY,
    admin_email VARCHAR(255) NOT NULL,
    action_type VARCHAR(80) NOT NULL,
    target_type VARCHAR(80) NOT NULL,
    target_key VARCHAR(255) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    correlation_id VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_admin_action_audits_action_type CHECK (action_type IN (
        'SUBSCRIPTION_UPDATE',
        'AI_QUOTA_RESET',
        'AI_QUOTA_ADDON_GRANT',
        'SUBSCRIPTION_FEATURE_UPDATE'
    )),
    CONSTRAINT chk_admin_action_audits_target_type CHECK (target_type IN (
        'USER_SUBSCRIPTION',
        'SUBSCRIPTION_FEATURE'
    ))
);

CREATE INDEX IF NOT EXISTS idx_admin_action_audits_created_at
    ON admin_action_audits(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_admin_action_audits_action_target
    ON admin_action_audits(action_type, target_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_admin_action_audits_admin_email
    ON admin_action_audits(admin_email, created_at DESC);

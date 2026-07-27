CREATE TABLE ai_operations_policy (
    id BIGINT PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    circuit_open BOOLEAN NOT NULL DEFAULT FALSE,
    failure_rate_threshold DOUBLE PRECISION NOT NULL,
    rejection_rate_threshold DOUBLE PRECISION NOT NULL,
    max_tokens_per24_hours BIGINT NOT NULL,
    max_cost_per24_hours DOUBLE PRECISION NOT NULL,
    cost_currency VARCHAR(12) NOT NULL,
    active_model VARCHAR(120),
    active_prompt_version VARCHAR(120),
    previous_model VARCHAR(120),
    previous_prompt_version VARCHAR(120),
    change_reason VARCHAR(500) NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT chk_ai_ops_failure_threshold CHECK (failure_rate_threshold > 0 AND failure_rate_threshold <= 1),
    CONSTRAINT chk_ai_ops_rejection_threshold CHECK (rejection_rate_threshold > 0 AND rejection_rate_threshold <= 1),
    CONSTRAINT chk_ai_ops_token_budget CHECK (max_tokens_per24_hours >= 1000),
    CONSTRAINT chk_ai_ops_cost_budget CHECK (max_cost_per24_hours > 0)
);

INSERT INTO ai_operations_policy (
    id, version, circuit_open, failure_rate_threshold, rejection_rate_threshold,
    max_tokens_per24_hours, max_cost_per24_hours, cost_currency,
    change_reason, updated_by, updated_at
) VALUES (
    1, 0, FALSE, 0.20, 0.40, 1000000, 20.0, 'USD',
    'Initial production-safe AI operations policy.', 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP
);

ALTER TABLE admin_action_audits DROP CONSTRAINT IF EXISTS chk_admin_action_audits_action_type;
ALTER TABLE admin_action_audits ADD CONSTRAINT chk_admin_action_audits_action_type CHECK (action_type IN (
    'SUBSCRIPTION_UPDATE', 'AI_QUOTA_RESET', 'AI_QUOTA_ADDON_GRANT', 'AI_QUOTA_REFUND',
    'AI_REQUEST_INSPECT', 'AI_CREDIT_PRICING_UPDATE', 'AI_OPERATIONS_POLICY_UPDATE',
    'AI_OPERATIONS_DEPLOYMENT_ROLLBACK', 'SUBSCRIPTION_FEATURE_UPDATE',
    'SUBSCRIPTION_ENTITLEMENT_MATRIX_APPLY', 'RETENTION_POLICY_UPDATE', 'RECIPE_CREATE',
    'RECIPE_REVIEW_UPDATE', 'USER_STATUS_UPDATE', 'USER_SUPPORT_NOTE_CREATE',
    'USER_SESSION_REVOKE', 'PRODUCT_QUALITY_AI_SETTINGS_UPDATE',
    'NOTIFICATION_CAMPAIGN_CREATE', 'NOTIFICATION_CAMPAIGN_UPDATE',
    'NOTIFICATION_CAMPAIGN_SCHEDULE', 'NOTIFICATION_CAMPAIGN_CANCEL', 'PROMO_CREATE',
    'PROMO_UPDATE', 'PROMO_ACTIVATE', 'PROMO_DEACTIVATE', 'PROMO_RECONCILE',
    'PROMO_REDEMPTION_RECORD', 'ADMIN_ROLE_UPDATE', 'ADMIN_STATUS_UPDATE',
    'ADMIN_MFA_STATUS_UPDATE', 'CATALOG_EXERCISE_CREATE', 'CATALOG_EXERCISE_UPDATE',
    'CATALOG_EXERCISE_REVIEW', 'CATALOG_REVIEW_ASSIGNMENT'
));

ALTER TABLE admin_action_audits DROP CONSTRAINT IF EXISTS chk_admin_action_audits_target_type;
ALTER TABLE admin_action_audits ADD CONSTRAINT chk_admin_action_audits_target_type CHECK (target_type IN (
    'USER_SUBSCRIPTION', 'AI_REQUEST', 'AI_CREDIT_PRICING', 'AI_OPERATIONS_POLICY',
    'SUBSCRIPTION_FEATURE', 'RETENTION_POLICY', 'RECIPE', 'USER_ACCOUNT',
    'PRODUCT_QUALITY_AI_SETTINGS', 'NOTIFICATION_CAMPAIGN', 'PROMOTION',
    'ADMIN_ACCOUNT', 'EXERCISE_ITEM', 'CATALOG_REVIEW_ITEM'
));
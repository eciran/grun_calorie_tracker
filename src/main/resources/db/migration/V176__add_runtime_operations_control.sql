CREATE TABLE runtime_operations_policy (
    id BIGINT PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    maintenance_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    maintenance_message VARCHAR(240) NOT NULL,
    release_version VARCHAR(80) NOT NULL,
    deployment_environment VARCHAR(40) NOT NULL,
    minimum_ios_version VARCHAR(40) NOT NULL,
    minimum_android_version VARCHAR(40) NOT NULL,
    rollout_feature VARCHAR(80) NOT NULL,
    rollout_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    rollout_plan VARCHAR(30),
    rollout_region VARCHAR(30),
    rollout_segment VARCHAR(30) NOT NULL,
    rollout_percentage INTEGER NOT NULL,
    api_latency_warning_ms BIGINT NOT NULL,
    api_error_rate_threshold DOUBLE PRECISION NOT NULL,
    escalation_target VARCHAR(255),
    previous_snapshot TEXT,
    change_reason VARCHAR(500) NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT chk_runtime_rollout_percentage CHECK (rollout_percentage BETWEEN 0 AND 100),
    CONSTRAINT chk_runtime_api_latency CHECK (api_latency_warning_ms BETWEEN 50 AND 120000),
    CONSTRAINT chk_runtime_api_error_rate CHECK (api_error_rate_threshold BETWEEN 0.001 AND 1.0)
);

INSERT INTO runtime_operations_policy (
    id, version, maintenance_enabled, maintenance_message, release_version,
    deployment_environment, minimum_ios_version, minimum_android_version,
    rollout_feature, rollout_enabled, rollout_segment, rollout_percentage,
    api_latency_warning_ms, api_error_rate_threshold,
    change_reason, updated_by, updated_at
) VALUES (
    1, 0, FALSE, 'Scheduled maintenance is in progress. Please try again shortly.',
    '0.0.1-SNAPSHOT', 'local', '1.0.0', '1.0.0',
    'AI_INSIGHTS', FALSE, 'ALL', 0, 2000, 0.05,
    'Initial runtime operations policy.', 'SYSTEM_MIGRATION', CURRENT_TIMESTAMP
);

CREATE TABLE runtime_operation_records (
    id BIGSERIAL PRIMARY KEY,
    record_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    operation_key VARCHAR(120) NOT NULL,
    title VARCHAR(160) NOT NULL,
    summary VARCHAR(1000) NOT NULL,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    next_run_at TIMESTAMP,
    retryable BOOLEAN NOT NULL DEFAULT FALSE,
    retry_count INTEGER NOT NULL DEFAULT 0,
    parent_record_id BIGINT REFERENCES runtime_operation_records(id),
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT chk_runtime_record_type CHECK (record_type IN (
        'INCIDENT', 'BACKUP', 'RESTORE_DRILL', 'SCHEDULED_JOB'
    )),
    CONSTRAINT chk_runtime_record_status CHECK (status IN (
        'SCHEDULED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'DEAD_LETTER',
        'OPEN', 'MONITORING', 'RESOLVED'
    )),
    CONSTRAINT chk_runtime_retry_count CHECK (retry_count >= 0)
);

CREATE INDEX idx_runtime_records_type_status_created
    ON runtime_operation_records(record_type, status, created_at DESC);
CREATE INDEX idx_runtime_records_operation_key_created
    ON runtime_operation_records(operation_key, created_at DESC);

INSERT INTO runtime_operation_records (
    record_type, status, operation_key, title, summary, next_run_at,
    retryable, retry_count, created_by, created_at
) VALUES
('SCHEDULED_JOB', 'SCHEDULED', 'token-cleanup', 'Token cleanup', 'Removes expired authentication and account-link tokens.', CURRENT_TIMESTAMP, TRUE, 0, 'SYSTEM_REGISTRY', CURRENT_TIMESTAMP),
('SCHEDULED_JOB', 'SCHEDULED', 'notification-campaign-dispatch', 'Campaign dispatch', 'Dispatches queued in-app and push notification campaigns.', CURRENT_TIMESTAMP, TRUE, 0, 'SYSTEM_REGISTRY', CURRENT_TIMESTAMP),
('SCHEDULED_JOB', 'SCHEDULED', 'ai-completion-notifications', 'AI completion notifications', 'Notifies users when asynchronous AI requests finish.', CURRENT_TIMESTAMP, TRUE, 0, 'SYSTEM_REGISTRY', CURRENT_TIMESTAMP),
('SCHEDULED_JOB', 'SCHEDULED', 'ai-photo-cleanup', 'AI photo cleanup', 'Removes expired AI photo reference assets.', CURRENT_TIMESTAMP, TRUE, 0, 'SYSTEM_REGISTRY', CURRENT_TIMESTAMP),
('SCHEDULED_JOB', 'SCHEDULED', 'catalog-stale-scan', 'Catalog stale scan', 'Finds stale food catalog records for review.', CURRENT_TIMESTAMP, TRUE, 0, 'SYSTEM_REGISTRY', CURRENT_TIMESTAMP),
('SCHEDULED_JOB', 'SCHEDULED', 'catalog-quality-scan', 'Catalog quality scan', 'Queues configured product quality validation work.', CURRENT_TIMESTAMP, TRUE, 0, 'SYSTEM_REGISTRY', CURRENT_TIMESTAMP),
('SCHEDULED_JOB', 'SCHEDULED', 'product-search-telemetry-cleanup', 'Search telemetry cleanup', 'Removes expired search telemetry according to retention policy.', CURRENT_TIMESTAMP, TRUE, 0, 'SYSTEM_REGISTRY', CURRENT_TIMESTAMP),
('SCHEDULED_JOB', 'SCHEDULED', 'user-activity-cleanup', 'User activity cleanup', 'Removes expired product analytics activity.', CURRENT_TIMESTAMP, TRUE, 0, 'SYSTEM_REGISTRY', CURRENT_TIMESTAMP),
('SCHEDULED_JOB', 'SCHEDULED', 'fasting-reminders', 'Fasting reminders', 'Scans active fasting sessions for due reminders.', CURRENT_TIMESTAMP, TRUE, 0, 'SYSTEM_REGISTRY', CURRENT_TIMESTAMP),
('SCHEDULED_JOB', 'SCHEDULED', 'step-reminders', 'Step reminders', 'Scans step goals for due reminders.', CURRENT_TIMESTAMP, TRUE, 0, 'SYSTEM_REGISTRY', CURRENT_TIMESTAMP),
('SCHEDULED_JOB', 'SCHEDULED', 'water-reminders', 'Water reminders', 'Scans hydration settings for due reminders.', CURRENT_TIMESTAMP, TRUE, 0, 'SYSTEM_REGISTRY', CURRENT_TIMESTAMP);

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
    'CATALOG_EXERCISE_REVIEW', 'CATALOG_REVIEW_ASSIGNMENT',
    'RUNTIME_POLICY_UPDATE', 'RUNTIME_POLICY_ROLLBACK', 'RUNTIME_RECORD_CREATE',
    'RUNTIME_JOB_RETRY'
));

ALTER TABLE admin_action_audits DROP CONSTRAINT IF EXISTS chk_admin_action_audits_target_type;
ALTER TABLE admin_action_audits ADD CONSTRAINT chk_admin_action_audits_target_type CHECK (target_type IN (
    'USER_SUBSCRIPTION', 'AI_REQUEST', 'AI_CREDIT_PRICING', 'AI_OPERATIONS_POLICY',
    'SUBSCRIPTION_FEATURE', 'RETENTION_POLICY', 'RECIPE', 'USER_ACCOUNT',
    'PRODUCT_QUALITY_AI_SETTINGS', 'NOTIFICATION_CAMPAIGN', 'PROMOTION',
    'ADMIN_ACCOUNT', 'EXERCISE_ITEM', 'CATALOG_REVIEW_ITEM', 'RUNTIME_OPERATIONS'
));

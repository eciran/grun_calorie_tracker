CREATE TABLE notification_definitions (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    notification_key VARCHAR(80) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    protected_definition BOOLEAN NOT NULL DEFAULT FALSE,
    channel VARCHAR(32) NOT NULL,
    severity VARCHAR(16),
    target_route VARCHAR(255),
    title_en VARCHAR(120),
    message_en VARCHAR(1000),
    title_tr VARCHAR(120),
    message_tr VARCHAR(1000),
    created_by VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT chk_notification_definition_channel CHECK (channel IN ('IN_APP', 'PUSH', 'IN_APP_AND_PUSH')),
    CONSTRAINT chk_notification_definition_severity CHECK (severity IS NULL OR severity IN ('INFO', 'WARNING', 'CRITICAL')),
    CONSTRAINT chk_notification_definition_key CHECK (notification_key ~ '^[a-z0-9_]+$')
);

INSERT INTO notification_definitions
    (notification_key, display_name, description, enabled, protected_definition, channel, created_by, updated_by, created_at, updated_at)
VALUES
    ('ai_request_ready', 'AI result ready', 'Sent when a background AI request is ready for review.', TRUE, FALSE, 'IN_APP_AND_PUSH', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ai_request_failed', 'AI request failed', 'Sent when a background AI request cannot be completed.', TRUE, FALSE, 'IN_APP_AND_PUSH', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ai_quota_refund_approved', 'AI quota refund approved', 'Sent after an admin approves an AI credit refund.', TRUE, FALSE, 'IN_APP_AND_PUSH', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ai_quota_refund_rejected', 'AI quota refund rejected', 'Sent after an admin rejects an AI credit refund.', TRUE, FALSE, 'IN_APP_AND_PUSH', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('recipe_review_approved', 'Recipe approved', 'Sent after a submitted recipe is approved.', TRUE, FALSE, 'IN_APP_AND_PUSH', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('recipe_review_rejected', 'Recipe rejected', 'Sent after a submitted recipe is rejected.', TRUE, FALSE, 'IN_APP_AND_PUSH', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('product_intake', 'Product review update', 'Sent for user-submitted product review outcomes and evidence requests.', TRUE, FALSE, 'IN_APP_AND_PUSH', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('fasting_reminder', 'Fasting reminder', 'Scheduled fasting lifecycle reminders.', TRUE, FALSE, 'IN_APP_AND_PUSH', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('step_reminder', 'Step reminder', 'Scheduled step-goal reminders.', TRUE, FALSE, 'IN_APP_AND_PUSH', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('water_reminder', 'Water reminder', 'Scheduled hydration reminders.', TRUE, FALSE, 'IN_APP_AND_PUSH', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('subscription', 'Subscription update', 'Subscription and entitlement lifecycle information.', TRUE, FALSE, 'IN_APP_AND_PUSH', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ai_rejection_alert', 'AI rejection operations alert', 'Internal alert created when a user rejects an AI result.', TRUE, TRUE, 'IN_APP_AND_PUSH', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('system_alert', 'Mail provider alert', 'Internal alert for transactional mail delivery failures.', TRUE, TRUE, 'IN_APP_AND_PUSH', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('subscription_provider_alert', 'Subscription provider alert', 'Internal alert for failed subscription provider events.', TRUE, TRUE, 'IN_APP_AND_PUSH', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('admin_security_alert', 'Admin security alert', 'Critical owner and administrator security events.', TRUE, TRUE, 'IN_APP_AND_PUSH', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

ALTER TABLE admin_action_audits DROP CONSTRAINT IF EXISTS chk_admin_action_audits_action_type;
ALTER TABLE admin_action_audits ADD CONSTRAINT chk_admin_action_audits_action_type CHECK (action_type IN (
'SUBSCRIPTION_UPDATE','AI_QUOTA_RESET','AI_QUOTA_ADDON_GRANT','AI_QUOTA_REFUND','AI_REQUEST_INSPECT',
'AI_OPERATIONS_POLICY_UPDATE','AI_OPERATIONS_DEPLOYMENT_ROLLBACK','AI_CREDIT_PRICING_UPDATE',
'SUBSCRIPTION_FEATURE_UPDATE','SUBSCRIPTION_ENTITLEMENT_MATRIX_APPLY','RETENTION_POLICY_UPDATE',
'RECIPE_CREATE','RECIPE_REVIEW_UPDATE','USER_STATUS_UPDATE','USER_SUPPORT_NOTE_CREATE','USER_SESSION_REVOKE',
'PRODUCT_QUALITY_AI_SETTINGS_UPDATE','NOTIFICATION_CAMPAIGN_CREATE','NOTIFICATION_CAMPAIGN_UPDATE',
'NOTIFICATION_CAMPAIGN_SCHEDULE','NOTIFICATION_CAMPAIGN_CANCEL','NOTIFICATION_DEFINITION_CREATE','NOTIFICATION_DEFINITION_UPDATE',
'PROMO_CREATE','PROMO_UPDATE','PROMO_ACTIVATE','PROMO_DEACTIVATE','PROMO_RECONCILE','PROMO_REDEMPTION_RECORD',
'ADMIN_ROLE_UPDATE','ADMIN_STATUS_UPDATE','ADMIN_INVITATION_CREATE','ADMIN_INVITATION_RESEND','ADMIN_INVITATION_REVOKE',
'ADMIN_INVITATION_ACCEPT','ADMIN_PASSWORD_RESET_REQUEST','ADMIN_PASSWORD_RESET_CONFIRM','ADMIN_MFA_STATUS_UPDATE',
'ADMIN_MFA_ENROLL','ADMIN_MFA_DISABLE','ADMIN_MFA_REAUTHENTICATE','ADMIN_MFA_FAILURE','ADMIN_MFA_RECOVERY_CODE_USE',
'ADMIN_SESSION_CREATE','RECOVERY_OWNER_LOGIN','ADMIN_SESSION_REVOKE','ADMIN_OTHER_SESSIONS_REVOKE',
'ADMIN_APPROVAL_REQUEST','ADMIN_APPROVAL_APPROVE','ADMIN_APPROVAL_REJECT','ADMIN_APPROVAL_EXECUTION_FAILED',
'GDPR_REQUEST_UPDATE','PRODUCTION_VERIFICATION_RECORD','CATALOG_EXERCISE_CREATE','CATALOG_EXERCISE_UPDATE',
'CATALOG_EXERCISE_REVIEW','CATALOG_REVIEW_ASSIGNMENT','RUNTIME_POLICY_UPDATE','RUNTIME_POLICY_ROLLBACK',
'RUNTIME_RECORD_CREATE','RUNTIME_JOB_RETRY','FASTING_OPERATIONS_CONFIG_UPDATE','AI_EXERCISE_RESOLVE','AI_EXERCISE_DISMISS'
));

ALTER TABLE admin_action_audits DROP CONSTRAINT IF EXISTS chk_admin_action_audits_target_type;
ALTER TABLE admin_action_audits ADD CONSTRAINT chk_admin_action_audits_target_type CHECK (target_type IN (
'USER_SUBSCRIPTION','AI_REQUEST','AI_OPERATIONS_POLICY','AI_CREDIT_PRICING','SUBSCRIPTION_FEATURE','RETENTION_POLICY',
'RECIPE','USER_ACCOUNT','PRODUCT_QUALITY_AI_SETTINGS','NOTIFICATION_CAMPAIGN','NOTIFICATION_DEFINITION','PROMOTION',
'ADMIN_ACCOUNT','ADMIN_INVITATION','ADMIN_SESSION','EXERCISE_ITEM','CATALOG_REVIEW_ITEM','RUNTIME_OPERATIONS',
'ADMIN_APPROVAL','GDPR_REQUEST','PRODUCTION_VERIFICATION','FASTING_GOVERNANCE','AI_EXERCISE_RESOLUTION'
));

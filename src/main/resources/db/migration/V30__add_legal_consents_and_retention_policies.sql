CREATE TABLE IF NOT EXISTS user_consents (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    consent_type VARCHAR(80) NOT NULL,
    version VARCHAR(80) NOT NULL,
    status VARCHAR(40) NOT NULL,
    source VARCHAR(80),
    ip_address VARCHAR(128),
    user_agent VARCHAR(512),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_consents_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT chk_user_consents_type CHECK (consent_type IN (
        'TERMS_OF_SERVICE',
        'PRIVACY_POLICY',
        'MARKETING_EMAIL',
        'HEALTH_DATA_PROCESSING',
        'AI_RECOMMENDATION_PROCESSING'
    )),
    CONSTRAINT chk_user_consents_status CHECK (status IN ('ACCEPTED', 'REVOKED'))
);

CREATE INDEX IF NOT EXISTS idx_user_consents_user_created_at
    ON user_consents(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_consents_user_type_created_at
    ON user_consents(user_id, consent_type, created_at DESC);

CREATE TABLE IF NOT EXISTS retention_policies (
    id BIGSERIAL PRIMARY KEY,
    policy_key VARCHAR(80) NOT NULL UNIQUE,
    retention_days INTEGER NOT NULL,
    legal_basis VARCHAR(255) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_retention_policies_key CHECK (policy_key IN (
        'ACCOUNT_PROFILE',
        'FOOD_LOGS',
        'EXERCISE_LOGS',
        'HEALTH_DATA',
        'NOTIFICATIONS',
        'PAYMENT_AUDIT_EVENTS',
        'ADMIN_AUDIT_EVENTS',
        'CONSENT_HISTORY'
    )),
    CONSTRAINT chk_retention_policies_days CHECK (retention_days >= 0 AND retention_days <= 36500)
);

INSERT INTO retention_policies (policy_key, retention_days, legal_basis, description, active, updated_by)
VALUES
    ('ACCOUNT_PROFILE', 0, 'User deletion request', 'Account profile is anonymized during GDPR delete while access credentials are invalidated.', TRUE, 'system'),
    ('FOOD_LOGS', 0, 'User deletion request', 'Food logs are deleted during GDPR delete unless a future legal hold is configured.', TRUE, 'system'),
    ('EXERCISE_LOGS', 0, 'User deletion request', 'Exercise logs are deleted during GDPR delete unless a future legal hold is configured.', TRUE, 'system'),
    ('HEALTH_DATA', 0, 'Explicit consent and user deletion request', 'Health connection and metric data is deleted during GDPR delete.', TRUE, 'system'),
    ('NOTIFICATIONS', 0, 'User deletion request', 'User notifications are deleted during GDPR delete.', TRUE, 'system'),
    ('PAYMENT_AUDIT_EVENTS', 2555, 'Payment reconciliation, fraud prevention, and legal obligation', 'RevenueCat/payment provider events are retained without user reference after GDPR delete.', TRUE, 'system'),
    ('ADMIN_AUDIT_EVENTS', 2555, 'Security audit and abuse prevention', 'Admin setting changes are retained for operational audit.', TRUE, 'system'),
    ('CONSENT_HISTORY', 2555, 'Legal obligation and consent evidence', 'Consent decisions are retained as legal evidence.', TRUE, 'system')
ON CONFLICT (policy_key) DO NOTHING;

ALTER TABLE admin_action_audits
    DROP CONSTRAINT IF EXISTS chk_admin_action_audits_action_type;

ALTER TABLE admin_action_audits
    ADD CONSTRAINT chk_admin_action_audits_action_type CHECK (action_type IN (
        'SUBSCRIPTION_UPDATE',
        'AI_QUOTA_RESET',
        'AI_QUOTA_ADDON_GRANT',
        'SUBSCRIPTION_FEATURE_UPDATE',
        'RETENTION_POLICY_UPDATE'
    ));

ALTER TABLE admin_action_audits
    DROP CONSTRAINT IF EXISTS chk_admin_action_audits_target_type;

ALTER TABLE admin_action_audits
    ADD CONSTRAINT chk_admin_action_audits_target_type CHECK (target_type IN (
        'USER_SUBSCRIPTION',
        'SUBSCRIPTION_FEATURE',
        'RETENTION_POLICY'
    ));

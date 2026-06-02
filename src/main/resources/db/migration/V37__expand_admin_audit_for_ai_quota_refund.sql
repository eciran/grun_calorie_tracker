ALTER TABLE admin_action_audits
    DROP CONSTRAINT IF EXISTS chk_admin_action_audits_action_type;

ALTER TABLE admin_action_audits
    ADD CONSTRAINT chk_admin_action_audits_action_type CHECK (action_type IN (
        'SUBSCRIPTION_UPDATE',
        'AI_QUOTA_RESET',
        'AI_QUOTA_ADDON_GRANT',
        'AI_QUOTA_REFUND',
        'SUBSCRIPTION_FEATURE_UPDATE',
        'RETENTION_POLICY_UPDATE'
    ));

ALTER TABLE admin_action_audits
    DROP CONSTRAINT IF EXISTS chk_admin_action_audits_target_type;

ALTER TABLE admin_action_audits
    ADD CONSTRAINT chk_admin_action_audits_target_type CHECK (target_type IN (
        'USER_SUBSCRIPTION',
        'AI_REQUEST',
        'SUBSCRIPTION_FEATURE',
        'RETENTION_POLICY'
    ));

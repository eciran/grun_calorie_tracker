-- Extend the current immutable audit allowlists without duplicating the full historical enum list.
DO $$
DECLARE definition TEXT;
BEGIN
    SELECT pg_get_constraintdef(oid) INTO definition FROM pg_constraint
      WHERE conname = 'chk_admin_action_audits_action_type' AND conrelid = 'admin_action_audits'::regclass;
    EXECUTE 'ALTER TABLE admin_action_audits DROP CONSTRAINT chk_admin_action_audits_action_type';
    definition := replace(definition, '''OWNER_ALERT_ACKNOWLEDGE''', '''OWNER_ALERT_ACKNOWLEDGE'',''OWNER_ERROR_GROUP_STATUS_UPDATE''');
    EXECUTE 'ALTER TABLE admin_action_audits ADD CONSTRAINT chk_admin_action_audits_action_type ' || definition;

    SELECT pg_get_constraintdef(oid) INTO definition FROM pg_constraint
      WHERE conname = 'chk_admin_action_audits_target_type' AND conrelid = 'admin_action_audits'::regclass;
    EXECUTE 'ALTER TABLE admin_action_audits DROP CONSTRAINT chk_admin_action_audits_target_type';
    definition := replace(definition, '''OWNER_OPERATIONAL_ALERT''', '''OWNER_OPERATIONAL_ALERT'',''OWNER_ERROR_GROUP''');
    EXECUTE 'ALTER TABLE admin_action_audits ADD CONSTRAINT chk_admin_action_audits_target_type ' || definition;
END $$;

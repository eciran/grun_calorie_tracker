-- Allow audited one-to-one notifications sent from Customer 360.
DO $$
DECLARE definition TEXT;
BEGIN
    SELECT pg_get_constraintdef(oid) INTO definition FROM pg_constraint
      WHERE conname = 'chk_admin_action_audits_action_type' AND conrelid = 'admin_action_audits'::regclass;
    EXECUTE 'ALTER TABLE admin_action_audits DROP CONSTRAINT chk_admin_action_audits_action_type';
    definition := replace(
        definition,
        '(''USER_SUPPORT_NOTE_CREATE''::character varying)::text',
        '(''USER_SUPPORT_NOTE_CREATE''::character varying)::text, ''USER_NOTIFICATION_SEND''::text'
    );
    IF position('USER_NOTIFICATION_SEND' in definition) = 0 THEN
        definition := replace(
            definition,
            '''USER_SUPPORT_NOTE_CREATE''::text',
            '''USER_SUPPORT_NOTE_CREATE''::text, ''USER_NOTIFICATION_SEND''::text'
        );
    END IF;
    IF position('USER_NOTIFICATION_SEND' in definition) = 0 THEN
        RAISE EXCEPTION 'Could not extend admin audit action constraint for USER_NOTIFICATION_SEND';
    END IF;
    EXECUTE 'ALTER TABLE admin_action_audits ADD CONSTRAINT chk_admin_action_audits_action_type ' || definition;
END $$;

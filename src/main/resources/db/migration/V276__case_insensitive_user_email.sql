-- Never select a winner or merge subscriptions or personal data implicitly.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM users WHERE email IS NOT NULL
        GROUP BY lower(btrim(email)) HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Canonical user email conflicts exist. Resolve ownership explicitly before applying V276.';
    END IF;
END $$;

CREATE UNIQUE INDEX uq_users_email_canonical ON users (lower(btrim(email)));

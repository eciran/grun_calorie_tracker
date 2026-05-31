ALTER TABLE users
    ADD COLUMN IF NOT EXISTS preferred_language VARCHAR(8);

UPDATE users
SET preferred_language = 'EN'
WHERE preferred_language IS NULL;

ALTER TABLE users
    ALTER COLUMN preferred_language SET DEFAULT 'EN';

ALTER TABLE users
    ALTER COLUMN preferred_language SET NOT NULL;

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS chk_users_preferred_language;

ALTER TABLE users
    ADD CONSTRAINT chk_users_preferred_language
        CHECK (preferred_language IN ('EN', 'TR'));

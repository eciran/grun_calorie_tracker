ALTER TABLE users ADD COLUMN auth_token_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE refresh_tokens ADD COLUMN auth_token_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE admin_sessions ADD COLUMN auth_token_version BIGINT NOT NULL DEFAULT 0;

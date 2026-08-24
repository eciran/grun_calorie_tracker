CREATE TABLE admin_invitations (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    role VARCHAR(64) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    invited_by_user_id BIGINT NOT NULL REFERENCES users(id),
    accepted_user_id BIGINT REFERENCES users(id),
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_admin_invitations_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED'))
);

CREATE INDEX idx_admin_invitations_email_status ON admin_invitations (LOWER(email), status);
CREATE INDEX idx_admin_invitations_created_at ON admin_invitations (created_at DESC);

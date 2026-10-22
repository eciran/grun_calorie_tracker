CREATE TABLE admin_user_permissions (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission VARCHAR(64) NOT NULL,
    PRIMARY KEY (user_id, permission)
);

CREATE TABLE admin_invitation_permissions (
    invitation_id BIGINT NOT NULL REFERENCES admin_invitations(id) ON DELETE CASCADE,
    permission VARCHAR(64) NOT NULL,
    PRIMARY KEY (invitation_id, permission)
);

CREATE INDEX idx_admin_user_permissions_permission ON admin_user_permissions(permission);

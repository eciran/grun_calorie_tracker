-- OWNER is the only full-catalog role. Legacy ADMIN accounts are deliberately
-- migrated to the safest predefined role and can later be assigned a scoped
-- role by an authenticated owner.
UPDATE users
SET role = 'ADMIN_READ_ONLY',
    admin_role_updated_at = CURRENT_TIMESTAMP
WHERE role = 'ADMIN';

DROP INDEX IF EXISTS idx_users_admin_roles;
CREATE INDEX idx_users_admin_roles
    ON users(role, account_enabled)
    WHERE role = 'OWNER' OR role LIKE 'ADMIN%';

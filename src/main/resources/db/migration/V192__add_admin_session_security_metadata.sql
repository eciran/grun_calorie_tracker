ALTER TABLE admin_sessions ADD COLUMN device_label VARCHAR(200);
ALTER TABLE admin_sessions ADD COLUMN masked_ip VARCHAR(64);
CREATE INDEX idx_admin_sessions_user_created ON admin_sessions(user_id, created_at DESC);

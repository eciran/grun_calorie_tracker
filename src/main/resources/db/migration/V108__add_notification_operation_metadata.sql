ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS severity VARCHAR(32),
    ADD COLUMN IF NOT EXISTS source VARCHAR(64),
    ADD COLUMN IF NOT EXISTS target_type VARCHAR(64),
    ADD COLUMN IF NOT EXISTS target_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS target_route VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_notifications_user_severity_read_created
    ON notifications (user_id, severity, is_read, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_target
    ON notifications (target_type, target_id);
ALTER TABLE owner_error_events ALTER COLUMN status DROP NOT NULL;
ALTER TABLE owner_error_events DROP CONSTRAINT IF EXISTS owner_error_events_status_check;
ALTER TABLE owner_error_events ADD CONSTRAINT owner_error_events_status_check CHECK (status IS NULL OR status BETWEEN 400 AND 599);
ALTER TABLE owner_error_events ADD COLUMN source VARCHAR(24) NOT NULL DEFAULT 'BACKEND';
ALTER TABLE owner_error_events ADD COLUMN client_platform VARCHAR(24);
ALTER TABLE owner_error_events ADD COLUMN app_version VARCHAR(40);
ALTER TABLE owner_error_events ADD CONSTRAINT chk_owner_error_source CHECK (source IN ('BACKEND','PROXY','ADMIN_WEB','MOBILE'));
CREATE INDEX idx_owner_errors_source_time ON owner_error_events (source, occurred_at DESC);

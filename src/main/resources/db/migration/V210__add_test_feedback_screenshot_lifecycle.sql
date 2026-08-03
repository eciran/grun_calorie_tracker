ALTER TABLE test_feedback_submissions
    ADD COLUMN IF NOT EXISTS screenshot_storage_key VARCHAR(500),
    ADD COLUMN IF NOT EXISTS screenshot_content_type VARCHAR(80),
    ADD COLUMN IF NOT EXISTS screenshot_size_bytes BIGINT,
    ADD COLUMN IF NOT EXISTS screenshot_sha256 VARCHAR(64),
    ADD COLUMN IF NOT EXISTS screenshot_attached_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS screenshot_expires_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS screenshot_deleted_at TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_test_feedback_screenshot_expiry
    ON test_feedback_submissions (screenshot_expires_at)
    WHERE screenshot_storage_key IS NOT NULL AND screenshot_deleted_at IS NULL;
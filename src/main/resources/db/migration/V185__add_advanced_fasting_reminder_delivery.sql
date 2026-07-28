CREATE TABLE fasting_reminder_deliveries (
 id BIGSERIAL PRIMARY KEY, occurrence_id BIGINT NOT NULL REFERENCES fasting_program_occurrences(id) ON DELETE CASCADE,
 notification_id BIGINT REFERENCES notifications(id) ON DELETE SET NULL, occurrence_key VARCHAR(180) NOT NULL,
 reminder_type VARCHAR(32) NOT NULL, status VARCHAR(24) NOT NULL, scheduled_for TIMESTAMP NOT NULL,
 next_attempt_at TIMESTAMP, attempt_count INTEGER NOT NULL DEFAULT 0, last_error VARCHAR(500), delivered_at TIMESTAMP,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_fasting_reminder_occurrence_key UNIQUE(occurrence_key),
 CONSTRAINT chk_fasting_reminder_type CHECK(reminder_type IN ('PRE_START','START','NEARING_COMPLETION','COMPLETION','MISSED_PLAN')),
 CONSTRAINT chk_fasting_reminder_status CHECK(status IN ('PENDING','DEFERRED','SENT','FAILED','SUPPRESSED')),
 CONSTRAINT chk_fasting_reminder_attempts CHECK(attempt_count >= 0)
);
CREATE INDEX idx_fasting_reminder_retry ON fasting_reminder_deliveries(status,next_attempt_at);
ALTER TABLE users ADD COLUMN notification_quiet_hours_start TIME, ADD COLUMN notification_quiet_hours_end TIME;

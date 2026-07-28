ALTER TABLE fasting_sessions
    ADD COLUMN IF NOT EXISTS manual_entry BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_fasting_sessions_user_active_history
    ON fasting_sessions (user_id, started_at DESC)
    WHERE archived_at IS NULL;

CREATE TABLE IF NOT EXISTS fasting_history_corrections (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES fasting_sessions(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    actor_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    action VARCHAR(16) NOT NULL,
    correction_reason VARCHAR(64) NOT NULL,
    old_started_at TIMESTAMP,
    old_ended_at TIMESTAMP,
    new_started_at TIMESTAMP,
    new_ended_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_fasting_history_correction_action
        CHECK (action IN ('CREATE', 'UPDATE', 'ARCHIVE'))
);

CREATE INDEX IF NOT EXISTS idx_fasting_history_corrections_session_created
    ON fasting_history_corrections (session_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_fasting_history_corrections_user_created
    ON fasting_history_corrections (user_id, created_at DESC);
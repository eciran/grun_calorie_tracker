CREATE TABLE test_feedback_screenshot_events (
    id BIGSERIAL PRIMARY KEY,
    feedback_id BIGINT NOT NULL REFERENCES test_feedback_submissions(id) ON DELETE CASCADE,
    event_type VARCHAR(48) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    reported_size_bytes BIGINT,
    actual_size_bytes BIGINT,
    content_type VARCHAR(80),
    error_code VARCHAR(80),
    detail VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_test_feedback_screenshot_events_feedback_created
    ON test_feedback_screenshot_events (feedback_id, created_at, id);

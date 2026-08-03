CREATE TABLE test_feedback_submissions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    feedback_type VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'NEW',
    platform VARCHAR(16) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    route VARCHAR(240) NOT NULL,
    previous_route VARCHAR(240),
    description VARCHAR(2000),
    app_version VARCHAR(40),
    build_number VARCHAR(40),
    eas_build_id VARCHAR(100),
    commit_sha VARCHAR(64),
    os_version VARCHAR(80),
    device_model VARCHAR(120),
    language_tag VARCHAR(20),
    market_region VARCHAR(24),
    last_http_status INTEGER,
    last_http_duration_ms BIGINT,
    last_correlation_id VARCHAR(100),
    network_state VARCHAR(32),
    admin_note VARCHAR(2000),
    reviewed_by_user_id BIGINT REFERENCES users(id),
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_test_feedback_user_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT chk_test_feedback_type CHECK (feedback_type IN ('WORKS_WELL', 'PROBLEM', 'IMPROVEMENT')),
    CONSTRAINT chk_test_feedback_status CHECK (status IN ('NEW', 'REVIEWING', 'FIX_PLANNED', 'FIXED', 'RETEST_NEEDED', 'CLOSED')),
    CONSTRAINT chk_test_feedback_platform CHECK (platform IN ('ANDROID', 'IOS')),
    CONSTRAINT chk_test_feedback_http_status CHECK (last_http_status IS NULL OR last_http_status BETWEEN 100 AND 599),
    CONSTRAINT chk_test_feedback_duration CHECK (last_http_duration_ms IS NULL OR last_http_duration_ms >= 0)
);

CREATE INDEX idx_test_feedback_created ON test_feedback_submissions(created_at DESC);
CREATE INDEX idx_test_feedback_status_created ON test_feedback_submissions(status, created_at DESC);
CREATE INDEX idx_test_feedback_route_created ON test_feedback_submissions(route, created_at DESC);
CREATE INDEX idx_test_feedback_type_platform ON test_feedback_submissions(feedback_type, platform);

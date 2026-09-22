CREATE TABLE owner_error_events (
    id BIGSERIAL PRIMARY KEY,
    event_key UUID NOT NULL UNIQUE,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status INTEGER NOT NULL CHECK (status BETWEEN 400 AND 599),
    method VARCHAR(12) NOT NULL,
    route VARCHAR(300) NOT NULL,
    correlation_id VARCHAR(36),
    error_code VARCHAR(80),
    exception_type VARCHAR(120),
    technical_location VARCHAR(2000),
    duration_ms BIGINT NOT NULL
);
CREATE INDEX idx_owner_errors_time ON owner_error_events (occurred_at DESC, id DESC);
CREATE INDEX idx_owner_errors_status_time ON owner_error_events (status, occurred_at DESC);
CREATE INDEX idx_owner_errors_correlation ON owner_error_events (correlation_id);

CREATE TABLE owner_error_group_state_history (
    id BIGSERIAL PRIMARY KEY,
    fingerprint VARCHAR(64) NOT NULL,
    previous_status VARCHAR(20) NOT NULL,
    lifecycle_status VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    updated_by VARCHAR(320) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_owner_error_group_history_previous CHECK (previous_status IN ('NEW','INVESTIGATING','RESOLVED','REOPENED')),
    CONSTRAINT chk_owner_error_group_history_status CHECK (lifecycle_status IN ('INVESTIGATING','RESOLVED','REOPENED'))
);

CREATE INDEX idx_owner_error_group_history_status_time
    ON owner_error_group_state_history (lifecycle_status, occurred_at DESC);

CREATE INDEX idx_owner_error_group_history_fingerprint_time
    ON owner_error_group_state_history (fingerprint, occurred_at DESC);

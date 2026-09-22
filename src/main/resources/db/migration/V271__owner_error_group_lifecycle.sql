CREATE TABLE owner_error_group_states (
    fingerprint VARCHAR(64) PRIMARY KEY,
    source VARCHAR(24) NOT NULL,
    status INTEGER,
    method VARCHAR(12) NOT NULL,
    route VARCHAR(300) NOT NULL,
    error_code VARCHAR(80),
    lifecycle_status VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    updated_by VARCHAR(320) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_owner_error_group_lifecycle CHECK (lifecycle_status IN ('INVESTIGATING','RESOLVED','REOPENED')),
    CONSTRAINT chk_owner_error_group_source CHECK (source IN ('BACKEND','PROXY','ADMIN_WEB','MOBILE')),
    CONSTRAINT chk_owner_error_group_http_status CHECK (status IS NULL OR status BETWEEN 400 AND 599)
);
CREATE INDEX idx_owner_error_group_state_status ON owner_error_group_states (lifecycle_status, updated_at DESC);

CREATE TABLE gdpr_admin_requests (
    id BIGSERIAL PRIMARY KEY,
    request_type VARCHAR(16) NOT NULL,
    status VARCHAR(20) NOT NULL,
    subject_reference VARCHAR(64) NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    due_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    assigned_to VARCHAR(320),
    result_code VARCHAR(64),
    evidence_reference VARCHAR(128),
    failure_summary VARCHAR(300),
    escalated_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_gdpr_admin_request_type CHECK (request_type IN ('EXPORT', 'DELETE')),
    CONSTRAINT chk_gdpr_admin_request_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED', 'ESCALATED'))
);
CREATE INDEX idx_gdpr_admin_requests_status_due ON gdpr_admin_requests(status, due_at);
CREATE INDEX idx_gdpr_admin_requests_requested ON gdpr_admin_requests(requested_at DESC);

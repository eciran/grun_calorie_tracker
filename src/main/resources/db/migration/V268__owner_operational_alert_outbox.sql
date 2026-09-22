CREATE TABLE owner_operational_alerts (
    id BIGSERIAL PRIMARY KEY,
    dedupe_key VARCHAR(240) NOT NULL UNIQUE,
    category VARCHAR(60) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    title_en VARCHAR(240) NOT NULL,
    title_tr VARCHAR(240) NOT NULL,
    message_en VARCHAR(2000) NOT NULL,
    message_tr VARCHAR(2000) NOT NULL,
    target_path VARCHAR(500) NOT NULL,
    occurrence_count BIGINT NOT NULL,
    first_occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempt_count INTEGER NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    sent_at TIMESTAMP WITH TIME ZONE,
    last_error_type VARCHAR(240),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT
);

CREATE INDEX idx_owner_operational_alert_delivery
    ON owner_operational_alerts (status, next_attempt_at, id);

CREATE INDEX idx_owner_operational_alert_last_occurrence
    ON owner_operational_alerts (last_occurred_at DESC, id DESC);

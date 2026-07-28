CREATE TABLE fasting_schedule_exceptions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    program_id BIGINT NOT NULL REFERENCES fasting_programs(id) ON DELETE CASCADE,
    program_version_id BIGINT NOT NULL REFERENCES fasting_program_versions(id) ON DELETE CASCADE,
    source_date DATE NOT NULL,
    target_date DATE,
    exception_type VARCHAR(32) NOT NULL,
    moved_start_time TIME,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_fasting_exception_user_source_date UNIQUE (user_id, source_date),
    CONSTRAINT chk_fasting_exception_type CHECK (exception_type IN ('SKIP','MOVE_START_TIME','MOVE_REDUCED_DAY'))
);
CREATE INDEX idx_fasting_exception_user_target ON fasting_schedule_exceptions(user_id, target_date);

CREATE TABLE fasting_schedule_exception_audits (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    exception_id BIGINT,
    source_date DATE NOT NULL,
    action VARCHAR(16) NOT NULL,
    old_value VARCHAR(500),
    new_value VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_fasting_exception_audit_action CHECK (action IN ('UPSERT','DELETE'))
);
CREATE INDEX idx_fasting_exception_audit_user_date ON fasting_schedule_exception_audits(user_id, source_date, created_at DESC);
CREATE TABLE fasting_programs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    effective_from DATE,
    effective_until DATE,
    current_version_number INTEGER NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_fasting_program_status CHECK (status IN ('DRAFT','ACTIVE','PAUSED','ARCHIVED')),
    CONSTRAINT chk_fasting_program_dates CHECK (effective_until IS NULL OR effective_from IS NULL OR effective_until >= effective_from),
    CONSTRAINT chk_fasting_program_version_positive CHECK (current_version_number > 0)
);
CREATE INDEX idx_fasting_program_user_status ON fasting_programs(user_id, status);
CREATE UNIQUE INDEX uk_fasting_program_one_active_per_user ON fasting_programs(user_id) WHERE status = 'ACTIVE';

CREATE TABLE fasting_program_versions (
    id BIGSERIAL PRIMARY KEY,
    program_id BIGINT NOT NULL REFERENCES fasting_programs(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    safety_policy_version VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_fasting_program_version UNIQUE (program_id, version_number),
    CONSTRAINT chk_fasting_program_version_number CHECK (version_number > 0)
);
CREATE INDEX idx_fasting_program_versions_program ON fasting_program_versions(program_id, version_number DESC);

CREATE TABLE fasting_program_day_rules (
    id BIGSERIAL PRIMARY KEY,
    program_version_id BIGINT NOT NULL REFERENCES fasting_program_versions(id) ON DELETE CASCADE,
    day_of_week VARCHAR(12) NOT NULL,
    rule_type VARCHAR(24) NOT NULL,
    fasting_minutes INTEGER,
    preferred_start_time TIME,
    reduced_calorie_target INTEGER,
    CONSTRAINT uk_fasting_program_version_weekday UNIQUE (program_version_id, day_of_week),
    CONSTRAINT chk_fasting_rule_day CHECK (day_of_week IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')),
    CONSTRAINT chk_fasting_rule_type CHECK (rule_type IN ('FAST','REDUCED_CALORIE','NORMAL','REST')),
    CONSTRAINT chk_fasting_rule_minutes CHECK (fasting_minutes IS NULL OR fasting_minutes BETWEEN 60 AND 1440),
    CONSTRAINT chk_fasting_rule_calories CHECK (reduced_calorie_target IS NULL OR reduced_calorie_target BETWEEN 300 AND 1200),
    CONSTRAINT chk_fasting_rule_shape CHECK (
        (rule_type = 'FAST' AND fasting_minutes IS NOT NULL AND preferred_start_time IS NOT NULL AND reduced_calorie_target IS NULL)
        OR (rule_type = 'REDUCED_CALORIE' AND fasting_minutes IS NULL AND preferred_start_time IS NULL AND reduced_calorie_target IS NOT NULL)
        OR (rule_type IN ('NORMAL','REST') AND fasting_minutes IS NULL AND preferred_start_time IS NULL AND reduced_calorie_target IS NULL)
    )
);
CREATE INDEX idx_fasting_day_rules_version ON fasting_program_day_rules(program_version_id, day_of_week);

ALTER TABLE fasting_sessions
    ADD COLUMN planned_rule_id BIGINT REFERENCES fasting_program_day_rules(id) ON DELETE SET NULL,
    ADD COLUMN planned_program_version INTEGER,
    ADD COLUMN planned_rule_type VARCHAR(24),
    ADD COLUMN planned_fasting_minutes INTEGER,
    ADD COLUMN planned_reduced_calorie_target INTEGER,
    ADD COLUMN planned_safety_policy_version VARCHAR(50);
CREATE INDEX idx_fasting_sessions_planned_rule ON fasting_sessions(planned_rule_id);

CREATE OR REPLACE FUNCTION reject_fasting_version_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'fasting program versions and day rules are immutable';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_fasting_program_version_immutable BEFORE UPDATE ON fasting_program_versions
FOR EACH ROW EXECUTE FUNCTION reject_fasting_version_mutation();
CREATE TRIGGER trg_fasting_day_rule_immutable BEFORE UPDATE ON fasting_program_day_rules
FOR EACH ROW EXECUTE FUNCTION reject_fasting_version_mutation();

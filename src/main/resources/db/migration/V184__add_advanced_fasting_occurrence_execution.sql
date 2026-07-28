CREATE TABLE fasting_program_occurrences (
 id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 program_id BIGINT NOT NULL REFERENCES fasting_programs(id) ON DELETE CASCADE,
 program_version_id BIGINT NOT NULL REFERENCES fasting_program_versions(id), day_rule_id BIGINT NOT NULL REFERENCES fasting_program_day_rules(id),
 fasting_session_id BIGINT REFERENCES fasting_sessions(id) ON DELETE SET NULL, occurrence_date DATE NOT NULL,
 rule_type VARCHAR(24) NOT NULL, status VARCHAR(24) NOT NULL, adherence_status VARCHAR(24) NOT NULL,
 planned_start_at TIMESTAMP, planned_end_at TIMESTAMP, planned_fasting_minutes INTEGER, planned_calorie_target INTEGER,
 actual_calories DOUBLE PRECISION, evaluated_at TIMESTAMP, skip_reason VARCHAR(32), reason_note VARCHAR(500),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_fasting_occurrence_user_date UNIQUE(user_id,occurrence_date), CONSTRAINT uk_fasting_occurrence_session UNIQUE(fasting_session_id),
 CONSTRAINT chk_fasting_occurrence_status CHECK(status IN ('PLANNED','IN_PROGRESS','COMPLETED','MISSED','SKIPPED')),
 CONSTRAINT chk_fasting_occurrence_adherence CHECK(adherence_status IN ('NOT_APPLICABLE','PENDING','MET','NOT_MET','UNKNOWN')),
 CONSTRAINT chk_fasting_occurrence_calories CHECK(actual_calories IS NULL OR actual_calories >= 0)
);
CREATE INDEX idx_fasting_occurrence_program_date ON fasting_program_occurrences(program_id,occurrence_date);
CREATE INDEX idx_fasting_occurrence_user_status ON fasting_program_occurrences(user_id,status,occurrence_date DESC);
ALTER TABLE fasting_sessions ADD COLUMN planned_start_at TIMESTAMP, ADD COLUMN planned_end_at TIMESTAMP, ADD COLUMN outcome_reason VARCHAR(32);

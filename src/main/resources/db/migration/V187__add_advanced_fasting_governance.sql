CREATE TABLE advanced_fasting_operations_config (
    id BIGINT PRIMARY KEY,
    reminder_enabled BOOLEAN NOT NULL,
    pre_start_minutes INTEGER NOT NULL CHECK (pre_start_minutes BETWEEN 10 AND 60),
    nearing_completion_minutes INTEGER NOT NULL CHECK (nearing_completion_minutes BETWEEN 10 AND 60),
    missed_plan_minutes INTEGER NOT NULL CHECK (missed_plan_minutes BETWEEN 30 AND 180),
    max_retry_attempts INTEGER NOT NULL CHECK (max_retry_attempts BETWEEN 1 AND 5),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

INSERT INTO advanced_fasting_operations_config (
    id, reminder_enabled, pre_start_minutes, nearing_completion_minutes,
    missed_plan_minutes, max_retry_attempts, version, created_at, updated_at
) VALUES (1, TRUE, 30, 30, 60, 3, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
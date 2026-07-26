CREATE TABLE sleep_goals (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_minutes INTEGER NOT NULL,
    preferred_bedtime TIME,
    preferred_wake_time TIME,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_sleep_goals_user UNIQUE (user_id),
    CONSTRAINT chk_sleep_goal_target CHECK (target_minutes BETWEEN 60 AND 960)
);

CREATE TABLE sleep_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sleep_date DATE NOT NULL,
    duration_minutes INTEGER NOT NULL,
    time_zone VARCHAR(64) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    external_id VARCHAR(255),
    quality_score INTEGER NOT NULL,
    quality_confidence VARCHAR(32) NOT NULL,
    note VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT chk_sleep_session_times CHECK (ended_at > started_at),
    CONSTRAINT chk_sleep_session_duration CHECK (duration_minutes BETWEEN 1 AND 2160),
    CONSTRAINT chk_sleep_session_quality CHECK (quality_score BETWEEN 0 AND 100),
    CONSTRAINT chk_sleep_session_provider CHECK (provider IN ('APPLE_HEALTH', 'GOOGLE_FIT', 'HEALTH_CONNECT', 'MANUAL')),
    CONSTRAINT chk_sleep_session_external_id CHECK (provider = 'MANUAL' OR external_id IS NOT NULL)
);

CREATE INDEX idx_sleep_sessions_user_date
    ON sleep_sessions(user_id, sleep_date DESC, started_at DESC);

CREATE INDEX idx_sleep_sessions_user_start
    ON sleep_sessions(user_id, started_at DESC);

CREATE UNIQUE INDEX uk_sleep_sessions_provider_external
    ON sleep_sessions(user_id, provider, external_id)
    WHERE external_id IS NOT NULL;

CREATE TABLE sleep_stages (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES sleep_sessions(id) ON DELETE CASCADE,
    stage_type VARCHAR(24) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE NOT NULL,
    duration_minutes INTEGER NOT NULL,
    CONSTRAINT chk_sleep_stage_type CHECK (stage_type IN ('AWAKE', 'LIGHT', 'DEEP', 'REM', 'ASLEEP', 'UNKNOWN')),
    CONSTRAINT chk_sleep_stage_times CHECK (ended_at > started_at),
    CONSTRAINT chk_sleep_stage_duration CHECK (duration_minutes BETWEEN 1 AND 2160)
);

CREATE INDEX idx_sleep_stages_session_start
    ON sleep_stages(session_id, started_at ASC);

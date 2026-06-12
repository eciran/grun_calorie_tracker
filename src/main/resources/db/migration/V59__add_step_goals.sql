CREATE TABLE IF NOT EXISTS step_goals (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    target_steps INTEGER NOT NULL DEFAULT 10000,
    reminder_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_step_goals_target_steps CHECK (target_steps BETWEEN 1000 AND 50000)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_step_goals_user ON step_goals(user_id);

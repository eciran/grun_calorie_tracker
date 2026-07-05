CREATE TABLE IF NOT EXISTS workout_plans (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL,
    status VARCHAR(40) NOT NULL,
    source_ai_request_id BIGINT,
    plan_payload TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT fk_workout_plans_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_workout_plans_ai_request FOREIGN KEY (source_ai_request_id) REFERENCES ai_request_history(id)
);

CREATE INDEX IF NOT EXISTS idx_workout_plans_user_created_at ON workout_plans (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_workout_plans_user_active ON workout_plans (user_id, active);

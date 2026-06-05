CREATE TABLE IF NOT EXISTS water_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    log_date DATE NOT NULL,
    amount_ml INTEGER NOT NULL,
    source VARCHAR(50) NOT NULL DEFAULT 'MANUAL',
    logged_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_water_logs_amount_ml_positive CHECK (amount_ml > 0)
);

CREATE INDEX IF NOT EXISTS idx_water_logs_user_log_date ON water_logs(user_id, log_date);
CREATE INDEX IF NOT EXISTS idx_water_logs_user_logged_at ON water_logs(user_id, logged_at);

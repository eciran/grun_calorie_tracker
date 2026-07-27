ALTER TABLE users
    ADD COLUMN IF NOT EXISTS recent_products_cleared_at TIMESTAMP;

ALTER TABLE food_logs
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_food_logs_user_created_at
    ON food_logs (user_id, created_at DESC);
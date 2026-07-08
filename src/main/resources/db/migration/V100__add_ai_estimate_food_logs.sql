ALTER TABLE food_logs ADD COLUMN IF NOT EXISTS display_name VARCHAR(255);
ALTER TABLE food_logs ADD COLUMN IF NOT EXISTS estimated BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE food_logs ADD COLUMN IF NOT EXISTS ai_request_id BIGINT;
ALTER TABLE food_logs ADD COLUMN IF NOT EXISTS ai_confidence DOUBLE PRECISION;

ALTER TABLE food_logs
    ADD CONSTRAINT fk_food_logs_ai_request
    FOREIGN KEY (ai_request_id) REFERENCES ai_request_history(id);

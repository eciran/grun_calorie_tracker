ALTER TABLE food_items ADD COLUMN IF NOT EXISTS external_image_url VARCHAR(255);
ALTER TABLE food_items ADD COLUMN IF NOT EXISTS display_image_url VARCHAR(255);
ALTER TABLE food_items ADD COLUMN IF NOT EXISTS data_source VARCHAR(255);
ALTER TABLE food_items ADD COLUMN IF NOT EXISTS verification_status VARCHAR(255);
ALTER TABLE food_items ADD COLUMN IF NOT EXISTS image_source VARCHAR(255);
ALTER TABLE food_items ADD COLUMN IF NOT EXISTS image_status VARCHAR(255);
ALTER TABLE food_items ADD COLUMN IF NOT EXISTS usage_count BIGINT;
ALTER TABLE food_items ADD COLUMN IF NOT EXISTS quality_score INTEGER;
ALTER TABLE food_items ADD COLUMN IF NOT EXISTS review_priority INTEGER;
ALTER TABLE food_items ADD COLUMN IF NOT EXISTS last_external_sync_at TIMESTAMP;
ALTER TABLE food_items ADD COLUMN IF NOT EXISTS last_reviewed_at TIMESTAMP;
ALTER TABLE food_items ADD COLUMN IF NOT EXISTS reviewed_by VARCHAR(255);

ALTER TABLE exercise_items ADD COLUMN IF NOT EXISTS primary_muscle_group VARCHAR(255);
ALTER TABLE exercise_items ADD COLUMN IF NOT EXISTS secondary_muscle_groups VARCHAR(255);
ALTER TABLE exercise_items ADD COLUMN IF NOT EXISTS equipment VARCHAR(255);
ALTER TABLE exercise_items ADD COLUMN IF NOT EXISTS difficulty VARCHAR(255);
ALTER TABLE exercise_items ADD COLUMN IF NOT EXISTS instructions VARCHAR(2000);
ALTER TABLE exercise_items ADD COLUMN IF NOT EXISTS safety_notes VARCHAR(1000);
ALTER TABLE exercise_items ADD COLUMN IF NOT EXISTS thumbnail_url VARCHAR(255);
ALTER TABLE exercise_items ADD COLUMN IF NOT EXISTS video_url VARCHAR(255);
ALTER TABLE exercise_items ADD COLUMN IF NOT EXISTS animation_url VARCHAR(255);
ALTER TABLE exercise_items ADD COLUMN IF NOT EXISTS ai_eligible BOOLEAN;
ALTER TABLE exercise_items ADD COLUMN IF NOT EXISTS active BOOLEAN;

ALTER TABLE exercise_logs ADD COLUMN IF NOT EXISTS source VARCHAR(255);
ALTER TABLE exercise_logs ADD COLUMN IF NOT EXISTS external_id VARCHAR(255);
ALTER TABLE exercise_logs ADD COLUMN IF NOT EXISTS extra_data VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_exercise_logs_user_source ON exercise_logs (user_id, source);

ALTER TABLE exercise_logs
    DROP CONSTRAINT IF EXISTS uk_exercise_logs_user_source_external;

ALTER TABLE exercise_logs
    ADD CONSTRAINT uk_exercise_logs_user_source_external UNIQUE (user_id, source, external_id);

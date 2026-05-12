CREATE INDEX IF NOT EXISTS idx_food_items_barcode ON food_items (barcode);

CREATE INDEX IF NOT EXISTS idx_food_items_review_queue
    ON food_items (verification_status, image_status, review_priority DESC, usage_count DESC, id ASC);

CREATE INDEX IF NOT EXISTS idx_food_logs_user_log_date ON food_logs (user_id, log_date);

CREATE INDEX IF NOT EXISTS idx_exercise_logs_user_log_date ON exercise_logs (user_id, log_date);

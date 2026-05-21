ALTER TABLE food_items
    ADD COLUMN IF NOT EXISTS created_by_user_id BIGINT;

ALTER TABLE food_items
    ADD CONSTRAINT fk_food_items_created_by_user
        FOREIGN KEY (created_by_user_id) REFERENCES users(id);

CREATE INDEX IF NOT EXISTS idx_food_items_custom_owner
    ON food_items (created_by_user_id, is_custom, name);

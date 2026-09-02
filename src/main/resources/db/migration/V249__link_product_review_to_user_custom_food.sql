ALTER TABLE food_product_review_cases
    ADD COLUMN user_custom_food_id BIGINT;

ALTER TABLE food_product_review_cases
    ADD CONSTRAINT fk_food_product_review_user_custom_food
        FOREIGN KEY (user_custom_food_id) REFERENCES food_items(id) ON DELETE SET NULL;

CREATE INDEX idx_food_product_review_user_custom_food
    ON food_product_review_cases(user_custom_food_id);

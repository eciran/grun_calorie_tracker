CREATE TABLE food_item_source_categories (
    food_item_id BIGINT NOT NULL,
    category_tag VARCHAR(180) NOT NULL,
    CONSTRAINT fk_food_item_source_categories_food_item
        FOREIGN KEY (food_item_id) REFERENCES food_items(id) ON DELETE CASCADE,
    CONSTRAINT uq_food_item_source_categories UNIQUE (food_item_id, category_tag)
);

CREATE INDEX idx_food_item_source_categories_tag
    ON food_item_source_categories(category_tag, food_item_id);

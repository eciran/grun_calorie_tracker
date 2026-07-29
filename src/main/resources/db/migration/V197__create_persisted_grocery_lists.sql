CREATE TABLE grocery_lists (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_type VARCHAR(30) NOT NULL,
    source_meal_plan_id BIGINT NOT NULL REFERENCES meal_plans(id) ON DELETE CASCADE,
    source_updated_at TIMESTAMP NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_grocery_list_source_type CHECK (source_type IN ('MEAL_PLAN')),
    CONSTRAINT chk_grocery_list_status CHECK (status IN ('ACTIVE','COMPLETED','ARCHIVED'))
);

CREATE UNIQUE INDEX uk_grocery_lists_active_meal_plan
    ON grocery_lists(user_id, source_meal_plan_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_grocery_lists_user_updated
    ON grocery_lists(user_id, updated_at DESC);

CREATE INDEX idx_grocery_lists_source_meal_plan
    ON grocery_lists(source_meal_plan_id);

CREATE TABLE grocery_list_items (
    id BIGSERIAL PRIMARY KEY,
    grocery_list_id BIGINT NOT NULL REFERENCES grocery_lists(id) ON DELETE CASCADE,
    food_item_id BIGINT REFERENCES food_items(id) ON DELETE SET NULL,
    source VARCHAR(20) NOT NULL,
    generated_source_key VARCHAR(120),
    display_name VARCHAR(160) NOT NULL,
    category VARCHAR(30) NOT NULL DEFAULT 'OTHER',
    purchased BOOLEAN NOT NULL DEFAULT FALSE,
    excluded BOOLEAN NOT NULL DEFAULT FALSE,
    quantity_overridden BOOLEAN NOT NULL DEFAULT FALSE,
    display_quantity DOUBLE PRECISION NOT NULL,
    display_unit VARCHAR(30) NOT NULL,
    normalized_grams DOUBLE PRECISION,
    planned_uses INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_grocery_item_source CHECK (source IN ('GENERATED','MANUAL')),
    CONSTRAINT chk_grocery_item_category CHECK (category IN (
        'PRODUCE','MEAT_AND_SEAFOOD','DAIRY_AND_EGGS','BAKERY',
        'GRAINS_AND_PASTA','PANTRY','FROZEN','BEVERAGES','OTHER'
    )),
    CONSTRAINT chk_grocery_item_display_unit CHECK (display_unit IN (
        'GRAM','MILLILITER','TABLESPOON','TEASPOON','SLICE','SERVING','PIECE'
    )),
    CONSTRAINT chk_grocery_item_display_quantity CHECK (
        display_quantity > 0 AND display_quantity <= 100000
    ),
    CONSTRAINT chk_grocery_item_normalized_grams CHECK (
        normalized_grams IS NULL OR (normalized_grams >= 0 AND normalized_grams <= 1000000)
    ),
    CONSTRAINT chk_grocery_item_planned_uses CHECK (planned_uses >= 0 AND planned_uses <= 10000),
    CONSTRAINT chk_grocery_item_generated_identity CHECK (
        (source = 'GENERATED' AND generated_source_key IS NOT NULL)
        OR (source = 'MANUAL' AND generated_source_key IS NULL)
    )
);

CREATE UNIQUE INDEX uk_grocery_list_generated_item
    ON grocery_list_items(grocery_list_id, generated_source_key)
    WHERE source = 'GENERATED';

CREATE INDEX idx_grocery_list_items_list_category
    ON grocery_list_items(grocery_list_id, category, purchased, excluded);

CREATE INDEX idx_grocery_list_items_food
    ON grocery_list_items(food_item_id)
    WHERE food_item_id IS NOT NULL;

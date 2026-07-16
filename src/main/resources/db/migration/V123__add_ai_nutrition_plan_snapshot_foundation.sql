ALTER TABLE meal_plans
    ADD COLUMN IF NOT EXISTS generation_mode VARCHAR(30),
    ADD COLUMN IF NOT EXISTS workout_plan_id BIGINT,
    ADD COLUMN IF NOT EXISTS source_ai_request_id BIGINT,
    ADD COLUMN IF NOT EXISTS schema_version VARCHAR(50) NOT NULL DEFAULT 'meal_plan_v1',
    ADD COLUMN IF NOT EXISTS prompt_version VARCHAR(100);

ALTER TABLE meal_plans
    DROP CONSTRAINT IF EXISTS fk_meal_plans_workout_plan;

ALTER TABLE meal_plans
    ADD CONSTRAINT fk_meal_plans_workout_plan
        FOREIGN KEY (workout_plan_id) REFERENCES workout_plans(id) ON DELETE SET NULL;

ALTER TABLE meal_plans
    DROP CONSTRAINT IF EXISTS fk_meal_plans_source_ai_request;

ALTER TABLE meal_plans
    ADD CONSTRAINT fk_meal_plans_source_ai_request
        FOREIGN KEY (source_ai_request_id) REFERENCES ai_request_history(id) ON DELETE SET NULL;

ALTER TABLE meal_plans
    DROP CONSTRAINT IF EXISTS chk_meal_plans_generation_mode;

ALTER TABLE meal_plans
    ADD CONSTRAINT chk_meal_plans_generation_mode CHECK (
        (generation_mode IS NULL AND workout_plan_id IS NULL)
        OR (generation_mode = 'GENERAL' AND workout_plan_id IS NULL)
        OR (generation_mode = 'WORKOUT_ALIGNED' AND workout_plan_id IS NOT NULL)
    );

ALTER TABLE meal_plans
    ALTER COLUMN schema_version DROP DEFAULT;

CREATE INDEX IF NOT EXISTS idx_meal_plans_workout_plan
    ON meal_plans(workout_plan_id);

CREATE INDEX IF NOT EXISTS idx_meal_plans_source_ai_request
    ON meal_plans(source_ai_request_id);

ALTER TABLE meal_plan_items
    ADD COLUMN IF NOT EXISTS link_state VARCHAR(30) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS snapshot_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS snapshot_description TEXT,
    ADD COLUMN IF NOT EXISTS short_preparation_state VARCHAR(120),
    ADD COLUMN IF NOT EXISTS snapshot_calories DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_protein DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_carbs DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_fat DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_fiber DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_sugar DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_saturated_fat DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_sodium DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_potassium DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_cholesterol DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_calcium DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_iron DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_magnesium DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_zinc DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_vitamin_a DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_vitamin_c DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_vitamin_d DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_vitamin_e DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_vitamin_b12 DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS allergens_payload TEXT,
    ADD COLUMN IF NOT EXISTS warnings_payload TEXT,
    ADD COLUMN IF NOT EXISTS assumptions_payload TEXT,
    ADD COLUMN IF NOT EXISTS snapshot_payload TEXT,
    ADD COLUMN IF NOT EXISTS workout_relation VARCHAR(30) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS source_ai_request_id BIGINT,
    ADD COLUMN IF NOT EXISTS schema_version VARCHAR(50),
    ADD COLUMN IF NOT EXISTS prompt_version VARCHAR(100);

ALTER TABLE meal_plan_items
    DROP CONSTRAINT IF EXISTS chk_meal_plan_items_one_target;

ALTER TABLE meal_plan_items
    ADD CONSTRAINT chk_meal_plan_items_one_target CHECK (
        (item_type = 'FOOD_ITEM' AND food_item_id IS NOT NULL AND recipe_id IS NULL)
        OR (item_type = 'RECIPE' AND recipe_id IS NOT NULL AND food_item_id IS NULL)
        OR (item_type = 'AI_SNAPSHOT' AND NOT (food_item_id IS NOT NULL AND recipe_id IS NOT NULL))
    );

ALTER TABLE meal_plan_items
    ADD CONSTRAINT chk_meal_plan_items_ai_snapshot_required CHECK (
        item_type <> 'AI_SNAPSHOT'
        OR (
            snapshot_name IS NOT NULL
            AND portion_size IS NOT NULL
            AND portion_size > 0
            AND portion_unit IS NOT NULL
            AND snapshot_calories IS NOT NULL
            AND snapshot_protein IS NOT NULL
            AND snapshot_carbs IS NOT NULL
            AND snapshot_fat IS NOT NULL
        )
    );


ALTER TABLE meal_plan_items
    ADD CONSTRAINT chk_meal_plan_items_snapshot_nutrition_non_negative CHECK (
        COALESCE(snapshot_calories, 0) >= 0
        AND COALESCE(snapshot_protein, 0) >= 0
        AND COALESCE(snapshot_carbs, 0) >= 0
        AND COALESCE(snapshot_fat, 0) >= 0
        AND COALESCE(snapshot_fiber, 0) >= 0
        AND COALESCE(snapshot_sugar, 0) >= 0
        AND COALESCE(snapshot_saturated_fat, 0) >= 0
        AND COALESCE(snapshot_sodium, 0) >= 0
        AND COALESCE(snapshot_potassium, 0) >= 0
        AND COALESCE(snapshot_cholesterol, 0) >= 0
        AND COALESCE(snapshot_calcium, 0) >= 0
        AND COALESCE(snapshot_iron, 0) >= 0
        AND COALESCE(snapshot_magnesium, 0) >= 0
        AND COALESCE(snapshot_zinc, 0) >= 0
        AND COALESCE(snapshot_vitamin_a, 0) >= 0
        AND COALESCE(snapshot_vitamin_c, 0) >= 0
        AND COALESCE(snapshot_vitamin_d, 0) >= 0
        AND COALESCE(snapshot_vitamin_e, 0) >= 0
        AND COALESCE(snapshot_vitamin_b12, 0) >= 0
    );
ALTER TABLE meal_plan_items
    ADD CONSTRAINT chk_meal_plan_items_link_state CHECK (
        link_state IN ('NONE', 'SUGGESTED', 'USER_CONFIRMED', 'VERIFIED')
    );

ALTER TABLE meal_plan_items
    ADD CONSTRAINT chk_meal_plan_items_workout_relation CHECK (
        workout_relation IN ('NONE', 'PRE_WORKOUT', 'POST_WORKOUT', 'RECOVERY')
    );

ALTER TABLE meal_plan_items
    ADD CONSTRAINT fk_meal_plan_items_source_ai_request
        FOREIGN KEY (source_ai_request_id) REFERENCES ai_request_history(id) ON DELETE SET NULL;

ALTER TABLE meal_plan_items
    ALTER COLUMN link_state DROP DEFAULT,
    ALTER COLUMN workout_relation DROP DEFAULT;

CREATE INDEX IF NOT EXISTS idx_meal_plan_items_source_ai_request
    ON meal_plan_items(source_ai_request_id);

CREATE TABLE IF NOT EXISTS meal_plan_item_consumptions (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    meal_plan_item_id BIGINT NOT NULL REFERENCES meal_plan_items(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    food_log_id BIGINT REFERENCES food_logs(id) ON DELETE SET NULL,
    recipe_log_id BIGINT REFERENCES recipe_logs(id) ON DELETE SET NULL,
    status VARCHAR(30) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    planned_quantity DOUBLE PRECISION NOT NULL,
    planned_unit VARCHAR(30) NOT NULL,
    consumed_quantity DOUBLE PRECISION,
    consumed_unit VARCHAR(30),
    snapshot_calories DOUBLE PRECISION,
    snapshot_protein DOUBLE PRECISION,
    snapshot_carbs DOUBLE PRECISION,
    snapshot_fat DOUBLE PRECISION,
    snapshot_fiber DOUBLE PRECISION,
    snapshot_sugar DOUBLE PRECISION,
    snapshot_saturated_fat DOUBLE PRECISION,
    snapshot_sodium DOUBLE PRECISION,
    snapshot_potassium DOUBLE PRECISION,
    snapshot_cholesterol DOUBLE PRECISION,
    snapshot_calcium DOUBLE PRECISION,
    snapshot_iron DOUBLE PRECISION,
    snapshot_magnesium DOUBLE PRECISION,
    snapshot_zinc DOUBLE PRECISION,
    snapshot_vitamin_a DOUBLE PRECISION,
    snapshot_vitamin_c DOUBLE PRECISION,
    snapshot_vitamin_d DOUBLE PRECISION,
    snapshot_vitamin_e DOUBLE PRECISION,
    snapshot_vitamin_b12 DOUBLE PRECISION,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_meal_plan_consumption_user_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT chk_meal_plan_consumption_status CHECK (
        status IN ('PLANNED', 'LOGGED', 'PARTIALLY_CONSUMED', 'SKIPPED', 'REPLACED')
    ),
    CONSTRAINT chk_meal_plan_consumption_quantities CHECK (
        planned_quantity > 0
        AND (consumed_quantity IS NULL OR consumed_quantity > 0)
    ),
    CONSTRAINT chk_meal_plan_consumption_log_target CHECK (
        NOT (food_log_id IS NOT NULL AND recipe_log_id IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_meal_plan_consumptions_item_created
    ON meal_plan_item_consumptions(meal_plan_item_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_meal_plan_consumptions_user_created
    ON meal_plan_item_consumptions(user_id, created_at DESC);

ALTER TABLE ai_request_history
    DROP CONSTRAINT IF EXISTS chk_ai_request_history_type;

ALTER TABLE ai_request_history
    ADD CONSTRAINT chk_ai_request_history_type CHECK (request_type IN (
        'VOICE_FOOD_LOG', 'PHOTO_MEAL_LOG', 'AI_RECIPE_GENERATION',
        'AI_NUTRITION_PLAN', 'AI_MEAL_PREPARATION_GUIDE', 'AI_WORKOUT_PLAN',
        'AI_DAILY_INSIGHT', 'AI_WEEKLY_INSIGHT'
    ));

ALTER TABLE subscription_plan_features
    ADD COLUMN IF NOT EXISTS ai_credit_cost INTEGER NOT NULL DEFAULT 1;

ALTER TABLE subscription_plan_features
    ADD CONSTRAINT chk_subscription_plan_feature_ai_credit_cost
        CHECK (ai_credit_cost BETWEEN 1 AND 50);

ALTER TABLE subscription_plan_features
    DROP CONSTRAINT IF EXISTS chk_subscription_plan_feature_feature;

ALTER TABLE subscription_plan_features
    ADD CONSTRAINT chk_subscription_plan_feature_feature CHECK (feature IN (
        'AI_MEAL_DRAFTS', 'AI_WORKOUT_PLANNER', 'AI_RECIPE_GENERATION',
        'AI_NUTRITION_PLAN', 'AI_MEAL_PREPARATION_GUIDE', 'AI_INSIGHTS',
        'HEALTH_INTEGRATION', 'ADVANCED_ANALYTICS', 'AD_FREE', 'CUSTOM_FOOD_LIBRARY'
    ));

ALTER TABLE user_subscription_entitlements
    DROP CONSTRAINT IF EXISTS chk_user_subscription_entitlement_feature;

ALTER TABLE user_subscription_entitlements
    ADD CONSTRAINT chk_user_subscription_entitlement_feature CHECK (feature IN (
        'AI_MEAL_DRAFTS', 'AI_WORKOUT_PLANNER', 'AI_RECIPE_GENERATION',
        'AI_NUTRITION_PLAN', 'AI_MEAL_PREPARATION_GUIDE', 'AI_INSIGHTS',
        'HEALTH_INTEGRATION', 'ADVANCED_ANALYTICS', 'AD_FREE', 'CUSTOM_FOOD_LIBRARY'
    ));

INSERT INTO subscription_plan_features
    (plan_type, feature, enabled, effective_from, updated_at, ai_credit_cost)
VALUES
    ('FREE', 'AI_MEAL_PREPARATION_GUIDE', FALSE, CURRENT_DATE, CURRENT_TIMESTAMP, 1),
    ('PLUS', 'AI_MEAL_PREPARATION_GUIDE', TRUE, CURRENT_DATE, CURRENT_TIMESTAMP, 1),
    ('PRO', 'AI_MEAL_PREPARATION_GUIDE', TRUE, CURRENT_DATE, CURRENT_TIMESTAMP, 1)
ON CONFLICT (plan_type, feature) DO NOTHING;

CREATE TABLE IF NOT EXISTS meal_plan_preparation_guides (
    id BIGSERIAL PRIMARY KEY,
    meal_plan_item_id BIGINT NOT NULL REFERENCES meal_plan_items(id) ON DELETE CASCADE,
    source_ai_request_id BIGINT NOT NULL REFERENCES ai_request_history(id) ON DELETE RESTRICT,
    version INTEGER NOT NULL CHECK (version > 0),
    schema_version VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('ACTIVE', 'REJECTED')),
    guide_payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_preparation_guide_item_version UNIQUE (meal_plan_item_id, version),
    CONSTRAINT uk_preparation_guide_ai_request UNIQUE (source_ai_request_id)
);

CREATE INDEX IF NOT EXISTS idx_preparation_guide_item_latest
    ON meal_plan_preparation_guides(meal_plan_item_id, version DESC);

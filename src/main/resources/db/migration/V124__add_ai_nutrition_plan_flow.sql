ALTER TABLE ai_request_history
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_request_user_type_idempotency
    ON ai_request_history(user_id, request_type, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

ALTER TABLE ai_request_history
    DROP CONSTRAINT IF EXISTS chk_ai_request_history_type;

ALTER TABLE ai_request_history
    ADD CONSTRAINT chk_ai_request_history_type CHECK (request_type IN (
        'VOICE_FOOD_LOG', 'PHOTO_MEAL_LOG', 'AI_RECIPE_GENERATION',
        'AI_NUTRITION_PLAN', 'AI_WORKOUT_PLAN', 'AI_DAILY_INSIGHT', 'AI_WEEKLY_INSIGHT'
    ));

ALTER TABLE ai_request_history
    DROP CONSTRAINT IF EXISTS chk_ai_request_history_status;

ALTER TABLE ai_request_history
    ADD CONSTRAINT chk_ai_request_history_status CHECK (status IN (
        'PROCESSING', 'DRAFT_CREATED', 'CONFIRMED', 'REJECTED', 'FAILED'
    ));

ALTER TABLE subscription_plan_features
    DROP CONSTRAINT IF EXISTS chk_subscription_plan_feature_feature;

ALTER TABLE subscription_plan_features
    ADD CONSTRAINT chk_subscription_plan_feature_feature CHECK (feature IN (
        'AI_MEAL_DRAFTS', 'AI_WORKOUT_PLANNER', 'AI_RECIPE_GENERATION',
        'AI_NUTRITION_PLAN', 'AI_INSIGHTS', 'HEALTH_INTEGRATION',
        'ADVANCED_ANALYTICS', 'AD_FREE', 'CUSTOM_FOOD_LIBRARY'
    ));

ALTER TABLE user_subscription_entitlements
    DROP CONSTRAINT IF EXISTS chk_user_subscription_entitlement_feature;

ALTER TABLE user_subscription_entitlements
    ADD CONSTRAINT chk_user_subscription_entitlement_feature CHECK (feature IN (
        'AI_MEAL_DRAFTS', 'AI_WORKOUT_PLANNER', 'AI_RECIPE_GENERATION',
        'AI_NUTRITION_PLAN', 'AI_INSIGHTS', 'HEALTH_INTEGRATION',
        'ADVANCED_ANALYTICS', 'AD_FREE', 'CUSTOM_FOOD_LIBRARY'
    ));

INSERT INTO subscription_plan_features (plan_type, feature, enabled, effective_from, updated_at)
VALUES
    ('FREE', 'AI_NUTRITION_PLAN', TRUE, CURRENT_DATE, CURRENT_TIMESTAMP),
    ('PLUS', 'AI_NUTRITION_PLAN', TRUE, CURRENT_DATE, CURRENT_TIMESTAMP),
    ('PRO', 'AI_NUTRITION_PLAN', TRUE, CURRENT_DATE, CURRENT_TIMESTAMP)
ON CONFLICT (plan_type, feature) DO NOTHING;

INSERT INTO user_subscription_entitlements (
    subscription_id, user_id, feature, enabled, source_plan,
    valid_from, valid_until, created_at, updated_at
)
SELECT
    s.id, s.user_id, 'AI_NUTRITION_PLAN', TRUE, s.plan_type,
    COALESCE(s.start_date, CURRENT_DATE), s.end_date,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM subscriptions s
WHERE s.user_id IS NOT NULL
  AND s.status IN ('ACTIVE', 'TRIALING', 'CANCELED')
  AND (s.end_date IS NULL OR s.end_date >= CURRENT_DATE)
  AND NOT EXISTS (
      SELECT 1
      FROM user_subscription_entitlements e
      WHERE e.subscription_id = s.id
        AND e.feature = 'AI_NUTRITION_PLAN'
        AND e.enabled = TRUE
        AND (e.valid_until IS NULL OR e.valid_until >= CURRENT_DATE)
  );
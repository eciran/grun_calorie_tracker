ALTER TABLE subscription_plan_features
    DROP CONSTRAINT IF EXISTS chk_subscription_plan_feature_feature;

ALTER TABLE subscription_plan_features
    ADD CONSTRAINT chk_subscription_plan_feature_feature
    CHECK (feature IN (
        'BARCODE_SCANNER','MANUAL_FOOD_LOGGING','FOOD_DIARY','WEIGHT_PROGRESS',
        'WATER_TRACKING','WORKOUT_LOGGING','SAVED_MEAL_TEMPLATES','RECIPE_BUILDER',
        'PUBLIC_RECIPE_LIBRARY','NEXT_MEAL_SUGGESTIONS','ADVANCED_MACRO_TARGETS',
        'MICRONUTRIENT_DETAILS','MICRONUTRIENT_ANALYTICS','DATA_EXPORT','FASTING_BASIC',
        'FASTING_ADVANCED','AI_MEAL_DRAFTS','AI_WORKOUT_PLANNER','AI_RECIPE_GENERATION',
        'AI_MEAL_PREPARATION_GUIDE','AI_NUTRITION_PLAN','AI_INSIGHTS',
        'HEALTH_INTEGRATION','ADVANCED_ANALYTICS','AD_FREE','CUSTOM_FOOD_LIBRARY'
    ));

ALTER TABLE user_subscription_entitlements
    DROP CONSTRAINT IF EXISTS chk_user_subscription_entitlement_feature;

ALTER TABLE user_subscription_entitlements
    ADD CONSTRAINT chk_user_subscription_entitlement_feature
    CHECK (feature IN (
        'BARCODE_SCANNER','MANUAL_FOOD_LOGGING','FOOD_DIARY','WEIGHT_PROGRESS',
        'WATER_TRACKING','WORKOUT_LOGGING','SAVED_MEAL_TEMPLATES','RECIPE_BUILDER',
        'PUBLIC_RECIPE_LIBRARY','NEXT_MEAL_SUGGESTIONS','ADVANCED_MACRO_TARGETS',
        'MICRONUTRIENT_DETAILS','MICRONUTRIENT_ANALYTICS','DATA_EXPORT','FASTING_BASIC',
        'FASTING_ADVANCED','AI_MEAL_DRAFTS','AI_WORKOUT_PLANNER','AI_RECIPE_GENERATION',
        'AI_MEAL_PREPARATION_GUIDE','AI_NUTRITION_PLAN','AI_INSIGHTS',
        'HEALTH_INTEGRATION','ADVANCED_ANALYTICS','AD_FREE','CUSTOM_FOOD_LIBRARY'
    ));

INSERT INTO subscription_plan_features (
    plan_type,
    feature,
    enabled,
    effective_from,
    updated_at
)
VALUES
    ('FREE', 'MICRONUTRIENT_ANALYTICS', FALSE, CURRENT_DATE, CURRENT_TIMESTAMP),
    ('PLUS', 'MICRONUTRIENT_ANALYTICS', FALSE, CURRENT_DATE, CURRENT_TIMESTAMP),
    ('PRO', 'MICRONUTRIENT_ANALYTICS', TRUE, CURRENT_DATE, CURRENT_TIMESTAMP)
ON CONFLICT (plan_type, feature)
DO UPDATE SET
    enabled = EXCLUDED.enabled,
    effective_from = EXCLUDED.effective_from,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO user_subscription_entitlements (
    subscription_id,
    user_id,
    feature,
    enabled,
    source_plan,
    valid_from,
    valid_until,
    created_at,
    updated_at
)
SELECT subscription.id,
       subscription.user_id,
       'MICRONUTRIENT_ANALYTICS',
       TRUE,
       subscription.plan_type,
       COALESCE(subscription.start_date, CURRENT_DATE),
       subscription.end_date,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM subscriptions subscription
WHERE subscription.plan_type = 'PRO'
  AND subscription.user_id IS NOT NULL
  AND subscription.status IN ('ACTIVE', 'TRIALING', 'CANCELED')
  AND (subscription.end_date IS NULL OR subscription.end_date >= CURRENT_DATE)
  AND NOT EXISTS (
      SELECT 1
      FROM user_subscription_entitlements entitlement
      WHERE entitlement.subscription_id = subscription.id
        AND entitlement.feature = 'MICRONUTRIENT_ANALYTICS'
        AND entitlement.enabled = TRUE
        AND (entitlement.valid_until IS NULL OR entitlement.valid_until >= CURRENT_DATE)
  );
INSERT INTO subscription_plan_features (
    plan_type,
    feature,
    enabled,
    effective_from,
    updated_at
)
SELECT plan_type,
       feature,
       plan_type = 'PRO',
       CURRENT_DATE,
       CURRENT_TIMESTAMP
FROM (VALUES ('FREE'), ('PLUS'), ('PRO')) AS plans(plan_type)
CROSS JOIN (VALUES ('AI_NUTRITION_PLAN'), ('AI_WORKOUT_PLANNER')) AS features(feature)
ON CONFLICT (plan_type, feature)
DO UPDATE SET
    enabled = EXCLUDED.enabled,
    effective_from = EXCLUDED.effective_from,
    updated_at = CURRENT_TIMESTAMP;

UPDATE user_subscription_entitlements entitlement
SET enabled = FALSE,
    valid_until = CURRENT_DATE - 1,
    updated_at = CURRENT_TIMESTAMP
FROM subscriptions subscription
WHERE entitlement.subscription_id = subscription.id
  AND entitlement.feature IN ('AI_NUTRITION_PLAN', 'AI_WORKOUT_PLANNER')
  AND subscription.plan_type <> 'PRO'
  AND entitlement.enabled = TRUE;

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
       feature.feature,
       TRUE,
       subscription.plan_type,
       COALESCE(subscription.start_date, CURRENT_DATE),
       subscription.end_date,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM subscriptions subscription
CROSS JOIN (VALUES ('AI_NUTRITION_PLAN'), ('AI_WORKOUT_PLANNER')) AS feature(feature)
WHERE subscription.plan_type = 'PRO'
  AND subscription.user_id IS NOT NULL
  AND subscription.status IN ('ACTIVE', 'TRIALING', 'CANCELED')
  AND (subscription.end_date IS NULL OR subscription.end_date >= CURRENT_DATE)
  AND NOT EXISTS (
      SELECT 1
      FROM user_subscription_entitlements entitlement
      WHERE entitlement.subscription_id = subscription.id
        AND entitlement.feature = feature.feature
        AND entitlement.enabled = TRUE
        AND (entitlement.valid_until IS NULL OR entitlement.valid_until >= CURRENT_DATE)
  );

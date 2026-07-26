INSERT INTO subscription_plan_features (
    plan_type,
    feature,
    enabled,
    effective_from,
    updated_at
)
VALUES
    ('FREE', 'PUBLIC_RECIPE_LIBRARY', FALSE, CURRENT_DATE, CURRENT_TIMESTAMP),
    ('PLUS', 'PUBLIC_RECIPE_LIBRARY', TRUE, CURRENT_DATE, CURRENT_TIMESTAMP),
    ('PRO', 'PUBLIC_RECIPE_LIBRARY', TRUE, CURRENT_DATE, CURRENT_TIMESTAMP)
ON CONFLICT (plan_type, feature)
DO UPDATE SET
    enabled = EXCLUDED.enabled,
    effective_from = CURRENT_DATE,
    updated_at = CURRENT_TIMESTAMP;

UPDATE user_subscription_entitlements entitlement
SET enabled = FALSE,
    valid_until = CURRENT_DATE - 1,
    updated_at = CURRENT_TIMESTAMP
FROM subscriptions subscription
WHERE entitlement.subscription_id = subscription.id
  AND subscription.plan_type = 'FREE'
  AND entitlement.feature = 'PUBLIC_RECIPE_LIBRARY'
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
       'PUBLIC_RECIPE_LIBRARY',
       TRUE,
       subscription.plan_type,
       COALESCE(subscription.start_date, CURRENT_DATE),
       subscription.end_date,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM subscriptions subscription
WHERE subscription.plan_type IN ('PLUS', 'PRO')
  AND subscription.user_id IS NOT NULL
  AND subscription.status IN ('ACTIVE', 'TRIALING', 'CANCELED')
  AND (subscription.end_date IS NULL OR subscription.end_date >= CURRENT_DATE)
  AND NOT EXISTS (
      SELECT 1
      FROM user_subscription_entitlements entitlement
      WHERE entitlement.subscription_id = subscription.id
        AND entitlement.feature = 'PUBLIC_RECIPE_LIBRARY'
        AND entitlement.enabled = TRUE
        AND (entitlement.valid_until IS NULL OR entitlement.valid_until >= CURRENT_DATE)
  );
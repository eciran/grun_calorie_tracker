UPDATE subscription_plan_features
SET enabled = FALSE,
    effective_from = CURRENT_DATE,
    updated_at = CURRENT_TIMESTAMP
WHERE plan_type IN ('FREE', 'PLUS')
  AND feature = 'GROCERY_LIST';

UPDATE subscription_plan_features
SET enabled = TRUE,
    effective_from = CURRENT_DATE,
    updated_at = CURRENT_TIMESTAMP
WHERE plan_type = 'PRO'
  AND feature = 'GROCERY_LIST';

DELETE FROM user_subscription_entitlements entitlement
USING subscriptions subscription
WHERE entitlement.subscription_id = subscription.id
  AND entitlement.feature = 'GROCERY_LIST'
  AND subscription.plan_type IN ('FREE', 'PLUS');

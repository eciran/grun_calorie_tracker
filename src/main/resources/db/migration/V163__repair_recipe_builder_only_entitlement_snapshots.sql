-- V162 enabled RECIPE_BUILDER for every plan and backfilled active subscriptions.
-- Subscriptions without an existing entitlement snapshot consequently received a
-- one-feature snapshot. Effective access treats any active snapshot as complete,
-- so those users lost every feature except RECIPE_BUILDER. Expire only snapshots
-- whose sole active feature is RECIPE_BUILDER; they will correctly fall back to
-- the plan feature matrix on the next resolved-access request.
WITH recipe_only_snapshots AS (
    SELECT entitlement.subscription_id
    FROM user_subscription_entitlements entitlement
    WHERE entitlement.enabled = TRUE
      AND entitlement.valid_from <= CURRENT_DATE
      AND (entitlement.valid_until IS NULL OR entitlement.valid_until >= CURRENT_DATE)
    GROUP BY entitlement.subscription_id
    HAVING COUNT(*) = 1
       AND MIN(entitlement.feature) = 'RECIPE_BUILDER'
       AND MAX(entitlement.feature) = 'RECIPE_BUILDER'
)
UPDATE user_subscription_entitlements entitlement
SET enabled = FALSE,
    valid_until = CURRENT_DATE - 1,
    updated_at = CURRENT_TIMESTAMP
FROM recipe_only_snapshots snapshot
WHERE entitlement.subscription_id = snapshot.subscription_id
  AND entitlement.feature = 'RECIPE_BUILDER'
  AND entitlement.enabled = TRUE
  AND entitlement.valid_from <= CURRENT_DATE
  AND (entitlement.valid_until IS NULL OR entitlement.valid_until >= CURRENT_DATE);
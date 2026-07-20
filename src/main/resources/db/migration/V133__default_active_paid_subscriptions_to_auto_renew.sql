UPDATE subscriptions
SET auto_renew = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE plan_type IN ('PLUS', 'PRO')
  AND status IN ('ACTIVE', 'TRIALING')
  AND COALESCE(auto_renew, FALSE) = FALSE;
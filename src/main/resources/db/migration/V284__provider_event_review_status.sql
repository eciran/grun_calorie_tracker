UPDATE subscription_provider_events
SET status = 'REQUIRES_REVIEW',
    next_attempt_at = NULL
WHERE status = 'FAILED'
  AND processing_error LIKE '%SUBSCRIPTION_OWNERSHIP_CONFLICT%';

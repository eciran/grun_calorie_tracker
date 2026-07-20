ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS ai_addon_used INTEGER NOT NULL DEFAULT 0;

UPDATE subscriptions
SET ai_addon_used = CASE
    WHEN COALESCE(ai_addon_quota, 0) > 0
        AND (ai_addon_quota_expires_at IS NULL OR ai_addon_quota_expires_at >= CURRENT_DATE)
        THEN LEAST(COALESCE(ai_addon_quota, 0), COALESCE(ai_used_this_period, 0))
    ELSE 0
END;

ALTER TABLE subscriptions
    ADD CONSTRAINT chk_subscriptions_ai_addon_used_non_negative
        CHECK (ai_addon_used >= 0);

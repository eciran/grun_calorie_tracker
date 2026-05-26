ALTER TABLE subscriptions
    ALTER COLUMN plan_type TYPE VARCHAR(50);

ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS status VARCHAR(50),
    ADD COLUMN IF NOT EXISTS billing_period VARCHAR(50),
    ADD COLUMN IF NOT EXISTS ai_monthly_quota INTEGER,
    ADD COLUMN IF NOT EXISTS ai_addon_quota INTEGER,
    ADD COLUMN IF NOT EXISTS ai_addon_quota_expires_at DATE,
    ADD COLUMN IF NOT EXISTS ai_used_this_period INTEGER,
    ADD COLUMN IF NOT EXISTS ai_quota_period_start_date DATE,
    ADD COLUMN IF NOT EXISTS ai_quota_period_end_date DATE,
    ADD COLUMN IF NOT EXISTS auto_renew BOOLEAN,
    ADD COLUMN IF NOT EXISTS provider VARCHAR(100),
    ADD COLUMN IF NOT EXISTS provider_subscription_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

UPDATE subscriptions
SET plan_type = CASE
        WHEN plan_type IS NULL THEN 'FREE'
        WHEN UPPER(plan_type) = 'STANDARD' THEN 'FREE'
        ELSE UPPER(plan_type)
    END,
    status = COALESCE(status, 'ACTIVE'),
    billing_period = COALESCE(billing_period, 'NONE'),
    ai_monthly_quota = COALESCE(ai_monthly_quota, CASE
        WHEN UPPER(plan_type) = 'PRO' THEN 100
        WHEN UPPER(plan_type) = 'PLUS' THEN 15
        ELSE 3
    END),
    ai_addon_quota = COALESCE(ai_addon_quota, 0),
    ai_used_this_period = COALESCE(ai_used_this_period, 0),
    ai_quota_period_start_date = COALESCE(ai_quota_period_start_date, start_date, CURRENT_DATE),
    ai_quota_period_end_date = COALESCE(
        ai_quota_period_end_date,
        CASE
            WHEN end_date IS NOT NULL THEN end_date
            WHEN COALESCE(billing_period, 'NONE') = 'MONTHLY' THEN (COALESCE(start_date, CURRENT_DATE) + INTERVAL '1 month' - INTERVAL '1 day')::date
            WHEN COALESCE(billing_period, 'NONE') = 'YEARLY' THEN (COALESCE(start_date, CURRENT_DATE) + INTERVAL '1 year' - INTERVAL '1 day')::date
            ELSE NULL
        END
    ),
    auto_renew = COALESCE(auto_renew, false),
    updated_at = COALESCE(updated_at, CURRENT_TIMESTAMP);

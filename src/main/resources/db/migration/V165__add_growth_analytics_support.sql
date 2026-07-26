ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_users_email_verified_at
    ON users (email_verified_at)
    WHERE email_verified_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_users_growth_dimensions
    ON users (created_at, market_region, preferred_language)
    WHERE created_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_onboarding_drafts_completed_user
    ON onboarding_drafts (completed_at, user_id)
    WHERE completed_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_food_logs_user_log_date
    ON food_logs (user_id, log_date);

CREATE INDEX IF NOT EXISTS idx_product_analytics_user_type_created
    ON product_analytics_events (user_id, event_type, created_at);

CREATE INDEX IF NOT EXISTS idx_subscriptions_user_plan_status_start
    ON subscriptions (user_id, plan_type, status, start_date);

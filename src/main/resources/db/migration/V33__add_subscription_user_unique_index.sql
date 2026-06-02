CREATE UNIQUE INDEX IF NOT EXISTS uq_subscriptions_user_id
    ON subscriptions(user_id)
    WHERE user_id IS NOT NULL;

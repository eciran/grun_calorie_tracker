CREATE INDEX IF NOT EXISTS idx_user_favorites_user_created
    ON user_favorites (user_id, created_at DESC);

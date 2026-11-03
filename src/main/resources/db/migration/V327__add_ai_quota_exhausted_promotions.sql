CREATE TABLE ai_quota_promotion_policy (
    id BIGINT PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    minimum_interval_hours INTEGER NOT NULL DEFAULT 12 CHECK (minimum_interval_hours BETWEEN 1 AND 720),
    max_impressions_24h INTEGER NOT NULL DEFAULT 2 CHECK (max_impressions_24h BETWEEN 1 AND 24),
    dismiss_cooldown_hours INTEGER NOT NULL DEFAULT 12 CHECK (dismiss_cooldown_hours BETWEEN 1 AND 720),
    rollout_percentage INTEGER NOT NULL DEFAULT 0 CHECK (rollout_percentage BETWEEN 0 AND 100),
    campaign_version BIGINT NOT NULL DEFAULT 1,
    change_reason VARCHAR(500) NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

INSERT INTO ai_quota_promotion_policy
    (id, version, enabled, minimum_interval_hours, max_impressions_24h, dismiss_cooldown_hours,
     rollout_percentage, campaign_version, change_reason, updated_by, updated_at)
VALUES (1, 0, FALSE, 12, 2, 12, 0, 1, 'Safe disabled default', 'system', CURRENT_TIMESTAMP);

CREATE TABLE ai_quota_promotion_reservations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    reservation_token VARCHAR(64) NOT NULL UNIQUE,
    session_id VARCHAR(100) NOT NULL,
    campaign_version BIGINT NOT NULL,
    reserved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    impression_at TIMESTAMP WITH TIME ZONE,
    dismissed_at TIMESTAMP WITH TIME ZONE,
    cta_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_ai_quota_promotion_user_impressions
    ON ai_quota_promotion_reservations(user_id, impression_at DESC);
CREATE INDEX idx_ai_quota_promotion_active_reservations
    ON ai_quota_promotion_reservations(user_id, expires_at DESC);

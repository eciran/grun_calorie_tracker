CREATE TABLE IF NOT EXISTS subscription_plan_features (
    id BIGSERIAL PRIMARY KEY,
    plan_type VARCHAR(30) NOT NULL,
    feature VARCHAR(60) NOT NULL,
    enabled BOOLEAN NOT NULL,
    effective_from DATE NOT NULL DEFAULT CURRENT_DATE,
    updated_at TIMESTAMP,
    CONSTRAINT uk_subscription_plan_feature UNIQUE (plan_type, feature),
    CONSTRAINT chk_subscription_plan_feature_plan CHECK (plan_type IN ('FREE', 'PLUS', 'PRO')),
    CONSTRAINT chk_subscription_plan_feature_feature CHECK (feature IN (
        'AI_WORKOUT_PLANNER',
        'HEALTH_INTEGRATION',
        'ADVANCED_ANALYTICS',
        'AD_FREE',
        'CUSTOM_FOOD_LIBRARY'
    ))
);

CREATE TABLE IF NOT EXISTS user_subscription_entitlements (
    id BIGSERIAL PRIMARY KEY,
    subscription_id BIGINT NOT NULL REFERENCES subscriptions(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    feature VARCHAR(60) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    source_plan VARCHAR(30) NOT NULL,
    valid_from DATE NOT NULL,
    valid_until DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT chk_user_subscription_entitlement_plan CHECK (source_plan IN ('FREE', 'PLUS', 'PRO')),
    CONSTRAINT chk_user_subscription_entitlement_feature CHECK (feature IN (
        'AI_WORKOUT_PLANNER',
        'HEALTH_INTEGRATION',
        'ADVANCED_ANALYTICS',
        'AD_FREE',
        'CUSTOM_FOOD_LIBRARY'
    ))
);

CREATE INDEX IF NOT EXISTS idx_subscription_plan_features_plan
    ON subscription_plan_features(plan_type);

CREATE INDEX IF NOT EXISTS idx_user_subscription_entitlements_subscription_feature
    ON user_subscription_entitlements(subscription_id, feature, valid_from, valid_until);

CREATE INDEX IF NOT EXISTS idx_user_subscription_entitlements_user
    ON user_subscription_entitlements(user_id);

INSERT INTO subscription_plan_features (plan_type, feature, enabled, effective_from, updated_at)
VALUES
    ('FREE', 'AI_WORKOUT_PLANNER', TRUE, CURRENT_DATE, CURRENT_TIMESTAMP),
    ('FREE', 'HEALTH_INTEGRATION', FALSE, CURRENT_DATE, CURRENT_TIMESTAMP),
    ('FREE', 'ADVANCED_ANALYTICS', FALSE, CURRENT_DATE, CURRENT_TIMESTAMP),
    ('FREE', 'AD_FREE', FALSE, CURRENT_DATE, CURRENT_TIMESTAMP),
    ('FREE', 'CUSTOM_FOOD_LIBRARY', TRUE, CURRENT_DATE, CURRENT_TIMESTAMP),

    ('PLUS', 'AI_WORKOUT_PLANNER', TRUE, CURRENT_DATE, CURRENT_TIMESTAMP),
    ('PLUS', 'HEALTH_INTEGRATION', TRUE, CURRENT_DATE, CURRENT_TIMESTAMP),
    ('PLUS', 'ADVANCED_ANALYTICS', TRUE, CURRENT_DATE, CURRENT_TIMESTAMP),
    ('PLUS', 'AD_FREE', FALSE, CURRENT_DATE, CURRENT_TIMESTAMP),
    ('PLUS', 'CUSTOM_FOOD_LIBRARY', TRUE, CURRENT_DATE, CURRENT_TIMESTAMP),

    ('PRO', 'AI_WORKOUT_PLANNER', TRUE, CURRENT_DATE, CURRENT_TIMESTAMP),
    ('PRO', 'HEALTH_INTEGRATION', TRUE, CURRENT_DATE, CURRENT_TIMESTAMP),
    ('PRO', 'ADVANCED_ANALYTICS', TRUE, CURRENT_DATE, CURRENT_TIMESTAMP),
    ('PRO', 'AD_FREE', TRUE, CURRENT_DATE, CURRENT_TIMESTAMP),
    ('PRO', 'CUSTOM_FOOD_LIBRARY', TRUE, CURRENT_DATE, CURRENT_TIMESTAMP)
ON CONFLICT (plan_type, feature) DO NOTHING;

INSERT INTO user_subscription_entitlements (
    subscription_id,
    user_id,
    feature,
    enabled,
    source_plan,
    valid_from,
    valid_until,
    created_at,
    updated_at
)
SELECT
    s.id,
    s.user_id,
    f.feature,
    TRUE,
    s.plan_type,
    COALESCE(s.start_date, CURRENT_DATE),
    s.end_date,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM subscriptions s
JOIN subscription_plan_features f
    ON f.plan_type = s.plan_type
   AND f.enabled = TRUE
WHERE s.user_id IS NOT NULL
  AND s.status IN ('ACTIVE', 'TRIALING', 'CANCELED')
  AND (s.end_date IS NULL OR s.end_date >= CURRENT_DATE);

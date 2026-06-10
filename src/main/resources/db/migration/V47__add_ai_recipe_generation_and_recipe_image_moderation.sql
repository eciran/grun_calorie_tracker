ALTER TABLE ai_request_history
    DROP CONSTRAINT IF EXISTS chk_ai_request_history_type;

ALTER TABLE ai_request_history
    ADD CONSTRAINT chk_ai_request_history_type CHECK (request_type IN (
        'VOICE_FOOD_LOG',
        'PHOTO_MEAL_LOG',
        'AI_RECIPE_GENERATION'
    ));

ALTER TABLE recipes
    ADD COLUMN IF NOT EXISTS image_source VARCHAR(40),
    ADD COLUMN IF NOT EXISTS image_status VARCHAR(40),
    ADD COLUMN IF NOT EXISTS image_review_note TEXT,
    ADD COLUMN IF NOT EXISTS image_reviewed_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS image_reviewed_at TIMESTAMP;

UPDATE recipes
SET image_source = 'USER_UPLOAD',
    image_status = 'NEEDS_REVIEW'
WHERE image_url IS NOT NULL
  AND image_url <> ''
  AND image_status IS NULL;

ALTER TABLE admin_action_audits
    DROP CONSTRAINT IF EXISTS chk_admin_action_audits_action_type;

ALTER TABLE admin_action_audits
    ADD CONSTRAINT chk_admin_action_audits_action_type CHECK (action_type IN (
        'SUBSCRIPTION_UPDATE',
        'AI_QUOTA_RESET',
        'AI_QUOTA_ADDON_GRANT',
        'AI_QUOTA_REFUND',
        'SUBSCRIPTION_FEATURE_UPDATE',
        'RETENTION_POLICY_UPDATE',
        'RECIPE_REVIEW_UPDATE'
    ));

ALTER TABLE admin_action_audits
    DROP CONSTRAINT IF EXISTS chk_admin_action_audits_target_type;

ALTER TABLE admin_action_audits
    ADD CONSTRAINT chk_admin_action_audits_target_type CHECK (target_type IN (
        'USER_SUBSCRIPTION',
        'AI_REQUEST',
        'SUBSCRIPTION_FEATURE',
        'RETENTION_POLICY',
        'RECIPE'
    ));

ALTER TABLE subscription_plan_features
    DROP CONSTRAINT IF EXISTS chk_subscription_plan_feature_feature;

ALTER TABLE subscription_plan_features
    ADD CONSTRAINT chk_subscription_plan_feature_feature CHECK (feature IN (
        'AI_WORKOUT_PLANNER',
        'AI_RECIPE_GENERATION',
        'HEALTH_INTEGRATION',
        'ADVANCED_ANALYTICS',
        'AD_FREE',
        'CUSTOM_FOOD_LIBRARY'
    ));

ALTER TABLE user_subscription_entitlements
    DROP CONSTRAINT IF EXISTS chk_user_subscription_entitlement_feature;

ALTER TABLE user_subscription_entitlements
    ADD CONSTRAINT chk_user_subscription_entitlement_feature CHECK (feature IN (
        'AI_WORKOUT_PLANNER',
        'AI_RECIPE_GENERATION',
        'HEALTH_INTEGRATION',
        'ADVANCED_ANALYTICS',
        'AD_FREE',
        'CUSTOM_FOOD_LIBRARY'
    ));

INSERT INTO subscription_plan_features (plan_type, feature, enabled, effective_from, updated_at)
VALUES
    ('FREE', 'AI_RECIPE_GENERATION', TRUE, CURRENT_DATE, CURRENT_TIMESTAMP),
    ('PLUS', 'AI_RECIPE_GENERATION', TRUE, CURRENT_DATE, CURRENT_TIMESTAMP),
    ('PRO', 'AI_RECIPE_GENERATION', TRUE, CURRENT_DATE, CURRENT_TIMESTAMP)
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
    'AI_RECIPE_GENERATION',
    TRUE,
    s.plan_type,
    COALESCE(s.start_date, CURRENT_DATE),
    s.end_date,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM subscriptions s
JOIN subscription_plan_features f
    ON f.plan_type = s.plan_type
   AND f.feature = 'AI_RECIPE_GENERATION'
   AND f.enabled = TRUE
WHERE s.user_id IS NOT NULL
  AND s.status IN ('ACTIVE', 'TRIALING', 'CANCELED')
  AND (s.end_date IS NULL OR s.end_date >= CURRENT_DATE)
  AND NOT EXISTS (
      SELECT 1
      FROM user_subscription_entitlements e
      WHERE e.subscription_id = s.id
        AND e.feature = 'AI_RECIPE_GENERATION'
        AND e.enabled = TRUE
        AND (e.valid_until IS NULL OR e.valid_until >= CURRENT_DATE)
  );

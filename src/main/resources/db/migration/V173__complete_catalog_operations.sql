ALTER TABLE exercise_items
    ADD COLUMN IF NOT EXISTS technique_review_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS technique_review_note VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS technique_reviewed_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS technique_reviewed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS source_name VARCHAR(160),
    ADD COLUMN IF NOT EXISTS source_url VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS license_name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS license_url VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS source_last_refreshed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS review_assignee VARCHAR(255),
    ADD COLUMN IF NOT EXISTS review_due_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS review_claimed_at TIMESTAMP;

ALTER TABLE food_items
    ADD COLUMN IF NOT EXISTS review_assignee VARCHAR(255),
    ADD COLUMN IF NOT EXISTS review_due_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS review_claimed_at TIMESTAMP;

ALTER TABLE recipes
    ADD COLUMN IF NOT EXISTS review_assignee VARCHAR(255),
    ADD COLUMN IF NOT EXISTS review_due_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS review_claimed_at TIMESTAMP;

ALTER TABLE exercise_items DROP CONSTRAINT IF EXISTS chk_exercise_technique_review_status;
ALTER TABLE exercise_items
    ADD CONSTRAINT chk_exercise_technique_review_status
        CHECK (technique_review_status IN ('PENDING', 'IN_REVIEW', 'APPROVED', 'REJECTED'));

CREATE INDEX IF NOT EXISTS idx_exercise_items_technique_review
    ON exercise_items (technique_review_status, active, review_due_at);
CREATE INDEX IF NOT EXISTS idx_food_items_review_assignment
    ON food_items (review_assignee, review_due_at);
CREATE INDEX IF NOT EXISTS idx_recipes_review_assignment
    ON recipes (review_assignee, review_due_at);

ALTER TABLE admin_action_audits DROP CONSTRAINT IF EXISTS chk_admin_action_audits_action_type;
ALTER TABLE admin_action_audits ADD CONSTRAINT chk_admin_action_audits_action_type
    CHECK (action_type IN (
        'SUBSCRIPTION_UPDATE', 'AI_QUOTA_RESET', 'AI_QUOTA_ADDON_GRANT',
        'AI_QUOTA_REFUND', 'AI_REQUEST_INSPECT', 'AI_CREDIT_PRICING_UPDATE',
        'SUBSCRIPTION_FEATURE_UPDATE', 'SUBSCRIPTION_ENTITLEMENT_MATRIX_APPLY',
        'RETENTION_POLICY_UPDATE', 'RECIPE_CREATE', 'RECIPE_REVIEW_UPDATE',
        'USER_STATUS_UPDATE', 'USER_SUPPORT_NOTE_CREATE', 'USER_SESSION_REVOKE',
        'PRODUCT_QUALITY_AI_SETTINGS_UPDATE', 'NOTIFICATION_CAMPAIGN_CREATE',
        'NOTIFICATION_CAMPAIGN_UPDATE', 'NOTIFICATION_CAMPAIGN_SCHEDULE',
        'NOTIFICATION_CAMPAIGN_CANCEL', 'PROMO_CREATE', 'PROMO_UPDATE',
        'PROMO_ACTIVATE', 'PROMO_DEACTIVATE', 'PROMO_RECONCILE',
        'PROMO_REDEMPTION_RECORD', 'ADMIN_ROLE_UPDATE', 'ADMIN_STATUS_UPDATE',
        'ADMIN_MFA_STATUS_UPDATE', 'CATALOG_EXERCISE_CREATE',
        'CATALOG_EXERCISE_UPDATE', 'CATALOG_EXERCISE_REVIEW',
        'CATALOG_REVIEW_ASSIGNMENT'
    ));

ALTER TABLE admin_action_audits DROP CONSTRAINT IF EXISTS chk_admin_action_audits_target_type;
ALTER TABLE admin_action_audits ADD CONSTRAINT chk_admin_action_audits_target_type
    CHECK (target_type IN (
        'USER_SUBSCRIPTION', 'AI_REQUEST', 'AI_CREDIT_PRICING',
        'SUBSCRIPTION_FEATURE', 'RETENTION_POLICY', 'RECIPE', 'USER_ACCOUNT',
        'PRODUCT_QUALITY_AI_SETTINGS', 'NOTIFICATION_CAMPAIGN', 'PROMOTION',
        'ADMIN_ACCOUNT', 'EXERCISE_ITEM', 'CATALOG_REVIEW_ITEM'
    ));

ALTER TABLE promo_codes
    ALTER COLUMN eligibility_rule TYPE VARCHAR(40),
    ALTER COLUMN eligibility_rule SET DEFAULT 'ALL_USERS';

UPDATE promo_codes
SET eligibility_rule = 'ALL_USERS'
WHERE eligibility_rule IS NULL
   OR eligibility_rule NOT IN ('ALL_USERS', 'NO_PRIOR_PROMO_REDEMPTION', 'FIRST_PAID_PURCHASE', 'LAPSED_SUBSCRIBER', 'ADMIN_SUPPORT_ONLY');

ALTER TABLE promo_codes ALTER COLUMN eligibility_rule SET NOT NULL;
ALTER TABLE promo_codes DROP CONSTRAINT IF EXISTS chk_promo_codes_eligibility_rule;
ALTER TABLE promo_codes ADD CONSTRAINT chk_promo_codes_eligibility_rule CHECK (eligibility_rule IN (
    'ALL_USERS', 'NO_PRIOR_PROMO_REDEMPTION', 'FIRST_PAID_PURCHASE', 'LAPSED_SUBSCRIBER', 'ADMIN_SUPPORT_ONLY'
));

ALTER TABLE applied_promos
    ADD COLUMN IF NOT EXISTS duplicate_hits INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_duplicate_at TIMESTAMP;

ALTER TABLE applied_promos DROP CONSTRAINT IF EXISTS uq_applied_promos_provider_event;
ALTER TABLE applied_promos ADD CONSTRAINT uq_applied_promos_provider_event UNIQUE (provider_event_id);

ALTER TABLE subscription_provider_events
    ADD COLUMN IF NOT EXISTS store VARCHAR(40),
    ADD COLUMN IF NOT EXISTS presented_offering_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS purchase_currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS price_amount_minor BIGINT;

CREATE INDEX IF NOT EXISTS idx_promo_codes_provider_match
    ON promo_codes(provider_product_id, provider_offer_id, target_store, status, active, start_at, end_at);
CREATE INDEX IF NOT EXISTS idx_applied_promos_admin_queue
    ON applied_promos(status, applied_at DESC);

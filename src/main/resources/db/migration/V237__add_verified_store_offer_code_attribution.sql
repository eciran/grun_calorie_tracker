ALTER TABLE promo_codes
    ADD COLUMN store_offer_code_required BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE subscription_provider_events
    ADD COLUMN store_offer_code VARCHAR(160);

CREATE INDEX idx_subscription_provider_events_offer_code
    ON subscription_provider_events (store_offer_code)
    WHERE store_offer_code IS NOT NULL;

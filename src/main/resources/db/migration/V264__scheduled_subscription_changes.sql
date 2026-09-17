ALTER TABLE subscriptions
    ADD COLUMN scheduled_product_id VARCHAR(255),
    ADD COLUMN scheduled_change_at TIMESTAMPTZ,
    ADD COLUMN scheduled_event_at TIMESTAMPTZ;

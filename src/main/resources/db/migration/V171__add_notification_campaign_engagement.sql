ALTER TABLE notification_campaigns
    ADD COLUMN opened_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN clicked_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN dismissed_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN converted_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN suppressed_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN frequency_cap_hours INTEGER NOT NULL DEFAULT 24,
    ADD COLUMN frequency_cap_max INTEGER NOT NULL DEFAULT 3;

ALTER TABLE notification_campaigns
    ADD CONSTRAINT chk_notification_campaign_frequency_cap
        CHECK (frequency_cap_hours BETWEEN 1 AND 168 AND frequency_cap_max BETWEEN 1 AND 20);

ALTER TABLE notification_campaign_recipients
    ADD COLUMN opened_at TIMESTAMP,
    ADD COLUMN clicked_at TIMESTAMP,
    ADD COLUMN dismissed_at TIMESTAMP,
    ADD COLUMN converted_at TIMESTAMP,
    ADD COLUMN suppression_reason VARCHAR(160);

ALTER TABLE notification_campaign_recipients
    DROP CONSTRAINT IF EXISTS chk_notification_campaign_recipient_status;

ALTER TABLE notification_campaign_recipients
    ADD CONSTRAINT chk_notification_campaign_recipient_status
        CHECK (status IN ('CREATED', 'DELIVERED', 'SUPPRESSED', 'FAILED'));

CREATE INDEX idx_campaign_recipient_user_created
    ON notification_campaign_recipients (user_id, created_at DESC);

CREATE INDEX idx_campaign_recipient_campaign_status
    ON notification_campaign_recipients (campaign_id, status, processed_at DESC);

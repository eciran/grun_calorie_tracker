ALTER TABLE notification_campaigns
    DROP CONSTRAINT IF EXISTS chk_notification_campaign_channel;

ALTER TABLE notification_campaigns
    ADD CONSTRAINT chk_notification_campaign_channel CHECK (channel IN (
        'IN_APP', 'PUSH', 'IN_APP_AND_PUSH', 'EMAIL', 'EMAIL_AND_IN_APP', 'EMAIL_PUSH_IN_APP'
    )),
    ADD COLUMN email_template_id BIGINT,
    ADD COLUMN email_sent_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN email_failed_count BIGINT NOT NULL DEFAULT 0;

ALTER TABLE notification_campaign_recipients
    ADD COLUMN email_attempted INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN email_sent INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN email_failed INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN email_message_id VARCHAR(255);

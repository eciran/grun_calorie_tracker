ALTER TABLE notification_definitions
    ADD COLUMN classification VARCHAR(40) NOT NULL DEFAULT 'USER_REQUESTED_RESULT',
    ADD COLUMN parameter_schema_json TEXT NOT NULL DEFAULT '{}';

ALTER TABLE notification_definitions
    ADD CONSTRAINT chk_notification_definition_classification CHECK (classification IN (
        'TRANSACTIONAL_ACCOUNT', 'USER_REQUESTED_RESULT', 'BEHAVIOR_REMINDER',
        'MARKETING', 'INTERNAL_OPERATIONAL'
    ));

UPDATE notification_definitions
SET classification = 'BEHAVIOR_REMINDER'
WHERE notification_key IN (
    'fasting_reminder', 'step_reminder', 'water_reminder',
    'meal_reminder_breakfast', 'meal_reminder_lunch', 'meal_reminder_dinner',
    'meal_reminder_dinner_kcal', 'meal_reminder_daily_catchup'
);

UPDATE notification_definitions
SET classification = 'TRANSACTIONAL_ACCOUNT'
WHERE notification_key IN ('subscription', 'ai_quota_refund_approved', 'ai_quota_refund_rejected');

UPDATE notification_definitions
SET classification = 'INTERNAL_OPERATIONAL'
WHERE notification_key IN (
    'ai_rejection_alert', 'system_alert', 'subscription_provider_alert', 'admin_security_alert'
);

INSERT INTO notification_definitions (
    notification_key, display_name, description, enabled, protected_definition, channel,
    classification, parameter_schema_json, severity, target_route,
    title_en, message_en, title_tr, message_tr,
    created_by, updated_by, created_at, updated_at
) VALUES
    ('subscription_started', 'Subscription started', 'Successful initial subscription or trial start.', TRUE, TRUE,
     'IN_APP_AND_PUSH', 'TRANSACTIONAL_ACCOUNT', '{"allowed":["planName","periodEndDate"]}', 'INFO', 'manage-subscription',
     'Your subscription is ready', 'Your {planName} subscription is active. You can review its details any time.',
     'Aboneliğin hazır', '{planName} aboneliğin aktif. Ayrıntılarını dilediğin zaman gözden geçirebilirsin.',
     'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('subscription_renewed', 'Subscription renewed', 'Successful recurring subscription renewal.', TRUE, TRUE,
     'IN_APP', 'TRANSACTIONAL_ACCOUNT', '{"allowed":["planName","periodEndDate"]}', 'INFO', 'manage-subscription',
     'Subscription renewed', 'Your {planName} subscription has been renewed. Your new period ends on {periodEndDate}.',
     'Aboneliğin yenilendi', '{planName} aboneliğin yenilendi. Yeni dönemin {periodEndDate} tarihinde sona eriyor.',
     'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('subscription_cancelled', 'Subscription renewal cancelled', 'Auto-renewal was disabled while access may continue.', TRUE, TRUE,
     'IN_APP_AND_PUSH', 'TRANSACTIONAL_ACCOUNT', '{"allowed":["planName","accessUntilDate"]}', 'WARNING', 'manage-subscription',
     'Renewal has been turned off', 'Your {planName} access continues until {accessUntilDate}. You can manage renewal from your subscription settings.',
     'Yenileme kapatıldı', '{planName} erişimin {accessUntilDate} tarihine kadar devam ediyor. Yenilemeyi abonelik ayarlarından yönetebilirsin.',
     'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('subscription_resumed', 'Subscription renewal resumed', 'Auto-renewal was enabled again.', TRUE, TRUE,
     'IN_APP_AND_PUSH', 'TRANSACTIONAL_ACCOUNT', '{"allowed":["planName","periodEndDate"]}', 'INFO', 'manage-subscription',
     'Renewal is active again', 'Your {planName} subscription will renew as usual. The current period ends on {periodEndDate}.',
     'Yenileme yeniden aktif', '{planName} aboneliğin normal şekilde yenilenecek. Mevcut dönem {periodEndDate} tarihinde sona eriyor.',
     'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('subscription_billing_issue', 'Subscription billing issue', 'The store reported a billing problem.', TRUE, TRUE,
     'IN_APP_AND_PUSH', 'TRANSACTIONAL_ACCOUNT', '{"allowed":["planName","accessUntilDate"]}', 'CRITICAL', 'manage-subscription',
     'Please check your subscription', 'There is a billing issue with your {planName} subscription. Open subscription settings to review it.',
     'Aboneliğini kontrol et', '{planName} aboneliğinde bir ödeme sorunu var. Kontrol etmek için abonelik ayarlarını açabilirsin.',
     'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('subscription_expired', 'Subscription expired', 'Paid subscription access ended.', TRUE, TRUE,
     'IN_APP_AND_PUSH', 'TRANSACTIONAL_ACCOUNT', '{"allowed":["planName","expiredAt"]}', 'WARNING', 'manage-subscription',
     'Subscription period ended', 'Your {planName} subscription ended on {expiredAt}. Your GRUN account remains available on its current access level.',
     'Abonelik dönemin sona erdi', '{planName} aboneliğin {expiredAt} tarihinde sona erdi. GRUN hesabını mevcut erişim düzeyinde kullanmaya devam edebilirsin.',
     'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('subscription_plan_changed', 'Subscription plan changed', 'A subscription product change was scheduled or applied.', TRUE, TRUE,
     'IN_APP_AND_PUSH', 'TRANSACTIONAL_ACCOUNT', '{"allowed":["planName","effectiveDate"]}', 'INFO', 'manage-subscription',
     'Your plan is changing', 'Your plan will change to {planName} on {effectiveDate}.',
     'Planın değişiyor', 'Planın {effectiveDate} tarihinde {planName} olarak değişecek.',
     'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('subscription_paused', 'Subscription pause scheduled', 'A provider-backed subscription pause was scheduled without ending current access early.', TRUE, TRUE,
     'IN_APP_AND_PUSH', 'TRANSACTIONAL_ACCOUNT', '{"allowed":["planName","effectiveDate"]}', 'WARNING', 'manage-subscription',
     'Subscription pause scheduled', 'Your {planName} subscription will pause from {effectiveDate}. Review the details in subscription settings.',
     'Abonelik duraklatma planlandı', '{planName} aboneliğin {effectiveDate} tarihinden itibaren duraklatılacak. Ayrıntıları abonelik ayarlarından inceleyebilirsin.',
     'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('subscription_refunded', 'Subscription refunded', 'A subscription refund or revocation was applied.', TRUE, TRUE,
     'IN_APP_AND_PUSH', 'TRANSACTIONAL_ACCOUNT', '{"allowed":["planName","effectiveDate"]}', 'CRITICAL', 'manage-subscription',
     'Subscription refund update', 'A refund update was applied to your {planName} subscription on {effectiveDate}.',
     'Abonelik iade güncellemesi', '{planName} aboneliğine {effectiveDate} tarihinde bir iade güncellemesi uygulandı.',
     'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ai_addon_purchased', 'AI add-on purchased', 'A verified one-off AI credit purchase was applied.', TRUE, TRUE,
     'IN_APP_AND_PUSH', 'TRANSACTIONAL_ACCOUNT', '{"allowed":["creditAmount","validUntilDate"]}', 'INFO', 'ai-credits',
     'AI credits added', '{creditAmount} AI credits were added to your account. They are valid until {validUntilDate}.',
     'AI kredilerin eklendi', 'Hesabına {creditAmount} AI kredisi eklendi. Kredilerin {validUntilDate} tarihine kadar geçerli.',
     'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (notification_key) DO NOTHING;

CREATE TABLE notification_occurrences (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_type VARCHAR(80) NOT NULL,
    classification VARCHAR(40) NOT NULL,
    definition_key VARCHAR(80) NOT NULL,
    definition_version BIGINT,
    source VARCHAR(80) NOT NULL,
    source_event_id VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    parameters_json TEXT NOT NULL DEFAULT '{}',
    reason_code VARCHAR(80),
    eligible_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    notification_id BIGINT REFERENCES notifications(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_notification_occurrence_source_event
        UNIQUE (source, source_event_id, definition_key, user_id),
    CONSTRAINT chk_notification_occurrence_expiry CHECK (expires_at > eligible_at),
    CONSTRAINT chk_notification_occurrence_classification CHECK (classification IN (
        'TRANSACTIONAL_ACCOUNT', 'USER_REQUESTED_RESULT', 'BEHAVIOR_REMINDER',
        'MARKETING', 'INTERNAL_OPERATIONAL'
    )),
    CONSTRAINT chk_notification_occurrence_status CHECK (status IN (
        'PENDING', 'QUEUED', 'PROCESSING', 'COMPLETED', 'SUPPRESSED',
        'FAILED_FINAL', 'UNKNOWN', 'EXPIRED', 'CANCELLED'
    ))
);

CREATE INDEX idx_notification_occurrence_user_created
    ON notification_occurrences(user_id, created_at DESC);
CREATE INDEX idx_notification_occurrence_source_event
    ON notification_occurrences(source, source_event_id);

CREATE TABLE notification_outbox (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    occurrence_id BIGINT NOT NULL REFERENCES notification_occurrences(id) ON DELETE CASCADE,
    notification_id BIGINT NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lease_owner VARCHAR(100),
    lease_until TIMESTAMP WITH TIME ZONE,
    dispatch_count INTEGER NOT NULL DEFAULT 0,
    last_error_code VARCHAR(80),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_notification_outbox_occurrence_channel UNIQUE (occurrence_id, channel),
    CONSTRAINT chk_notification_outbox_channel CHECK (channel IN ('IN_APP', 'PUSH', 'EMAIL')),
    CONSTRAINT chk_notification_outbox_status CHECK (status IN (
        'PENDING', 'PROCESSING', 'RETRY', 'COMPLETED', 'FAILED_FINAL',
        'UNKNOWN', 'EXPIRED', 'CANCELLED'
    ))
);

CREATE INDEX idx_notification_outbox_due
    ON notification_outbox(status, available_at, expires_at);

CREATE TABLE notification_delivery_attempts (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    outbox_id BIGINT NOT NULL REFERENCES notification_outbox(id) ON DELETE CASCADE,
    push_token_id BIGINT REFERENCES user_push_tokens(id) ON DELETE CASCADE,
    channel VARCHAR(20) NOT NULL,
    provider VARCHAR(20),
    status VARCHAR(30) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    provider_message_id VARCHAR(512),
    error_code VARCHAR(80),
    provider_accepted_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_notification_attempt_outbox_token UNIQUE (outbox_id, push_token_id),
    CONSTRAINT chk_notification_attempt_channel CHECK (channel IN ('IN_APP', 'PUSH', 'EMAIL')),
    CONSTRAINT chk_notification_attempt_status CHECK (status IN (
        'PENDING', 'PROCESSING', 'PROVIDER_ACCEPTED', 'DELIVERED',
        'FAILED_RETRYABLE', 'FAILED_FINAL', 'INVALID_TOKEN', 'UNKNOWN', 'SUPPRESSED'
    ))
);

CREATE INDEX idx_notification_attempt_due
    ON notification_delivery_attempts(status, next_attempt_at);
CREATE INDEX idx_notification_attempt_provider_message
    ON notification_delivery_attempts(provider_message_id)
    WHERE provider_message_id IS NOT NULL;

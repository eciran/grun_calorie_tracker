ALTER TABLE user_consents
    DROP CONSTRAINT IF EXISTS chk_user_consents_type;

ALTER TABLE user_consents
    ADD CONSTRAINT chk_user_consents_type CHECK (consent_type IN (
        'TERMS_OF_SERVICE',
        'PRIVACY_POLICY',
        'MARKETING_EMAIL',
        'HEALTH_DATA_PROCESSING',
        'AI_RECOMMENDATION_PROCESSING',
        'PERSONALIZATION_PROCESSING',
        'APPLE_REFUND_CONSUMPTION_SHARING'
    ));

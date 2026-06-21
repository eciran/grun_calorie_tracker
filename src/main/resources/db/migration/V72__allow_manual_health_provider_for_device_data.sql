ALTER TABLE device_data
    DROP CONSTRAINT IF EXISTS chk_device_data_health_provider;

ALTER TABLE device_data
    ADD CONSTRAINT chk_device_data_health_provider
        CHECK (provider IS NULL OR provider IN ('APPLE_HEALTH', 'GOOGLE_FIT', 'HEALTH_CONNECT', 'MANUAL'));

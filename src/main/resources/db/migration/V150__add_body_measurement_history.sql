CREATE TABLE body_measurements (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recorded_at TIMESTAMP NOT NULL,
    weight_kg DOUBLE PRECISION,
    body_fat_percentage DOUBLE PRECISION,
    waist_cm DOUBLE PRECISION,
    chest_cm DOUBLE PRECISION,
    hip_cm DOUBLE PRECISION,
    upper_arm_cm DOUBLE PRECISION,
    thigh_cm DOUBLE PRECISION,
    neck_cm DOUBLE PRECISION,
    provider VARCHAR(32) NOT NULL,
    external_id VARCHAR(255),
    note VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT chk_body_measurement_provider CHECK (provider IN ('APPLE_HEALTH', 'GOOGLE_FIT', 'HEALTH_CONNECT', 'MANUAL')),
    CONSTRAINT chk_body_measurement_values CHECK (
        weight_kg IS NOT NULL OR body_fat_percentage IS NOT NULL OR waist_cm IS NOT NULL OR
        chest_cm IS NOT NULL OR hip_cm IS NOT NULL OR upper_arm_cm IS NOT NULL OR
        thigh_cm IS NOT NULL OR neck_cm IS NOT NULL
    ),
    CONSTRAINT chk_body_measurement_weight CHECK (weight_kg IS NULL OR weight_kg BETWEEN 20 AND 500),
    CONSTRAINT chk_body_measurement_body_fat CHECK (body_fat_percentage IS NULL OR body_fat_percentage BETWEEN 0 AND 80),
    CONSTRAINT chk_body_measurement_waist CHECK (waist_cm IS NULL OR waist_cm BETWEEN 10 AND 300),
    CONSTRAINT chk_body_measurement_chest CHECK (chest_cm IS NULL OR chest_cm BETWEEN 10 AND 300),
    CONSTRAINT chk_body_measurement_hip CHECK (hip_cm IS NULL OR hip_cm BETWEEN 10 AND 300),
    CONSTRAINT chk_body_measurement_upper_arm CHECK (upper_arm_cm IS NULL OR upper_arm_cm BETWEEN 10 AND 150),
    CONSTRAINT chk_body_measurement_thigh CHECK (thigh_cm IS NULL OR thigh_cm BETWEEN 10 AND 200),
    CONSTRAINT chk_body_measurement_neck CHECK (neck_cm IS NULL OR neck_cm BETWEEN 10 AND 100),
    CONSTRAINT chk_body_measurement_external_id CHECK (provider = 'MANUAL' OR external_id IS NOT NULL)
);

CREATE INDEX idx_body_measurements_user_recorded
    ON body_measurements(user_id, recorded_at DESC, id DESC);

CREATE UNIQUE INDEX uk_body_measurements_provider_external
    ON body_measurements(user_id, provider, external_id)
    WHERE external_id IS NOT NULL;

INSERT INTO body_measurements (
    user_id, recorded_at, weight_kg, provider, external_id, note, created_at, updated_at
)
SELECT
    user_id,
    log_date,
    weight,
    'MANUAL',
    'progress-log:' || id,
    note,
    log_date,
    log_date
FROM progress_logs
WHERE weight IS NOT NULL;

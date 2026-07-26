CREATE TABLE onboarding_drafts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    name VARCHAR(120),
    age INTEGER,
    gender VARCHAR(30),
    height DOUBLE PRECISION,
    weight DOUBLE PRECISION,
    body_fat_percentage DOUBLE PRECISION,
    market_region VARCHAR(20),
    preferred_language VARCHAR(10),
    time_zone VARCHAR(80),
    unit_preference VARCHAR(20),
    target_weight DOUBLE PRECISION,
    weekly_weight_change_target_kg DOUBLE PRECISION,
    goal_type VARCHAR(30),
    activity_level VARCHAR(30),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    CONSTRAINT fk_onboarding_drafts_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_onboarding_drafts_user UNIQUE (user_id),
    CONSTRAINT chk_onboarding_drafts_status
        CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT chk_onboarding_drafts_age
        CHECK (age IS NULL OR age BETWEEN 13 AND 100),
    CONSTRAINT chk_onboarding_drafts_gender
        CHECK (gender IS NULL OR UPPER(gender) IN ('MALE', 'FEMALE')),
    CONSTRAINT chk_onboarding_drafts_height
        CHECK (height IS NULL OR height BETWEEN 100 AND 250),
    CONSTRAINT chk_onboarding_drafts_weight
        CHECK (weight IS NULL OR weight BETWEEN 30 AND 300),
    CONSTRAINT chk_onboarding_drafts_body_fat
        CHECK (body_fat_percentage IS NULL OR body_fat_percentage BETWEEN 0 AND 80),
    CONSTRAINT chk_onboarding_drafts_target_weight
        CHECK (target_weight IS NULL OR target_weight BETWEEN 30 AND 300)
);

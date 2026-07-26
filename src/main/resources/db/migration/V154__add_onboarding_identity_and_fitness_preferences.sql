ALTER TABLE users
    ADD COLUMN birth_date DATE,
    ADD COLUMN country_code VARCHAR(2);

ALTER TABLE onboarding_drafts
    ADD COLUMN birth_date DATE,
    ADD COLUMN country_code VARCHAR(2);

UPDATE users
SET country_code = CASE
    WHEN market_region = 'TR' THEN 'TR'
    ELSE NULL
END
WHERE country_code IS NULL;

UPDATE onboarding_drafts
SET country_code = CASE
    WHEN market_region = 'TR' THEN 'TR'
    ELSE NULL
END
WHERE country_code IS NULL;

ALTER TABLE users
    ADD CONSTRAINT chk_users_country_code CHECK (country_code IS NULL OR country_code IN ('IE', 'GB', 'TR'));

ALTER TABLE onboarding_drafts
    ADD CONSTRAINT chk_onboarding_drafts_country_code CHECK (country_code IS NULL OR country_code IN ('IE', 'GB', 'TR'));

CREATE TABLE user_fitness_preferences (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    weekly_workout_frequency VARCHAR(30),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_fitness_preferences_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_user_fitness_preferences_user UNIQUE (user_id),
    CONSTRAINT chk_user_fitness_preferences_frequency CHECK (
        weekly_workout_frequency IS NULL OR weekly_workout_frequency IN ('NONE', 'ONE_TO_TWO', 'THREE_TO_FOUR', 'FIVE_PLUS')
    )
);

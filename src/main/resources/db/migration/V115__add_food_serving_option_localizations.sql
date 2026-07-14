CREATE TABLE IF NOT EXISTS food_item_serving_option_localizations (
    id BIGSERIAL PRIMARY KEY,
    serving_option_id BIGINT NOT NULL REFERENCES food_item_serving_options(id) ON DELETE CASCADE,
    language VARCHAR(10) NOT NULL,
    label VARCHAR(120) NOT NULL,
    source VARCHAR(60),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_food_serving_option_localization_language
    ON food_item_serving_option_localizations(serving_option_id, language);

CREATE INDEX IF NOT EXISTS idx_food_serving_option_localization_lookup
    ON food_item_serving_option_localizations(serving_option_id, language, active);
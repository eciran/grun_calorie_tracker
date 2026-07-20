CREATE TABLE user_nutrition_preferences (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_nutrition_preferences_user UNIQUE (user_id),
    CONSTRAINT fk_user_nutrition_preferences_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE user_nutrition_allergens (
    preference_id BIGINT NOT NULL,
    allergen VARCHAR(60) NOT NULL,
    CONSTRAINT pk_user_nutrition_allergens
        PRIMARY KEY (preference_id, allergen),
    CONSTRAINT fk_user_nutrition_allergens_preference
        FOREIGN KEY (preference_id)
        REFERENCES user_nutrition_preferences(id) ON DELETE CASCADE
);

CREATE TABLE user_excluded_foods (
    preference_id BIGINT NOT NULL,
    sort_order INTEGER NOT NULL,
    food_name VARCHAR(80) NOT NULL,
    CONSTRAINT pk_user_excluded_foods
        PRIMARY KEY (preference_id, sort_order),
    CONSTRAINT fk_user_excluded_foods_preference
        FOREIGN KEY (preference_id)
        REFERENCES user_nutrition_preferences(id) ON DELETE CASCADE
);

CREATE TABLE user_dietary_preferences (
    preference_id BIGINT NOT NULL,
    sort_order INTEGER NOT NULL,
    preference VARCHAR(80) NOT NULL,
    CONSTRAINT pk_user_dietary_preferences
        PRIMARY KEY (preference_id, sort_order),
    CONSTRAINT fk_user_dietary_preferences_preference
        FOREIGN KEY (preference_id)
        REFERENCES user_nutrition_preferences(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_nutrition_preferences_user
    ON user_nutrition_preferences(user_id);

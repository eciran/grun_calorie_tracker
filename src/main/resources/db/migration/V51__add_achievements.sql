CREATE TABLE IF NOT EXISTS achievement_definitions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(500) NOT NULL,
    metric_key VARCHAR(80) NOT NULL,
    category VARCHAR(40) NOT NULL,
    tier VARCHAR(20) NOT NULL,
    target_value INTEGER NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_achievements (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    achievement_code VARCHAR(80) NOT NULL,
    progress_value INTEGER NOT NULL DEFAULT 0,
    target_value INTEGER NOT NULL DEFAULT 1,
    unlocked BOOLEAN NOT NULL DEFAULT FALSE,
    unlocked_at TIMESTAMP NULL,
    last_evaluated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_achievements_user_code UNIQUE (user_id, achievement_code)
);

CREATE INDEX IF NOT EXISTS idx_user_achievements_user_unlocked
    ON user_achievements(user_id, unlocked);

CREATE INDEX IF NOT EXISTS idx_achievement_definitions_active_sort
    ON achievement_definitions(active, sort_order, code);

INSERT INTO achievement_definitions
    (code, title, description, metric_key, category, tier, target_value, active, sort_order)
VALUES
    ('PROFILE_COMPLETED', 'Profile Completed', 'Complete the core profile fields needed for personalized targets.', 'PROFILE_COMPLETED', 'ONBOARDING', 'BRONZE', 1, TRUE, 10),
    ('FIRST_GOAL_SET', 'First Goal Set', 'Create your first calorie and macro goal.', 'GOAL_SET', 'ONBOARDING', 'BRONZE', 1, TRUE, 20),
    ('FIRST_MEAL_LOGGED', 'First Meal Logged', 'Log your first food item.', 'FOOD_LOG_COUNT', 'FOOD', 'BRONZE', 1, TRUE, 100),
    ('THREE_MEALS_DAY', 'Full Day Logged', 'Log breakfast, lunch, and dinner on the same day.', 'CORE_MEALS_SINGLE_DAY', 'FOOD', 'SILVER', 3, TRUE, 110),
    ('SEVEN_DAY_FOOD_STREAK', '7 Day Food Streak', 'Log food on seven distinct days.', 'FOOD_DISTINCT_DAYS', 'FOOD', 'GOLD', 7, TRUE, 120),
    ('FIRST_WORKOUT_LOGGED', 'First Workout Logged', 'Log your first exercise.', 'EXERCISE_LOG_COUNT', 'EXERCISE', 'BRONZE', 1, TRUE, 200),
    ('WEEKLY_BURN_1000', 'Weekly Burn 1000', 'Burn 1000 kcal from exercise in the last seven days.', 'EXERCISE_WEEKLY_BURN_CALORIES', 'EXERCISE', 'SILVER', 1000, TRUE, 210),
    ('FIRST_FAST_COMPLETED', 'First Fast Completed', 'Complete your first fasting session.', 'FASTING_COMPLETED_COUNT', 'FASTING', 'BRONZE', 1, TRUE, 300),
    ('SEVEN_DAY_FASTING_STREAK', '7 Day Fasting Streak', 'Complete fasting sessions on seven distinct days.', 'FASTING_DISTINCT_COMPLETED_DAYS', 'FASTING', 'GOLD', 7, TRUE, 310),
    ('FIRST_WEIGHT_LOGGED', 'First Weight Logged', 'Add your first progress log.', 'PROGRESS_LOG_COUNT', 'PROGRESS', 'BRONZE', 1, TRUE, 400),
    ('WEIGHT_PROGRESS_1KG', '1kg Progress', 'Move at least 1 kg from your starting weight toward your goal.', 'WEIGHT_PROGRESS_KG', 'PROGRESS', 'SILVER', 1, TRUE, 410),
    ('FIRST_WATER_LOGGED', 'First Water Logged', 'Log your first water intake.', 'WATER_LOG_COUNT', 'WATER', 'BRONZE', 1, TRUE, 500),
    ('WATER_TARGET_HIT', 'Water Target Hit', 'Reach your daily water target at least once.', 'WATER_TARGET_HIT_COUNT', 'WATER', 'SILVER', 1, TRUE, 510)
ON CONFLICT (code) DO UPDATE SET
    title = EXCLUDED.title,
    description = EXCLUDED.description,
    metric_key = EXCLUDED.metric_key,
    category = EXCLUDED.category,
    tier = EXCLUDED.tier,
    target_value = EXCLUDED.target_value,
    active = EXCLUDED.active,
    sort_order = EXCLUDED.sort_order;

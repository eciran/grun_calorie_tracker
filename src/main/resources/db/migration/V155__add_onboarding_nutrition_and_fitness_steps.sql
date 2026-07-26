ALTER TABLE onboarding_drafts
    ADD COLUMN primary_dietary_preference VARCHAR(30),
    ADD COLUMN allergen_selection_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN weekly_workout_frequency VARCHAR(30),
    ADD COLUMN fitness_preference_completed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE onboarding_drafts
    ADD CONSTRAINT chk_onboarding_drafts_dietary_preference
        CHECK (
            primary_dietary_preference IS NULL
            OR primary_dietary_preference IN (
                'NO_PREFERENCE',
                'BALANCED',
                'HIGH_PROTEIN',
                'LOW_CARB',
                'VEGETARIAN',
                'VEGAN',
                'KETO',
                'MEDITERRANEAN'
            )
        ),
    ADD CONSTRAINT chk_onboarding_drafts_workout_frequency
        CHECK (
            weekly_workout_frequency IS NULL
            OR weekly_workout_frequency IN (
                'NONE',
                'ONE_TO_TWO',
                'THREE_TO_FOUR',
                'FIVE_PLUS'
            )
        );

CREATE TABLE onboarding_draft_allergens (
    draft_id BIGINT NOT NULL,
    allergen VARCHAR(60) NOT NULL,
    CONSTRAINT pk_onboarding_draft_allergens PRIMARY KEY (draft_id, allergen),
    CONSTRAINT fk_onboarding_draft_allergens_draft
        FOREIGN KEY (draft_id) REFERENCES onboarding_drafts(id) ON DELETE CASCADE
);

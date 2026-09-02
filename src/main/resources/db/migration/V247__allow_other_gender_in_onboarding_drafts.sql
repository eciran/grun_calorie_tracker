ALTER TABLE onboarding_drafts
    DROP CONSTRAINT IF EXISTS chk_onboarding_drafts_gender;

ALTER TABLE onboarding_drafts
    ADD CONSTRAINT chk_onboarding_drafts_gender
        CHECK (gender IS NULL OR UPPER(gender) IN ('MALE', 'FEMALE', 'OTHER'));

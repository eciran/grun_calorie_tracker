ALTER TABLE onboarding_drafts
    ADD COLUMN preferences_selection_confirmed BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE onboarding_drafts
SET preferences_selection_confirmed = TRUE
WHERE market_region IS NOT NULL
  AND preferred_language IS NOT NULL
  AND time_zone IS NOT NULL
  AND unit_preference IS NOT NULL;

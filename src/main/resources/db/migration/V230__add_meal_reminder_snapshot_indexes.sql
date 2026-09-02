-- Reminder snapshots read bounded user pages over a half-open local-day range.
CREATE INDEX IF NOT EXISTS idx_goals_user_effective_local_date_from
    ON goals (user_id, effective_local_date, effective_from DESC);

-- Food/recipe and fasting indexes already exist in V4/V46/V44. Keep those canonical
-- indexes instead of adding feature-specific duplicates.

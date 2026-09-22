ALTER TABLE progress_logs
    ADD COLUMN onboarding_baseline BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX uk_progress_logs_onboarding_baseline
    ON progress_logs(user_id)
    WHERE onboarding_baseline = TRUE;

INSERT INTO progress_logs (user_id, log_date, weight, onboarding_baseline)
SELECT draft.user_id, draft.completed_at, draft.weight, TRUE
FROM onboarding_drafts draft
WHERE draft.status = 'COMPLETED'
  AND draft.completed_at IS NOT NULL
  AND draft.weight IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM progress_logs progress
      WHERE progress.user_id = draft.user_id
        AND progress.onboarding_baseline = TRUE
  );

INSERT INTO body_measurements (
    user_id, recorded_at, weight_kg, provider, external_id, note, created_at, updated_at
)
SELECT
    progress.user_id,
    progress.log_date,
    progress.weight,
    'MANUAL',
    'progress-log:' || progress.id,
    progress.note,
    progress.log_date,
    progress.log_date
FROM progress_logs progress
WHERE progress.onboarding_baseline = TRUE
  AND NOT EXISTS (
      SELECT 1
      FROM body_measurements measurement
      WHERE measurement.user_id = progress.user_id
        AND measurement.provider = 'MANUAL'
        AND measurement.external_id = 'progress-log:' || progress.id
  );

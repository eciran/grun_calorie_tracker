UPDATE ai_request_history
SET status = 'CONFIRMED',
    confirmed_at = COALESCE(confirmed_at, created_at),
    confirmation_payload = COALESCE(
        confirmation_payload,
        '{"autoConfirmed":true,"migration":"V131"}'
    )
WHERE request_type IN ('AI_DAILY_INSIGHT', 'AI_WEEKLY_INSIGHT')
  AND status = 'DRAFT_CREATED';

ALTER TABLE ai_request_history
    ADD COLUMN IF NOT EXISTS quota_consumed_amount INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS quota_refunded_amount INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS quota_refund_reason TEXT,
    ADD COLUMN IF NOT EXISTS quota_refunded_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS quota_refunded_at TIMESTAMP;

UPDATE ai_request_history
SET quota_consumed_amount = 1
WHERE quota_consumed = TRUE
  AND COALESCE(quota_consumed_amount, 0) = 0;

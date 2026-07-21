ALTER TABLE ai_request_history
    ADD COLUMN IF NOT EXISTS quota_refund_decision VARCHAR(20),
    ADD COLUMN IF NOT EXISTS quota_refund_decision_reason TEXT,
    ADD COLUMN IF NOT EXISTS quota_refund_decided_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS quota_refund_decided_at TIMESTAMP;

ALTER TABLE ai_request_history
    DROP CONSTRAINT IF EXISTS chk_ai_request_history_quota_refund_decision;

ALTER TABLE ai_request_history
    ADD CONSTRAINT chk_ai_request_history_quota_refund_decision
        CHECK (quota_refund_decision IS NULL OR quota_refund_decision IN ('PENDING', 'APPROVED', 'REJECTED'));

UPDATE ai_request_history
SET quota_refund_decision = 'APPROVED',
    quota_refund_decision_reason = quota_refund_reason,
    quota_refund_decided_by = quota_refunded_by,
    quota_refund_decided_at = quota_refunded_at
WHERE COALESCE(quota_refunded_amount, 0) > 0
  AND quota_refund_decision IS NULL;

UPDATE ai_request_history
SET quota_refund_decision = 'PENDING'
WHERE status = 'REJECTED'
  AND quota_consumed = TRUE
  AND COALESCE(quota_consumed_amount, 0) > COALESCE(quota_refunded_amount, 0)
  AND quota_refund_decision IS NULL;
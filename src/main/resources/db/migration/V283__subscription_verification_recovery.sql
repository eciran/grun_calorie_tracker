CREATE TABLE subscription_verifications (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    product_id VARCHAR(255) NOT NULL,
    attempt_id VARCHAR(100) NOT NULL,
    status VARCHAR(40) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    lease_until TIMESTAMP WITH TIME ZONE,
    allocation_reference VARCHAR(255),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_subscription_verification_due ON subscription_verifications(next_attempt_at)
    WHERE next_attempt_at IS NOT NULL;

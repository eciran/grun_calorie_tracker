CREATE TABLE store_subscription_ownership (
    store VARCHAR(50) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    original_transaction_id VARCHAR(255) NOT NULL,
    owner_user_id BIGINT NOT NULL,
    requires_review BOOLEAN NOT NULL DEFAULT FALSE,
    first_event_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (store, environment, original_transaction_id)
);

-- Keep ownership even if a GRUN user is later deleted. Never silently recycle receipts.
WITH evidence AS (
    SELECT user_id, provider_event_id,
           UPPER(raw_payload::jsonb -> 'event' ->> 'store') AS store,
           UPPER(raw_payload::jsonb -> 'event' ->> 'environment') AS environment,
           NULLIF(TRIM(raw_payload::jsonb -> 'event' ->> 'original_transaction_id'), '') AS chain_id
    FROM subscription_provider_events
    WHERE provider = 'REVENUECAT' AND user_id IS NOT NULL
)
INSERT INTO store_subscription_ownership
    (store, environment, original_transaction_id, owner_user_id, requires_review, first_event_id)
SELECT store, environment, chain_id, MIN(user_id), COUNT(DISTINCT user_id) > 1, MIN(provider_event_id)
FROM evidence
WHERE store IS NOT NULL AND environment IN ('SANDBOX', 'PRODUCTION') AND chain_id IS NOT NULL
GROUP BY store, environment, chain_id;

CREATE TABLE food_branded_duplicate_batch_jobs (
    id BIGSERIAL PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    query_filter VARCHAR(300),
    safe_only BOOLEAN NOT NULL DEFAULT TRUE,
    page_size INTEGER NOT NULL DEFAULT 100,
    next_page INTEGER NOT NULL DEFAULT 0,
    next_item_index INTEGER NOT NULL DEFAULT 0,
    total_groups BIGINT NOT NULL DEFAULT 0,
    eligible_groups BIGINT NOT NULL DEFAULT 0,
    processed_groups BIGINT NOT NULL DEFAULT 0,
    applied_groups BIGINT NOT NULL DEFAULT 0,
    skipped_groups BIGINT NOT NULL DEFAULT 0,
    failed_groups BIGINT NOT NULL DEFAULT 0,
    pause_requested BOOLEAN NOT NULL DEFAULT FALSE,
    requested_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    last_error VARCHAR(2000),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_food_branded_duplicate_batch_status CHECK (
        status IN ('QUEUED','DISCOVERING','RUNNING','PAUSE_REQUESTED','PAUSED','COMPLETED','FAILED')
    ),
    CONSTRAINT chk_food_branded_duplicate_batch_page_size CHECK (page_size BETWEEN 1 AND 100)
);

CREATE INDEX idx_food_branded_duplicate_batch_jobs_status
    ON food_branded_duplicate_batch_jobs(status, created_at DESC);

-- Enforce one active worker across all application instances, not only within one JVM.
CREATE UNIQUE INDEX uq_food_branded_duplicate_single_active_job
    ON food_branded_duplicate_batch_jobs ((1))
    WHERE status IN ('QUEUED','DISCOVERING','RUNNING','PAUSE_REQUESTED');

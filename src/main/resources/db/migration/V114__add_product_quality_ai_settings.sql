CREATE TABLE IF NOT EXISTS product_quality_ai_settings (
    id BIGINT PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    max_products_per_run INTEGER NOT NULL DEFAULT 25,
    daily_product_limit INTEGER NOT NULL DEFAULT 250,
    monthly_product_limit INTEGER NOT NULL DEFAULT 2000,
    force_rescan_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    admin_note VARCHAR(1000),
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    CONSTRAINT chk_product_quality_ai_settings_max_run CHECK (max_products_per_run BETWEEN 1 AND 25),
    CONSTRAINT chk_product_quality_ai_settings_daily_limit CHECK (daily_product_limit BETWEEN 1 AND 10000),
    CONSTRAINT chk_product_quality_ai_settings_monthly_limit CHECK (monthly_product_limit BETWEEN 1 AND 100000)
);

INSERT INTO product_quality_ai_settings (
    id,
    enabled,
    max_products_per_run,
    daily_product_limit,
    monthly_product_limit,
    force_rescan_allowed,
    admin_note,
    updated_at,
    updated_by
)
VALUES (
    1,
    TRUE,
    25,
    250,
    2000,
    TRUE,
    'Default AI product quality validation guardrails.',
    CURRENT_TIMESTAMP,
    'migration'
)
ON CONFLICT (id) DO NOTHING;
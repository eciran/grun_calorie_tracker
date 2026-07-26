ALTER TABLE food_items
    ADD COLUMN IF NOT EXISTS search_selection_count BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS food_search_telemetry (
    id VARCHAR(36) PRIMARY KEY,
    safe_query VARCHAR(120) NOT NULL,
    query_fingerprint VARCHAR(64) NOT NULL,
    query_language VARCHAR(20),
    market_region VARCHAR(20),
    result_count INTEGER NOT NULL,
    result_food_item_ids VARCHAR(2000) NOT NULL DEFAULT '',
    selected_food_item_id BIGINT REFERENCES food_items(id) ON DELETE SET NULL,
    selected_rank INTEGER,
    searched_at TIMESTAMP WITH TIME ZONE NOT NULL,
    selected_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_food_search_result_count CHECK (result_count >= 0),
    CONSTRAINT chk_food_search_selected_rank CHECK (selected_rank IS NULL OR selected_rank > 0)
);

CREATE INDEX IF NOT EXISTS idx_food_search_telemetry_searched_at ON food_search_telemetry (searched_at DESC);
CREATE INDEX IF NOT EXISTS idx_food_search_telemetry_zero_result ON food_search_telemetry (market_region, query_language, searched_at DESC) WHERE result_count = 0;
CREATE INDEX IF NOT EXISTS idx_food_search_telemetry_no_selection ON food_search_telemetry (searched_at DESC) WHERE selected_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_food_search_telemetry_selected_product ON food_search_telemetry (selected_food_item_id, selected_at DESC) WHERE selected_food_item_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_food_search_telemetry_expiry ON food_search_telemetry (expires_at);
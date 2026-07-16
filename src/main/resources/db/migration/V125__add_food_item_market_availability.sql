CREATE TABLE food_item_market_regions (
    food_item_id BIGINT NOT NULL,
    market_region VARCHAR(32) NOT NULL,
    CONSTRAINT pk_food_item_market_regions PRIMARY KEY (food_item_id, market_region),
    CONSTRAINT fk_food_item_market_regions_food_item
        FOREIGN KEY (food_item_id) REFERENCES food_items(id) ON DELETE CASCADE,
    CONSTRAINT chk_food_item_market_regions_region
        CHECK (market_region IN ('GLOBAL', 'UK_IE', 'EU', 'TR'))
);

INSERT INTO food_item_market_regions (food_item_id, market_region)
SELECT id, market_region
FROM food_items
WHERE market_region IS NOT NULL
ON CONFLICT (food_item_id, market_region) DO NOTHING;

CREATE INDEX idx_food_item_market_regions_market
    ON food_item_market_regions (market_region, food_item_id);

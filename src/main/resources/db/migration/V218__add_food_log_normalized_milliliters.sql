ALTER TABLE food_logs
    ADD COLUMN normalized_portion_milliliters DOUBLE PRECISION;

ALTER TABLE food_logs
    ADD CONSTRAINT chk_food_logs_normalized_milliliters_positive
        CHECK (normalized_portion_milliliters IS NULL OR normalized_portion_milliliters > 0);

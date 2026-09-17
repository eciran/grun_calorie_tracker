-- Search equivalence only; product identity and barcode constraints are unchanged.
CREATE INDEX IF NOT EXISTS idx_food_items_brand_separator_search
    ON food_items (replace(replace(replace(replace(replace(replace(replace(replace(replace(
        lower(brand), ' ', ''), '-', ''), U&'\2022', ''), U&'\00b7', ''),
        U&'\2010', ''), U&'\2011', ''), U&'\2013', ''), U&'\2014', ''), U&'\00a0', ''));

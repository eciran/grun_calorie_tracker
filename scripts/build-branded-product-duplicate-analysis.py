#!/usr/bin/env python3
import argparse
from pathlib import Path


def build(cluster_limit: int, sample_limit: int) -> str:
    if not 1 <= cluster_limit <= 5000:
        raise ValueError("cluster_limit must be between 1 and 5000")
    if not 1 <= sample_limit <= 100:
        raise ValueError("sample_limit must be between 1 and 100")
    identity = """SELECT item.id,
       coalesce(brand.canonical_name, nullif(trim(item.brand), ''), '<missing-brand>') brand_name,
       coalesce('brand:' || item.brand_id::text,
                'legacy:' || regexp_replace(lower(coalesce(item.brand, '')), '[^[:alnum:]]+', '', 'g')) brand_key,
       coalesce(item.display_name, item.name) product_name,
       regexp_replace(lower(coalesce(item.display_name, item.name, '')), '[^[:alnum:]]+', '', 'g') name_key,
       nullif(trim(item.normalized_barcode), '') barcode,
       nullif(trim(item.source_key), '') source_key,
       item.market_region,
       item.preparation_state,
       item.serving_size_grams,
       item.serving_unit,
       item.calories, item.protein, item.carbs, item.fat,
       item.quality_score, item.verification_status, item.data_source
FROM food_items item
LEFT JOIN food_brands brand ON brand.id = item.brand_id
WHERE item.catalog_type = 'BRANDED_PRODUCT'
  AND coalesce(item.is_custom, false) = false
  AND item.publication_status = 'PUBLISHED'
  AND coalesce(item.display_name, item.name) IS NOT NULL"""
    return f"""BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '5min';

WITH branded_identity AS (
{identity}
), groups AS (
SELECT brand_key, name_key,
       min(brand_name) brand_name,
       min(product_name) representative_name,
       count(*) product_count,
       count(DISTINCT barcode) FILTER (WHERE barcode IS NOT NULL) barcode_count,
       count(*) FILTER (WHERE barcode IS NULL) missing_barcode_count,
       count(DISTINCT source_key) FILTER (WHERE source_key IS NOT NULL) source_key_count,
       count(DISTINCT market_region) market_count,
       count(DISTINCT preparation_state) FILTER (WHERE preparation_state IS NOT NULL) preparation_count,
       count(DISTINCT concat_ws('|', serving_size_grams::text, serving_unit)) serving_count,
       count(*) FILTER (WHERE serving_size_grams IS NULL OR serving_unit IS NULL) serving_missing_count,
       count(DISTINCT concat_ws('|', round(calories::numeric, 1), round(protein::numeric, 1),
                                round(carbs::numeric, 1), round(fat::numeric, 1))) nutrition_count
       ,count(*) FILTER (WHERE calories IS NULL OR protein IS NULL OR carbs IS NULL OR fat IS NULL) nutrition_missing_count
FROM branded_identity
WHERE name_key <> '' AND brand_key NOT IN ('legacy:', 'legacy:<missingbrand>')
GROUP BY brand_key, name_key
HAVING count(*) > 1
), classified AS (
SELECT groups.*,
       CASE
         WHEN barcode_count = 1 AND missing_barcode_count = 0 THEN 'AUTO_SAFE_SAME_GTIN'
         WHEN source_key_count = 1 AND missing_barcode_count = product_count THEN 'AUTO_SAFE_SAME_SOURCE_KEY'
         WHEN barcode_count > 1 THEN 'REVIEW_DIFFERENT_GTIN'
         WHEN missing_barcode_count > 0 THEN 'REVIEW_MISSING_GTIN'
         ELSE 'REVIEW_IDENTITY'
       END decision,
       CASE
         WHEN market_count > 1 OR preparation_count > 1 OR serving_count > 1 OR nutrition_count > 1
           THEN true ELSE false
       END variant_signal
FROM groups
)

SELECT 'SUMMARY' section, count(*)::text value1,
       coalesce(sum(product_count), 0)::text value2,
       count(*) FILTER (WHERE decision LIKE 'AUTO_SAFE%')::text value3,
       count(*) FILTER (WHERE decision LIKE 'REVIEW%')::text value4,
       count(*) FILTER (WHERE variant_signal)::text value5
FROM classified;

WITH branded_identity AS (
{identity}
), groups AS (
SELECT brand_key, name_key,
       min(brand_name) brand_name, min(product_name) representative_name,
       count(*) product_count,
       count(DISTINCT barcode) FILTER (WHERE barcode IS NOT NULL) barcode_count,
       count(*) FILTER (WHERE barcode IS NULL) missing_barcode_count,
       count(DISTINCT source_key) FILTER (WHERE source_key IS NOT NULL) source_key_count,
       count(DISTINCT market_region) market_count,
       count(DISTINCT preparation_state) FILTER (WHERE preparation_state IS NOT NULL) preparation_count,
       count(DISTINCT concat_ws('|', serving_size_grams::text, serving_unit)) serving_count,
       count(*) FILTER (WHERE serving_size_grams IS NULL OR serving_unit IS NULL) serving_missing_count,
       count(DISTINCT concat_ws('|', round(calories::numeric, 1), round(protein::numeric, 1),
                                round(carbs::numeric, 1), round(fat::numeric, 1))) nutrition_count
       ,count(*) FILTER (WHERE calories IS NULL OR protein IS NULL OR carbs IS NULL OR fat IS NULL) nutrition_missing_count
FROM branded_identity
WHERE name_key <> '' AND brand_key NOT IN ('legacy:', 'legacy:<missingbrand>')
GROUP BY brand_key, name_key HAVING count(*) > 1
), classified AS (
SELECT groups.*,
       CASE
         WHEN barcode_count = 1 AND missing_barcode_count = 0 THEN 'AUTO_SAFE_SAME_GTIN'
         WHEN source_key_count = 1 AND missing_barcode_count = product_count THEN 'AUTO_SAFE_SAME_SOURCE_KEY'
         WHEN barcode_count > 1 THEN 'REVIEW_DIFFERENT_GTIN'
         WHEN missing_barcode_count > 0 THEN 'REVIEW_MISSING_GTIN'
         ELSE 'REVIEW_IDENTITY'
       END decision,
       (market_count > 1 OR preparation_count > 1 OR serving_count > 1 OR nutrition_count > 1) variant_signal
FROM groups
)
SELECT 'DECISION', decision, count(*)::text, sum(product_count)::text, '', ''
FROM classified GROUP BY decision ORDER BY decision;

WITH branded_identity AS (
{identity}
), groups AS (
SELECT brand_key, name_key,
       min(brand_name) brand_name, min(product_name) representative_name,
       count(*) product_count,
       count(DISTINCT barcode) FILTER (WHERE barcode IS NOT NULL) barcode_count,
       count(*) FILTER (WHERE barcode IS NULL) missing_barcode_count,
       count(DISTINCT source_key) FILTER (WHERE source_key IS NOT NULL) source_key_count,
       count(DISTINCT market_region) market_count,
       count(DISTINCT preparation_state) FILTER (WHERE preparation_state IS NOT NULL) preparation_count,
       count(DISTINCT concat_ws('|', serving_size_grams::text, serving_unit)) serving_count,
       count(*) FILTER (WHERE serving_size_grams IS NULL OR serving_unit IS NULL) serving_missing_count,
       count(DISTINCT concat_ws('|', round(calories::numeric, 1), round(protein::numeric, 1),
                                round(carbs::numeric, 1), round(fat::numeric, 1))) nutrition_count
       ,count(*) FILTER (WHERE calories IS NULL OR protein IS NULL OR carbs IS NULL OR fat IS NULL) nutrition_missing_count
FROM branded_identity
WHERE name_key <> '' AND brand_key NOT IN ('legacy:', 'legacy:<missingbrand>')
GROUP BY brand_key, name_key HAVING count(*) > 1
), classified AS (
SELECT groups.*,
       CASE
         WHEN barcode_count = 1 AND missing_barcode_count = 0 THEN 'AUTO_SAFE_SAME_GTIN'
         WHEN source_key_count = 1 AND missing_barcode_count = product_count THEN 'AUTO_SAFE_SAME_SOURCE_KEY'
         WHEN barcode_count > 1 THEN 'REVIEW_DIFFERENT_GTIN'
         WHEN missing_barcode_count > 0 THEN 'REVIEW_MISSING_GTIN'
         ELSE 'REVIEW_IDENTITY'
       END decision,
       (market_count > 1 OR preparation_count > 1 OR serving_count > 1 OR nutrition_count > 1) variant_signal
FROM groups
)
SELECT 'CLUSTER', brand_name, representative_name, product_count::text,
       decision, concat_ws(',', 'barcodes=' || barcode_count, 'missing=' || missing_barcode_count,
                           'markets=' || market_count, 'servings=' || serving_count,
                           'servingMissing=' || serving_missing_count,
                           'nutrition=' || nutrition_count,
                           'nutritionMissing=' || nutrition_missing_count,
                           'variant=' || variant_signal)
FROM classified
ORDER BY variant_signal ASC, serving_missing_count ASC, nutrition_missing_count ASC,
         product_count DESC, brand_name, representative_name
LIMIT {cluster_limit};

WITH branded_identity AS (
{identity}
)
SELECT 'WISPA_SAMPLE' section, item.id::text, item.brand_name, item.product_name,
       coalesce(item.barcode, ''),
       concat_ws(',', item.market_region::text, item.serving_size_grams::text,
                 item.serving_unit, item.calories::text, item.data_source::text)
FROM branded_identity item
WHERE lower(item.product_name) ~ '(^|[^[:alnum:]])wispa([^[:alnum:]]|$)'
ORDER BY item.name_key, item.barcode NULLS LAST, item.id
LIMIT {sample_limit};

COMMIT;
"""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True)
    parser.add_argument("--cluster-limit", type=int, default=250)
    parser.add_argument("--sample-limit", type=int, default=50)
    args = parser.parse_args()
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(build(args.cluster_limit, args.sample_limit), encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()

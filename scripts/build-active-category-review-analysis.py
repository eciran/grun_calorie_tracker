#!/usr/bin/env python3
"""Build a read-only PostgreSQL report for the active canonical-category queue."""

import argparse
import json
from pathlib import Path


def build(sample_limit: int = 120, tag_limit: int = 200, signature_limit: int = 150) -> str:
    if not 1 <= sample_limit <= 500:
        raise ValueError("sample_limit must be between 1 and 500")
    if not 1 <= tag_limit <= 1000:
        raise ValueError("tag_limit must be between 1 and 1000")
    if not 1 <= signature_limit <= 500:
        raise ValueError("signature_limit must be between 1 and 500")

    return f"""-- Active canonical-category review queue analysis. No data mutation.
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '5min';

WITH active_queue AS (
    SELECT DISTINCT issue.food_item_id
    FROM food_product_quality_issues issue
    WHERE issue.issue_type = 'MISSING_CANONICAL_CATEGORY'
      AND issue.resolved = false
      AND NOT EXISTS (
          SELECT 1
          FROM food_item_categories assigned
          WHERE assigned.food_item_id = issue.food_item_id
            AND assigned.primary_category
      )
), product_tags AS (
    SELECT queue.food_item_id,
           lower(trim(source.category_tag)) AS source_tag
    FROM active_queue queue
    JOIN food_item_source_categories source
      ON source.food_item_id = queue.food_item_id
), tag_mapping AS (
    SELECT tags.food_item_id, tags.source_tag,
           mapping.status AS mapping_status,
           category.slug AS mapped_category,
           mapping.primary_priority,
           mapping.confidence_score
    FROM product_tags tags
    LEFT JOIN food_items item ON item.id = tags.food_item_id
    LEFT JOIN food_category_source_mappings mapping
      ON mapping.data_source = item.data_source
     AND mapping.normalized_source_tag = tags.source_tag
     AND (mapping.market_region IS NULL OR mapping.market_region = item.market_region)
    LEFT JOIN food_categories category ON category.id = mapping.category_id
), signatures AS (
    SELECT queue.food_item_id,
           string_agg(DISTINCT tags.source_tag, ',' ORDER BY tags.source_tag) AS tag_signature
    FROM active_queue queue
    JOIN product_tags tags ON tags.food_item_id = queue.food_item_id
    GROUP BY queue.food_item_id
), signature_counts AS (
    SELECT tag_signature, count(*) AS products,
           min(food_item_id) AS first_product_id
    FROM signatures
    GROUP BY tag_signature
), ranked_signatures AS (
    SELECT signature_counts.*,
           row_number() OVER (ORDER BY products DESC, tag_signature) AS signature_rank
    FROM signature_counts
), source_tag_counts AS (
    SELECT mapping.source_tag, mapping.mapping_status, mapping.mapped_category,
           count(DISTINCT mapping.food_item_id) AS products,
           min(mapping.primary_priority) AS primary_priority,
           max(mapping.confidence_score) AS confidence_score
    FROM tag_mapping mapping
    GROUP BY mapping.source_tag, mapping.mapping_status, mapping.mapped_category
), ranked_source_tags AS (
    SELECT source_tag_counts.*,
           row_number() OVER (ORDER BY products DESC, source_tag) AS tag_rank
    FROM source_tag_counts
), brands AS (
    SELECT coalesce(nullif(trim(item.brand), ''), '(NO BRAND)') AS brand,
           count(*) AS products
    FROM active_queue queue
    JOIN food_items item ON item.id = queue.food_item_id
    GROUP BY coalesce(nullif(trim(item.brand), ''), '(NO BRAND)')
), ranked_brands AS (
    SELECT brands.*,
           row_number() OVER (ORDER BY products DESC, brand) AS brand_rank
    FROM brands
), samples AS (
    SELECT item.id,
           coalesce(item.display_name, item.name) AS product_name,
           coalesce(item.brand, '') AS brand,
           coalesce(item.data_source, '') AS data_source,
           coalesce(item.market_region, '') AS market_region,
           coalesce(signatures.tag_signature, '') AS tag_signature,
           row_number() OVER (ORDER BY md5(item.id::text), item.id) AS sample_rank
    FROM active_queue queue
    JOIN food_items item ON item.id = queue.food_item_id
    LEFT JOIN signatures ON signatures.food_item_id = item.id
), report AS (
    SELECT 0 AS report_order, 0::bigint AS item_order, 'SUMMARY'::text AS section,
           count(*)::text AS value1,
           count(*) FILTER (WHERE EXISTS (
               SELECT 1 FROM product_tags tags WHERE tags.food_item_id = queue.food_item_id
           ))::text AS value2,
           count(*) FILTER (WHERE NOT EXISTS (
               SELECT 1 FROM product_tags tags WHERE tags.food_item_id = queue.food_item_id
           ))::text AS value3,
           min(queue.food_item_id)::text AS value4,
           max(queue.food_item_id)::text AS value5,
           ''::text AS value6
    FROM active_queue queue

    UNION ALL
    SELECT 1, -count(*)::bigint, 'SCOPE',
           coalesce(item.data_source, 'UNKNOWN'),
           coalesce(item.market_region, 'UNKNOWN'),
           count(*)::text, '', '', ''
    FROM active_queue queue
    JOIN food_items item ON item.id = queue.food_item_id
    GROUP BY item.data_source, item.market_region

    UNION ALL
    SELECT 2, tags.tag_rank, 'SOURCE_TAG', tags.source_tag,
           coalesce(tags.mapping_status, 'UNMAPPED'),
           coalesce(tags.mapped_category, ''), tags.products::text,
           coalesce(tags.primary_priority::text, ''),
           coalesce(tags.confidence_score::text, '')
    FROM ranked_source_tags tags
    WHERE tags.tag_rank <= {tag_limit}

    UNION ALL
    SELECT 3, signatures.signature_rank, 'TAG_SIGNATURE',
           signatures.products::text,
           signatures.first_product_id::text,
           signatures.tag_signature,
           '', '', ''
    FROM ranked_signatures signatures
    WHERE signatures.signature_rank <= {signature_limit}

    UNION ALL
    SELECT 4, brands.brand_rank, 'BRAND', brands.brand, brands.products::text,
           '', '', '', ''
    FROM ranked_brands brands
    WHERE brands.brand_rank <= 100

    UNION ALL
    SELECT 5, samples.sample_rank, 'SAMPLE', samples.id::text,
           samples.product_name, samples.brand, samples.data_source,
           samples.market_region, samples.tag_signature
    FROM samples
    WHERE samples.sample_rank <= {sample_limit}
)
SELECT section, value1, value2, value3, value4, value5, value6
FROM report
ORDER BY report_order, item_order, value1;

COMMIT;
"""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True)
    parser.add_argument("--sample-limit", type=int, default=120)
    parser.add_argument("--tag-limit", type=int, default=200)
    parser.add_argument("--signature-limit", type=int, default=150)
    args = parser.parse_args()

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        build(args.sample_limit, args.tag_limit, args.signature_limit),
        encoding="utf-8",
        newline="\n",
    )
    print(json.dumps({"output": str(output), "mode": "READ_ONLY"}, indent=2))


if __name__ == "__main__":
    main()

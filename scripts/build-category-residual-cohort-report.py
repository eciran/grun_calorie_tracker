#!/usr/bin/env python3
import argparse
import json
from pathlib import Path


def literal(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def build(config: dict) -> str:
    default = config.get("defaultDecision", "REVIEW_REQUIRED").strip().upper()
    rows = []
    seen = set()
    for rule in config.get("rules", []):
        tag = rule["sourceTag"].strip().lower()
        decision = rule.get("decision", default).strip().upper()
        if tag in seen:
            raise ValueError(f"Duplicate source tag: {tag}")
        if decision not in {"AUTO_SAFE", "REVIEW_REQUIRED"}:
            raise ValueError(f"Invalid decision: {decision}")
        seen.add(tag)
        rows.append(
            f"({literal(tag)}, {literal(rule['categorySlug'].strip())}, "
            f"{int(rule['primaryPriority'])}, {literal(decision)})"
        )
    if not rows:
        raise ValueError("At least one rule is required")

    values = ",\n        ".join(rows)
    return f"""-- Read-only report for review products that still have no primary category.
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '3min';

WITH rules(source_tag, category_slug, primary_priority, decision) AS (
    VALUES
        {values}
), raw_candidates AS (
    SELECT source.food_item_id, rules.source_tag, rules.category_slug,
           rules.primary_priority, rules.decision
    FROM food_item_source_categories source
    JOIN rules ON lower(trim(source.category_tag)) = rules.source_tag
), candidates AS (
    SELECT food_item_id, category_slug, min(primary_priority) AS primary_priority,
           CASE WHEN bool_or(decision = 'AUTO_SAFE')
                THEN 'AUTO_SAFE' ELSE 'REVIEW_REQUIRED' END AS decision
    FROM raw_candidates
    GROUP BY food_item_id, category_slug
), ranked AS (
    SELECT candidates.*,
           row_number() OVER (
               PARTITION BY food_item_id
               ORDER BY primary_priority, category_slug
           ) AS primary_rank
    FROM candidates
), residual_products AS (
    SELECT ranked.food_item_id, ranked.category_slug
    FROM ranked
    WHERE ranked.primary_rank = 1
      AND ranked.decision = 'REVIEW_REQUIRED'
      AND NOT EXISTS (
          SELECT 1
          FROM food_item_categories assigned
          WHERE assigned.food_item_id = ranked.food_item_id
            AND assigned.primary_category
      )
), category_counts AS (
    SELECT category_slug, count(*) AS products
    FROM residual_products
    GROUP BY category_slug
), co_tag_counts AS (
    SELECT product.category_slug, lower(trim(source.category_tag)) AS source_tag,
           count(DISTINCT product.food_item_id) AS products
    FROM residual_products product
    JOIN food_item_source_categories source ON source.food_item_id = product.food_item_id
    WHERE NOT EXISTS (
        SELECT 1 FROM rules WHERE rules.source_tag = lower(trim(source.category_tag))
    )
    GROUP BY product.category_slug, lower(trim(source.category_tag))
), ranked_co_tags AS (
    SELECT co_tag_counts.*,
           row_number() OVER (
               PARTITION BY category_slug
               ORDER BY products DESC, source_tag
           ) AS tag_rank
    FROM co_tag_counts
), sample_base AS (
    SELECT product.category_slug, item.id,
           coalesce(item.display_name, item.name) AS product_name,
           coalesce(item.brand, '') AS brand,
           string_agg(DISTINCT lower(trim(source.category_tag)), ','
                      ORDER BY lower(trim(source.category_tag))) AS tags
    FROM residual_products product
    JOIN food_items item ON item.id = product.food_item_id
    JOIN food_item_source_categories source ON source.food_item_id = product.food_item_id
    GROUP BY product.category_slug, item.id, item.display_name, item.name, item.brand
), samples AS (
    SELECT sample_base.*,
           row_number() OVER (
               PARTITION BY category_slug ORDER BY md5(id::text), id
           ) AS sample_rank
    FROM sample_base
), report AS (
    SELECT 0 AS report_order, 0::bigint AS item_order, 'SUMMARY'::text AS section,
           count(*)::text AS value1, count(DISTINCT category_slug)::text AS value2,
           ''::text AS value3, ''::text AS value4, ''::text AS value5
    FROM residual_products
    UNION ALL
    SELECT 1, -products, 'CATEGORY', category_slug, products::text, '', '', ''
    FROM category_counts
    UNION ALL
    SELECT 2, -products, 'CO_TAG', category_slug, source_tag,
           products::text, tag_rank::text, ''
    FROM ranked_co_tags WHERE tag_rank <= 30
    UNION ALL
    SELECT 3, sample_rank, 'SAMPLE', category_slug, id::text,
           product_name, brand, tags
    FROM samples WHERE sample_rank <= 20
)
SELECT section, value1, value2, value3, value4, value5
FROM report
ORDER BY report_order, item_order, value1;

COMMIT;
"""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--decisions", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    config = json.loads(Path(args.decisions).read_text(encoding="utf-8"))
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(build(config), encoding="utf-8", newline="\n")
    print(json.dumps({"output": str(output)}, indent=2))


if __name__ == "__main__":
    main()

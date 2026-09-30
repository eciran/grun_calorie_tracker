#!/usr/bin/env python3
import argparse
import json
from pathlib import Path


def sql_literal(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def build(decisions: dict) -> str:
    rules = decisions.get("rules", [])
    if not rules:
        raise ValueError("At least one category mapping rule is required")
    default_decision = decisions.get("defaultDecision", "REVIEW_REQUIRED").strip().upper()
    allowed_decisions = {"AUTO_SAFE", "REVIEW_REQUIRED"}
    if default_decision not in allowed_decisions:
        raise ValueError(f"Invalid default decision: {default_decision}")

    seen_tags = set()
    rows = []
    for rule in rules:
        tag = rule["sourceTag"].strip().lower()
        slug = rule["categorySlug"].strip()
        priority = int(rule["primaryPriority"])
        decision = rule.get("decision", default_decision).strip().upper()
        if not tag.startswith("en:"):
            raise ValueError(f"Only explicit OFF English tags are accepted: {tag}")
        if tag in seen_tags:
            raise ValueError(f"Duplicate source tag: {tag}")
        if priority < 1:
            raise ValueError(f"Invalid priority for {tag}")
        if decision not in allowed_decisions:
            raise ValueError(f"Invalid decision for {tag}: {decision}")
        seen_tags.add(tag)
        rows.append(
            f"({sql_literal(tag)}, {sql_literal(slug)}, {priority}, {sql_literal(decision)})"
        )

    values = ",\n        ".join(rows)
    return f"""-- Generated read-only category mapping analysis. No catalog mutation.
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '2min';

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
), annotated AS (
    SELECT candidates.*,
           min(primary_priority) OVER (PARTITION BY food_item_id) AS best_priority
    FROM candidates
), ranked AS (
    SELECT annotated.*,
           row_number() OVER (
               PARTITION BY food_item_id
               ORDER BY primary_priority, category_slug
           ) AS primary_rank
    FROM annotated
), safe_products AS (
    SELECT food_item_id
    FROM ranked
    WHERE primary_rank = 1
      AND decision = 'AUTO_SAFE'
), safe_assignments AS (
    SELECT candidates.*
    FROM candidates
    JOIN safe_products USING (food_item_id)
    WHERE candidates.decision = 'AUTO_SAFE'
), product_rollup AS (
    SELECT food_item_id,
           count(*) AS category_count,
           min(best_priority) AS best_priority,
           count(*) FILTER (WHERE primary_priority = best_priority) AS best_priority_count
    FROM annotated
    GROUP BY food_item_id
), tag_counts AS (
    SELECT rules.source_tag, rules.category_slug, rules.primary_priority, rules.decision,
           count(DISTINCT source.food_item_id) AS products
    FROM rules
    LEFT JOIN food_item_source_categories source
      ON lower(trim(source.category_tag)) = rules.source_tag
    GROUP BY rules.source_tag, rules.category_slug, rules.primary_priority, rules.decision
), pair_counts AS (
    SELECT first.category_slug AS first_slug, second.category_slug AS second_slug,
           count(*) AS products
    FROM candidates first
    JOIN candidates second
      ON second.food_item_id = first.food_item_id
     AND second.category_slug > first.category_slug
    GROUP BY first.category_slug, second.category_slug
), tie_rows AS (
    SELECT item.id, coalesce(item.display_name, item.name) AS product_name,
           coalesce(item.brand, '') AS brand,
           string_agg(annotated.category_slug, ',' ORDER BY annotated.category_slug) AS categories
    FROM annotated
    JOIN food_items item ON item.id = annotated.food_item_id
    WHERE annotated.primary_priority = annotated.best_priority
    GROUP BY item.id, item.display_name, item.name, item.brand
    HAVING count(*) > 1
), sample_base AS (
    SELECT ranked.category_slug, ranked.decision, item.id,
           coalesce(item.display_name, item.name) AS product_name,
           coalesce(item.brand, '') AS brand,
           string_agg(DISTINCT raw_candidates.source_tag, ',' ORDER BY raw_candidates.source_tag) AS source_tags
    FROM ranked
    JOIN food_items item ON item.id = ranked.food_item_id
    JOIN raw_candidates ON raw_candidates.food_item_id = ranked.food_item_id
    WHERE ranked.primary_rank = 1
    GROUP BY ranked.category_slug, ranked.decision, item.id, item.display_name, item.name, item.brand
), sample_rows AS (
    SELECT sample_base.*,
           row_number() OVER (
               PARTITION BY category_slug, decision
               ORDER BY md5(id::text), id
           ) AS sample_rank
    FROM sample_base
), report_rows AS (
    SELECT 0 AS report_order, 0::bigint AS item_order,
           'SUMMARY'::text AS section,
           (SELECT count(*) FROM rules)::text AS value1,
           (SELECT count(*) FROM candidates)::text AS value2,
           (SELECT count(*) FROM product_rollup)::text AS value3,
           (SELECT count(*) FROM product_rollup WHERE category_count > 1)::text AS value4,
           (SELECT count(*) FROM product_rollup WHERE best_priority_count > 1)::text AS value5
    UNION ALL
    SELECT 1, -products, 'RULE', source_tag, category_slug,
           primary_priority::text, products::text, decision
    FROM tag_counts
    UNION ALL
    SELECT 2, 0, 'APPLY_GATE',
           (SELECT count(*) FROM rules WHERE decision = 'AUTO_SAFE')::text,
           (SELECT count(*) FROM safe_products)::text,
           (SELECT count(*) FROM safe_assignments)::text,
           (SELECT count(DISTINCT category_slug) FROM safe_assignments)::text,
           (SELECT count(*) FROM rules
             LEFT JOIN food_categories ON food_categories.slug = rules.category_slug
            WHERE rules.decision = 'AUTO_SAFE' AND food_categories.id IS NULL)::text
    UNION ALL
    SELECT 3, -count(*), 'DECISION', decision, count(*)::text, '', '', ''
    FROM ranked
    WHERE primary_rank = 1
    GROUP BY decision
    UNION ALL
    SELECT 4, -count(*), 'PRIMARY', category_slug, count(*)::text,
           count(*) FILTER (WHERE decision = 'AUTO_SAFE')::text,
           count(*) FILTER (WHERE decision = 'REVIEW_REQUIRED')::text, ''
    FROM ranked
    WHERE primary_rank = 1
    GROUP BY category_slug
    UNION ALL
    SELECT 5, -products, 'PAIR', first_slug, second_slug, products::text, '', ''
    FROM pair_counts
    UNION ALL
    SELECT 6, id, 'TIE', id::text, product_name, brand, categories, ''
    FROM tie_rows
    UNION ALL
    SELECT 7, sample_rank, 'SAMPLE', category_slug, id::text,
           product_name, brand, source_tags || '|decision=' || decision
    FROM sample_rows
    WHERE sample_rank <= CASE WHEN decision = 'REVIEW_REQUIRED' THEN 20 ELSE 5 END
)
SELECT section, value1, value2, value3, value4, value5
FROM report_rows
ORDER BY report_order, item_order, value1;

COMMIT;
"""


def build_apply_gate(decisions: dict) -> str:
    rules = decisions.get("rules", [])
    if not rules:
        raise ValueError("At least one category mapping rule is required")
    default_decision = decisions.get("defaultDecision", "REVIEW_REQUIRED").strip().upper()
    allowed_decisions = {"AUTO_SAFE", "REVIEW_REQUIRED"}
    if default_decision not in allowed_decisions:
        raise ValueError(f"Invalid default decision: {default_decision}")

    rows = []
    seen_tags = set()
    for rule in rules:
        tag = rule["sourceTag"].strip().lower()
        slug = rule["categorySlug"].strip()
        priority = int(rule["primaryPriority"])
        decision = rule.get("decision", default_decision).strip().upper()
        if not tag.startswith("en:"):
            raise ValueError(f"Only explicit OFF English tags are accepted: {tag}")
        if tag in seen_tags:
            raise ValueError(f"Duplicate source tag: {tag}")
        if priority < 1:
            raise ValueError(f"Invalid priority for {tag}")
        if decision not in allowed_decisions:
            raise ValueError(f"Invalid decision for {tag}: {decision}")
        seen_tags.add(tag)
        rows.append(
            f"({sql_literal(tag)}, {sql_literal(slug)}, {priority}, {sql_literal(decision)})"
        )

    values = ",\n        ".join(rows)
    return f"""-- Generated focused read-only apply gate. No catalog mutation.
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '2min';

WITH rules(source_tag, category_slug, primary_priority, decision) AS (
    VALUES
        {values}
), raw_candidates AS (
    SELECT source.food_item_id, rules.category_slug, rules.primary_priority, rules.decision
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
), safe_products AS (
    SELECT food_item_id
    FROM ranked
    WHERE primary_rank = 1 AND decision = 'AUTO_SAFE'
), safe_assignments AS (
    SELECT candidates.food_item_id, candidates.category_slug,
           candidates.primary_priority
    FROM candidates
    JOIN safe_products USING (food_item_id)
    WHERE candidates.decision = 'AUTO_SAFE'
)
SELECT 'APPLY_GATE' AS section,
       (SELECT count(*) FROM rules WHERE decision = 'AUTO_SAFE') AS auto_rules,
       (SELECT count(*) FROM safe_products) AS safe_products,
       (SELECT count(*) FROM safe_assignments) AS safe_assignments,
       (SELECT count(DISTINCT category_slug) FROM safe_assignments) AS categories,
       (SELECT count(*) FROM rules
         LEFT JOIN food_categories ON food_categories.slug = rules.category_slug
        WHERE rules.decision = 'AUTO_SAFE' AND food_categories.id IS NULL) AS missing_categories;

COMMIT;
"""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--decisions", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--apply-gate-only", action="store_true")
    args = parser.parse_args()
    decisions = json.loads(Path(args.decisions).read_text(encoding="utf-8"))
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    sql = build_apply_gate(decisions) if args.apply_gate_only else build(decisions)
    output.write_text(sql, encoding="utf-8", newline="\n")
    print(json.dumps({"rules": len(decisions["rules"]), "output": str(output)}, indent=2))


if __name__ == "__main__":
    main()

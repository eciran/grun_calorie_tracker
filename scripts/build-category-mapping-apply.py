#!/usr/bin/env python3
import argparse
import json
from pathlib import Path


ALLOWED_DECISIONS = {"AUTO_SAFE", "REVIEW_REQUIRED"}


def sql_literal(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def build(decisions: dict, expected_products: int, expected_assignments: int,
          expected_categories: int, commit: bool) -> str:
    rules = decisions.get("rules", [])
    if not rules:
        raise ValueError("At least one category mapping rule is required")
    default_decision = decisions.get("defaultDecision", "REVIEW_REQUIRED").strip().upper()
    if default_decision not in ALLOWED_DECISIONS:
        raise ValueError(f"Invalid default decision: {default_decision}")

    seen_tags = set()
    rows = []
    auto_rule_count = 0
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
        if decision not in ALLOWED_DECISIONS:
            raise ValueError(f"Invalid decision for {tag}: {decision}")
        seen_tags.add(tag)
        auto_rule_count += decision == "AUTO_SAFE"
        rows.append(
            f"({sql_literal(tag)}, {sql_literal(slug)}, {priority}, {sql_literal(decision)})"
        )

    if min(expected_products, expected_assignments, expected_categories) < 1:
        raise ValueError("Expected gate counts must be positive")

    values = ",\n        ".join(rows)
    finish = "COMMIT;" if commit else "ROLLBACK;"
    mode = "APPLY" if commit else "REHEARSAL_ROLLBACK"
    return f"""-- Generated bounded category mapping {mode.lower()}.
BEGIN ISOLATION LEVEL REPEATABLE READ;
SET LOCAL statement_timeout = '5min';
SET LOCAL lock_timeout = '15s';

CREATE TEMP TABLE tmp_category_rules(
    source_tag text primary key,
    category_slug text not null,
    primary_priority integer not null,
    decision text not null
) ON COMMIT DROP;

INSERT INTO tmp_category_rules(source_tag, category_slug, primary_priority, decision)
VALUES
        {values};

CREATE TEMP TABLE tmp_category_candidates ON COMMIT DROP AS
SELECT source.food_item_id, rules.category_slug,
       min(rules.primary_priority) AS primary_priority,
       CASE WHEN bool_or(rules.decision = 'AUTO_SAFE')
            THEN 'AUTO_SAFE' ELSE 'REVIEW_REQUIRED' END AS decision
FROM food_item_source_categories source
JOIN tmp_category_rules rules ON lower(trim(source.category_tag)) = rules.source_tag
GROUP BY source.food_item_id, rules.category_slug;

CREATE TEMP TABLE tmp_category_ranked ON COMMIT DROP AS
SELECT candidates.*,
       row_number() OVER (
           PARTITION BY food_item_id
           ORDER BY primary_priority, category_slug
       ) AS primary_rank
FROM tmp_category_candidates candidates;

CREATE TEMP TABLE tmp_safe_products ON COMMIT DROP AS
SELECT food_item_id, category_slug AS primary_category_slug
FROM tmp_category_ranked
WHERE primary_rank = 1 AND decision = 'AUTO_SAFE';

CREATE TEMP TABLE tmp_safe_assignments ON COMMIT DROP AS
SELECT candidates.food_item_id, candidates.category_slug,
       candidates.primary_priority
FROM tmp_category_candidates candidates
JOIN tmp_safe_products USING (food_item_id)
WHERE candidates.decision = 'AUTO_SAFE';

DO $$
DECLARE
    actual bigint;
BEGIN
    SELECT count(*) INTO actual FROM tmp_category_rules WHERE decision = 'AUTO_SAFE';
    IF actual <> {auto_rule_count} THEN
        RAISE EXCEPTION 'AUTO_SAFE rule gate failed: expected {auto_rule_count}, got %', actual;
    END IF;

    SELECT count(*) INTO actual FROM tmp_safe_products;
    IF actual <> {expected_products} THEN
        RAISE EXCEPTION 'Safe product gate failed: expected {expected_products}, got %', actual;
    END IF;

    SELECT count(*) INTO actual FROM tmp_safe_assignments;
    IF actual <> {expected_assignments} THEN
        RAISE EXCEPTION 'Safe assignment gate failed: expected {expected_assignments}, got %', actual;
    END IF;

    SELECT count(DISTINCT category_slug) INTO actual FROM tmp_safe_assignments;
    IF actual <> {expected_categories} THEN
        RAISE EXCEPTION 'Safe category gate failed: expected {expected_categories}, got %', actual;
    END IF;

    SELECT count(*) INTO actual
    FROM (SELECT DISTINCT category_slug FROM tmp_category_rules WHERE decision = 'AUTO_SAFE') rules
    LEFT JOIN food_categories category ON category.slug = rules.category_slug
    WHERE category.id IS NULL OR category.active IS NOT TRUE;
    IF actual <> 0 THEN
        RAISE EXCEPTION 'Missing or inactive canonical categories: %', actual;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM food_category_source_mappings mapping
        JOIN tmp_category_rules rules
          ON rules.source_tag = mapping.normalized_source_tag
         AND rules.decision = 'AUTO_SAFE'
        JOIN food_categories category ON category.slug = rules.category_slug
        WHERE mapping.data_source = 'OPEN_FOOD_FACTS'
          AND mapping.market_region IS NULL
          AND mapping.category_id <> category.id
    ) THEN
        RAISE EXCEPTION 'Conflicting existing source mapping detected';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM food_item_categories assignment
        JOIN tmp_safe_products product ON product.food_item_id = assignment.food_item_id
        JOIN food_categories category ON category.id = assignment.category_id
        WHERE assignment.primary_category IS TRUE
          AND category.slug <> product.primary_category_slug
    ) THEN
        RAISE EXCEPTION 'Conflicting existing primary category detected';
    END IF;
END $$;

INSERT INTO food_category_source_mappings(
    data_source, source_tag, normalized_source_tag, market_region, category_id,
    status, confidence_score, created_by, updated_by
)
SELECT 'OPEN_FOOD_FACTS', rules.source_tag, rules.source_tag, NULL, category.id,
       'ACTIVE', 95, 'category-mapping-v1', 'category-mapping-v1'
FROM tmp_category_rules rules
JOIN food_categories category ON category.slug = rules.category_slug
WHERE rules.decision = 'AUTO_SAFE'
ON CONFLICT DO NOTHING;

INSERT INTO food_item_categories(
    food_item_id, category_id, primary_category, assignment_source,
    confidence_score, reviewed, created_by, updated_by
)
SELECT assignment.food_item_id, category.id,
       assignment.category_slug = product.primary_category_slug,
       'SOURCE_MAPPING', 95, false, 'category-mapping-v1', 'category-mapping-v1'
FROM tmp_safe_assignments assignment
JOIN tmp_safe_products product USING (food_item_id)
JOIN food_categories category ON category.slug = assignment.category_slug
ON CONFLICT DO NOTHING;

DO $$
DECLARE
    actual bigint;
BEGIN
    SELECT count(*) INTO actual
    FROM tmp_category_rules rules
    JOIN food_categories category ON category.slug = rules.category_slug
    JOIN food_category_source_mappings mapping
      ON mapping.data_source = 'OPEN_FOOD_FACTS'
     AND mapping.normalized_source_tag = rules.source_tag
     AND mapping.market_region IS NULL
     AND mapping.category_id = category.id
     AND mapping.status = 'ACTIVE'
    WHERE rules.decision = 'AUTO_SAFE';
    IF actual <> {auto_rule_count} THEN
        RAISE EXCEPTION 'Applied source mapping verification failed: expected {auto_rule_count}, got %', actual;
    END IF;

    SELECT count(*) INTO actual
    FROM tmp_safe_assignments expected
    JOIN food_categories category ON category.slug = expected.category_slug
    JOIN food_item_categories actual_assignment
      ON actual_assignment.food_item_id = expected.food_item_id
     AND actual_assignment.category_id = category.id;
    IF actual <> {expected_assignments} THEN
        RAISE EXCEPTION 'Applied assignment verification failed: expected {expected_assignments}, got %', actual;
    END IF;

    SELECT count(*) INTO actual
    FROM tmp_safe_products expected
    JOIN food_categories category ON category.slug = expected.primary_category_slug
    JOIN food_item_categories assignment
      ON assignment.food_item_id = expected.food_item_id
     AND assignment.category_id = category.id
     AND assignment.primary_category IS TRUE;
    IF actual <> {expected_products} THEN
        RAISE EXCEPTION 'Applied primary verification failed: expected {expected_products}, got %', actual;
    END IF;
END $$;

SELECT '{mode}' AS mode,
       (SELECT count(*) FROM tmp_category_rules WHERE decision = 'AUTO_SAFE') AS source_mappings,
       (SELECT count(*) FROM tmp_safe_products) AS products,
       (SELECT count(*) FROM tmp_safe_assignments) AS assignments,
       (SELECT count(DISTINCT category_slug) FROM tmp_safe_assignments) AS categories;

{finish}
"""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--decisions", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--expected-products", type=int, required=True)
    parser.add_argument("--expected-assignments", type=int, required=True)
    parser.add_argument("--expected-categories", type=int, required=True)
    parser.add_argument("--commit", action="store_true")
    args = parser.parse_args()

    decisions = json.loads(Path(args.decisions).read_text(encoding="utf-8"))
    sql = build(
        decisions,
        args.expected_products,
        args.expected_assignments,
        args.expected_categories,
        args.commit,
    )
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(sql, encoding="utf-8", newline="\n")
    print(json.dumps({"output": str(output), "commit": args.commit}, indent=2))


if __name__ == "__main__":
    main()

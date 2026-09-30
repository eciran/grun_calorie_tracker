#!/usr/bin/env python3
import argparse
import json
from pathlib import Path


def lit(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def array(values: list[str]) -> str:
    return "ARRAY[" + ",".join(lit(value.strip().lower()) for value in values) + "]::text[]"


def build(base: dict, refinements: dict) -> str:
    default = base.get("defaultDecision", "REVIEW_REQUIRED").strip().upper()
    base_rows = []
    for rule in base.get("rules", []):
        decision = rule.get("decision", default).strip().upper()
        base_rows.append(
            f"({lit(rule['sourceTag'].strip().lower())}, {lit(rule['categorySlug'].strip())}, "
            f"{int(rule['primaryPriority'])}, {lit(decision)})"
        )
    refinement_rows = []
    ids = set()
    for rule in refinements.get("rules", []):
        rule_id = rule["id"].strip()
        required = rule.get("requiredAnyTags", [])
        excluded = rule.get("excludedAnyTags", [])
        if rule_id in ids:
            raise ValueError(f"Duplicate refinement id: {rule_id}")
        if not required:
            raise ValueError(f"Required tags missing: {rule_id}")
        ids.add(rule_id)
        refinement_rows.append(
            f"({lit(rule_id)}, {lit(rule['sourceReviewCategory'].strip())}, "
            f"{lit(rule['targetCategory'].strip())}, {array(required)}, {array(excluded)})"
        )
    if not base_rows or not refinement_rows:
        raise ValueError("Base and refinement rules are required")

    return f"""-- Read-only conditional category refinement report.
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '3min';
WITH base_rules(source_tag, category_slug, primary_priority, decision) AS (
    VALUES {','.join(base_rows)}
), refinement_rules(rule_id, source_category, target_category, required_tags, excluded_tags) AS (
    VALUES {','.join(refinement_rows)}
), raw AS (
    SELECT source.food_item_id, rule.category_slug, rule.primary_priority, rule.decision
    FROM food_item_source_categories source
    JOIN base_rules rule ON lower(trim(source.category_tag)) = rule.source_tag
), candidates AS (
    SELECT food_item_id, category_slug, min(primary_priority) primary_priority,
           CASE WHEN bool_or(decision='AUTO_SAFE') THEN 'AUTO_SAFE' ELSE 'REVIEW_REQUIRED' END decision
    FROM raw GROUP BY food_item_id, category_slug
), ranked AS (
    SELECT candidates.*, row_number() OVER (
        PARTITION BY food_item_id ORDER BY primary_priority, category_slug
    ) primary_rank FROM candidates
), review_products AS (
    SELECT food_item_id, category_slug FROM ranked
    WHERE primary_rank=1 AND decision='REVIEW_REQUIRED'
      AND NOT EXISTS (
          SELECT 1 FROM food_item_categories assigned
          WHERE assigned.food_item_id=ranked.food_item_id
            AND assigned.primary_category
      )
), product_tags AS (
    SELECT product.food_item_id, product.category_slug,
           array_agg(DISTINCT lower(trim(source.category_tag))) tags
    FROM review_products product
    JOIN food_item_source_categories source ON source.food_item_id=product.food_item_id
    GROUP BY product.food_item_id, product.category_slug
), evaluated AS (
    SELECT tags.food_item_id, rule.rule_id, rule.source_category, rule.target_category,
           tags.tags && rule.required_tags AS has_required,
           tags.tags && rule.excluded_tags AS has_excluded,
           tags.tags
    FROM product_tags tags
    JOIN refinement_rules rule ON rule.source_category=tags.category_slug
), eligible AS (
    SELECT * FROM evaluated WHERE has_required AND NOT has_excluded
), conflicts AS (
    SELECT food_item_id, count(DISTINCT target_category) targets
    FROM eligible GROUP BY food_item_id HAVING count(DISTINCT target_category)>1
), sample_base AS (
    SELECT eligible.*, coalesce(item.display_name,item.name) product_name,
           coalesce(item.brand,'') brand,
           row_number() OVER (PARTITION BY rule_id ORDER BY md5(item.id::text),item.id) sample_rank
    FROM eligible JOIN food_items item ON item.id=eligible.food_item_id
), report AS (
    SELECT 0 report_order, 0::bigint item_order, 'SUMMARY'::text section,
           (SELECT count(DISTINCT food_item_id) FROM eligible)::text value1,
           (SELECT count(*) FROM eligible)::text value2,
           (SELECT count(*) FROM conflicts)::text value3,
           (SELECT count(*) FROM review_products)::text value4, ''::text value5
    UNION ALL
    SELECT 1, -count(*), 'RULE', rule.rule_id, rule.source_category, rule.target_category,
           count(*) FILTER (WHERE evaluated.has_required AND NOT evaluated.has_excluded)::text,
           count(*) FILTER (WHERE evaluated.has_required AND evaluated.has_excluded)::text
    FROM refinement_rules rule
    LEFT JOIN evaluated ON evaluated.rule_id=rule.rule_id GROUP BY rule.rule_id,rule.source_category,rule.target_category
    UNION ALL
    SELECT 2, food_item_id, 'CONFLICT', food_item_id::text, targets::text, '', '', '' FROM conflicts
    UNION ALL
    SELECT 3, sample_rank, 'SAMPLE', rule_id, food_item_id::text, product_name, brand,
           array_to_string(tags,',') FROM sample_base WHERE sample_rank<=25
)
SELECT section,value1,value2,value3,value4,value5 FROM report
ORDER BY report_order,item_order,value1;
COMMIT;
"""


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--base",required=True)
    parser.add_argument("--refinements",required=True)
    parser.add_argument("--output",required=True)
    args=parser.parse_args()
    sql=build(json.loads(Path(args.base).read_text(encoding="utf-8")),json.loads(Path(args.refinements).read_text(encoding="utf-8")))
    output=Path(args.output); output.parent.mkdir(parents=True,exist_ok=True); output.write_text(sql,encoding="utf-8",newline="\n")

if __name__=="__main__": main()

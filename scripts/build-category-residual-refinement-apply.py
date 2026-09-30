#!/usr/bin/env python3
import argparse
import importlib.util
import json
from pathlib import Path


REPORT_PATH = Path(__file__).with_name("build-category-conditional-refinement-report.py")
SPEC = importlib.util.spec_from_file_location("conditional_report", REPORT_PATH)
REPORT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(REPORT)


def build(base: dict, refinements: dict, expected: int, commit: bool) -> str:
    if expected < 1:
        raise ValueError("Expected assignments must be positive")
    version = int(refinements.get("version", 0))
    if version < 1:
        raise ValueError("Refinement version must be positive")
    actor = f"conditional-category-refinement-v{version}"
    default = base.get("defaultDecision", "REVIEW_REQUIRED").strip().upper()
    base_rows = []
    for rule in base["rules"]:
        decision = rule.get("decision", default).strip().upper()
        base_rows.append(
            f"({REPORT.lit(rule['sourceTag'].strip().lower())},"
            f"{REPORT.lit(rule['categorySlug'].strip())},{int(rule['primaryPriority'])},"
            f"{REPORT.lit(decision)})"
        )
    ref_rows = []
    for rule in refinements["rules"]:
        ref_rows.append(
            f"({REPORT.lit(rule['id'])},{REPORT.lit(rule['sourceReviewCategory'])},"
            f"{REPORT.lit(rule['targetCategory'])},{REPORT.array(rule['requiredAnyTags'])},"
            f"{REPORT.array(rule.get('excludedAnyTags', []))})"
        )
    finish = "COMMIT;" if commit else "ROLLBACK;"
    mode = "APPLY" if commit else "REHEARSAL_ROLLBACK"
    return f"""BEGIN ISOLATION LEVEL REPEATABLE READ;
SET LOCAL statement_timeout='5min'; SET LOCAL lock_timeout='15s';
CREATE TEMP TABLE tmp_base(source_tag text,category_slug text,primary_priority int,decision text) ON COMMIT DROP;
INSERT INTO tmp_base VALUES {','.join(base_rows)};
CREATE TEMP TABLE tmp_ref(rule_id text,source_category text,target_category text,required_tags text[],excluded_tags text[]) ON COMMIT DROP;
INSERT INTO tmp_ref VALUES {','.join(ref_rows)};
CREATE TEMP TABLE tmp_raw ON COMMIT DROP AS
SELECT s.food_item_id,r.category_slug,r.primary_priority,r.decision
FROM food_item_source_categories s JOIN tmp_base r ON lower(trim(s.category_tag))=r.source_tag;
CREATE TEMP TABLE tmp_candidates ON COMMIT DROP AS
SELECT food_item_id,category_slug,min(primary_priority) primary_priority,
       CASE WHEN bool_or(decision='AUTO_SAFE') THEN 'AUTO_SAFE' ELSE 'REVIEW_REQUIRED' END decision
FROM tmp_raw GROUP BY food_item_id,category_slug;
CREATE TEMP TABLE tmp_ranked ON COMMIT DROP AS
SELECT c.*,row_number() OVER(PARTITION BY food_item_id ORDER BY primary_priority,category_slug) primary_rank
FROM tmp_candidates c;
CREATE TEMP TABLE tmp_review ON COMMIT DROP AS
SELECT ranked.food_item_id,ranked.category_slug FROM tmp_ranked ranked
WHERE ranked.primary_rank=1 AND ranked.decision='REVIEW_REQUIRED'
  AND NOT EXISTS (
      SELECT 1 FROM food_item_categories assigned
      WHERE assigned.food_item_id=ranked.food_item_id AND assigned.primary_category
  );
CREATE TEMP TABLE tmp_tags ON COMMIT DROP AS
SELECT p.food_item_id,p.category_slug,array_agg(DISTINCT lower(trim(s.category_tag))) tags
FROM tmp_review p JOIN food_item_source_categories s ON s.food_item_id=p.food_item_id
GROUP BY p.food_item_id,p.category_slug;
CREATE TEMP TABLE tmp_eligible ON COMMIT DROP AS
SELECT t.food_item_id,r.rule_id,r.target_category FROM tmp_tags t JOIN tmp_ref r ON r.source_category=t.category_slug
WHERE t.tags&&r.required_tags AND NOT(t.tags&&r.excluded_tags);
DO $$ DECLARE actual bigint; BEGIN
 SELECT count(*) INTO actual FROM tmp_eligible;
 IF actual<>{expected} THEN RAISE EXCEPTION 'Residual assignment gate failed: expected {expected}, got %',actual; END IF;
 SELECT count(*) INTO actual FROM (SELECT food_item_id FROM tmp_eligible GROUP BY food_item_id HAVING count(DISTINCT target_category)>1) x;
 IF actual<>0 THEN RAISE EXCEPTION 'Residual target conflicts: %',actual; END IF;
 SELECT count(*) INTO actual FROM (SELECT DISTINCT target_category FROM tmp_eligible) e LEFT JOIN food_categories c ON c.slug=e.target_category WHERE c.id IS NULL OR c.active IS NOT TRUE;
 IF actual<>0 THEN RAISE EXCEPTION 'Missing target categories: %',actual; END IF;
 IF EXISTS(SELECT 1 FROM food_item_categories a JOIN tmp_eligible e ON e.food_item_id=a.food_item_id WHERE a.primary_category) THEN RAISE EXCEPTION 'Existing primary assignment in residual cohort'; END IF;
END $$;
INSERT INTO food_item_categories(food_item_id,category_id,primary_category,assignment_source,confidence_score,reviewed,created_by,updated_by)
SELECT e.food_item_id,c.id,true,'SOURCE_MAPPING',97,false,{REPORT.lit(actor)},{REPORT.lit(actor)}
FROM tmp_eligible e JOIN food_categories c ON c.slug=e.target_category ON CONFLICT DO NOTHING;
DO $$ DECLARE actual bigint; BEGIN
 SELECT count(*) INTO actual FROM tmp_eligible e JOIN food_categories c ON c.slug=e.target_category
 JOIN food_item_categories a ON a.food_item_id=e.food_item_id AND a.category_id=c.id AND a.primary_category;
 IF actual<>{expected} THEN RAISE EXCEPTION 'Residual apply verification failed: expected {expected}, got %',actual; END IF;
END $$;
SELECT '{mode}' mode,count(*) assignments,count(DISTINCT food_item_id) products,
       count(DISTINCT target_category) categories FROM tmp_eligible;
{finish}
"""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", required=True)
    parser.add_argument("--refinements", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--expected", type=int, required=True)
    parser.add_argument("--commit", action="store_true")
    args = parser.parse_args()
    sql = build(
        json.loads(Path(args.base).read_text(encoding="utf-8")),
        json.loads(Path(args.refinements).read_text(encoding="utf-8")),
        args.expected,
        args.commit,
    )
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(sql, encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()

"""Bounded R6 category/duplicate inventory. SELECT only; candidates, not approvals."""
import argparse
import importlib.util
from pathlib import Path

spec = importlib.util.spec_from_file_location("duplicates", Path(__file__).with_name("build-branded-product-duplicate-analysis.py"))
duplicates = importlib.util.module_from_spec(spec)
spec.loader.exec_module(duplicates)


def build(group_limit=100, member_limit=5):
    if not 1 <= group_limit <= 200 or not 2 <= member_limit <= 10:
        raise ValueError("Bounded group/member sample required")
    cte = duplicates.build(group_limit, member_limit, cte_only=True)
    return f"""BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout='90s'; SET LOCAL lock_timeout='3s';
{cte},
published AS MATERIALIZED (
 SELECT f.id,f.catalog_type,f.data_source,f.market_region,f.brand_id FROM food_items f
 WHERE NOT coalesce(f.is_custom,false) AND f.publication_status='PUBLISHED'
), assignments AS (
 SELECT a.food_item_id,count(*) AS assigned,
 count(*) FILTER(WHERE c.active) AS active_assigned,
 count(*) FILTER(WHERE a.primary_category AND c.active) AS active_primary,
 count(*) FILTER(WHERE a.primary_category) AS all_primary
 FROM food_item_categories a JOIN food_categories c ON c.id=a.category_id GROUP BY a.food_item_id
), source_tags AS (
 SELECT food_item_id,count(*) AS tags FROM food_item_source_categories GROUP BY food_item_id
), coverage AS MATERIALIZED (
 SELECT p.*,coalesce(a.assigned,0) AS assigned,coalesce(a.active_assigned,0) AS active_assigned,
 coalesce(a.active_primary,0) AS active_primary,coalesce(a.all_primary,0) AS all_primary,
 coalesce(s.tags,0) AS source_tags
 FROM published p LEFT JOIN assignments a ON a.food_item_id=p.id LEFT JOIN source_tags s ON s.food_item_id=p.id
), sampled_groups AS MATERIALIZED (
 SELECT * FROM classified ORDER BY variant_signal, nutrition_missing_count, serving_missing_count,
 product_count DESC,brand_key,name_key LIMIT {group_limit}
), frames AS (
 SELECT 'inventory'::text AS id,jsonb_build_object(
 'nonCustom',(SELECT count(*) FROM food_items WHERE NOT coalesce(is_custom,false)),
 'published',count(*),'withAnyCategory',count(*) FILTER(WHERE assigned>0),
 'withActivePrimary',count(*) FILTER(WHERE active_primary>0),
 'withoutActivePrimary',count(*) FILTER(WHERE active_primary=0),
 'withoutActivePrimaryWithTags',count(*) FILTER(WHERE active_primary=0 AND source_tags>0),
 'withoutActivePrimaryWithoutTags',count(*) FILTER(WHERE active_primary=0 AND source_tags=0),
 'brandedWithoutCanonicalBrand',count(*) FILTER(WHERE catalog_type='BRANDED_PRODUCT' AND brand_id IS NULL),
 'activeCollapseGroups',(SELECT count(*) FROM food_branded_duplicate_search_collapses WHERE active)) AS values FROM coverage
 UNION ALL
 SELECT 'category_matrix',coalesce(jsonb_agg(to_jsonb(t) ORDER BY catalog_type,market_region),'[]'::jsonb)
 FROM (SELECT catalog_type,market_region,count(*) AS products,count(*) FILTER(WHERE active_primary>0) AS with_primary,
 count(*) FILTER(WHERE active_primary=0 AND source_tags>0) AS source_tags_no_primary,
 count(*) FILTER(WHERE active_primary=0 AND source_tags=0) AS no_tags_no_primary
 FROM coverage GROUP BY catalog_type,market_region) t
 UNION ALL
 SELECT 'category_distribution',coalesce(jsonb_agg(to_jsonb(t) ORDER BY slug),'[]'::jsonb)
 FROM (SELECT c.slug,c.name_en,c.name_tr,c.active,parent.slug AS parent_slug,
 count(DISTINCT a.food_item_id) AS assigned_products,count(DISTINCT a.food_item_id) FILTER(WHERE a.primary_category) AS primary_products
 FROM food_categories c LEFT JOIN food_categories parent ON parent.id=c.parent_id
 LEFT JOIN food_item_categories a ON a.category_id=c.id AND a.food_item_id IN (SELECT id FROM published)
 GROUP BY c.id,parent.slug) t
 UNION ALL
 SELECT 'unassigned_source_tags',coalesce(jsonb_agg(to_jsonb(t) ORDER BY products DESC,category_tag),'[]'::jsonb)
 FROM (SELECT s.category_tag,count(DISTINCT s.food_item_id) AS products,
 (array_agg(DISTINCT s.food_item_id ORDER BY s.food_item_id))[1:5] AS sample_ids
 FROM food_item_source_categories s JOIN coverage c ON c.id=s.food_item_id WHERE c.active_primary=0
 GROUP BY s.category_tag ORDER BY products DESC,s.category_tag LIMIT 100) t
 UNION ALL
 SELECT 'duplicate_summary',jsonb_build_object('groups',count(*),'candidateRows',coalesce(sum(product_count),0),
 'variantGroups',count(*) FILTER(WHERE variant_signal),'sampleLimit',{group_limit},'memberLimit',{member_limit},
 'sampledGroups',(SELECT count(*) FROM sampled_groups),'candidateOnly',true,
 'decisions',(SELECT coalesce(jsonb_object_agg(decision,n),'{{}}'::jsonb) FROM
 (SELECT decision,count(*) AS n FROM classified GROUP BY decision) d)) FROM classified
 UNION ALL
 SELECT 'duplicate_group_index',coalesce(jsonb_agg(to_jsonb(g) ORDER BY g.variant_signal,g.product_count DESC,g.brand_key,g.name_key),'[]'::jsonb)
 FROM classified g
 UNION ALL
 SELECT 'duplicate_samples',coalesce(jsonb_agg(to_jsonb(g)||jsonb_build_object('members',(
 SELECT coalesce(jsonb_agg(to_jsonb(m) ORDER BY m.id),'[]'::jsonb) FROM (
 SELECT f.id,to_jsonb(f) AS food,
 coalesce((SELECT jsonb_agg(to_jsonb(o) ORDER BY o.id) FROM food_item_serving_options o WHERE o.food_item_id=f.id),'[]'::jsonb) AS options,
 coalesce((SELECT jsonb_agg(e) FROM (SELECT field_name,numeric_value,basis,provider,external_id,source_version,observed_at,fingerprint
 FROM food_product_source_evidence WHERE food_item_id=f.id ORDER BY id DESC LIMIT 30) e),'[]'::jsonb) AS source_evidence,
 (SELECT count(*) FROM food_product_source_evidence WHERE food_item_id=f.id) AS source_evidence_count,
 coalesce((SELECT jsonb_agg(s.category_tag ORDER BY s.category_tag) FROM food_item_source_categories s WHERE s.food_item_id=f.id),'[]'::jsonb) AS source_tags
 FROM branded_identity i JOIN food_items f ON f.id=i.id WHERE i.brand_key=g.brand_key AND i.name_key=g.name_key
 ORDER BY f.id LIMIT {member_limit}) m),'sampleComplete',g.product_count<={member_limit},'applyAllowed',false)
 ORDER BY g.variant_signal,g.product_count DESC,g.brand_key,g.name_key),'[]'::jsonb) FROM sampled_groups g
 UNION ALL
 SELECT 'integrity',jsonb_build_object('multiplePrimaryPublished',count(*) FILTER(WHERE all_primary>1),
 'inactiveOnlyPrimary',count(*) FILTER(WHERE all_primary>0 AND active_primary=0),
 'orphanAssignments',(SELECT count(*) FROM food_item_categories a LEFT JOIN food_items f ON f.id=a.food_item_id
 LEFT JOIN food_categories c ON c.id=a.category_id WHERE f.id IS NULL OR c.id IS NULL)) FROM coverage
 UNION ALL
 SELECT 'source_identity_collisions',coalesce(jsonb_agg(to_jsonb(t)),'[]'::jsonb) FROM (
 SELECT data_source,source_key,count(*) AS products FROM food_items WHERE NOT coalesce(is_custom,false)
 AND nullif(trim(source_key),'') IS NOT NULL GROUP BY data_source,source_key HAVING count(*)>1
 ORDER BY products DESC,data_source,source_key LIMIT 100) t
)
SELECT jsonb_build_object('kind','start','time',now(),'readonly',current_setting('transaction_read_only'),
 'count',(SELECT count(*) FROM frames),'schema',(SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1))
UNION ALL
SELECT jsonb_build_object('kind','row','row',jsonb_build_object('id',id,'values',values)) FROM frames
UNION ALL
SELECT jsonb_build_object('kind','end','count',(SELECT count(*) FROM frames));
ROLLBACK;
"""


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(build(), encoding="utf-8")

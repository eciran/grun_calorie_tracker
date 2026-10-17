"""Small read-only integrity/availability query, not API ranking certification."""
import re


def query(owner):
    if not re.fullmatch(r"r6-approved-[a-f0-9]{12}", owner or ""):
        raise ValueError("Exact approved operation marker required")
    return f"""BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout='90s'; SET LOCAL lock_timeout='3s';
SELECT jsonb_build_object('kind','start','time',now(),'readonly',current_setting('transaction_read_only'),'count',1,
 'schema',(SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1));
WITH cohort AS(SELECT a.* FROM food_item_categories a WHERE created_by='{owner}'),
queries(q) AS(VALUES('banana'),('muz'),('broccoli'),('brokoli'),('milk'),('sut'),('chicken breast'),('tavuk gogsu'),('rice'),('pirinc'))
SELECT jsonb_build_object('kind','row','row',jsonb_build_object('id','r6-integrity',
 'global',jsonb_build_object(
 'nonCustom',(SELECT count(*) FROM food_items WHERE NOT coalesce(is_custom,false)),
 'published',(SELECT count(*) FROM food_items WHERE NOT coalesce(is_custom,false) AND publication_status='PUBLISHED'),
 'publishedCategorized',(SELECT count(DISTINCT a.food_item_id) FROM food_item_categories a JOIN food_items f ON f.id=a.food_item_id JOIN food_categories c ON c.id=a.category_id WHERE a.primary_category AND c.active AND NOT coalesce(f.is_custom,false) AND f.publication_status='PUBLISHED'),
 'duplicatePrimary',(SELECT count(*) FROM(SELECT food_item_id FROM food_item_categories WHERE primary_category GROUP BY food_item_id HAVING count(*)>1)t),
 'orphanAssignments',(SELECT count(*) FROM food_item_categories a LEFT JOIN food_categories c ON c.id=a.category_id LEFT JOIN food_items f ON f.id=a.food_item_id WHERE c.id IS NULL OR f.id IS NULL)),
 'operation',jsonb_build_object('assignments',(SELECT count(*) FROM cohort),
 'validAssignments',(SELECT count(*) FROM cohort WHERE primary_category AND reviewed AND assignment_source='ADMIN' AND updated_by='{owner}' AND confidence_score IS NULL),
 'byCategory',(SELECT jsonb_object_agg(slug,n) FROM(SELECT c.slug,count(*) n FROM cohort a JOIN food_categories c ON c.id=a.category_id GROUP BY c.slug)t),
 'staleMissingCategoryIssues',(SELECT count(*) FROM food_product_quality_issues i JOIN cohort a ON a.food_item_id=i.food_item_id WHERE i.issue_type='MISSING_CANONICAL_CATEGORY' AND NOT coalesce(i.resolved,false))),
 'availabilityOnly',(SELECT jsonb_object_agg(q,n) FROM(SELECT q,(SELECT count(*) FROM food_items f WHERE NOT coalesce(f.is_custom,false) AND f.publication_status='PUBLISHED'
 AND (lower(coalesce(f.display_name,f.name)) LIKE '%'||q||'%' OR EXISTS(SELECT 1 FROM food_item_search_aliases a WHERE a.food_item_id=f.id AND a.active AND a.normalized_alias LIKE '%'||q||'%'))) n FROM queries)t),
 'availabilityLimitation','STORED_NAME_OR_ACTIVE_ALIAS_MATCH_NOT_AUTHENTICATED_API_OR_RANKING'));
SELECT jsonb_build_object('kind','end','count',1);
ROLLBACK;
"""

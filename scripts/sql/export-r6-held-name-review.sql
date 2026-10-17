BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout='90s'; SET LOCAL lock_timeout='3s';
WITH candidates AS MATERIALIZED (
 SELECT f.* FROM food_items f WHERE NOT coalesce(f.is_custom,false)
 AND f.publication_status='PUBLISHED' AND f.id IN (__SELECTED_IDS__)
), frames AS (
 SELECT 0 AS frame,0::bigint AS row_id,jsonb_build_object('kind','start','time',now(),
 'readonly',current_setting('transaction_read_only'),'count',(SELECT count(*) FROM candidates),
 'schema',(SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1)) AS message
 UNION ALL
 SELECT 1,f.id,jsonb_build_object('kind','row','row',jsonb_build_object(
 'id',f.id,'food',to_jsonb(f),
 'assignments',coalesce((SELECT jsonb_agg(to_jsonb(a)||jsonb_build_object('slug',c.slug,'active',c.active) ORDER BY a.category_id)
 FROM food_item_categories a JOIN food_categories c ON c.id=a.category_id WHERE a.food_item_id=f.id),'[]'::jsonb),
 'source_tags',coalesce((SELECT jsonb_agg(s.category_tag ORDER BY s.category_tag) FROM food_item_source_categories s WHERE s.food_item_id=f.id),'[]'::jsonb),
 'evidence',coalesce((SELECT jsonb_agg(to_jsonb(e) ORDER BY e.id) FROM food_product_source_evidence e WHERE e.food_item_id=f.id),'[]'::jsonb),
 'name_review',jsonb_build_object(
 'aliases',coalesce((SELECT jsonb_agg(to_jsonb(a) ORDER BY a.id) FROM food_item_search_aliases a WHERE a.food_item_id=f.id),'[]'::jsonb),
 'localizations',coalesce((SELECT jsonb_agg(to_jsonb(l) ORDER BY l.id) FROM food_item_localizations l WHERE l.food_item_id=f.id),'[]'::jsonb),
 'options',coalesce((SELECT jsonb_agg(to_jsonb(o) ORDER BY o.id) FROM food_item_serving_options o WHERE o.food_item_id=f.id),'[]'::jsonb),
 'option_localizations',coalesce((SELECT jsonb_agg(to_jsonb(l) ORDER BY l.id) FROM food_item_serving_option_localizations l
 JOIN food_item_serving_options o ON o.id=l.serving_option_id WHERE o.food_item_id=f.id),'[]'::jsonb))))
 FROM candidates f
 UNION ALL
 SELECT 2,0::bigint,jsonb_build_object('kind','metrics','values',jsonb_build_object(
 'requestedAliasConflicts',coalesce((SELECT jsonb_agg(to_jsonb(a) ORDER BY a.food_item_id,a.id)
 FROM food_item_search_aliases a WHERE a.active AND (a.language,a.normalized_alias) IN (__REQUESTED_ALIASES__)),'[]'::jsonb)))
 UNION ALL
 SELECT 3,0::bigint,jsonb_build_object('kind','end','count',(SELECT count(*) FROM candidates))
)
SELECT message FROM frames ORDER BY frame,row_id;
ROLLBACK;

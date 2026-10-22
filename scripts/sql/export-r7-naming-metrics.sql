BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout='90s'; SET LOCAL lock_timeout='3s';

WITH base AS MATERIALIZED (
  SELECT id, publication_status::text publication_status, catalog_type::text catalog_type,
         data_source::text data_source, name, display_name, short_display_name, brand
  FROM food_items WHERE NOT coalesce(is_custom,false)
), fields AS MATERIALIZED (
  SELECT id,'display_name' field_name,display_name value FROM base
  UNION ALL SELECT id,'short_display_name',short_display_name FROM base
), locale_fields AS MATERIALIZED (
  SELECT l.food_item_id id,l.language::text language,'display_name' field_name,l.display_name value
  FROM food_item_localizations l JOIN base b ON b.id=l.food_item_id WHERE l.active
  UNION ALL
  SELECT l.food_item_id,l.language::text,'short_display_name',l.short_display_name
  FROM food_item_localizations l JOIN base b ON b.id=l.food_item_id WHERE l.active
), metrics AS (
 SELECT jsonb_build_object(
   'products',(SELECT count(*) FROM base),
   'published',(SELECT count(*) FROM base WHERE publication_status='PUBLISHED'),
   'missingDisplay',(SELECT count(*) FROM base WHERE nullif(btrim(display_name),'') IS NULL),
   'baseFields',(SELECT count(*) FROM fields WHERE value IS NOT NULL),
   'allLowerOrUpper',(SELECT count(*) FROM fields WHERE value IS NOT NULL AND lower(value)<>upper(value) AND (value=lower(value) OR value=upper(value))),
   'cosmeticWhitespace',(SELECT count(*) FROM fields WHERE value IS NOT NULL AND value IS DISTINCT FROM regexp_replace(regexp_replace(btrim(value),'[[:space:]]+',' ','g'),'[[:space:]]+([,.;:!?])','\1','g')),
   'htmlCandidates',(SELECT count(*) FROM fields WHERE value ~ '</?[A-Za-z][^>]*>'),
   'controlCandidates',(SELECT count(*) FROM fields WHERE value ~ '[[:cntrl:]]'),
   'unbalancedParentheses',(SELECT count(*) FROM fields WHERE length(value)-length(replace(value,'(','')) <> length(value)-length(replace(value,')',''))),
   'displayShortMismatch',(SELECT count(*) FROM base WHERE display_name IS DISTINCT FROM short_display_name),
   'activeLocalizationFields',(SELECT count(*) FROM locale_fields WHERE value IS NOT NULL),
   'localizationCosmeticWhitespace',(SELECT count(*) FROM locale_fields WHERE value IS NOT NULL AND value IS DISTINCT FROM regexp_replace(regexp_replace(btrim(value),'[[:space:]]+',' ','g'),'[[:space:]]+([,.;:!?])','\1','g')),
   'localizationAllLowerOrUpper',(SELECT count(*) FROM locale_fields WHERE value IS NOT NULL AND lower(value)<>upper(value) AND (value=lower(value) OR value=upper(value))),
   'activeEnProducts',(SELECT count(DISTINCT id) FROM locale_fields WHERE language='EN'),
   'activeTrProducts',(SELECT count(DISTINCT id) FROM locale_fields WHERE language='TR')
 ) values
), frames AS (
 SELECT 0 frame,'' row_id,json_build_object('kind','start','time',now(),'readonly',current_setting('transaction_read_only'),'count',1,'schema',(SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1)) message
 UNION ALL SELECT 1,'metrics',json_build_object('kind','row','row',json_build_object('id','naming_metrics','values',values)) FROM metrics
 UNION ALL SELECT 2,'',json_build_object('kind','end','count',1)
)
SELECT message FROM frames ORDER BY frame,row_id;
ROLLBACK;

BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout='90s'; SET LOCAL lock_timeout='3s';
WITH base AS MATERIALIZED (
 SELECT f.id,f.source_key,f.barcode,f.name,f.display_name,f.short_display_name,f.brand,
        f.publication_status::text publication_status,f.catalog_type::text catalog_type,
        f.data_source::text data_source,f.market_region::text market_region,
        coalesce((SELECT json_agg(to_jsonb(l) ORDER BY l.id) FROM food_item_localizations l WHERE l.food_item_id=f.id AND l.active),'[]'::json) localizations
 FROM food_items f WHERE NOT coalesce(f.is_custom,false)
), issues AS MATERIALIZED (
 SELECT *,array_remove(ARRAY[
   CASE WHEN nullif(btrim(display_name),'') IS NULL THEN 'MISSING_DISPLAY' END,
   CASE WHEN display_name IS DISTINCT FROM short_display_name THEN 'DISPLAY_SHORT_MISMATCH' END,
   CASE WHEN coalesce(display_name,'') ~ '[[:cntrl:]]' OR coalesce(short_display_name,'') ~ '[[:cntrl:]]' THEN 'CONTROL_CHARACTER' END,
   CASE WHEN length(coalesce(display_name,''))-length(replace(coalesce(display_name,''),'(','')) <> length(coalesce(display_name,''))-length(replace(coalesce(display_name,''),')','')) THEN 'DISPLAY_UNBALANCED_PARENTHESES' END,
   CASE WHEN length(coalesce(short_display_name,''))-length(replace(coalesce(short_display_name,''),'(','')) <> length(coalesce(short_display_name,''))-length(replace(coalesce(short_display_name,''),')','')) THEN 'SHORT_UNBALANCED_PARENTHESES' END,
   CASE WHEN display_name IS NOT NULL AND lower(display_name)<>upper(display_name) AND (display_name=lower(display_name) OR display_name=upper(display_name)) THEN 'DISPLAY_ALL_LOWER_OR_UPPER' END,
   CASE WHEN short_display_name IS NOT NULL AND lower(short_display_name)<>upper(short_display_name) AND (short_display_name=lower(short_display_name) OR short_display_name=upper(short_display_name)) THEN 'SHORT_ALL_LOWER_OR_UPPER' END
 ],NULL) issue_codes
 FROM base
), candidates AS MATERIALIZED (SELECT * FROM issues WHERE cardinality(issue_codes)>0), frames AS (
 SELECT 0 frame,0::bigint row_id,json_build_object('kind','start','time',now(),'readonly',current_setting('transaction_read_only'),'count',(SELECT count(*) FROM candidates),'schema',(SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1)) message
 UNION ALL
 SELECT 1,id,json_build_object('kind','row','row',json_build_object('id',id,'source_key',source_key,'barcode',barcode,'name',name,'display_name',display_name,'short_display_name',short_display_name,'brand',brand,'publication_status',publication_status,'catalog_type',catalog_type,'data_source',data_source,'market_region',market_region,'localizations',localizations,'issue_codes',issue_codes)) FROM candidates
 UNION ALL SELECT 2,0,json_build_object('kind','end','count',(SELECT count(*) FROM candidates))
)
SELECT message FROM frames ORDER BY frame,row_id;
ROLLBACK;

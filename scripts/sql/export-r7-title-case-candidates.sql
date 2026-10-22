BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '90s';
SET LOCAL lock_timeout = '3s';

WITH candidate_fields AS MATERIALIZED (
    SELECT 'BASE'::text AS owner_type,
           f.id AS owner_id,
           f.id AS food_item_id,
           NULL::text AS language,
           'display_name'::text AS field_name,
           f.display_name AS current_value,
           f.publication_status::text AS publication_status,
           f.catalog_type::text AS catalog_type,
           f.data_source::text AS data_source
           ,f.source_key
           ,f.name AS source_name
           ,f.brand
    FROM food_items f
    WHERE NOT coalesce(f.is_custom, false)
      AND f.display_name IS NOT NULL
    UNION ALL
    SELECT 'BASE', f.id, f.id, NULL, 'short_display_name', f.short_display_name,
           f.publication_status::text, f.catalog_type::text, f.data_source::text
           ,f.source_key, f.name, f.brand
    FROM food_items f
    WHERE NOT coalesce(f.is_custom, false)
      AND f.short_display_name IS NOT NULL
    UNION ALL
    SELECT 'LOCALIZATION', l.id, l.food_item_id, l.language::text, 'display_name', l.display_name,
           f.publication_status::text, f.catalog_type::text, f.data_source::text
           ,f.source_key, f.name, f.brand
    FROM food_item_localizations l
    JOIN food_items f ON f.id = l.food_item_id
    WHERE NOT coalesce(f.is_custom, false) AND l.active AND l.display_name IS NOT NULL
    UNION ALL
    SELECT 'LOCALIZATION', l.id, l.food_item_id, l.language::text, 'short_display_name', l.short_display_name,
           f.publication_status::text, f.catalog_type::text, f.data_source::text
           ,f.source_key, f.name, f.brand
    FROM food_item_localizations l
    JOIN food_items f ON f.id = l.food_item_id
    WHERE NOT coalesce(f.is_custom, false) AND l.active AND l.short_display_name IS NOT NULL
), candidates AS MATERIALIZED (
    SELECT *
    FROM candidate_fields
    WHERE lower(current_value) <> upper(current_value)
      AND (current_value = lower(current_value) OR current_value = upper(current_value))
), frames AS (
    SELECT 0 AS frame, ''::text AS row_id,
           json_build_object(
               'kind', 'start',
               'time', now(),
               'readonly', current_setting('transaction_read_only'),
               'count', (SELECT count(*) FROM candidates),
               'schema', (SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1)
           ) AS message
    UNION ALL
    SELECT 1,
           concat(owner_type, ':', owner_id, ':', field_name),
           json_build_object(
               'kind', 'row',
               'row', json_build_object(
                   'id', concat(owner_type, ':', owner_id, ':', field_name),
                   'owner_type', owner_type,
                   'owner_id', owner_id,
                   'food_item_id', food_item_id,
                   'language', language,
                   'field_name', field_name,
                   'current_value', current_value,
                   'publication_status', publication_status,
                   'catalog_type', catalog_type,
                   'data_source', data_source
                   ,'source_key', source_key
                   ,'source_name', source_name
                   ,'brand', brand
               )
           )
    FROM candidates
    UNION ALL
    SELECT 2, '', json_build_object('kind', 'end', 'count', (SELECT count(*) FROM candidates))
)
SELECT message FROM frames ORDER BY frame, row_id;

ROLLBACK;

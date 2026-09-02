-- Goal versioning stores historical rows per user. Remove legacy schema objects
-- that still enforce one goal row for the user's entire lifetime.
DO $$
DECLARE
    legacy_constraint RECORD;
    legacy_index RECORD;
BEGIN
    FOR legacy_constraint IN
        SELECT constraint_definition.conname
        FROM pg_constraint constraint_definition
        JOIN pg_class goal_table ON goal_table.oid = constraint_definition.conrelid
        JOIN pg_namespace goal_schema ON goal_schema.oid = goal_table.relnamespace
        WHERE goal_schema.nspname = current_schema()
          AND goal_table.relname = 'goals'
          AND constraint_definition.contype = 'u'
          AND ARRAY(
              SELECT goal_column.attname::text
              FROM unnest(constraint_definition.conkey) WITH ORDINALITY AS constrained_column(attnum, position)
              JOIN pg_attribute goal_column
                ON goal_column.attrelid = goal_table.oid
               AND goal_column.attnum = constrained_column.attnum
              ORDER BY constrained_column.position
          ) = ARRAY['user_id']::text[]
    LOOP
        EXECUTE format(
            'ALTER TABLE %I.%I DROP CONSTRAINT %I',
            current_schema(),
            'goals',
            legacy_constraint.conname
        );
    END LOOP;

    -- Also handle a legacy standalone unique index, if an older deployment
    -- created one without a backing PostgreSQL constraint.
    FOR legacy_index IN
        SELECT index_table.relname AS index_name
        FROM pg_index index_definition
        JOIN pg_class goal_table ON goal_table.oid = index_definition.indrelid
        JOIN pg_namespace goal_schema ON goal_schema.oid = goal_table.relnamespace
        JOIN pg_class index_table ON index_table.oid = index_definition.indexrelid
        WHERE goal_schema.nspname = current_schema()
          AND goal_table.relname = 'goals'
          AND index_definition.indisunique
          AND index_definition.indpred IS NULL
          AND NOT EXISTS (
              SELECT 1
              FROM pg_constraint backing_constraint
              WHERE backing_constraint.conindid = index_definition.indexrelid
          )
          AND ARRAY(
              SELECT goal_column.attname::text
              FROM unnest(index_definition.indkey) WITH ORDINALITY AS indexed_column(attnum, position)
              JOIN pg_attribute goal_column
                ON goal_column.attrelid = goal_table.oid
               AND goal_column.attnum = indexed_column.attnum
              WHERE indexed_column.attnum > 0
              ORDER BY indexed_column.position
          ) = ARRAY['user_id']::text[]
    LOOP
        EXECUTE format('DROP INDEX %I.%I', current_schema(), legacy_index.index_name);
    END LOOP;
END $$;

-- Keep the intended invariant: history is allowed, but at most one row may be active.
CREATE UNIQUE INDEX IF NOT EXISTS uk_goals_one_active_per_user
    ON goals (user_id)
    WHERE effective_until IS NULL;

-- Keep V308 writer fencing; do not mistake a concurrent stamp reader for a writer.
CREATE OR REPLACE FUNCTION public.grun_product_cache_stamp() RETURNS bigint
LANGUAGE plpgsql VOLATILE SET search_path = pg_catalog, public AS $$
DECLARE
    before_stamp bigint;
    after_stamp bigint;
BEGIN
    SELECT last_value INTO before_stamp FROM public.grun_product_cache_revision;
    IF EXISTS (
        SELECT 1 FROM pg_locks
        WHERE locktype = 'advisory' AND classid = 17491 AND objid = 308
            AND objsubid = 2 AND mode = 'ShareLock'
            AND database = (SELECT oid FROM pg_database WHERE datname = current_database())
    ) THEN RETURN NULL; END IF;
    SELECT last_value INTO after_stamp FROM public.grun_product_cache_revision;
    -- A writer spanning the lock snapshot either remains visible or changes the sequence.
    IF before_stamp IS DISTINCT FROM after_stamp THEN RETURN NULL; END IF;
    RETURN after_stamp;
END;
$$;

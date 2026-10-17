-- Additive cache metadata only. No product, nutrient, identity or portion rewrite.
CREATE SEQUENCE public.grun_product_cache_revision START WITH 1 NO CYCLE;
SELECT nextval('public.grun_product_cache_revision');

CREATE FUNCTION public.grun_product_cache_write_fence() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, public AS $$
BEGIN
    -- Shared transaction locks do not serialize independent catalog writers.
    PERFORM pg_advisory_xact_lock_shared(17491, 308);
    PERFORM nextval('public.grun_product_cache_revision');
    RETURN NULL;
END;
$$;

CREATE FUNCTION public.grun_product_cache_stamp() RETURNS bigint
LANGUAGE plpgsql SET search_path = pg_catalog, public AS $$
DECLARE stamp bigint;
BEGIN
    -- Never expose a generation while another session still has a pending writer.
    IF NOT pg_try_advisory_lock(17491, 308) THEN RETURN NULL; END IF;
    BEGIN
        SELECT last_value INTO stamp FROM public.grun_product_cache_revision;
        PERFORM pg_advisory_unlock(17491, 308);
        RETURN stamp;
    EXCEPTION WHEN OTHERS OR query_canceled THEN
        PERFORM pg_advisory_unlock(17491, 308);
        RAISE;
    END;
END;
$$;

DO $$
DECLARE
    target text;
    metadata_columns text;
BEGIN
    FOREACH target IN ARRAY ARRAY[
        'food_item_localizations', 'food_item_search_aliases',
        'food_item_serving_options', 'food_item_serving_option_localizations',
        'food_item_categories', 'food_categories', 'food_item_market_regions',
        'food_item_source_categories', 'food_product_quality_issues', 'food_product_source_evidence',
        'food_canonical_resolutions', 'food_branded_duplicate_search_collapses',
        'food_branded_duplicate_search_collapse_members', 'food_brands', 'food_brand_aliases'
    ] LOOP
        EXECUTE format('CREATE TRIGGER trg_product_cache_fence BEFORE INSERT OR UPDATE OR DELETE OR TRUNCATE ON public.%I FOR EACH STATEMENT EXECUTE FUNCTION public.grun_product_cache_write_fence()', target);
    END LOOP;
    CREATE TRIGGER trg_product_cache_fence_insert_delete
        BEFORE INSERT OR DELETE OR TRUNCATE ON public.food_items
        FOR EACH STATEMENT EXECUTE FUNCTION public.grun_product_cache_write_fence();
    -- Frequently updated popularity counters are bounded by TTL, not a global cache flush per tap.
    SELECT string_agg(quote_ident(attname), ', ' ORDER BY attnum) INTO metadata_columns
    FROM pg_attribute WHERE attrelid = 'public.food_items'::regclass AND attnum > 0
        AND NOT attisdropped AND attname NOT IN ('usage_count', 'search_selection_count');
    EXECUTE format('CREATE TRIGGER trg_product_cache_fence_update BEFORE UPDATE OF %s ON public.food_items FOR EACH STATEMENT EXECUTE FUNCTION public.grun_product_cache_write_fence()', metadata_columns);
END;
$$;

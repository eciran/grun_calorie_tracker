package com.grun.calorietracker.config;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

@Slf4j
@RequiredArgsConstructor
public class FailOpenCacheErrorHandler implements CacheErrorHandler {

    private final MeterRegistry meterRegistry;

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        record(cache, "get", exception);
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        record(cache, "put", exception);
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        record(cache, "evict", exception);
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        record(cache, "clear", exception);
    }

    private void record(Cache cache, String operation, RuntimeException exception) {
        String cacheName = cache == null ? "unknown" : cache.getName();
        meterRegistry.counter("grun.cache.errors", "cache", cacheName, "operation", operation).increment();
        log.warn("Cache operation failed cache={} operation={} type={}",
                cacheName, operation, exception.getClass().getSimpleName());
    }
}

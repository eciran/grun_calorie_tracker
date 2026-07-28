package com.grun.calorietracker.service.support;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserAnalyticsCacheGateway {

    private final CacheManager cacheManager;
    private final MeterRegistry meterRegistry;

    public <T> T get(String cacheName, String key, Supplier<T> loader) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return compute(cacheName, loader);
        }

        Cache.ValueWrapper cached;
        try {
            cached = cache.get(key);
        } catch (RuntimeException cacheReadFailure) {
            recordCacheFailure(cacheName, "get", cacheReadFailure);
            return compute(cacheName, loader);
        }

        if (cached != null) {
            meterRegistry.counter("grun.cache.requests", "cache", cacheName, "result", "hit").increment();
            @SuppressWarnings("unchecked")
            T value = (T) cached.get();
            return value;
        }

        meterRegistry.counter("grun.cache.requests", "cache", cacheName, "result", "miss").increment();
        T value = compute(cacheName, loader);
        if (value != null) {
            try {
                cache.put(key, value);
            } catch (RuntimeException cacheWriteFailure) {
                recordCacheFailure(cacheName, "put", cacheWriteFailure);
            }
        }
        return value;
    }

    private <T> T compute(String cacheName, Supplier<T> loader) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return loader.get();
        } finally {
            sample.stop(meterRegistry.timer("grun.cache.compute.duration", "cache", cacheName));
        }
    }

    private void recordCacheFailure(String cacheName, String operation, RuntimeException failure) {
        meterRegistry.counter("grun.cache.errors", "cache", cacheName, "operation", operation).increment();
        log.warn("Analytics cache operation failed cache={} operation={} type={}",
                cacheName, operation, failure.getClass().getSimpleName());
    }
}

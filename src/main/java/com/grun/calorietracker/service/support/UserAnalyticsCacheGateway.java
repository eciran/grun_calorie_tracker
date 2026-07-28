package com.grun.calorietracker.service.support;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserAnalyticsCacheGateway {

    private final CacheManager cacheManager;
    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, CompletableFuture<Object>> inFlightLoads = new ConcurrentHashMap<>();

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
            return cast(cached.get());
        }

        meterRegistry.counter("grun.cache.requests", "cache", cacheName, "result", "miss").increment();
        return loadSingleFlight(cacheName, key, cache, loader);
    }

    private <T> T loadSingleFlight(String cacheName, String key, Cache cache, Supplier<T> loader) {
        String flightKey = cacheName + "::" + key;
        CompletableFuture<Object> ownedFlight = new CompletableFuture<>();
        CompletableFuture<Object> existingFlight = inFlightLoads.putIfAbsent(flightKey, ownedFlight);
        if (existingFlight != null) {
            meterRegistry.counter("grun.cache.requests", "cache", cacheName, "result", "coalesced").increment();
            return await(existingFlight);
        }

        try {
            T value = compute(cacheName, loader);
            if (value != null) {
                try {
                    cache.put(key, value);
                } catch (RuntimeException cacheWriteFailure) {
                    recordCacheFailure(cacheName, "put", cacheWriteFailure);
                }
            }
            ownedFlight.complete(value);
            return value;
        } catch (RuntimeException failure) {
            ownedFlight.completeExceptionally(failure);
            throw failure;
        } finally {
            inFlightLoads.remove(flightKey, ownedFlight);
        }
    }

    private <T> T await(CompletableFuture<Object> flight) {
        try {
            return cast(flight.join());
        } catch (CompletionException failure) {
            if (failure.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw failure;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T cast(Object value) {
        return (T) value;
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

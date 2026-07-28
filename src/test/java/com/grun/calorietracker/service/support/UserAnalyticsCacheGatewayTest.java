package com.grun.calorietracker.service.support;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserAnalyticsCacheGatewayTest {

    @Test
    void get_loadsOnceAndThenReturnsCachedValue() {
        var registry = new SimpleMeterRegistry();
        var gateway = new UserAnalyticsCacheGateway(new ConcurrentMapCacheManager("analytics"), registry);
        var loads = new AtomicInteger();

        assertEquals("value", gateway.get("analytics", "key", () -> {
            loads.incrementAndGet();
            return "value";
        }));
        assertEquals("value", gateway.get("analytics", "key", () -> "other"));

        assertEquals(1, loads.get());
        assertEquals(1.0, registry.counter("grun.cache.requests", "cache", "analytics", "result", "miss").count());
        assertEquals(1.0, registry.counter("grun.cache.requests", "cache", "analytics", "result", "hit").count());
    }

    @Test
    void get_whenCacheReadFails_computesExactlyOnce() {
        CacheManager manager = mock(CacheManager.class);
        Cache cache = mock(Cache.class);
        when(manager.getCache("analytics")).thenReturn(cache);
        when(cache.get("key")).thenThrow(new IllegalStateException("redis unavailable"));
        var loads = new AtomicInteger();
        var gateway = new UserAnalyticsCacheGateway(manager, new SimpleMeterRegistry());

        String result = gateway.get("analytics", "key", () -> {
            loads.incrementAndGet();
            return "fallback";
        });

        assertEquals("fallback", result);
        assertEquals(1, loads.get());
    }

    @Test
    void get_whenLoaderFails_doesNotRetryTheLoader() {
        var gateway = new UserAnalyticsCacheGateway(new ConcurrentMapCacheManager("analytics"), new SimpleMeterRegistry());
        var loads = new AtomicInteger();
        Supplier<String> loader = () -> {
            loads.incrementAndGet();
            throw new IllegalArgumentException("calculation failed");
        };

        assertThrows(IllegalArgumentException.class, () -> gateway.get("analytics", "key", loader));
        assertEquals(1, loads.get());
    }
}

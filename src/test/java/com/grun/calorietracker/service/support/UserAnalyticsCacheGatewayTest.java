package com.grun.calorietracker.service.support;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void get_whenRequestsAreConcurrent_coalescesLoaderExecution() throws Exception {
        var registry = new SimpleMeterRegistry();
        var gateway = new UserAnalyticsCacheGateway(new ConcurrentMapCacheManager("analytics"), registry);
        var loads = new AtomicInteger();
        int requestCount = 8;
        var ready = new CountDownLatch(requestCount);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(requestCount);
        try {
            List<java.util.concurrent.Future<String>> futures = IntStream.range(0, requestCount)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return gateway.get("analytics", "shared-key", () -> {
                            loads.incrementAndGet();
                            try {
                                Thread.sleep(150);
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(interrupted);
                            }
                            return "shared-value";
                        });
                    }))
                    .toList();

            assertEquals(true, ready.await(2, TimeUnit.SECONDS));
            start.countDown();
            for (var future : futures) {
                assertEquals("shared-value", future.get(2, TimeUnit.SECONDS));
            }

            assertEquals(1, loads.get());
            assertEquals(requestCount - 1.0, registry.counter(
                    "grun.cache.requests", "cache", "analytics", "result", "coalesced"
            ).count());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void get_whenLoaderReturnsNull_doesNotCacheResult() {
        var gateway = new UserAnalyticsCacheGateway(new ConcurrentMapCacheManager("analytics"), new SimpleMeterRegistry());
        var loads = new AtomicInteger();

        assertNull(gateway.get("analytics", "nullable-key", () -> {
            loads.incrementAndGet();
            return null;
        }));
        assertEquals("value", gateway.get("analytics", "nullable-key", () -> {
            loads.incrementAndGet();
            return "value";
        }));

        assertEquals(2, loads.get());
    }

    @Test
    void get_afterFailedFlight_allowsSuccessfulRetry() {
        var gateway = new UserAnalyticsCacheGateway(new ConcurrentMapCacheManager("analytics"), new SimpleMeterRegistry());
        var loads = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> gateway.get("analytics", "retry-key", () -> {
            loads.incrementAndGet();
            throw new IllegalStateException("temporary failure");
        }));
        assertEquals("recovered", gateway.get("analytics", "retry-key", () -> {
            loads.incrementAndGet();
            return "recovered";
        }));

        assertEquals(2, loads.get());
    }

    @Test
    void get_whenConcurrentCacheReadsFail_coalescesFallbackComputation() throws Exception {
        CacheManager manager = mock(CacheManager.class);
        Cache cache = mock(Cache.class);
        when(manager.getCache("analytics")).thenReturn(cache);
        when(cache.get("outage-key")).thenThrow(new IllegalStateException("redis unavailable"));
        var gateway = new UserAnalyticsCacheGateway(manager, new SimpleMeterRegistry());
        var loads = new AtomicInteger();
        int requestCount = 8;
        var ready = new CountDownLatch(requestCount);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(requestCount);
        try {
            List<java.util.concurrent.Future<String>> futures = IntStream.range(0, requestCount)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return gateway.get("analytics", "outage-key", () -> {
                            loads.incrementAndGet();
                            try {
                                Thread.sleep(150);
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(interrupted);
                            }
                            return "fallback";
                        });
                    })).toList();

            assertEquals(true, ready.await(2, TimeUnit.SECONDS));
            start.countDown();
            for (var future : futures) {
                assertEquals("fallback", future.get(2, TimeUnit.SECONDS));
            }
            assertEquals(1, loads.get());
        } finally {
            executor.shutdownNow();
        }
    }}

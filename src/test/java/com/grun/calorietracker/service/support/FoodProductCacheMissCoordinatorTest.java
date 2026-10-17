package com.grun.calorietracker.service.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Ticker;
import com.grun.calorietracker.config.FoodProductCacheProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FoodProductCacheMissCoordinatorTest {
    SimpleMeterRegistry meters;
    FoodProductCacheMissCoordinator coordinator;
    FoodProductSnapshotCache cache;
    ExecutorService executor;
    CountDownLatch entered;
    CountDownLatch release;
    FoodProductCacheRevision.Key key = new FoodProductCacheRevision.Key(1L, "query");

    @BeforeEach void setup() {
        meters = new SimpleMeterRegistry();
        var policy = new FoodProductCacheProperties.Coalescing();
        policy.setMaxConcurrentLoads(1); policy.setWaitTimeout(Duration.ofMillis(50));
        coordinator = new FoodProductCacheMissCoordinator(policy, meters);
        var revision = mock(FoodProductCacheRevision.class); when(revision.current()).thenReturn(1L);
        cache = new FoodProductSnapshotCache("foodProductSearch", new FoodProductCacheProperties().getSearch(),
                revision, new ObjectMapper(), meters, true, Ticker.systemTicker());
        executor = Executors.newSingleThreadExecutor(); entered = new CountDownLatch(1); release = new CountDownLatch(1);
    }

    Future<Object> blockedLeader() throws Exception {
        var future = executor.submit(() -> load(key, () -> {
            entered.countDown(); assertTrue(release.await(4, TimeUnit.SECONDS)); return "leader";
        }));
        assertTrue(entered.await(2, TimeUnit.SECONDS)); return future;
    }
    Object load(Object queryKey, FoodProductCacheMissCoordinator.Load action) throws Exception {
        try { return coordinator.load(cache, queryKey, action); }
        catch (Exception failure) { throw failure; }
        catch (Throwable failure) { throw new AssertionError(failure); }
    }
    double count(String result) {
        var counter = meters.find("grun.product.cache.coalescing").tag("result", result).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test void capacityIsBoundedAndDifferentKeysBypassWithoutWaiting() throws Exception {
        var leader = blockedLeader();
        assertEquals("other", load(new FoodProductCacheRevision.Key(1L, "other"), () -> "other"));
        assertEquals(1, count("capacity_bypass")); assertEquals(1, coordinator.activeLoads());
        release.countDown(); assertEquals("leader", leader.get(2, TimeUnit.SECONDS));
        assertEquals(0, coordinator.activeLoads());
    }

    @Test void timeoutBypassesInsteadOfHoldingTheCallerIndefinitely() throws Exception {
        var leader = blockedLeader();
        assertEquals("fallback", load(key, () -> "fallback"));
        assertEquals(1, count("wait")); assertEquals(1, count("timeout_bypass"));
        assertEquals(1, coordinator.activeLoads());
        release.countDown(); leader.get(2, TimeUnit.SECONDS); assertEquals(0, coordinator.activeLoads());
    }

    @Test void failureReleasesAdmissionAndDoesNotShareOrCacheAnException() throws Exception {
        var error = new IllegalStateException("test failure");
        assertSame(error, assertThrows(IllegalStateException.class, () -> load(key, () -> { throw error; })));
        assertEquals(0, coordinator.activeLoads());
        assertEquals("recovered", load(key, () -> "recovered")); assertEquals(2, count("leader"));
    }

    @Test void outerTransactionNeverWaitsForAnotherSession() throws Exception {
        var leader = blockedLeader();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertEquals("transaction", load(key, () -> "transaction")); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        assertEquals(1, count("transaction_bypass")); assertEquals(0, count("wait"));
        release.countDown(); leader.get(2, TimeUnit.SECONDS);
    }

    @Test void unavailableGenerationDoesNotCreateAFlight() throws Exception {
        assertEquals("uncached", load(new FoodProductCacheRevision.Key(null, "missing"), () -> "uncached"));
        assertEquals(0, count("leader")); assertEquals(0, coordinator.activeLoads());
    }

    @Test void recursiveSameThreadCallDoesNotWaitOnItself() throws Exception {
        assertEquals("nested", load(key, () -> load(key, () -> "nested")));
        assertEquals(1, count("recursive_bypass")); assertEquals(0, coordinator.activeLoads());
    }

    @Test void interruptedWaiterPreservesInterruptAndDoesNotStartItsLoader() throws Exception {
        var policy = new FoodProductCacheProperties.Coalescing();
        policy.setMaxConcurrentLoads(1); policy.setWaitTimeout(Duration.ofSeconds(3));
        coordinator = new FoodProductCacheMissCoordinator(policy, meters);
        var leader = blockedLeader();
        var failure = new AtomicReference<Throwable>(); var interrupted = new AtomicBoolean(); var loaded = new AtomicBoolean();
        Thread waiter = new Thread(() -> {
            try { load(key, () -> { loaded.set(true); return "not expected"; }); }
            catch (Throwable error) { failure.set(error); interrupted.set(Thread.currentThread().isInterrupted()); }
        }, "product-cache-test-waiter");
        waiter.start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (count("wait") == 0 && System.nanoTime() < deadline) Thread.sleep(1);
            waiter.interrupt(); waiter.join(2000);
            assertFalse(waiter.isAlive()); assertInstanceOf(CancellationException.class, failure.get());
            assertTrue(interrupted.get()); assertFalse(loaded.get());
        } finally { waiter.interrupt(); waiter.join(2000); release.countDown(); leader.get(2, TimeUnit.SECONDS); }
    }

    @AfterEach void cleanup() throws InterruptedException {
        release.countDown(); executor.shutdownNow(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        TransactionSynchronizationManager.setActualTransactionActive(false); meters.close();
    }
}

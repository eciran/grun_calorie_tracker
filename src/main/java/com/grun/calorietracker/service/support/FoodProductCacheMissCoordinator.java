package com.grun.calorietracker.service.support;

import com.grun.calorietracker.config.FoodProductCacheProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public final class FoodProductCacheMissCoordinator {
    @FunctionalInterface public interface Load { Object run() throws Throwable; }
    private record Key(String cache, Object dimension) { }
    private record Flight(Thread owner, CountDownLatch finished) { }
    private final ConcurrentHashMap<Key, Flight> flights = new ConcurrentHashMap<>();
    private final Semaphore admission;
    private final long waitNanos;
    private final MeterRegistry meters;

    public FoodProductCacheMissCoordinator(FoodProductCacheProperties.Coalescing policy, MeterRegistry meters) {
        admission = new Semaphore(policy.getMaxConcurrentLoads());
        waitNanos = policy.getWaitTimeout().toNanos();
        this.meters = meters;
    }

    public Object load(FoodProductSnapshotCache cache, Object key, Load invocation) throws Throwable {
        // Never wait while holding an outer transaction's connection or catalog locks.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            count("transaction_bypass"); return invocation.run();
        }
        if (!cache.canCoordinate(key) || cache.hasSnapshot(key)) return invocation.run();
        var flightKey = new Key(cache.getName(), key);
        Flight existing = flights.get(flightKey);
        if (existing == null) {
            if (!admission.tryAcquire()) {
                existing = flights.get(flightKey);
                if (existing == null) { count("capacity_bypass"); return invocation.run(); }
            } else {
                var proposed = new Flight(Thread.currentThread(), new CountDownLatch(1));
                existing = flights.putIfAbsent(flightKey, proposed);
                if (existing == null) {
                    count("leader");
                    try { return invocation.run(); }
                    finally {
                        flights.remove(flightKey, proposed); admission.release(); proposed.finished().countDown();
                    }
                }
                admission.release();
            }
        }
        if (existing.owner() == Thread.currentThread()) { count("recursive_bypass"); return invocation.run(); }
        count("wait");
        try {
            if (!existing.finished().await(waitNanos, TimeUnit.NANOSECONDS)) count("timeout_bypass");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); count("interrupted");
            throw new CancellationException("Product query cancelled while waiting for cache");
        }
        // Re-enter the real proxy chain: no shared DTO, exception or uncommitted result.
        return invocation.run();
    }

    private void count(String result) { meters.counter("grun.product.cache.coalescing", "result", result).increment(); }
    public int activeLoads() { return flights.size(); }
}

package com.grun.calorietracker.service.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.github.benmanes.caffeine.cache.Expiry;
import com.grun.calorietracker.config.FoodProductCacheProperties;
import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodProductSearchPageDto;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.concurrent.Callable;
import java.time.Duration;

public final class FoodProductSnapshotCache implements Cache {
    private final String name;
    private final Class<?> payloadType;
    private final FoodProductCacheProperties.Policy policy;
    private final FoodProductCacheRevision revision;
    private final ObjectMapper mapper;
    private final MeterRegistry meters;
    private final boolean enabled;
    private final Duration negativeTtl;
    private final com.github.benmanes.caffeine.cache.Cache<Object, Snapshot> storage;

    public FoodProductSnapshotCache(String name, FoodProductCacheProperties.Policy policy,
                                    FoodProductCacheRevision revision, ObjectMapper mapper,
                                    MeterRegistry meters, boolean enabled, Ticker ticker) {
        this(name, policy, revision, mapper, meters, enabled, ticker, Duration.ofSeconds(5));
    }

    public FoodProductSnapshotCache(String name, FoodProductCacheProperties.Policy policy,
                                    FoodProductCacheRevision revision, ObjectMapper mapper,
                                    MeterRegistry meters, boolean enabled, Ticker ticker, Duration negativeTtl) {
        this.name = name;
        this.payloadType = name.equals("foodProductSearch") ? FoodProductSearchPageDto.class : FoodProductDto.class;
        this.policy = policy;
        this.revision = revision;
        this.mapper = mapper.copy().deactivateDefaultTyping();
        this.meters = meters;
        this.enabled = enabled;
        this.negativeTtl = negativeTtl.compareTo(policy.getTtl()) < 0 ? negativeTtl : policy.getTtl();
        this.storage = Caffeine.newBuilder().maximumSize(policy.getMaxEntries())
                .expireAfter(new Expiry<Object, Snapshot>() {
                    @Override public long expireAfterCreate(Object key, Snapshot value, long now) { return value.ttlNanos(); }
                    @Override public long expireAfterUpdate(Object key, Snapshot value, long now, long remaining) { return value.ttlNanos(); }
                    @Override public long expireAfterRead(Object key, Snapshot value, long now, long remaining) { return remaining; }
                }).ticker(ticker).executor(Runnable::run).build();
    }

    @Override public String getName() { return name; }
    @Override public Object getNativeCache() { return storage; }

    private boolean usable(Object key) {
        return enabled && key instanceof FoodProductCacheRevision.Key versioned
                && versioned.revision() != null && versioned.dimension() instanceof String dimension
                && dimension.length() <= 256;
    }

    public boolean canCoordinate(Object key) { return usable(key); }
    public boolean hasSnapshot(Object key) { return usable(key) && storage.getIfPresent(key) != null; }

    @Override public ValueWrapper get(Object key) {
        if (!usable(key)) return null;
        Snapshot data = storage.getIfPresent(key);
        if (data == null) {
            meters.counter("grun.product.cache.access", "cache", name, "result", "miss").increment();
            return null;
        }
        try {
            Object value = mapper.readValue(data.bytes(), payloadType);
            meters.counter("grun.product.cache.access", "cache", name, "result", "hit").increment();
            return new SimpleValueWrapper(value);
        } catch (java.io.IOException invalid) {
            storage.invalidate(key);
            meters.counter("grun.product.cache.bypass", "reason", "decode").increment();
            return null;
        }
    }

    @Override public <T> T get(Object key, Class<T> type) {
        ValueWrapper value = get(key);
        if (value == null) return null;
        Object decoded = value.get();
        if (type != null && !type.isInstance(decoded)) throw new IllegalStateException("Unexpected product cache type");
        @SuppressWarnings("unchecked") T typed = (T) decoded;
        return typed;
    }

    @Override public <T> T get(Object key, Callable<T> loader) {
        ValueWrapper present = get(key);
        if (present != null) {
            @SuppressWarnings("unchecked") T value = (T) present.get();
            return value;
        }
        try {
            T value = loader.call();
            put(key, value);
            return value;
        } catch (Exception failure) {
            throw new ValueRetrievalException(key, loader, failure);
        }
    }

    @Override public void put(Object key, Object value) { store(key, value, false); }

    private void store(Object key, Object value, boolean absentOnly) {
        if (!usable(key) || value == null) return;
        boolean negative = value instanceof FoodProductSearchPageDto page && page.getContent() != null
                && page.getContent().isEmpty() && Long.valueOf(0).equals(page.getTotalElements());
        if (value instanceof FoodProductSearchPageDto page && !page.isCacheable()) return;
        if (negative && negativeTtl.isZero()) return;
        if (!payloadType.isInstance(value)) throw new IllegalArgumentException("Unexpected product cache payload");
        byte[] bytes;
        try {
            bytes = mapper.writeValueAsBytes(value);
        } catch (java.io.IOException invalid) {
            meters.counter("grun.product.cache.bypass", "reason", "encode").increment();
            return;
        }
        if (bytes.length > policy.getMaxValueBytes()) {
            meters.counter("grun.product.cache.bypass", "reason", "oversize").increment();
            return;
        }
        Snapshot snapshot = new Snapshot(bytes, (negative ? negativeTtl : policy.getTtl()).toNanos());
        // Capture a snapshot now, publish only after commit, and reject obsolete loaders.
        Runnable store = () -> {
            var versioned = (FoodProductCacheRevision.Key) key;
            Long current = revision.current();
            if (current == null || !Objects.equals(versioned.revision(), current)) return;
            if (absentOnly) storage.asMap().putIfAbsent(key, snapshot); else storage.put(key, snapshot);
            storage.cleanUp();
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            Pending pending = (Pending) TransactionSynchronizationManager.getResource(this);
            if (pending == null) {
                pending = new Pending();
                Pending captured = pending;
                TransactionSynchronizationManager.bindResource(this, captured);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void suspend() { TransactionSynchronizationManager.unbindResource(FoodProductSnapshotCache.this); }
                    @Override public void resume() { TransactionSynchronizationManager.bindResource(FoodProductSnapshotCache.this, captured); }
                    @Override public void afterCommit() { captured.actions.values().forEach(Runnable::run); }
                    @Override public void afterCompletion(int status) {
                        if (TransactionSynchronizationManager.getResource(FoodProductSnapshotCache.this) == captured)
                            TransactionSynchronizationManager.unbindResource(FoodProductSnapshotCache.this);
                    }
                });
            }
            if (!pending.actions.containsKey(key) && pending.actions.size() >= Math.min(16, policy.getMaxEntries())) return;
            int previous = pending.sizes.getOrDefault(key, 0);
            if (pending.bytes - previous + bytes.length > 1_048_576) return;
            pending.bytes += bytes.length - previous;
            pending.sizes.put(key, bytes.length);
            pending.actions.put(key, store);
        } else store.run();
    }

    @Override public ValueWrapper putIfAbsent(Object key, Object value) {
        ValueWrapper existing = get(key);
        if (existing == null) store(key, value, true);
        return existing;
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { action.run(); }
            });
        } else action.run();
    }

    @Override public void evict(Object key) { afterCommit(() -> storage.invalidate(key)); }
    @Override public boolean evictIfPresent(Object key) { return storage.asMap().remove(key) != null; }
    @Override public void clear() { afterCommit(storage::invalidateAll); }
    @Override public boolean invalidate() {
        boolean populated = storage.estimatedSize() > 0;
        storage.invalidateAll();
        return populated;
    }

    private static class Pending {
        final LinkedHashMap<Object, Runnable> actions = new LinkedHashMap<>();
        final LinkedHashMap<Object, Integer> sizes = new LinkedHashMap<>();
        int bytes;
    }
    private record Snapshot(byte[] bytes, long ttlNanos) { }
}

package com.grun.calorietracker.service;

import com.grun.calorietracker.service.support.OwnerErrorEvent;
import com.grun.calorietracker.service.support.OwnerErrorStore;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OwnerErrorRecorder {
    private record Pending(OwnerErrorEvent event, int attempt) { }
    private final ArrayBlockingQueue<Pending> queue = new ArrayBlockingQueue<>(2000);
    private final OwnerErrorStore store;
    private final int retentionDays, maxRows;
    private final AtomicLong saved = new AtomicLong(), dropped = new AtomicLong(), writeFailures = new AtomicLong();
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "owner-error-recorder"); thread.setDaemon(true); return thread;
    });
    private volatile Instant lastSaved, lastFailure;
    private OwnerOperationalAlertService ownerOperationalAlertService;
    private long nextPrune;
    public OwnerErrorRecorder(OwnerErrorStore store, @Value("${grun.error-center.retention-days:30}") int retentionDays,
                              @Value("${grun.error-center.max-rows:100000}") int maxRows) {
        this.store = store; this.retentionDays = Math.max(1, Math.min(365, retentionDays)); this.maxRows = Math.max(1000, Math.min(1000000, maxRows));
    }
    @Autowired(required = false)
    public void setOwnerOperationalAlertService(OwnerOperationalAlertService ownerOperationalAlertService) {
        this.ownerOperationalAlertService = ownerOperationalAlertService;
    }
    @PostConstruct public void start() { worker.scheduleWithFixedDelay(this::drain, 1, 1, TimeUnit.SECONDS); }
    public void record(OwnerErrorEvent event) {
        if (!queue.offer(new Pending(event, 0))) dropped.incrementAndGet();
    }
    public void captureFailed() { dropped.incrementAndGet(); lastFailure = Instant.now(); }
    public void drain() {
        int count = Math.min(100, queue.size());
        for (int i=0; i<count; i++) {
            Pending pending = queue.poll(); if (pending == null) break;
            try {
                store.insert(pending.event()); saved.incrementAndGet(); lastSaved = Instant.now();
                if (ownerOperationalAlertService != null) {
                    try { ownerOperationalAlertService.recordCriticalBackendError(pending.event()); }
                    catch (RuntimeException ignored) { /* Alert outbox failure must not poison durable error capture. */ }
                }
            }
            catch (RuntimeException failure) {
                writeFailures.incrementAndGet(); lastFailure = Instant.now();
                if (pending.attempt() >= 2 || !queue.offer(new Pending(pending.event(), pending.attempt()+1))) dropped.incrementAndGet();
                break;
            }
        }
        if (count > 0 || System.currentTimeMillis() >= nextPrune) {
            nextPrune = System.currentTimeMillis() + 60_000;
            try { store.prune(Instant.now().minus(retentionDays, ChronoUnit.DAYS), maxRows); }
            catch (RuntimeException failure) { writeFailures.incrementAndGet(); lastFailure = Instant.now(); }
        }
    }
    public record Health(String source, int queued, long saved, long dropped, long writeFailures,
                         Instant lastSaved, Instant lastFailure, int retentionDays, int maxRows) { }
    public Health health() { return new Health("BACKEND", queue.size(), saved.get(), dropped.get(), writeFailures.get(), lastSaved, lastFailure, retentionDays, maxRows); }
    @PreDestroy public void stop() { worker.shutdownNow(); }
}

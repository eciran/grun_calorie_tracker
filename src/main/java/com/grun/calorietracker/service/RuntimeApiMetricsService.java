package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminRuntimeApiMetricsDto;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.LongAdder;

@Service
public class RuntimeApiMetricsService {
    private static final int MAX_LATENCY_SAMPLES = 10_000;

    private final LongAdder requests = new LongAdder();
    private final LongAdder errors = new LongAdder();
    private final LongAdder rateLimited = new LongAdder();
    private final LongAdder authenticationFailures = new LongAdder();
    private final LongAdder authorizationFailures = new LongAdder();
    private final ConcurrentLinkedDeque<Long> latencySamples = new ConcurrentLinkedDeque<>();
    private final LocalDateTime windowStartedAt = LocalDateTime.now();

    public void record(long latencyMs, int status) {
        requests.increment();
        if (status >= 500) errors.increment();
        if (status == 429) rateLimited.increment();
        if (status == 401) authenticationFailures.increment();
        if (status == 403) authorizationFailures.increment();
        latencySamples.addLast(Math.max(0, latencyMs));
        while (latencySamples.size() > MAX_LATENCY_SAMPLES) latencySamples.pollFirst();
    }

    public AdminRuntimeApiMetricsDto snapshot(long latencyThresholdMs, double errorRateThreshold) {
        long requestCount = requests.sum();
        long errorCount = errors.sum();
        double errorRate = requestCount == 0 ? 0 : errorCount / (double) requestCount;
        List<Long> sorted = new ArrayList<>(latencySamples);
        sorted.sort(Long::compareTo);
        long p50 = percentile(sorted, 0.50);
        long p95 = percentile(sorted, 0.95);
        long p99 = percentile(sorted, 0.99);
        return new AdminRuntimeApiMetricsDto(
                requestCount, errorCount, errorRate, p50, p95, p99,
                rateLimited.sum(), authenticationFailures.sum(), authorizationFailures.sum(),
                p95 >= latencyThresholdMs, errorRate >= errorRateThreshold, windowStartedAt);
    }

    private long percentile(List<Long> sorted, double percentile) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.min(Math.max(index, 0), sorted.size() - 1));
    }
}

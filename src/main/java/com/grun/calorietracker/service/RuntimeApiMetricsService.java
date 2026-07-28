package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminRuntimeApiMetricsDto;
import com.grun.calorietracker.dto.AdminSystemReliabilityAnalyticsDto;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.LongAdder;

@Service
public class RuntimeApiMetricsService {
    private static final int MAX_LATENCY_SAMPLES = 10_000;
    private static final int MAX_TREND_HOURS = 168;

    private final LongAdder requests = new LongAdder();
    private final LongAdder errors = new LongAdder();
    private final LongAdder rateLimited = new LongAdder();
    private final LongAdder authenticationFailures = new LongAdder();
    private final LongAdder authorizationFailures = new LongAdder();
    private final ConcurrentLinkedDeque<Long> latencySamples = new ConcurrentLinkedDeque<>();
    private final Map<LocalDateTime, HourBucket> hourlyBuckets = new ConcurrentHashMap<>();
    private final LocalDateTime windowStartedAt = LocalDateTime.now();

    public void record(long latencyMs, int status) {
        long safeLatency = Math.max(0, latencyMs);
        requests.increment();
        if (status >= 500) errors.increment();
        if (status == 429) rateLimited.increment();
        if (status == 401) authenticationFailures.increment();
        if (status == 403) authorizationFailures.increment();
        latencySamples.addLast(safeLatency);
        while (latencySamples.size() > MAX_LATENCY_SAMPLES) latencySamples.pollFirst();

        LocalDateTime bucketKey = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        hourlyBuckets.computeIfAbsent(bucketKey, ignored -> new HourBucket()).record(safeLatency, status);
        LocalDateTime cutoff = bucketKey.minusHours(MAX_TREND_HOURS);
        hourlyBuckets.keySet().removeIf(bucket -> bucket.isBefore(cutoff));
    }

    public AdminRuntimeApiMetricsDto snapshot(long latencyThresholdMs, double errorRateThreshold) {
        long requestCount = requests.sum();
        long errorCount = errors.sum();
        double errorRate = requestCount == 0 ? 0 : errorCount / (double) requestCount;
        List<Long> sorted = sortedSamples(latencySamples);
        long p50 = percentile(sorted, 0.50);
        long p95 = percentile(sorted, 0.95);
        long p99 = percentile(sorted, 0.99);
        return new AdminRuntimeApiMetricsDto(
                requestCount, errorCount, errorRate, p50, p95, p99,
                rateLimited.sum(), authenticationFailures.sum(), authorizationFailures.sum(),
                p95 >= latencyThresholdMs, errorRate >= errorRateThreshold, windowStartedAt);
    }

    public LocalDateTime windowStartedAt() {
        return windowStartedAt;
    }

    public List<AdminSystemReliabilityAnalyticsDto.ApiTrendPoint> trend(int requestedHours) {
        int hours = Math.max(1, Math.min(requestedHours, MAX_TREND_HOURS));
        LocalDateTime cutoff = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).minusHours(hours - 1L);
        return hourlyBuckets.entrySet().stream()
                .filter(entry -> !entry.getKey().isBefore(cutoff))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getValue().toPoint(entry.getKey()))
                .toList();
    }

    private List<Long> sortedSamples(Iterable<Long> samples) {
        List<Long> sorted = new ArrayList<>();
        samples.forEach(sorted::add);
        sorted.sort(Comparator.naturalOrder());
        return sorted;
    }

    private long percentile(List<Long> sorted, double percentile) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.min(Math.max(index, 0), sorted.size() - 1));
    }

    private final class HourBucket {
        private final LongAdder requestCount = new LongAdder();
        private final LongAdder errorCount = new LongAdder();
        private final ConcurrentLinkedDeque<Long> latencies = new ConcurrentLinkedDeque<>();

        void record(long latencyMs, int status) {
            requestCount.increment();
            if (status >= 500) errorCount.increment();
            latencies.addLast(latencyMs);
            while (latencies.size() > MAX_LATENCY_SAMPLES) latencies.pollFirst();
        }

        AdminSystemReliabilityAnalyticsDto.ApiTrendPoint toPoint(LocalDateTime bucket) {
            long requestsInBucket = requestCount.sum();
            long errorsInBucket = errorCount.sum();
            double errorRate = requestsInBucket == 0 ? 0 : errorsInBucket / (double) requestsInBucket;
            return new AdminSystemReliabilityAnalyticsDto.ApiTrendPoint(
                    bucket, requestsInBucket, errorsInBucket, errorRate,
                    percentile(sortedSamples(latencies), 0.95));
        }
    }
}
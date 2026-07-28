package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdminMailMonitoringDto;
import com.grun.calorietracker.dto.AdminSystemHealthDto;
import com.grun.calorietracker.dto.AdminSystemReliabilityAnalyticsDto;
import com.grun.calorietracker.entity.RuntimeOperationRecordEntity;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.PushDeliveryStatus;
import com.grun.calorietracker.enums.RuntimeOperationRecordType;
import com.grun.calorietracker.enums.RuntimeOperationStatus;
import com.grun.calorietracker.enums.SubscriptionProviderEventStatus;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.repository.PushDeliveryLogRepository;
import com.grun.calorietracker.repository.RuntimeOperationRecordRepository;
import com.grun.calorietracker.repository.SubscriptionProviderEventRepository;
import com.grun.calorietracker.service.AdminMailMonitoringService;
import com.grun.calorietracker.service.AdminSystemHealthService;
import com.grun.calorietracker.service.AdminSystemReliabilityAnalyticsService;
import com.grun.calorietracker.service.RuntimeApiMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminSystemReliabilityAnalyticsServiceImpl implements AdminSystemReliabilityAnalyticsService {
    private final RuntimeApiMetricsService apiMetricsService;
    private final AdminSystemHealthService systemHealthService;
    private final AdminMailMonitoringService mailMonitoringService;
    private final SubscriptionProviderEventRepository providerEventRepository;
    private final AiRequestHistoryRepository aiRequestHistoryRepository;
    private final PushDeliveryLogRepository pushDeliveryLogRepository;
    private final RuntimeOperationRecordRepository operationRecordRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminSystemReliabilityAnalyticsDto getAnalytics(int requestedWindowHours) {
        int windowHours = Math.max(1, Math.min(requestedWindowHours, 168));
        LocalDateTime since = LocalDateTime.now().minusHours(windowHours);
        AdminSystemHealthDto health = systemHealthService.getHealth();
        List<RuntimeOperationRecordEntity> records = operationRecordRepository.findByCreatedAtAfterOrderByCreatedAtAsc(since);

        return new AdminSystemReliabilityAnalyticsDto(
                windowHours,
                LocalDateTime.now(),
                apiMetricsService.windowStartedAt(),
                apiMetricsService.trend(windowHours),
                infrastructure(health),
                providers(since, windowHours),
                operationMetrics(records),
                operationTrend(records)
        );
    }

    private List<AdminSystemReliabilityAnalyticsDto.InfrastructureMetric> infrastructure(AdminSystemHealthDto health) {
        double heapPercent = health.getHeapMaxMb() == null || health.getHeapMaxMb() <= 0 || health.getHeapUsedMb() == null
                ? 0.0 : roundRate(health.getHeapUsedMb() * 100.0 / health.getHeapMaxMb());
        return List.of(
                new AdminSystemReliabilityAnalyticsDto.InfrastructureMetric("PostgreSQL", health.getDatabaseStatus(), health.getDatabaseLatencyMs(), null),
                new AdminSystemReliabilityAnalyticsDto.InfrastructureMetric("Redis", health.getRedisStatus(), health.getRedisLatencyMs(), null),
                new AdminSystemReliabilityAnalyticsDto.InfrastructureMetric("JVM heap", heapPercent >= 85 ? "DEGRADED" : "UP", null, heapPercent),
                new AdminSystemReliabilityAnalyticsDto.InfrastructureMetric("Analytics cache", health.getAnalyticsCacheErrors() != null && health.getAnalyticsCacheErrors() > 0 ? "DEGRADED" : "UP", null,
                        health.getAnalyticsCacheHitRate() == null ? null : roundRate(health.getAnalyticsCacheHitRate() * 100.0))
        );
    }

    private List<AdminSystemReliabilityAnalyticsDto.ProviderMetric> providers(LocalDateTime since, int windowHours) {
        List<AdminSystemReliabilityAnalyticsDto.ProviderMetric> result = new ArrayList<>();

        long revenueSuccess = providerEventRepository.countByStatusAndReceivedAtAfter(SubscriptionProviderEventStatus.PROCESSED, since);
        long revenueFailure = providerEventRepository.countByStatusAndReceivedAtAfter(SubscriptionProviderEventStatus.FAILED, since);
        long revenueIgnored = providerEventRepository.countByStatusAndReceivedAtAfter(SubscriptionProviderEventStatus.IGNORED, since);
        result.add(provider("RevenueCat", "CONFIGURED", revenueSuccess + revenueFailure + revenueIgnored, revenueSuccess, revenueFailure));

        long aiAttempts = aiRequestHistoryRepository.countByCreatedAtAfter(since);
        long aiFailures = aiRequestHistoryRepository.countByStatusAndCreatedAtAfter(AiRequestStatus.FAILED, since);
        result.add(provider("AI", aiAttempts == 0 ? "NO_TRAFFIC" : "AVAILABLE", aiAttempts, Math.max(0, aiAttempts - aiFailures), aiFailures));

        long pushSent = pushDeliveryLogRepository.countByStatusAndCreatedAtAfter(PushDeliveryStatus.SENT, since);
        long pushFailed = pushDeliveryLogRepository.countByStatusAndCreatedAtAfter(PushDeliveryStatus.FAILED, since);
        long pushSkipped = pushDeliveryLogRepository.countByStatusAndCreatedAtAfter(PushDeliveryStatus.SKIPPED, since);
        result.add(provider("Push", pushSent + pushFailed + pushSkipped == 0 ? "NO_TRAFFIC" : "AVAILABLE", pushSent + pushFailed + pushSkipped, pushSent, pushFailed));

        AdminMailMonitoringDto mail = mailMonitoringService.getMonitoring(Math.max(1, (int) Math.ceil(windowHours / 24.0)), 1);
        Map<String, Long> counters = mail.getCounters() == null ? Map.of() : mail.getCounters();
        long mailAttempts = counters.getOrDefault("requests", 0L);
        long mailSuccess = counters.getOrDefault("delivered", 0L);
        long mailFailures = counters.getOrDefault("hardBounces", 0L)
                + counters.getOrDefault("softBounces", 0L)
                + counters.getOrDefault("blocked", 0L);
        String mailStatus = !mail.isApiKeyConfigured() ? "NOT_CONFIGURED" : mail.isProviderReachable() ? "AVAILABLE" : "UNREACHABLE";
        result.add(provider("Brevo", mailStatus, mailAttempts, mailSuccess, mailFailures));
        return result;
    }

    private AdminSystemReliabilityAnalyticsDto.ProviderMetric provider(
            String name, String status, long attempts, long successes, long failures) {
        Double successRate = attempts == 0 ? null : roundRate(successes * 100.0 / attempts);
        return new AdminSystemReliabilityAnalyticsDto.ProviderMetric(name, status, attempts, successes, failures, successRate);
    }

    private List<AdminSystemReliabilityAnalyticsDto.OperationMetric> operationMetrics(List<RuntimeOperationRecordEntity> records) {
        Map<RuntimeOperationRecordType, List<RuntimeOperationRecordEntity>> grouped = new EnumMap<>(RuntimeOperationRecordType.class);
        for (RuntimeOperationRecordEntity record : records) grouped.computeIfAbsent(record.getRecordType(), ignored -> new ArrayList<>()).add(record);
        List<AdminSystemReliabilityAnalyticsDto.OperationMetric> result = new ArrayList<>();
        for (RuntimeOperationRecordType type : RuntimeOperationRecordType.values()) {
            List<RuntimeOperationRecordEntity> items = grouped.getOrDefault(type, List.of());
            result.add(new AdminSystemReliabilityAnalyticsDto.OperationMetric(
                    type.name(), items.size(), count(items, RuntimeOperationStatus.SUCCEEDED),
                    count(items, RuntimeOperationStatus.FAILED), count(items, RuntimeOperationStatus.DEAD_LETTER),
                    count(items, RuntimeOperationStatus.OPEN) + count(items, RuntimeOperationStatus.MONITORING)
            ));
        }
        return result;
    }

    private List<AdminSystemReliabilityAnalyticsDto.OperationTrendPoint> operationTrend(List<RuntimeOperationRecordEntity> records) {
        Map<LocalDate, long[]> daily = new LinkedHashMap<>();
        for (RuntimeOperationRecordEntity record : records) {
            long[] counts = daily.computeIfAbsent(record.getCreatedAt().toLocalDate(), ignored -> new long[3]);
            if (record.getStatus() == RuntimeOperationStatus.SUCCEEDED) counts[0]++;
            if (record.getStatus() == RuntimeOperationStatus.FAILED) counts[1]++;
            if (record.getStatus() == RuntimeOperationStatus.DEAD_LETTER) counts[2]++;
        }
        return daily.entrySet().stream()
                .map(entry -> new AdminSystemReliabilityAnalyticsDto.OperationTrendPoint(
                        entry.getKey().toString(), entry.getValue()[0], entry.getValue()[1], entry.getValue()[2]))
                .toList();
    }

    private long count(List<RuntimeOperationRecordEntity> records, RuntimeOperationStatus status) {
        return records.stream().filter(record -> record.getStatus() == status).count();
    }

    private double roundRate(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdminMailMonitoringDto;
import com.grun.calorietracker.dto.AdminSystemHealthDto;
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
import com.grun.calorietracker.service.RuntimeApiMetricsService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminSystemReliabilityAnalyticsServiceImplTest {

    @Test
    void aggregatesPrivacySafeProviderInfrastructureAndOperationMetrics() {
        RuntimeApiMetricsService apiMetrics = new RuntimeApiMetricsService();
        apiMetrics.record(120, 200);
        apiMetrics.record(900, 503);
        AdminSystemHealthService healthService = mock(AdminSystemHealthService.class);
        AdminMailMonitoringService mailService = mock(AdminMailMonitoringService.class);
        SubscriptionProviderEventRepository revenueRepository = mock(SubscriptionProviderEventRepository.class);
        AiRequestHistoryRepository aiRepository = mock(AiRequestHistoryRepository.class);
        PushDeliveryLogRepository pushRepository = mock(PushDeliveryLogRepository.class);
        RuntimeOperationRecordRepository operationRepository = mock(RuntimeOperationRecordRepository.class);

        when(healthService.getHealth()).thenReturn(health());
        when(revenueRepository.countByStatusAndReceivedAtAfter(eq(SubscriptionProviderEventStatus.PROCESSED), any())).thenReturn(8L);
        when(revenueRepository.countByStatusAndReceivedAtAfter(eq(SubscriptionProviderEventStatus.FAILED), any())).thenReturn(1L);
        when(revenueRepository.countByStatusAndReceivedAtAfter(eq(SubscriptionProviderEventStatus.IGNORED), any())).thenReturn(1L);
        when(aiRepository.countByCreatedAtAfter(any())).thenReturn(10L);
        when(aiRepository.countByStatusAndCreatedAtAfter(eq(AiRequestStatus.FAILED), any())).thenReturn(2L);
        when(pushRepository.countByStatusAndCreatedAtAfter(eq(PushDeliveryStatus.SENT), any())).thenReturn(6L);
        when(pushRepository.countByStatusAndCreatedAtAfter(eq(PushDeliveryStatus.FAILED), any())).thenReturn(1L);
        when(pushRepository.countByStatusAndCreatedAtAfter(eq(PushDeliveryStatus.SKIPPED), any())).thenReturn(3L);
        when(mailService.getMonitoring(1, 1)).thenReturn(mail());
        when(operationRepository.findByCreatedAtAfterOrderByCreatedAtAsc(any())).thenReturn(List.of(operation(RuntimeOperationStatus.SUCCEEDED), operation(RuntimeOperationStatus.DEAD_LETTER)));

        var service = new AdminSystemReliabilityAnalyticsServiceImpl(
                apiMetrics, healthService, mailService, revenueRepository,
                aiRepository, pushRepository, operationRepository);
        var result = service.getAnalytics(24);

        assertEquals(24, result.windowHours());
        assertEquals(1, result.apiTrend().size());
        assertEquals(2, result.apiTrend().get(0).requests());
        assertEquals("UP", result.infrastructure().get(0).status());
        assertEquals(80.0, result.infrastructure().get(2).utilizationPercent());
        assertEquals(8, result.providers().get(0).successes());
        assertEquals(80.0, result.providers().get(0).successRate());
        assertEquals(8, result.providers().get(1).successes());
        assertEquals(6, result.providers().get(2).successes());
        assertEquals(18, result.providers().get(3).successes());
        assertEquals(1, result.operations().stream().filter(item -> item.recordType().equals("SCHEDULED_JOB")).findFirst().orElseThrow().deadLetters());
    }

    @Test
    void clampsWindowAndLeavesRateUnknownWhenProviderHasNoTraffic() {
        RuntimeApiMetricsService apiMetrics = new RuntimeApiMetricsService();
        AdminSystemHealthService healthService = mock(AdminSystemHealthService.class);
        AdminMailMonitoringService mailService = mock(AdminMailMonitoringService.class);
        SubscriptionProviderEventRepository revenueRepository = mock(SubscriptionProviderEventRepository.class);
        AiRequestHistoryRepository aiRepository = mock(AiRequestHistoryRepository.class);
        PushDeliveryLogRepository pushRepository = mock(PushDeliveryLogRepository.class);
        RuntimeOperationRecordRepository operationRepository = mock(RuntimeOperationRecordRepository.class);
        when(healthService.getHealth()).thenReturn(health());
        when(mailService.getMonitoring(7, 1)).thenReturn(new AdminMailMonitoringDto());
        when(operationRepository.findByCreatedAtAfterOrderByCreatedAtAsc(any())).thenReturn(List.of());

        var result = new AdminSystemReliabilityAnalyticsServiceImpl(
                apiMetrics, healthService, mailService, revenueRepository,
                aiRepository, pushRepository, operationRepository).getAnalytics(999);

        assertEquals(168, result.windowHours());
        assertNull(result.providers().get(0).successRate());
        assertEquals("NO_TRAFFIC", result.providers().get(1).status());
    }

    private AdminSystemHealthDto health() {
        AdminSystemHealthDto dto = new AdminSystemHealthDto();
        dto.setDatabaseStatus("UP");
        dto.setDatabaseLatencyMs(12L);
        dto.setRedisStatus("UP");
        dto.setRedisLatencyMs(4L);
        dto.setHeapUsedMb(400L);
        dto.setHeapMaxMb(500L);
        dto.setAnalyticsCacheErrors(0L);
        dto.setAnalyticsCacheHitRate(0.75);
        return dto;
    }

    private AdminMailMonitoringDto mail() {
        AdminMailMonitoringDto dto = new AdminMailMonitoringDto();
        dto.setApiKeyConfigured(true);
        dto.setProviderReachable(true);
        dto.setCounters(Map.of("requests", 20L, "delivered", 18L, "hardBounces", 1L, "softBounces", 0L, "blocked", 1L));
        return dto;
    }

    private RuntimeOperationRecordEntity operation(RuntimeOperationStatus status) {
        RuntimeOperationRecordEntity entity = new RuntimeOperationRecordEntity();
        entity.setRecordType(RuntimeOperationRecordType.SCHEDULED_JOB);
        entity.setStatus(status);
        entity.setCreatedAt(LocalDateTime.now().minusHours(1));
        return entity;
    }
}
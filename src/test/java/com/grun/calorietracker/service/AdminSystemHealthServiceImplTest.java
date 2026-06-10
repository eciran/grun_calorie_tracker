package com.grun.calorietracker.service;

import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.enums.AiDraftRejectReason;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.ProductAnalyticsEventType;
import com.grun.calorietracker.service.impl.AdminSystemHealthServiceImpl;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.SubscriptionProviderEventStatus;
import com.grun.calorietracker.enums.SubscriptionStatus;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.repository.ProductAnalyticsEventRepository;
import com.grun.calorietracker.repository.SubscriptionProviderEventRepository;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminSystemHealthServiceImplTest {

    @Test
    void getHealth_whenDatabaseIsValid_returnsUp() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Environment environment = mock(Environment.class);
        SubscriptionProviderEventRepository eventRepository = mock(SubscriptionProviderEventRepository.class);
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        AiRequestHistoryRepository aiRequestHistoryRepository = mock(AiRequestHistoryRepository.class);
        ProductAnalyticsEventRepository productAnalyticsEventRepository = mock(ProductAnalyticsEventRepository.class);
        AiProperties aiProperties = aiProperties(false, AiProvider.DISABLED);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);
        when(environment.getProperty("spring.application.name", "grun-calorie-tracker")).thenReturn("grun-calorie-tracker");
        when(environment.getProperty("info.app.version", "unknown")).thenReturn("0.0.1-SNAPSHOT");
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(eventRepository.countByStatus(SubscriptionProviderEventStatus.FAILED)).thenReturn(0L);
        when(subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE)).thenReturn(4L);
        when(subscriptionRepository.countByStatus(SubscriptionStatus.TRIALING)).thenReturn(1L);
        when(subscriptionRepository.countActiveSubscriptionsWithExhaustedAiQuota()).thenReturn(0L);
        when(notificationRepository.countByTypeAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq("system_alert"), org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(aiRequestHistoryRepository.countByCreatedAtAfter(org.mockito.ArgumentMatchers.any())).thenReturn(10L);
        when(aiRequestHistoryRepository.countByStatusAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(AiRequestStatus.FAILED), org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(aiRequestHistoryRepository.countByStatusAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(AiRequestStatus.DRAFT_CREATED), org.mockito.ArgumentMatchers.any())).thenReturn(1L);
        when(aiRequestHistoryRepository.countByStatusAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(AiRequestStatus.CONFIRMED), org.mockito.ArgumentMatchers.any())).thenReturn(8L);
        when(aiRequestHistoryRepository.countByStatusAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(AiRequestStatus.REJECTED), org.mockito.ArgumentMatchers.any())).thenReturn(1L);
        when(aiRequestHistoryRepository.countByRejectionReasonAndRejectedAtAfter(org.mockito.ArgumentMatchers.eq(AiDraftRejectReason.WRONG_PORTION), org.mockito.ArgumentMatchers.any())).thenReturn(1L);
        when(productAnalyticsEventRepository.countByEventTypeAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(ProductAnalyticsEventType.LOG_FLOW_COMPLETED), org.mockito.ArgumentMatchers.any())).thenReturn(12L);
        when(productAnalyticsEventRepository.averageDurationMs(org.mockito.ArgumentMatchers.eq(ProductAnalyticsEventType.LOG_FLOW_COMPLETED), org.mockito.ArgumentMatchers.any())).thenReturn(9000.0);
        when(productAnalyticsEventRepository.countByEventTypeAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(ProductAnalyticsEventType.QUICK_LOG_SUGGESTION_APPLIED), org.mockito.ArgumentMatchers.any())).thenReturn(7L);
        when(productAnalyticsEventRepository.countByEventTypeAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(ProductAnalyticsEventType.SEARCH_STARTED), org.mockito.ArgumentMatchers.any())).thenReturn(20L);

        AdminSystemHealthServiceImpl service = new AdminSystemHealthServiceImpl(dataSource, environment, eventRepository, subscriptionRepository, notificationRepository, aiProperties, aiRequestHistoryRepository, productAnalyticsEventRepository);

        var result = service.getHealth();

        assertEquals("UP", result.getStatus());
        assertEquals("UP", result.getDatabaseStatus());
        assertEquals("grun-calorie-tracker", result.getAppName());
        assertEquals("0.0.1-SNAPSHOT", result.getAppVersion());
        assertEquals("prod", result.getActiveProfiles().get(0));
        assertNotNull(result.getDatabaseLatencyMs());
        assertNotNull(result.getUptimeMs());
        assertNotNull(result.getHeapUsedMb());
        assertNotNull(result.getHeapMaxMb());
        assertEquals(5L, result.getActiveSubscriptions());
        assertEquals(0L, result.getFailedRevenueCatEvents());
        assertEquals(0L, result.getSystemAlertsLast24h());
        assertEquals(false, result.getAiEnabled());
        assertEquals("DISABLED", result.getAiProvider());
        assertEquals(10L, result.getAiRequestsLast24h());
        assertEquals(0L, result.getFailedAiRequestsLast24h());
        assertEquals(0.0, result.getAiFailureRateLast24h());
        assertEquals(10L, result.getAiDraftsLast7d());
        assertEquals(8L, result.getConfirmedAiDraftsLast7d());
        assertEquals(1L, result.getRejectedAiDraftsLast7d());
        assertEquals(1L, result.getAiRejectionReasonsLast7d().get("WRONG_PORTION"));
        assertEquals(1L, result.getOpenAiDraftsLast7d());
        assertEquals(0.8, result.getAiDraftConfirmationRateLast7d());
        assertEquals(12L, result.getLogFlowCompletedLast24h());
        assertEquals(9000L, result.getAverageLogFlowDurationMsLast24h());
        assertEquals(7L, result.getQuickLogSuggestionAppliedLast24h());
        assertEquals(20L, result.getSearchStartedLast24h());
        assertEquals(0, result.getWarnings().size());
        assertNotNull(result.getCheckedAt());
    }

    @Test
    void getHealth_whenDatabaseConnectionFails_returnsDegraded() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Environment environment = mock(Environment.class);
        SubscriptionProviderEventRepository eventRepository = mock(SubscriptionProviderEventRepository.class);
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        AiRequestHistoryRepository aiRequestHistoryRepository = mock(AiRequestHistoryRepository.class);
        ProductAnalyticsEventRepository productAnalyticsEventRepository = mock(ProductAnalyticsEventRepository.class);
        AiProperties aiProperties = aiProperties(true, AiProvider.LOG);
        when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));
        when(environment.getProperty("spring.application.name", "grun-calorie-tracker")).thenReturn("grun-calorie-tracker");
        when(environment.getProperty("info.app.version", "unknown")).thenReturn("unknown");
        when(environment.getActiveProfiles()).thenReturn(new String[]{});
        when(eventRepository.countByStatus(SubscriptionProviderEventStatus.FAILED)).thenReturn(2L);
        when(subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE)).thenReturn(1L);
        when(subscriptionRepository.countByStatus(SubscriptionStatus.TRIALING)).thenReturn(0L);
        when(subscriptionRepository.countActiveSubscriptionsWithExhaustedAiQuota()).thenReturn(1L);
        when(notificationRepository.countByTypeAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq("system_alert"), org.mockito.ArgumentMatchers.any())).thenReturn(2L);
        when(aiRequestHistoryRepository.countByCreatedAtAfter(org.mockito.ArgumentMatchers.any())).thenReturn(4L);
        when(aiRequestHistoryRepository.countByStatusAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(AiRequestStatus.FAILED), org.mockito.ArgumentMatchers.any())).thenReturn(1L);
        when(aiRequestHistoryRepository.countByStatusAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(AiRequestStatus.DRAFT_CREATED), org.mockito.ArgumentMatchers.any())).thenReturn(30L);
        when(aiRequestHistoryRepository.countByStatusAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(AiRequestStatus.CONFIRMED), org.mockito.ArgumentMatchers.any())).thenReturn(2L);
        when(aiRequestHistoryRepository.countByStatusAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(AiRequestStatus.REJECTED), org.mockito.ArgumentMatchers.any())).thenReturn(8L);
        when(aiRequestHistoryRepository.countByRejectionReasonAndRejectedAtAfter(org.mockito.ArgumentMatchers.eq(AiDraftRejectReason.IRRELEVANT_RESULT), org.mockito.ArgumentMatchers.any())).thenReturn(6L);
        when(productAnalyticsEventRepository.countByEventTypeAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(ProductAnalyticsEventType.LOG_FLOW_COMPLETED), org.mockito.ArgumentMatchers.any())).thenReturn(15L);
        when(productAnalyticsEventRepository.averageDurationMs(org.mockito.ArgumentMatchers.eq(ProductAnalyticsEventType.LOG_FLOW_COMPLETED), org.mockito.ArgumentMatchers.any())).thenReturn(35_000.0);
        when(productAnalyticsEventRepository.countByEventTypeAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(ProductAnalyticsEventType.QUICK_LOG_SUGGESTION_APPLIED), org.mockito.ArgumentMatchers.any())).thenReturn(2L);
        when(productAnalyticsEventRepository.countByEventTypeAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(ProductAnalyticsEventType.SEARCH_STARTED), org.mockito.ArgumentMatchers.any())).thenReturn(40L);

        AdminSystemHealthServiceImpl service = new AdminSystemHealthServiceImpl(dataSource, environment, eventRepository, subscriptionRepository, notificationRepository, aiProperties, aiRequestHistoryRepository, productAnalyticsEventRepository);

        var result = service.getHealth();

        assertEquals("DEGRADED", result.getStatus());
        assertEquals("DOWN", result.getDatabaseStatus());
        assertEquals("default", result.getActiveProfiles().get(0));
        assertEquals(2L, result.getFailedRevenueCatEvents());
        assertEquals(1L, result.getExhaustedAiQuotaSubscriptions());
        assertEquals(2L, result.getSystemAlertsLast24h());
        assertEquals(1L, result.getFailedAiRequestsLast24h());
        assertEquals(0.25, result.getAiFailureRateLast24h());
        assertEquals(40L, result.getAiDraftsLast7d());
        assertEquals(2L, result.getConfirmedAiDraftsLast7d());
        assertEquals(8L, result.getRejectedAiDraftsLast7d());
        assertEquals(6L, result.getAiRejectionReasonsLast7d().get("IRRELEVANT_RESULT"));
        assertEquals(30L, result.getOpenAiDraftsLast7d());
        assertEquals(0.05, result.getAiDraftConfirmationRateLast7d());
        assertEquals(15L, result.getLogFlowCompletedLast24h());
        assertEquals(35_000L, result.getAverageLogFlowDurationMsLast24h());
        assertEquals(2L, result.getQuickLogSuggestionAppliedLast24h());
        assertEquals(40L, result.getSearchStartedLast24h());
        assertEquals(9, result.getWarnings().size());
    }

    private AiProperties aiProperties(boolean enabled, AiProvider provider) {
        AiProperties properties = new AiProperties();
        properties.setEnabled(enabled);
        properties.setProvider(provider);
        properties.setModel(provider == AiProvider.LOG ? "log-draft-v1" : "not-configured");
        return properties;
    }

}

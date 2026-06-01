package com.grun.calorietracker.service;

import com.grun.calorietracker.service.impl.AdminSystemHealthServiceImpl;
import com.grun.calorietracker.enums.SubscriptionProviderEventStatus;
import com.grun.calorietracker.enums.SubscriptionStatus;
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

        AdminSystemHealthServiceImpl service = new AdminSystemHealthServiceImpl(dataSource, environment, eventRepository, subscriptionRepository, notificationRepository);

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
        when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));
        when(environment.getProperty("spring.application.name", "grun-calorie-tracker")).thenReturn("grun-calorie-tracker");
        when(environment.getProperty("info.app.version", "unknown")).thenReturn("unknown");
        when(environment.getActiveProfiles()).thenReturn(new String[]{});
        when(eventRepository.countByStatus(SubscriptionProviderEventStatus.FAILED)).thenReturn(2L);
        when(subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE)).thenReturn(1L);
        when(subscriptionRepository.countByStatus(SubscriptionStatus.TRIALING)).thenReturn(0L);
        when(subscriptionRepository.countActiveSubscriptionsWithExhaustedAiQuota()).thenReturn(1L);
        when(notificationRepository.countByTypeAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq("system_alert"), org.mockito.ArgumentMatchers.any())).thenReturn(2L);

        AdminSystemHealthServiceImpl service = new AdminSystemHealthServiceImpl(dataSource, environment, eventRepository, subscriptionRepository, notificationRepository);

        var result = service.getHealth();

        assertEquals("DEGRADED", result.getStatus());
        assertEquals("DOWN", result.getDatabaseStatus());
        assertEquals("default", result.getActiveProfiles().get(0));
        assertEquals(2L, result.getFailedRevenueCatEvents());
        assertEquals(1L, result.getExhaustedAiQuotaSubscriptions());
        assertEquals(2L, result.getSystemAlertsLast24h());
        assertEquals(4, result.getWarnings().size());
    }

}

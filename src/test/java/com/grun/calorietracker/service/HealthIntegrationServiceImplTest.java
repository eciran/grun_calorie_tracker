package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.HealthConnectionRequestDto;
import com.grun.calorietracker.dto.HealthMetricBatchSyncRequestDto;
import com.grun.calorietracker.dto.HealthMetricSyncRequestDto;
import com.grun.calorietracker.entity.DeviceDataEntity;
import com.grun.calorietracker.entity.HealthConnectionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.HealthConnectionStatus;
import com.grun.calorietracker.enums.HealthProvider;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.repository.DeviceDataRepository;
import com.grun.calorietracker.repository.HealthConnectionRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.HealthIntegrationServiceImpl;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HealthIntegrationServiceImplTest {

    private UserRepository userRepository;
    private HealthConnectionRepository healthConnectionRepository;
    private DeviceDataRepository deviceDataRepository;
    private SubscriptionService subscriptionService;
    private HealthIntegrationServiceImpl service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        healthConnectionRepository = mock(HealthConnectionRepository.class);
        deviceDataRepository = mock(DeviceDataRepository.class);
        subscriptionService = mock(SubscriptionService.class);
        service = new HealthIntegrationServiceImpl(
                userRepository,
                healthConnectionRepository,
                deviceDataRepository,
                subscriptionService,
                new UserTimeZoneSupport()
        );

        user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setTimeZone("Europe/Dublin");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    }

    @Test
    void connect_createsOrUpdatesConnection() {
        HealthConnectionRequestDto request = new HealthConnectionRequestDto();
        request.setProviderUserId("device-user");
        request.setDeviceModel("iPhone 15");
        request.setAppVersion("1.0.0");

        when(healthConnectionRepository.findByUserAndProvider(user, HealthProvider.APPLE_HEALTH))
                .thenReturn(Optional.empty());
        when(healthConnectionRepository.save(any(HealthConnectionEntity.class))).thenAnswer(invocation -> {
            HealthConnectionEntity entity = invocation.getArgument(0);
            entity.setId(10L);
            return entity;
        });

        var result = service.connect("user@example.com", HealthProvider.APPLE_HEALTH, request);

        assertEquals(10L, result.getId());
        assertEquals(HealthProvider.APPLE_HEALTH, result.getProvider());
        assertEquals(HealthConnectionStatus.CONNECTED, result.getStatus());
        assertEquals("device-user", result.getProviderUserId());
        assertNotNull(result.getConnectedAt());
        verify(subscriptionService).assertFeatureAccess("user@example.com", SubscriptionFeature.HEALTH_INTEGRATION);
    }

    @Test
    void connect_whenSubscriptionDoesNotAllowHealth_rejectsBeforeSaving() {
        doThrow(new IllegalArgumentException("Subscription does not allow access to feature: HEALTH_INTEGRATION"))
                .when(subscriptionService).assertFeatureAccess("user@example.com", SubscriptionFeature.HEALTH_INTEGRATION);

        assertThrows(IllegalArgumentException.class,
                () -> service.connect("user@example.com", HealthProvider.APPLE_HEALTH, new HealthConnectionRequestDto()));
        verify(healthConnectionRepository, never()).save(any());
    }

    @Test
    void getConnections_returnsUserConnections() {
        HealthConnectionEntity connection = new HealthConnectionEntity();
        connection.setId(1L);
        connection.setUser(user);
        connection.setProvider(HealthProvider.HEALTH_CONNECT);
        connection.setStatus(HealthConnectionStatus.CONNECTED);
        when(healthConnectionRepository.findByUserOrderByProviderAsc(user)).thenReturn(List.of(connection));

        var result = service.getConnections("user@example.com");

        assertEquals(1, result.size());
        assertEquals(HealthProvider.HEALTH_CONNECT, result.get(0).getProvider());
    }

    @Test
    void syncMetric_whenConnected_upsertsByExternalIdAndUpdatesLastSync() {
        HealthConnectionEntity connection = new HealthConnectionEntity();
        connection.setId(20L);
        connection.setUser(user);
        connection.setProvider(HealthProvider.GOOGLE_FIT);
        connection.setStatus(HealthConnectionStatus.CONNECTED);

        when(healthConnectionRepository.findByUserAndProvider(user, HealthProvider.GOOGLE_FIT))
                .thenReturn(Optional.of(connection));
        when(deviceDataRepository.findByUserAndProviderAndExternalId(user, HealthProvider.GOOGLE_FIT, "steps-1"))
                .thenReturn(Optional.empty());
        when(deviceDataRepository.save(any(DeviceDataEntity.class))).thenAnswer(invocation -> {
            DeviceDataEntity entity = invocation.getArgument(0);
            entity.setId(30L);
            return entity;
        });

        HealthMetricSyncRequestDto request = new HealthMetricSyncRequestDto();
        request.setExternalId("steps-1");
        request.setSteps(8400);
        request.setCaloriesBurned(320.5);
        request.setRecordedAt(LocalDateTime.of(2026, 5, 26, 8, 30));

        var result = service.syncMetric("user@example.com", HealthProvider.GOOGLE_FIT, request);

        assertEquals(30L, result.getId());
        assertEquals(HealthProvider.GOOGLE_FIT, result.getProvider());
        assertTrue(result.isInserted());

        ArgumentCaptor<DeviceDataEntity> metricCaptor = ArgumentCaptor.forClass(DeviceDataEntity.class);
        verify(deviceDataRepository).save(metricCaptor.capture());
        assertEquals(8400, metricCaptor.getValue().getSteps());
        assertEquals(320.5, metricCaptor.getValue().getCaloriesBurned());
        assertEquals("GOOGLE_FIT", metricCaptor.getValue().getSource());
        verify(healthConnectionRepository, atLeastOnce()).save(connection);
        assertEquals(LocalDateTime.of(2026, 5, 26, 8, 30), connection.getLastSyncAt());
    }

    @Test
    void syncMetric_whenExternalIdMissing_upsertsByProviderAndRecordedAt() {
        HealthConnectionEntity connection = new HealthConnectionEntity();
        connection.setUser(user);
        connection.setProvider(HealthProvider.HEALTH_CONNECT);
        connection.setStatus(HealthConnectionStatus.CONNECTED);

        DeviceDataEntity existingMetric = new DeviceDataEntity();
        existingMetric.setId(77L);

        LocalDateTime recordedAt = LocalDateTime.of(2026, 5, 26, 9, 0);
        when(healthConnectionRepository.findByUserAndProvider(user, HealthProvider.HEALTH_CONNECT))
                .thenReturn(Optional.of(connection));
        when(deviceDataRepository.findByUserAndProviderAndExternalIdIsNullAndRecordedAt(user, HealthProvider.HEALTH_CONNECT, recordedAt))
                .thenReturn(Optional.of(existingMetric));
        when(deviceDataRepository.save(any(DeviceDataEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HealthMetricSyncRequestDto request = new HealthMetricSyncRequestDto();
        request.setSteps(1000);
        request.setRecordedAt(recordedAt);

        var result = service.syncMetric("user@example.com", HealthProvider.HEALTH_CONNECT, request);

        assertEquals(77L, result.getId());
        assertFalse(result.isInserted());
        verify(deviceDataRepository).findByUserAndProviderAndExternalIdIsNullAndRecordedAt(user, HealthProvider.HEALTH_CONNECT, recordedAt);
    }

    @Test
    void getDailySummary_aggregatesMetricsAndConnectedProviders() {
        HealthConnectionEntity apple = new HealthConnectionEntity();
        apple.setProvider(HealthProvider.APPLE_HEALTH);
        apple.setStatus(HealthConnectionStatus.CONNECTED);
        apple.setLastSyncAt(LocalDateTime.of(2026, 5, 26, 10, 0));
        HealthConnectionEntity google = new HealthConnectionEntity();
        google.setProvider(HealthProvider.GOOGLE_FIT);
        google.setStatus(HealthConnectionStatus.DISCONNECTED);

        DeviceDataEntity first = new DeviceDataEntity();
        first.setSteps(1000);
        first.setCaloriesBurned(50.5);
        first.setDistanceMeters(800.0);
        first.setSleepHours(2.0);
        first.setHeartRate(70);
        DeviceDataEntity second = new DeviceDataEntity();
        second.setSteps(2000);
        second.setCaloriesBurned(100.0);
        second.setDistanceMeters(1500.0);
        second.setSleepHours(5.5);
        second.setHeartRate(80);

        when(healthConnectionRepository.findByUserOrderByProviderAsc(user)).thenReturn(List.of(apple, google));
        when(deviceDataRepository.findByUserAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(
                eq(user),
                eq(java.time.LocalDate.of(2026, 5, 26).atStartOfDay()),
                eq(java.time.LocalDate.of(2026, 5, 27).atStartOfDay())
        )).thenReturn(List.of(first, second));

        var result = service.getDailySummary("user@example.com", java.time.LocalDate.of(2026, 5, 26));

        assertEquals(List.of(HealthProvider.APPLE_HEALTH), result.getConnectedProviders());
        assertEquals(3000, result.getTotalSteps());
        assertEquals(150.5, result.getTotalCaloriesBurned());
        assertEquals(2300.0, result.getTotalDistanceMeters());
        assertEquals(7.5, result.getTotalSleepHours());
        assertEquals(75.0, result.getAverageHeartRate());
        assertEquals(true, result.getHasHealthData());
        assertEquals(LocalDateTime.of(2026, 5, 26, 10, 0), result.getLatestSyncAt());
    }

    @Test
    void syncMetrics_acceptsBatchAndReturnsInsertUpdateCounts() {
        HealthConnectionEntity connection = new HealthConnectionEntity();
        connection.setUser(user);
        connection.setProvider(HealthProvider.APPLE_HEALTH);
        connection.setStatus(HealthConnectionStatus.CONNECTED);

        when(healthConnectionRepository.findByUserAndProvider(user, HealthProvider.APPLE_HEALTH))
                .thenReturn(Optional.of(connection));
        when(deviceDataRepository.findByUserAndProviderAndExternalId(user, HealthProvider.APPLE_HEALTH, "new"))
                .thenReturn(Optional.empty());

        DeviceDataEntity existing = new DeviceDataEntity();
        existing.setId(55L);
        when(deviceDataRepository.findByUserAndProviderAndExternalId(user, HealthProvider.APPLE_HEALTH, "existing"))
                .thenReturn(Optional.of(existing));
        when(deviceDataRepository.save(any(DeviceDataEntity.class))).thenAnswer(invocation -> {
            DeviceDataEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(99L);
            }
            return entity;
        });

        HealthMetricSyncRequestDto first = new HealthMetricSyncRequestDto();
        first.setExternalId("new");
        first.setSteps(100);
        first.setRecordedAt(LocalDateTime.of(2026, 5, 26, 8, 0));
        HealthMetricSyncRequestDto second = new HealthMetricSyncRequestDto();
        second.setExternalId("existing");
        second.setSteps(200);
        second.setRecordedAt(LocalDateTime.of(2026, 5, 26, 9, 0));
        HealthMetricBatchSyncRequestDto request = new HealthMetricBatchSyncRequestDto();
        request.setMetrics(List.of(first, second));

        var result = service.syncMetrics("user@example.com", HealthProvider.APPLE_HEALTH, request);

        assertEquals(2, result.getAcceptedCount());
        assertEquals(1, result.getInsertedCount());
        assertEquals(1, result.getUpdatedCount());
        assertEquals(LocalDateTime.of(2026, 5, 26, 9, 0), result.getLatestRecordedAt());
    }

    @Test
    void syncMetrics_whenBatchExceedsLimit_rejectsBeforeRepositoryWork() {
        List<HealthMetricSyncRequestDto> metrics = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            HealthMetricSyncRequestDto metric = new HealthMetricSyncRequestDto();
            metric.setSteps(i);
            metric.setRecordedAt(LocalDateTime.of(2026, 5, 26, 8, 0).plusMinutes(i));
            metrics.add(metric);
        }
        HealthMetricBatchSyncRequestDto request = new HealthMetricBatchSyncRequestDto();
        request.setMetrics(metrics);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.syncMetrics("user@example.com", HealthProvider.APPLE_HEALTH, request)
        );

        assertEquals("Health metric batch cannot exceed 500 metrics.", ex.getMessage());
        verify(deviceDataRepository, never()).save(any());
    }

    @Test
    void getRangeSummary_returnsDailyAndRangeTotals() {
        HealthConnectionEntity connection = new HealthConnectionEntity();
        connection.setProvider(HealthProvider.APPLE_HEALTH);
        connection.setStatus(HealthConnectionStatus.CONNECTED);
        when(healthConnectionRepository.findByUserOrderByProviderAsc(user)).thenReturn(List.of(connection));

        DeviceDataEntity dayOne = new DeviceDataEntity();
        dayOne.setSteps(1000);
        dayOne.setCaloriesBurned(100.0);
        dayOne.setDistanceMeters(500.0);
        dayOne.setSleepHours(7.0);
        dayOne.setHeartRate(70);
        DeviceDataEntity dayTwo = new DeviceDataEntity();
        dayTwo.setSteps(2000);
        dayTwo.setCaloriesBurned(150.0);
        dayTwo.setDistanceMeters(700.0);
        dayTwo.setSleepHours(8.0);
        dayTwo.setHeartRate(80);

        when(deviceDataRepository.findByUserAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(
                eq(user),
                eq(java.time.LocalDate.of(2026, 5, 26).atStartOfDay()),
                eq(java.time.LocalDate.of(2026, 5, 27).atStartOfDay())
        )).thenReturn(List.of(dayOne));
        when(deviceDataRepository.findByUserAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(
                eq(user),
                eq(java.time.LocalDate.of(2026, 5, 27).atStartOfDay()),
                eq(java.time.LocalDate.of(2026, 5, 28).atStartOfDay())
        )).thenReturn(List.of(dayTwo));

        var result = service.getRangeSummary(
                "user@example.com",
                java.time.LocalDate.of(2026, 5, 26),
                java.time.LocalDate.of(2026, 5, 27)
        );

        assertEquals(2, result.getDays().size());
        assertEquals(3000, result.getTotalSteps());
        assertEquals(250.0, result.getTotalCaloriesBurned());
        assertEquals(1200.0, result.getTotalDistanceMeters());
        assertEquals(15.0, result.getTotalSleepHours());
        assertEquals(75.0, result.getAverageHeartRate());
        assertEquals(true, result.getHasHealthData());
    }

    @Test
    void deleteProviderData_deletesMetricsAndRevokesConnection() {
        HealthConnectionEntity connection = new HealthConnectionEntity();
        connection.setUser(user);
        connection.setProvider(HealthProvider.APPLE_HEALTH);
        connection.setStatus(HealthConnectionStatus.CONNECTED);
        connection.setLastSyncAt(LocalDateTime.now());

        when(healthConnectionRepository.findByUserAndProvider(user, HealthProvider.APPLE_HEALTH))
                .thenReturn(Optional.of(connection));
        when(deviceDataRepository.deleteByUserAndProvider(user, HealthProvider.APPLE_HEALTH)).thenReturn(12L);

        var result = service.deleteProviderData("user@example.com", HealthProvider.APPLE_HEALTH);

        assertEquals(HealthProvider.APPLE_HEALTH, result.getProvider());
        assertEquals(12L, result.getDeletedMetricCount());
        assertEquals(HealthConnectionStatus.REVOKED, connection.getStatus());
        assertNull(connection.getLastSyncAt());
        assertNotNull(connection.getDisconnectedAt());
        verify(healthConnectionRepository).save(connection);
        verify(subscriptionService, never()).assertFeatureAccess(any(), any());
    }

    @Test
    void deleteAllHealthData_deletesAllMetricsAndRevokesConnections() {
        HealthConnectionEntity connection = new HealthConnectionEntity();
        connection.setProvider(HealthProvider.HEALTH_CONNECT);
        connection.setStatus(HealthConnectionStatus.CONNECTED);

        when(deviceDataRepository.deleteByUser(user)).thenReturn(25L);
        when(healthConnectionRepository.findByUserOrderByProviderAsc(user)).thenReturn(List.of(connection));

        var result = service.deleteAllHealthData("user@example.com");

        assertNull(result.getProvider());
        assertEquals(25L, result.getDeletedMetricCount());
        assertEquals(HealthConnectionStatus.REVOKED, connection.getStatus());
        verify(healthConnectionRepository).save(connection);
    }

    @Test
    void syncMetric_whenProviderIsNotConnected_rejectsPayload() {
        when(healthConnectionRepository.findByUserAndProvider(user, HealthProvider.APPLE_HEALTH))
                .thenReturn(Optional.empty());

        HealthMetricSyncRequestDto request = new HealthMetricSyncRequestDto();
        request.setSteps(100);
        request.setRecordedAt(LocalDateTime.now());

        assertThrows(IllegalArgumentException.class,
                () -> service.syncMetric("user@example.com", HealthProvider.APPLE_HEALTH, request));
        verify(deviceDataRepository, never()).save(any());
    }
}

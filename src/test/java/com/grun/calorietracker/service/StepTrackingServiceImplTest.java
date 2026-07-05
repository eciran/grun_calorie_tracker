package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.StepGoalRequestDto;
import com.grun.calorietracker.dto.StepManualLogRequestDto;
import com.grun.calorietracker.entity.DeviceDataEntity;
import com.grun.calorietracker.entity.StepGoalEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.HealthProvider;
import com.grun.calorietracker.exception.DuplicateManualStepLogException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.DeviceDataRepository;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.StepGoalRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.StepTrackingServiceImpl;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StepTrackingServiceImplTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final StepGoalRepository stepGoalRepository = mock(StepGoalRepository.class);
    private final DeviceDataRepository deviceDataRepository = mock(DeviceDataRepository.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final PushDeliveryService pushDeliveryService = mock(PushDeliveryService.class);
    private final UserTimeZoneSupport userTimeZoneSupport = mock(UserTimeZoneSupport.class);
    private final StepTrackingServiceImpl service = new StepTrackingServiceImpl(
            userRepository,
            stepGoalRepository,
            deviceDataRepository,
            notificationRepository,
            pushDeliveryService,
            userTimeZoneSupport
    );

    @Test
    void getDailySummary_aggregatesStepsAndGoalProgress() {
        UserEntity user = user();
        StepGoalEntity goal = goal(user, 8000);
        LocalDate date = LocalDate.of(2026, 6, 12);
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(stepGoalRepository.findByUser(user)).thenReturn(Optional.of(goal));
        when(deviceDataRepository.findByUserAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(
                user,
                date.atStartOfDay(),
                date.plusDays(1).atStartOfDay()
        )).thenReturn(List.of(metric(5000, HealthProvider.HEALTH_CONNECT, date.atTime(12, 0)), metric(3500, HealthProvider.MANUAL, date.atTime(18, 0))));
        when(deviceDataRepository.findByUserAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(
                user,
                date.minusDays(1).atStartOfDay(),
                date.atStartOfDay()
        )).thenReturn(List.of(metric(9000, HealthProvider.HEALTH_CONNECT, date.minusDays(1).atTime(20, 0))));
        when(deviceDataRepository.findByUserAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(
                user,
                date.minusDays(2).atStartOfDay(),
                date.minusDays(1).atStartOfDay()
        )).thenReturn(List.of(metric(3000, HealthProvider.HEALTH_CONNECT, date.minusDays(2).atTime(20, 0))));

        var result = service.getDailySummary("user@grun.app", date);

        assertEquals(8500, result.getTotalSteps());
        assertEquals(8000, result.getTargetSteps());
        assertEquals(0, result.getRemainingSteps());
        assertEquals(106.25, result.getProgressPercent());
        assertEquals(true, result.getTargetReached());
        assertEquals(2, result.getCurrentStreakDays());
        assertEquals(List.of(HealthProvider.HEALTH_CONNECT, HealthProvider.MANUAL), result.getProviders());
    }

    @Test
    void getRangeSummary_usesDailyAggregateRows() {
        UserEntity user = user();
        StepGoalEntity goal = goal(user, 10000);
        LocalDate start = LocalDate.of(2026, 6, 10);
        LocalDate end = LocalDate.of(2026, 6, 12);
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(stepGoalRepository.findByUser(user)).thenReturn(Optional.of(goal));
        when(deviceDataRepository.aggregateDailyStepsByUser(user.getId(), start, end)).thenReturn(List.of(
                new Object[]{java.sql.Date.valueOf(start), 5000L, 3500.0, 200.0, Timestamp.valueOf(start.atTime(20, 0)), 1L},
                new Object[]{java.sql.Date.valueOf(end), 12000L, 8000.0, 480.0, Timestamp.valueOf(end.atTime(21, 0)), 2L}
        ));

        var result = service.getRangeSummary("user@grun.app", start, end);

        assertEquals(3, result.getDayCount());
        assertEquals(17000, result.getTotalSteps());
        assertEquals(5666.67, result.getAverageSteps());
        assertEquals(12000, result.getBestSteps());
        assertEquals(1, result.getTargetHitDays());
        assertEquals(0, result.getDays().get(1).getTotalSteps());
        assertEquals(false, result.getDays().get(1).getHasStepData());
    }

    @Test
    void updateGoal_savesTargetAndReminderPreference() {
        UserEntity user = user();
        StepGoalRequestDto request = new StepGoalRequestDto();
        request.setTargetSteps(12000);
        request.setReminderEnabled(true);
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(stepGoalRepository.findByUser(user)).thenReturn(Optional.empty());
        when(stepGoalRepository.save(any(StepGoalEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.updateGoal("user@grun.app", request);

        assertEquals(12000, result.getTargetSteps());
        assertEquals(true, result.getReminderEnabled());
    }

    @Test
    void getManualLogs_returnsManualMetricsForDateRange() {
        UserEntity user = user();
        LocalDate start = LocalDate.of(2026, 6, 12);
        DeviceDataEntity metric = metric(1200, HealthProvider.MANUAL, LocalDateTime.of(2026, 6, 12, 18, 30));
        metric.setId(55L);
        metric.setDistanceMeters(850.0);
        metric.setCaloriesBurned(45.0);
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(deviceDataRepository.findByUserAndProviderAndRecordedAtBetweenOrderByRecordedAtAsc(
                user,
                HealthProvider.MANUAL,
                start.atStartOfDay(),
                start.plusDays(1).atStartOfDay()
        )).thenReturn(List.of(metric));

        var result = service.getManualLogs("user@grun.app", start, start);

        assertEquals(1, result.size());
        assertEquals(55L, result.get(0).getId());
        assertEquals(1200, result.get(0).getSteps());
        assertEquals(45.0, result.get(0).getCaloriesBurned());
    }
    @Test
    void addManualLog_savesManualDeviceMetric() {
        UserEntity user = user();
        StepManualLogRequestDto request = manualRequest(1200, LocalDateTime.of(2026, 6, 12, 18, 30));
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(userTimeZoneSupport.today(user)).thenReturn(LocalDate.of(2026, 6, 12));
        when(deviceDataRepository.findByUserAndProviderAndExternalIdIsNullAndRecordedAt(user, HealthProvider.MANUAL, request.getRecordedAt()))
                .thenReturn(Optional.empty());
        when(deviceDataRepository.sumStepsByUserAndRecordedAtRange(user, request.getRecordedAt().toLocalDate().atStartOfDay(), request.getRecordedAt().toLocalDate().plusDays(1).atStartOfDay()))
                .thenReturn(0L);
        when(deviceDataRepository.save(any(DeviceDataEntity.class))).thenAnswer(invocation -> {
            DeviceDataEntity entity = invocation.getArgument(0);
            entity.setId(55L);
            assertEquals(HealthProvider.MANUAL, entity.getProvider());
            assertEquals("MANUAL", entity.getSource());
            return entity;
        });

        var result = service.addManualLog("user@grun.app", request);

        assertEquals(55L, result.getId());
        assertEquals(1200, result.getSteps());
    }

    @Test
    void addManualLog_whenDuplicateRecordedAt_rejectsRequest() {
        UserEntity user = user();
        StepManualLogRequestDto request = manualRequest(1200, LocalDateTime.of(2026, 6, 12, 18, 30));
        DeviceDataEntity existing = metric(1000, HealthProvider.MANUAL, request.getRecordedAt());
        existing.setId(10L);
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(userTimeZoneSupport.today(user)).thenReturn(LocalDate.of(2026, 6, 12));
        when(deviceDataRepository.findByUserAndProviderAndExternalIdIsNullAndRecordedAt(user, HealthProvider.MANUAL, request.getRecordedAt()))
                .thenReturn(Optional.of(existing));

        assertThrows(DuplicateManualStepLogException.class, () -> service.addManualLog("user@grun.app", request));
        verify(deviceDataRepository, never()).save(any(DeviceDataEntity.class));
    }

    @Test
    void addManualLog_whenDailyLimitWouldBeExceeded_rejectsRequest() {
        UserEntity user = user();
        StepManualLogRequestDto request = manualRequest(2000, LocalDateTime.of(2026, 6, 12, 18, 30));
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(userTimeZoneSupport.today(user)).thenReturn(LocalDate.of(2026, 6, 12));
        when(deviceDataRepository.findByUserAndProviderAndExternalIdIsNullAndRecordedAt(user, HealthProvider.MANUAL, request.getRecordedAt()))
                .thenReturn(Optional.empty());
        when(deviceDataRepository.sumStepsByUserAndRecordedAtRange(user, request.getRecordedAt().toLocalDate().atStartOfDay(), request.getRecordedAt().toLocalDate().plusDays(1).atStartOfDay()))
                .thenReturn(119000L);

        assertThrows(IllegalArgumentException.class, () -> service.addManualLog("user@grun.app", request));
        verify(deviceDataRepository, never()).save(any(DeviceDataEntity.class));
    }

    @Test
    void updateManualLog_whenOwnedManualLogExists_updatesIt() {
        UserEntity user = user();
        StepManualLogRequestDto request = manualRequest(1500, LocalDateTime.of(2026, 6, 12, 19, 0));
        DeviceDataEntity existing = metric(1000, HealthProvider.MANUAL, LocalDateTime.of(2026, 6, 12, 18, 30));
        existing.setId(55L);
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(deviceDataRepository.findByIdAndUserAndProvider(55L, user, HealthProvider.MANUAL)).thenReturn(Optional.of(existing));
        when(userTimeZoneSupport.today(user)).thenReturn(LocalDate.of(2026, 6, 12));
        when(deviceDataRepository.findByUserAndProviderAndExternalIdIsNullAndRecordedAt(user, HealthProvider.MANUAL, request.getRecordedAt()))
                .thenReturn(Optional.empty());
        when(deviceDataRepository.sumStepsByUserAndRecordedAtRange(user, request.getRecordedAt().toLocalDate().atStartOfDay(), request.getRecordedAt().toLocalDate().plusDays(1).atStartOfDay()))
                .thenReturn(1000L);
        when(deviceDataRepository.save(existing)).thenReturn(existing);

        var result = service.updateManualLog("user@grun.app", 55L, request);

        assertEquals(55L, result.getId());
        assertEquals(1500, result.getSteps());
        assertEquals(LocalDateTime.of(2026, 6, 12, 19, 0), existing.getRecordedAt());
    }

    @Test
    void updateManualLog_whenMissing_throwsNotFound() {
        UserEntity user = user();
        StepManualLogRequestDto request = manualRequest(1500, LocalDateTime.of(2026, 6, 12, 19, 0));
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(deviceDataRepository.findByIdAndUserAndProvider(55L, user, HealthProvider.MANUAL)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.updateManualLog("user@grun.app", 55L, request));
    }

    @Test
    void deleteManualLog_whenOwnedManualLogExists_deletesIt() {
        UserEntity user = user();
        DeviceDataEntity existing = metric(1000, HealthProvider.MANUAL, LocalDateTime.of(2026, 6, 12, 18, 30));
        existing.setId(55L);
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(deviceDataRepository.findByIdAndUserAndProvider(55L, user, HealthProvider.MANUAL)).thenReturn(Optional.of(existing));

        service.deleteManualLog("user@grun.app", 55L);

        verify(deviceDataRepository).delete(existing);
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@grun.app");
        return user;
    }

    private StepGoalEntity goal(UserEntity user, int targetSteps) {
        StepGoalEntity goal = new StepGoalEntity();
        goal.setUser(user);
        goal.setTargetSteps(targetSteps);
        goal.setReminderEnabled(false);
        return goal;
    }

    private StepManualLogRequestDto manualRequest(int steps, LocalDateTime recordedAt) {
        StepManualLogRequestDto request = new StepManualLogRequestDto();
        request.setSteps(steps);
        request.setRecordedAt(recordedAt);
        return request;
    }

    private DeviceDataEntity metric(int steps, HealthProvider provider, LocalDateTime recordedAt) {
        DeviceDataEntity metric = new DeviceDataEntity();
        metric.setSteps(steps);
        metric.setProvider(provider);
        metric.setRecordedAt(recordedAt);
        return metric;
    }
}
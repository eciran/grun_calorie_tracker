package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.StepGoalRequestDto;
import com.grun.calorietracker.dto.StepManualLogRequestDto;
import com.grun.calorietracker.entity.DeviceDataEntity;
import com.grun.calorietracker.entity.StepGoalEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.HealthProvider;
import com.grun.calorietracker.repository.DeviceDataRepository;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.StepGoalRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.StepTrackingServiceImpl;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StepTrackingServiceImplTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final StepGoalRepository stepGoalRepository = mock(StepGoalRepository.class);
    private final DeviceDataRepository deviceDataRepository = mock(DeviceDataRepository.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final UserTimeZoneSupport userTimeZoneSupport = mock(UserTimeZoneSupport.class);
    private final StepTrackingServiceImpl service = new StepTrackingServiceImpl(
            userRepository,
            stepGoalRepository,
            deviceDataRepository,
            notificationRepository,
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
    void addManualLog_savesManualDeviceMetric() {
        UserEntity user = user();
        StepManualLogRequestDto request = new StepManualLogRequestDto();
        request.setSteps(1200);
        request.setRecordedAt(LocalDateTime.of(2026, 6, 12, 18, 30));
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
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

    private DeviceDataEntity metric(int steps, HealthProvider provider, LocalDateTime recordedAt) {
        DeviceDataEntity metric = new DeviceDataEntity();
        metric.setSteps(steps);
        metric.setProvider(provider);
        metric.setRecordedAt(recordedAt);
        return metric;
    }
}

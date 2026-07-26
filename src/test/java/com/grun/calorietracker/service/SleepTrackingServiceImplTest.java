package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.impl.SleepTrackingServiceImpl;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SleepTrackingServiceImplTest {

    @Mock private SleepSessionRepository sessionRepository;
    @Mock private SleepGoalRepository goalRepository;
    @Mock private UserRepository userRepository;
    @Mock private HealthConnectionRepository healthConnectionRepository;
    @Mock private SubscriptionService subscriptionService;

    private SleepTrackingServiceImpl service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        service = new SleepTrackingServiceImpl(
                sessionRepository,
                goalRepository,
                userRepository,
                healthConnectionRepository,
                subscriptionService,
                new UserTimeZoneSupport());
        user = new UserEntity();
        user.setId(7L);
        user.setEmail("sleep@grun.app");
        user.setTimeZone("Europe/Dublin");
        lenient().when(userRepository.findByEmail("sleep@grun.app")).thenReturn(Optional.of(user));
        lenient().when(goalRepository.findByUser(user)).thenReturn(Optional.empty());
        lenient().when(sessionRepository.save(any())).thenAnswer(invocation -> {
            SleepSessionEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) entity.setId(11L);
            return entity;
        });
    }

    @Test
    void createManual_CalculatesCrossMidnightDurationAndWakeDate() {
        SleepSessionRequestDto request = request(
                "2026-07-20T22:30:00+01:00",
                "2026-07-21T06:30:00+01:00");

        SleepSessionDto result = service.createManual("sleep@grun.app", request);

        assertEquals(480, result.getDurationMinutes());
        assertEquals(LocalDate.of(2026, 7, 21), result.getSleepDate());
        assertEquals("Europe/Dublin", result.getTimeZone());
        assertEquals(100, result.getQualityScore());
        assertEquals(SleepQualityConfidence.DURATION_ONLY, result.getQualityConfidence());
        verify(sessionRepository).existsByUserAndProviderAndStartedAtAndEndedAt(
                eq(user), eq(HealthProvider.MANUAL), any(), any());
    }

    @Test
    void syncProvider_IsIdempotentAndUsesElapsedTimeAcrossDstChange() {
        HealthConnectionEntity connection = new HealthConnectionEntity();
        connection.setUser(user);
        connection.setProvider(HealthProvider.APPLE_HEALTH);
        connection.setStatus(HealthConnectionStatus.CONNECTED);
        when(healthConnectionRepository.findByUserAndProvider(user, HealthProvider.APPLE_HEALTH))
                .thenReturn(Optional.of(connection));

        SleepSessionEntity existing = new SleepSessionEntity();
        existing.setId(11L);
        existing.setStages(new java.util.ArrayList<>());
        when(sessionRepository.findByUserAndProviderAndExternalId(
                user, HealthProvider.APPLE_HEALTH, "apple-sleep-1"))
                .thenReturn(Optional.empty(), Optional.of(existing));

        SleepSessionRequestDto request = request(
                "2026-03-28T23:30:00Z",
                "2026-03-29T07:30:00+01:00");
        request.setExternalId("apple-sleep-1");
        request.setStages(List.of(stage(
                SleepStageType.ASLEEP,
                "2026-03-28T23:30:00Z",
                "2026-03-29T07:30:00+01:00")));

        SleepSessionDto first = service.syncProvider("sleep@grun.app", HealthProvider.APPLE_HEALTH, request);
        SleepSessionDto second = service.syncProvider("sleep@grun.app", HealthProvider.APPLE_HEALTH, request);

        assertEquals(420, first.getDurationMinutes());
        assertEquals(first.getId(), second.getId());
        assertEquals(11L, second.getId());
        assertEquals(LocalDate.of(2026, 3, 29), second.getSleepDate());
        assertEquals(SleepQualityConfidence.FULL_STAGES, second.getQualityConfidence());
        verify(subscriptionService, times(2)).assertFeatureAccess(
                "sleep@grun.app", SubscriptionFeature.HEALTH_INTEGRATION);
        verify(sessionRepository, times(2)).save(any(SleepSessionEntity.class));
    }

    @Test
    void createManual_RejectsDuplicateAndOverlappingStages() {
        SleepSessionRequestDto duplicate = request(
                "2026-07-20T22:30:00+01:00",
                "2026-07-21T06:30:00+01:00");
        when(sessionRepository.existsByUserAndProviderAndStartedAtAndEndedAt(
                eq(user), eq(HealthProvider.MANUAL), any(), any())).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
                () -> service.createManual("sleep@grun.app", duplicate));

        reset(sessionRepository);

        SleepSessionRequestDto overlap = request(
                "2026-07-20T22:00:00+01:00",
                "2026-07-21T06:00:00+01:00");
        overlap.setStages(List.of(
                stage(SleepStageType.LIGHT, "2026-07-20T22:00:00+01:00", "2026-07-21T02:00:00+01:00"),
                stage(SleepStageType.DEEP, "2026-07-21T01:30:00+01:00", "2026-07-21T03:00:00+01:00")));
        assertThrows(IllegalArgumentException.class,
                () -> service.createManual("sleep@grun.app", overlap));
    }

    @Test
    void weeklySummary_PreservesMissingDaysAndCalculatesLoggedAverages() {
        SleepSessionEntity first = entity(LocalDate.of(2026, 7, 20), 480, 90);
        SleepSessionEntity second = entity(LocalDate.of(2026, 7, 22), 420, 80);
        when(sessionRepository.findByUserAndSleepDateBetweenOrderByStartedAtAsc(
                user, LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 23)))
                .thenReturn(List.of(first, second));

        SleepWeeklySummaryDto result = service.weeklySummary(
                "sleep@grun.app", LocalDate.of(2026, 7, 23));

        verify(subscriptionService).assertFeatureAccess(
                "sleep@grun.app", SubscriptionFeature.ADVANCED_ANALYTICS);
        assertEquals(7, result.getDays().size());
        assertEquals(2, result.getLoggedDays());
        assertEquals(1, result.getTargetHitDays());
        assertEquals(450.0, result.getAverageSleepMinutesOnLoggedDays());
        assertEquals(85.0, result.getAverageQualityScore());
        assertEquals(0, result.getDays().get(0).getSessionCount());
    }

    private SleepSessionRequestDto request(String start, String end) {
        SleepSessionRequestDto request = new SleepSessionRequestDto();
        request.setStartAt(OffsetDateTime.parse(start));
        request.setEndAt(OffsetDateTime.parse(end));
        return request;
    }

    private SleepStageRequestDto stage(SleepStageType type, String start, String end) {
        SleepStageRequestDto stage = new SleepStageRequestDto();
        stage.setStageType(type);
        stage.setStartAt(OffsetDateTime.parse(start));
        stage.setEndAt(OffsetDateTime.parse(end));
        return stage;
    }

    private SleepSessionEntity entity(LocalDate date, int duration, int quality) {
        ZoneId zone = ZoneId.of("Europe/Dublin");
        SleepSessionEntity entity = new SleepSessionEntity();
        entity.setId((long) duration);
        entity.setUser(user);
        entity.setSleepDate(date);
        entity.setStartedAt(date.minusDays(1).atTime(22, 0).atZone(zone).toInstant());
        entity.setEndedAt(date.atTime(6, 0).atZone(zone).toInstant());
        entity.setDurationMinutes(duration);
        entity.setTimeZone(zone.getId());
        entity.setProvider(HealthProvider.MANUAL);
        entity.setQualityScore(quality);
        entity.setQualityConfidence(SleepQualityConfidence.DURATION_ONLY);
        entity.setStages(new java.util.ArrayList<>());
        return entity;
    }
}

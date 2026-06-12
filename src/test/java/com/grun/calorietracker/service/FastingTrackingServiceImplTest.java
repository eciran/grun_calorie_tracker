package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FastingPlanRequestDto;
import com.grun.calorietracker.dto.FastingRangeSummaryDto;
import com.grun.calorietracker.dto.FastingSessionCancelRequestDto;
import com.grun.calorietracker.dto.FastingSessionFinishRequestDto;
import com.grun.calorietracker.dto.FastingSessionStartRequestDto;
import com.grun.calorietracker.entity.FastingPlanEntity;
import com.grun.calorietracker.entity.FastingSessionEntity;
import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FastingPlanType;
import com.grun.calorietracker.enums.FastingSessionStatus;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.FastingPlanRepository;
import com.grun.calorietracker.repository.FastingSessionRepository;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.FastingTrackingServiceImpl;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FastingTrackingServiceImplTest {

    @Mock
    private FastingPlanRepository fastingPlanRepository;
    @Mock
    private FastingSessionRepository fastingSessionRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PushDeliveryService pushDeliveryService;

    private FastingTrackingServiceImpl service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new FastingTrackingServiceImpl(
                fastingPlanRepository,
                fastingSessionRepository,
                notificationRepository,
                userRepository,
                new UserTimeZoneSupport(),
                pushDeliveryService
        );
        ReflectionTestUtils.setField(service, "fastingRemindersEnabled", true);
        ReflectionTestUtils.setField(service, "fastingReminderLeadMinutes", 30);
        user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@grun.app");
        user.setTimeZone("Europe/Dublin");
    }

    @Test
    void updatePlan_savesUserFastingPlan() {
        FastingPlanRequestDto request = planRequest();
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(fastingPlanRepository.findByUser(user)).thenReturn(Optional.empty());
        when(fastingPlanRepository.save(any(FastingPlanEntity.class))).thenAnswer(invocation -> {
            FastingPlanEntity entity = invocation.getArgument(0);
            entity.setId(10L);
            return entity;
        });

        var result = service.updatePlan("user@grun.app", request);

        assertEquals(10L, result.getId());
        assertEquals(FastingPlanType.FASTING_16_8, result.getPlanType());
        assertEquals(16, result.getFastingHours());
        assertEquals(8, result.getEatingWindowHours());
        assertEquals(true, result.getActive());
        verify(fastingPlanRepository).save(any(FastingPlanEntity.class));
    }

    @Test
    void startSession_usesActivePlanTargetAndPreventsOverlap() {
        FastingPlanEntity plan = activePlan();
        FastingSessionStartRequestDto request = new FastingSessionStartRequestDto();
        request.setStartedAt(LocalDateTime.of(2026, 6, 5, 20, 0));

        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(fastingSessionRepository.findTopByUserAndStatusOrderByStartedAtDesc(user, FastingSessionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(fastingPlanRepository.findByUser(user)).thenReturn(Optional.of(plan));
        when(fastingSessionRepository.save(any(FastingSessionEntity.class))).thenAnswer(invocation -> {
            FastingSessionEntity entity = invocation.getArgument(0);
            entity.setId(20L);
            return entity;
        });

        var result = service.startSession("user@grun.app", request);

        assertEquals(20L, result.getId());
        assertEquals(FastingSessionStatus.ACTIVE, result.getStatus());
        assertEquals(960, result.getTargetMinutes());
        assertEquals(LocalDateTime.of(2026, 6, 6, 12, 0), result.getTargetEndAt());
    }

    @Test
    void startSession_whenActiveSessionExists_rejectsRequest() {
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(fastingSessionRepository.findTopByUserAndStatusOrderByStartedAtDesc(user, FastingSessionStatus.ACTIVE))
                .thenReturn(Optional.of(new FastingSessionEntity()));

        assertThrows(IllegalArgumentException.class, () -> service.startSession("user@grun.app", new FastingSessionStartRequestDto()));
    }

    @Test
    void finishSession_calculatesActualMinutesAndTargetReached() {
        FastingSessionEntity session = activeSession();
        FastingSessionFinishRequestDto request = new FastingSessionFinishRequestDto();
        request.setEndedAt(LocalDateTime.of(2026, 6, 6, 12, 5));
        request.setNote("Done");

        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(fastingSessionRepository.findByIdAndUser(20L, user)).thenReturn(Optional.of(session));
        when(fastingSessionRepository.save(session)).thenReturn(session);

        var result = service.finishSession("user@grun.app", 20L, request);

        assertEquals(FastingSessionStatus.COMPLETED, result.getStatus());
        assertEquals(965, result.getActualMinutes());
        assertEquals(true, result.getTargetReached());
        assertEquals("Done", result.getNote());
    }

    @Test
    void cancelSession_marksActiveSessionAsCancelled() {
        FastingSessionEntity session = activeSession();
        FastingSessionCancelRequestDto request = new FastingSessionCancelRequestDto();
        request.setCancelledAt(LocalDateTime.of(2026, 6, 5, 22, 30));
        request.setNote("Started by mistake");

        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(fastingSessionRepository.findByIdAndUser(20L, user)).thenReturn(Optional.of(session));
        when(fastingSessionRepository.save(session)).thenReturn(session);

        var result = service.cancelSession("user@grun.app", 20L, request);

        assertEquals(FastingSessionStatus.CANCELLED, result.getStatus());
        assertEquals(150, result.getActualMinutes());
        assertEquals(false, result.getTargetReached());
        assertEquals("Started by mistake", result.getNote());
    }

    @Test
    void finishSession_whenSessionMissing_throwsNotFound() {
        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(fastingSessionRepository.findByIdAndUser(99L, user)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.finishSession("user@grun.app", 99L, new FastingSessionFinishRequestDto()));
    }

    @Test
    void getDailySummary_returnsSessionsAndStreak() {
        FastingSessionEntity completed = activeSession();
        completed.setStatus(FastingSessionStatus.COMPLETED);
        completed.setEndedAt(LocalDateTime.of(2026, 6, 6, 12, 0));
        completed.setActualMinutes(960);
        completed.setTargetReached(true);

        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(fastingPlanRepository.findByUser(user)).thenReturn(Optional.of(activePlan()));
        when(fastingSessionRepository.findByUserAndFastingDateOrderByStartedAtAsc(user, LocalDate.of(2026, 6, 5)))
                .thenReturn(List.of(completed));
        when(fastingSessionRepository.findTopByUserAndStatusOrderByStartedAtDesc(user, FastingSessionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(fastingSessionRepository.existsByUserAndFastingDateAndStatusAndTargetReachedTrue(user, LocalDate.of(2026, 6, 5), FastingSessionStatus.COMPLETED))
                .thenReturn(true);
        when(fastingSessionRepository.existsByUserAndFastingDateAndStatusAndTargetReachedTrue(user, LocalDate.of(2026, 6, 4), FastingSessionStatus.COMPLETED))
                .thenReturn(false);

        var result = service.getDailySummary("user@grun.app", LocalDate.of(2026, 6, 5));

        assertEquals(1, result.getSessions().size());
        assertEquals(960, result.getTotalCompletedMinutes());
        assertEquals(1, result.getCurrentStreakDays());
    }

    @Test
    void getRangeSummary_returnsTrendMetrics() {
        FastingSessionEntity reached = activeSession();
        reached.setStatus(FastingSessionStatus.COMPLETED);
        reached.setFastingDate(LocalDate.of(2026, 6, 4));
        reached.setActualMinutes(970);
        reached.setTargetReached(true);

        FastingSessionEntity missed = activeSession();
        missed.setStatus(FastingSessionStatus.COMPLETED);
        missed.setFastingDate(LocalDate.of(2026, 6, 5));
        missed.setActualMinutes(800);
        missed.setTargetReached(false);

        when(userRepository.findByEmail("user@grun.app")).thenReturn(Optional.of(user));
        when(fastingSessionRepository.findByUserAndFastingDateBetweenOrderByFastingDateAscStartedAtAsc(
                user,
                LocalDate.of(2026, 6, 4),
                LocalDate.of(2026, 6, 6)
        )).thenReturn(List.of(reached, missed));
        when(fastingSessionRepository.existsByUserAndFastingDateAndStatusAndTargetReachedTrue(user, LocalDate.of(2026, 6, 6), FastingSessionStatus.COMPLETED))
                .thenReturn(false);

        FastingRangeSummaryDto result = service.getRangeSummary("user@grun.app", LocalDate.of(2026, 6, 4), LocalDate.of(2026, 6, 6));

        assertEquals(3, result.getDayCount());
        assertEquals(2, result.getSessionCount());
        assertEquals(2, result.getCompletedSessionCount());
        assertEquals(1, result.getTargetReachedSessionCount());
        assertEquals(1770, result.getTotalCompletedMinutes());
        assertEquals(885, result.getAverageCompletedMinutes());
        assertEquals(970, result.getBestSessionMinutes());
        assertEquals(0.5D, result.getTargetSuccessRate());
        assertEquals(3, result.getDailyTrends().size());
        assertEquals(0, result.getDailyTrends().get(2).getSessionCount());
    }

    @Test
    void getRangeSummary_rejectsInvalidRange() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getRangeSummary("user@grun.app", LocalDate.of(2026, 6, 7), LocalDate.of(2026, 6, 1)));
    }

    @Test
    void createDueReminderNotifications_createsOneNotificationAndMarksSession() {
        FastingSessionEntity session = activeSession();
        FastingPlanEntity plan = activePlan();
        plan.setReminderEnabled(true);
        session.setPlan(plan);
        session.setTargetEndAt(LocalDateTime.now().plusMinutes(20));

        when(fastingSessionRepository.findReminderCandidateSessions(any(FastingSessionStatus.class))).thenReturn(List.of(session));

        int created = service.createDueReminderNotifications();

        assertEquals(1, created);
        verify(notificationRepository).save(any(NotificationEntity.class));
        verify(fastingSessionRepository).saveAll(List.of(session));
    }

    private FastingPlanRequestDto planRequest() {
        FastingPlanRequestDto request = new FastingPlanRequestDto();
        request.setPlanType(FastingPlanType.FASTING_16_8);
        request.setFastingHours(16);
        request.setEatingWindowHours(8);
        request.setPreferredStartTime(LocalTime.of(20, 0));
        request.setActive(true);
        request.setReminderEnabled(true);
        return request;
    }

    private FastingPlanEntity activePlan() {
        FastingPlanEntity plan = new FastingPlanEntity();
        plan.setId(10L);
        plan.setUser(user);
        plan.setPlanType(FastingPlanType.FASTING_16_8);
        plan.setFastingHours(16);
        plan.setEatingWindowHours(8);
        plan.setPreferredStartTime(LocalTime.of(20, 0));
        plan.setActive(true);
        plan.setReminderEnabled(false);
        return plan;
    }

    private FastingSessionEntity activeSession() {
        FastingSessionEntity session = new FastingSessionEntity();
        session.setId(20L);
        session.setUser(user);
        session.setPlan(activePlan());
        session.setStatus(FastingSessionStatus.ACTIVE);
        session.setFastingDate(LocalDate.of(2026, 6, 5));
        session.setStartedAt(LocalDateTime.of(2026, 6, 5, 20, 0));
        session.setTargetEndAt(LocalDateTime.of(2026, 6, 6, 12, 0));
        session.setTargetMinutes(960);
        session.setTargetReached(false);
        return session;
    }
}

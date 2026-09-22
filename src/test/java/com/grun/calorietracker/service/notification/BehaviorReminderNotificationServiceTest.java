package com.grun.calorietracker.service.notification;

import com.grun.calorietracker.config.NotificationProducerMigrationProperties;
import com.grun.calorietracker.entity.FastingSessionEntity;
import com.grun.calorietracker.entity.StepGoalEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.WaterReminderSettingsEntity;
import com.grun.calorietracker.enums.NotificationDeliveryChannel;
import com.grun.calorietracker.enums.NotificationEventType;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.service.PushDeliveryService;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BehaviorReminderNotificationServiceTest {

    @Mock
    private NotificationOrchestrationService orchestrationService;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private PushDeliveryService pushDeliveryService;

    private BehaviorReminderNotificationService service;
    private UserEntity user;
    private NotificationProducerMigrationProperties migrationProperties;

    @BeforeEach
    void setUp() {
        migrationProperties = new NotificationProducerMigrationProperties();
        migrationProperties.setWaterEnabled(true);
        migrationProperties.setStepEnabled(true);
        migrationProperties.setBasicFastingEnabled(true);
        service = new BehaviorReminderNotificationService(orchestrationService, new UserTimeZoneSupport(),
                migrationProperties, notificationRepository, pushDeliveryService);
        user = new UserEntity();
        user.setId(42L);
        user.setTimeZone("Europe/Istanbul");
    }

    @Test
    void waterReminderUsesStableDueKeyWindowExpiryAndQuickAddAction() {
        WaterReminderSettingsEntity settings = new WaterReminderSettingsEntity();
        settings.setId(7L);
        settings.setUser(user);
        settings.setIntervalMinutes(120);
        settings.setEndTime(LocalTime.of(21, 0));
        settings.setLastReminderAt(LocalDateTime.of(2026, 8, 31, 10, 5));

        service.enqueueWater(settings, LocalDateTime.of(2026, 8, 31, 12, 10), "Water", "Take a sip");

        NotificationOrchestrationRequest request = captureRequest();
        assertEquals(NotificationEventType.WATER_REMINDER_DUE, request.eventType());
        assertEquals("water:7:due:2026-08-31T12:05", request.sourceEventId());
        assertEquals("QUICK_ADD_WATER", request.primaryAction());
        assertEquals(250, request.actionAmountMl());
        assertEquals(Instant.parse("2026-08-31T09:10:00Z"), request.eligibleAt());
        assertEquals(Instant.parse("2026-08-31T18:00:00Z"), request.expiresAt());
        assertEquals(java.util.Set.of(NotificationDeliveryChannel.IN_APP, NotificationDeliveryChannel.PUSH),
                request.requestedChannels());
    }

    @Test
    void stepReminderUsesGoalAndPreviousReminderForCrossInstanceIdempotency() {
        StepGoalEntity goal = new StepGoalEntity();
        goal.setId(9L);
        goal.setUser(user);
        goal.setReminderIntervalMinutes(90);
        goal.setReminderEndTime(LocalTime.of(20, 0));
        goal.setLastReminderAt(LocalDateTime.of(2026, 8, 31, 15, 0));

        service.enqueueStep(goal, LocalDateTime.of(2026, 8, 31, 16, 35), "Steps", "Short walk?");

        NotificationOrchestrationRequest request = captureRequest();
        assertEquals(NotificationEventType.STEP_REMINDER_DUE, request.eventType());
        assertEquals("step:9:due:2026-08-31T16:30", request.sourceEventId());
        assertEquals("VIEW_STEPS", request.primaryAction());
        assertEquals("steps", request.targetRoute());
    }

    @Test
    void fastingReminderIsUniquePerSessionAndExpiresAtTargetEnd() {
        FastingSessionEntity session = new FastingSessionEntity();
        session.setId(11L);
        session.setUser(user);
        session.setTargetEndAt(LocalDateTime.of(2026, 8, 31, 19, 0));

        service.enqueueFasting(session, LocalDateTime.of(2026, 8, 31, 18, 40), "Nearly there", "Almost done");

        NotificationOrchestrationRequest request = captureRequest();
        assertEquals(NotificationEventType.FASTING_REMINDER_DUE, request.eventType());
        assertEquals("fasting-session:11:nearing-completion", request.sourceEventId());
        assertEquals("11", request.targetId());
        assertEquals(Instant.parse("2026-08-31T16:00:00Z"), request.expiresAt());
    }

    @Test
    void rejectsExpiredFastingReminderBeforeWritingOccurrence() {
        FastingSessionEntity session = new FastingSessionEntity();
        session.setId(11L);
        session.setUser(user);
        session.setTargetEndAt(LocalDateTime.of(2026, 8, 31, 18, 0));

        assertThrows(IllegalArgumentException.class, () -> service.enqueueFasting(
                session, LocalDateTime.of(2026, 8, 31, 18, 1), "Late", "Late"));
    }

    @Test
    void defaultRolloutKeepsLegacyWaterDeliveryUntilSharedDispatcherIsEnabled() {
        migrationProperties.setWaterEnabled(false);
        WaterReminderSettingsEntity settings = new WaterReminderSettingsEntity();
        settings.setId(7L);
        settings.setUser(user);
        when(notificationRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            com.grun.calorietracker.entity.NotificationEntity notification = invocation.getArgument(0);
            notification.setId(55L);
            return notification;
        });

        service.enqueueWater(settings, LocalDateTime.of(2026, 8, 31, 12, 10), "Water", "Take a sip");

        ArgumentCaptor<com.grun.calorietracker.entity.NotificationEntity> notification =
                ArgumentCaptor.forClass(com.grun.calorietracker.entity.NotificationEntity.class);
        verify(notificationRepository).save(notification.capture());
        verify(pushDeliveryService).deliver(notification.getValue());
        verify(orchestrationService, never()).enqueue(org.mockito.ArgumentMatchers.any());
        assertEquals(250, notification.getValue().getActionAmountMl());
        assertEquals("water_reminder", notification.getValue().getType());
        assertEquals(LocalDateTime.of(2026, 8, 31, 9, 10), notification.getValue().getCreatedAt());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "Europe/Dublin,2026-09-22T20:04,2026-09-22T19:04",
        "Europe/Dublin,2026-12-22T20:04,2026-12-22T20:04",
        "Europe/Istanbul,2026-09-22T00:04,2026-09-21T21:04",
        "America/New_York,2026-09-22T20:04,2026-09-23T00:04"
    })
    void legacyStepTimestampRoundTripsToOriginalLocalTime(String zone, String local, String utc) {
        migrationProperties.setStepEnabled(false);
        user.setTimeZone(zone);
        StepGoalEntity goal = new StepGoalEntity();
        goal.setId(9L);
        goal.setUser(user);
        when(notificationRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));
        service.enqueueStep(goal, LocalDateTime.parse(local), "Steps", "Short walk?");
        var captor = ArgumentCaptor.forClass(com.grun.calorietracker.entity.NotificationEntity.class);
        verify(notificationRepository).save(captor.capture());
        var saved = captor.getValue();
        assertEquals(LocalDateTime.parse(utc), saved.getCreatedAt());
        assertEquals(LocalDateTime.parse(local), saved.getCreatedAt().toInstant(java.time.ZoneOffset.UTC)
                .atZone(java.time.ZoneId.of(zone)).toLocalDateTime());
        verify(pushDeliveryService).deliver(saved);
        verify(orchestrationService, never()).enqueue(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void legacyFastingStoresUtcWithoutChangingTargetEnd() {
        migrationProperties.setBasicFastingEnabled(false);
        var session = new FastingSessionEntity();
        session.setId(11L);
        session.setUser(user);
        var end = LocalDateTime.of(2026, 9, 22, 21, 0);
        session.setTargetEndAt(end);
        when(notificationRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));
        service.enqueueFasting(session, end.minusMinutes(20), "Nearly there", "Almost done");
        var captor = ArgumentCaptor.forClass(com.grun.calorietracker.entity.NotificationEntity.class);
        verify(notificationRepository).save(captor.capture());
        assertEquals(LocalDateTime.of(2026, 9, 22, 17, 40), captor.getValue().getCreatedAt());
        assertEquals(end, session.getTargetEndAt());
    }

    @Test
    void stepCandidateUsesLegacyPreferencesBeforeCutoverAndCentralPolicyAfterCutover() {
        migrationProperties.setStepEnabled(false);
        user.setPushNotificationsEnabled(false);
        user.setStepRemindersEnabled(false);
        assertFalse(service.shouldEvaluateStep(user));

        user.setPushNotificationsEnabled(true);
        user.setStepRemindersEnabled(true);
        assertTrue(service.shouldEvaluateStep(user));

        migrationProperties.setStepEnabled(true);
        user.setPushNotificationsEnabled(false);
        user.setStepRemindersEnabled(false);
        assertTrue(service.shouldEvaluateStep(user));
    }

    private NotificationOrchestrationRequest captureRequest() {
        ArgumentCaptor<NotificationOrchestrationRequest> captor =
                ArgumentCaptor.forClass(NotificationOrchestrationRequest.class);
        verify(orchestrationService).enqueue(captor.capture());
        return captor.getValue();
    }
}

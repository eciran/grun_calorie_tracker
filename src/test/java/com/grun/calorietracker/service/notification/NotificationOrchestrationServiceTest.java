package com.grun.calorietracker.service.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.NotificationDeliveryProperties;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.NotificationDefinitionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationOrchestrationServiceTest {
    @Mock UserRepository userRepository;
    @Mock NotificationDefinitionRepository definitionRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock NotificationOccurrenceRepository occurrenceRepository;
    @Mock NotificationOutboxRepository outboxRepository;
    @Mock NotificationPolicyEvaluator policyEvaluator;
    @Mock NotificationDefinitionPolicy definitionPolicy;

    private NotificationOrchestrationService service;
    private UserEntity user;
    private NotificationDefinitionEntity definition;
    private final Instant now = Instant.parse("2026-08-31T10:00:00Z");

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(42L);
        definition = new NotificationDefinitionEntity();
        definition.setId(7L);
        definition.setVersion(3L);
        definition.setKey("subscription_started");
        definition.setEnabled(true);
        definition.setChannel(NotificationCampaignChannel.IN_APP_AND_PUSH);

        lenient().when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
        lenient().when(occurrenceRepository.findBySourceAndSourceEventIdAndDefinitionKeyAndUserId(anyString(), anyString(), anyString(), eq(42L)))
                .thenReturn(Optional.empty());
        lenient().when(notificationRepository.save(any())).thenAnswer(invocation -> {
            NotificationEntity value = invocation.getArgument(0); value.setId(100L); return value;
        });
        lenient().when(occurrenceRepository.save(any())).thenAnswer(invocation -> {
            NotificationOccurrenceEntity value = invocation.getArgument(0); value.setId(200L); return value;
        });
        lenient().when(outboxRepository.save(any())).thenAnswer(invocation -> {
            NotificationOutboxEntity value = invocation.getArgument(0); value.setId(300L); return value;
        });
        lenient().when(definitionPolicy.presentation(any(), any(), anyMap())).thenAnswer(invocation -> {
            NotificationEntity notification = invocation.getArgument(0);
            return new NotificationDefinitionPolicy.NotificationPresentation(notification.getTitle(),
                    notification.getMessage(), notification.getSeverity(), notification.getTargetRoute());
        });
        NotificationDeliveryProperties delivery = new NotificationDeliveryProperties();
        delivery.setEnabled(true);
        delivery.setStage(NotificationReleaseStage.LIVE);
        delivery.setLivePercentage(100);
        service = new NotificationOrchestrationService(userRepository, definitionRepository, notificationRepository,
                occurrenceRepository, outboxRepository, policyEvaluator, new NotificationReleaseGate(delivery),
                definitionPolicy, delivery, new ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void createsTypedInboxOccurrenceAndPushOutboxAtomically() {
        when(definitionRepository.findByKey("subscription_started")).thenReturn(Optional.of(definition));
        when(policyEvaluator.evaluate(eq(user), eq(NotificationEventType.SUBSCRIPTION_STARTED), anySet(), eq(now)))
                .thenReturn(new NotificationPolicyDecision(
                        Set.of(NotificationDeliveryChannel.IN_APP, NotificationDeliveryChannel.PUSH), now, "ELIGIBLE"));

        NotificationOrchestrationResult result = service.enqueue(request(Map.of("planName", "PRO", "periodEndDate", "30 Sep 2026")));

        assertEquals(200L, result.occurrenceId());
        assertEquals(100L, result.notificationId());
        assertEquals(300L, result.outboxId());
        assertEquals(NotificationOccurrenceStatus.QUEUED, result.status());
        assertFalse(result.duplicate());

        ArgumentCaptor<NotificationOccurrenceEntity> occurrence = ArgumentCaptor.forClass(NotificationOccurrenceEntity.class);
        verify(occurrenceRepository, atLeastOnce()).save(occurrence.capture());
        assertEquals("REVENUECAT", occurrence.getValue().getSource());
        assertEquals(NotificationClassification.TRANSACTIONAL_ACCOUNT, occurrence.getValue().getClassification());
        assertTrue(occurrence.getValue().getParametersJson().contains("planName"));

        ArgumentCaptor<NotificationOutboxEntity> outbox = ArgumentCaptor.forClass(NotificationOutboxEntity.class);
        verify(outboxRepository).save(outbox.capture());
        assertEquals(NotificationDeliveryChannel.PUSH, outbox.getValue().getChannel());
        assertEquals(NotificationOutboxStatus.PENDING, outbox.getValue().getStatus());
    }

    @Test
    void repeatedProviderEventReturnsExistingOccurrenceWithoutCreatingAnotherNotification() {
        NotificationOccurrenceEntity existing = new NotificationOccurrenceEntity();
        existing.setId(9L);
        existing.setStatus(NotificationOccurrenceStatus.COMPLETED);
        when(occurrenceRepository.findBySourceAndSourceEventIdAndDefinitionKeyAndUserId(
                "REVENUECAT", "evt-1", "subscription_started", 42L)).thenReturn(Optional.of(existing));
        when(outboxRepository.findByOccurrenceId(9L)).thenReturn(Optional.empty());

        NotificationOrchestrationResult result = service.enqueue(request(Map.of("planName", "PRO")));

        assertTrue(result.duplicate());
        assertEquals(9L, result.occurrenceId());
        verifyNoInteractions(definitionRepository, policyEvaluator);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void missingManagedDefinitionRecordsSuppressionInsteadOfFallingBackToPush() {
        when(definitionRepository.findByKey("subscription_started")).thenReturn(Optional.empty());

        NotificationOrchestrationResult result = service.enqueue(request(Map.of("planName", "PRO")));

        assertEquals(NotificationOccurrenceStatus.SUPPRESSED, result.status());
        assertEquals("DEFINITION_MISSING", result.reasonCode());
        verifyNoInteractions(policyEvaluator);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void rejectsUnknownTemplateParameterBeforeAnyDatabaseWrite() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.enqueue(request(Map.of("unsafeField", "value"))));

        assertTrue(exception.getMessage().contains("unsupported template parameter"));
        verifyNoInteractions(userRepository, definitionRepository, notificationRepository);
    }

    @Test
    void rejectsMissingRequiredTemplateParameterBeforeAnyDatabaseWrite() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.enqueue(request(Map.of("periodEndDate", "30 Sep 2026"))));

        assertTrue(exception.getMessage().contains("missing a required template parameter"));
        verifyNoInteractions(userRepository, definitionRepository, notificationRepository);
    }

    @Test
    void preservesTypedWaterQuickAddAmountOnInboxNotification() {
        definition.setKey("water_reminder");
        when(definitionRepository.findByKey("water_reminder")).thenReturn(Optional.of(definition));
        when(policyEvaluator.evaluate(eq(user), eq(NotificationEventType.WATER_REMINDER_DUE), anySet(), eq(now)))
                .thenReturn(new NotificationPolicyDecision(
                        Set.of(NotificationDeliveryChannel.IN_APP, NotificationDeliveryChannel.PUSH), now, "ELIGIBLE"));

        service.enqueue(new NotificationOrchestrationRequest(
                user, NotificationEventType.WATER_REMINDER_DUE, "WATER_REMINDER", "water:7:initial:2026-08-31",
                "Hydration check-in", "Take a sip", "INFO", "WATER_TRACKING", "7", "water",
                "QUICK_ADD_WATER", Map.of(), Set.of(NotificationDeliveryChannel.IN_APP, NotificationDeliveryChannel.PUSH),
                now, now.plusSeconds(3600), 250));

        ArgumentCaptor<NotificationEntity> notification = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(notification.capture());
        assertEquals(250, notification.getValue().getActionAmountMl());
        assertEquals("QUICK_ADD_WATER", notification.getValue().getPrimaryAction());
    }

    @Test
    void outsideReleaseCohortKeepsInboxButDoesNotCreateStalePushOutbox() {
        NotificationDeliveryProperties delivery = new NotificationDeliveryProperties();
        delivery.setEnabled(true);
        delivery.setStage(NotificationReleaseStage.TEST_ACCOUNTS);
        NotificationOrchestrationService cohortService = new NotificationOrchestrationService(
                userRepository, definitionRepository, notificationRepository, occurrenceRepository, outboxRepository,
                policyEvaluator, new NotificationReleaseGate(delivery), definitionPolicy, delivery,
                new ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC));
        when(definitionRepository.findByKey("subscription_started")).thenReturn(Optional.of(definition));
        when(policyEvaluator.evaluate(eq(user), eq(NotificationEventType.SUBSCRIPTION_STARTED), anySet(), eq(now)))
                .thenReturn(new NotificationPolicyDecision(
                        Set.of(NotificationDeliveryChannel.IN_APP, NotificationDeliveryChannel.PUSH), now, "ELIGIBLE"));

        NotificationOrchestrationResult result = cohortService.enqueue(
                request(Map.of("planName", "PRO", "periodEndDate", "30 Sep 2026")));

        assertEquals(NotificationOccurrenceStatus.COMPLETED, result.status());
        assertEquals("OUTSIDE_TEST_COHORT", result.reasonCode());
        assertNull(result.outboxId());
        verify(notificationRepository).save(any());
        verify(outboxRepository, never()).save(any());
    }

    private NotificationOrchestrationRequest request(Map<String, String> parameters) {
        return new NotificationOrchestrationRequest(user, NotificationEventType.SUBSCRIPTION_STARTED,
                " revenuecat ", "evt-1", "Fallback title", "Fallback message", "INFO",
                "SUBSCRIPTION", "55", "manage-subscription", "MANAGE_SUBSCRIPTION", parameters,
                Set.of(NotificationDeliveryChannel.IN_APP, NotificationDeliveryChannel.PUSH), now, now.plusSeconds(3600));
    }
}

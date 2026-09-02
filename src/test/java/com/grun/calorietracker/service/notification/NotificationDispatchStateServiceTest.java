package com.grun.calorietracker.service.notification;

import com.grun.calorietracker.config.NotificationDeliveryProperties;
import com.grun.calorietracker.config.PushProperties;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.push.PushProviderSendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchStateServiceTest {
    @Mock NotificationOutboxRepository outboxRepository;
    @Mock NotificationDeliveryAttemptRepository attemptRepository;
    @Mock NotificationDefinitionRepository definitionRepository;
    @Mock UserPushTokenRepository tokenRepository;
    @Mock NotificationPolicyEvaluator policyEvaluator;

    private NotificationDispatchStateService service;
    private NotificationDeliveryAttemptEntity attempt;
    private NotificationOutboxEntity outbox;
    private final Instant now = Instant.parse("2026-08-31T10:00:00Z");

    @BeforeEach
    void setUp() {
        NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
        properties.setEnabled(true);
        properties.setStage(NotificationReleaseStage.LIVE);
        properties.setLivePercentage(100);
        properties.setBaseRetryDelay(Duration.ofMinutes(2));
        properties.setMaxAttempts(3);
        PushProperties pushProperties = new PushProperties();
        pushProperties.setEnabled(true);
        service = new NotificationDispatchStateService(outboxRepository, attemptRepository, definitionRepository,
                tokenRepository, policyEvaluator, new NotificationReleaseGate(properties), properties, pushProperties);

        NotificationEntity notification = new NotificationEntity();
        notification.setVisibleInApp(true);
        NotificationOccurrenceEntity occurrence = new NotificationOccurrenceEntity();
        occurrence.setStatus(NotificationOccurrenceStatus.PROCESSING);
        UserEntity user = new UserEntity();
        user.setId(42L);
        occurrence.setUser(user);
        outbox = new NotificationOutboxEntity();
        outbox.setId(20L);
        outbox.setNotification(notification);
        outbox.setOccurrence(occurrence);
        outbox.setStatus(NotificationOutboxStatus.PROCESSING);
        outbox.setExpiresAt(now.plusSeconds(3600));
        attempt = new NotificationDeliveryAttemptEntity();
        attempt.setId(30L);
        attempt.setOutbox(outbox);
        attempt.setStatus(NotificationDeliveryAttemptStatus.PROCESSING);
        attempt.setAttemptCount(1);
    }

    @Test
    void uncertainProviderOutcomeIsNeverBlindlyRetried() {
        when(attemptRepository.findById(30L)).thenReturn(Optional.of(attempt));
        when(attemptRepository.findByOutboxIdOrderById(20L)).thenReturn(List.of(attempt));

        service.recordProviderResult(30L, PushProviderSendResult.uncertain("timeout after write"), now);

        assertEquals(NotificationDeliveryAttemptStatus.UNKNOWN, attempt.getStatus());
        assertNull(attempt.getNextAttemptAt());
        assertEquals(NotificationOutboxStatus.UNKNOWN, outbox.getStatus());
        assertEquals(NotificationOccurrenceStatus.UNKNOWN, outbox.getOccurrence().getStatus());
        verify(outboxRepository).save(outbox);
    }

    @Test
    void definiteFailureUsesBoundedBackoffWithinTtl() {
        when(attemptRepository.findById(30L)).thenReturn(Optional.of(attempt));
        when(attemptRepository.findByOutboxIdOrderById(20L)).thenReturn(List.of(attempt));

        service.recordProviderResult(30L, PushProviderSendResult.failed("temporary provider error"), now);

        assertEquals(NotificationDeliveryAttemptStatus.FAILED_RETRYABLE, attempt.getStatus());
        assertEquals(now.plus(Duration.ofMinutes(2)), attempt.getNextAttemptAt());
        assertEquals(NotificationOutboxStatus.RETRY, outbox.getStatus());
        assertEquals(attempt.getNextAttemptAt(), outbox.getAvailableAt());
    }

    @Test
    void exhaustedFailureCompletesInboxButDoesNotClaimDelivery() {
        attempt.setAttemptCount(3);
        when(attemptRepository.findById(30L)).thenReturn(Optional.of(attempt));
        when(attemptRepository.findByOutboxIdOrderById(20L)).thenReturn(List.of(attempt));

        service.recordProviderResult(30L, PushProviderSendResult.failed("provider rejected"), now);

        assertEquals(NotificationDeliveryAttemptStatus.FAILED_FINAL, attempt.getStatus());
        assertEquals(NotificationOutboxStatus.FAILED_FINAL, outbox.getStatus());
        assertEquals(NotificationOccurrenceStatus.COMPLETED, outbox.getOccurrence().getStatus());
        assertEquals("ALL_ATTEMPTS_FAILED", outbox.getLastErrorCode());
    }

    @Test
    void prepareCancelsOutboxOutsideNamedTestCohortBeforeTokenOrProviderWork() {
        NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
        properties.setEnabled(true);
        properties.setStage(NotificationReleaseStage.TEST_ACCOUNTS);
        properties.setTestUserIds(Set.of(7L));
        PushProperties pushProperties = new PushProperties();
        pushProperties.setEnabled(true);
        NotificationDispatchStateService cohortService = new NotificationDispatchStateService(
                outboxRepository, attemptRepository, definitionRepository, tokenRepository, policyEvaluator,
                new NotificationReleaseGate(properties), properties, pushProperties);
        outbox.setLeaseOwner("worker");
        when(outboxRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(outbox));

        assertNull(cohortService.prepare(20L, "worker", now));

        assertEquals(NotificationOutboxStatus.CANCELLED, outbox.getStatus());
        assertEquals("OUTSIDE_TEST_COHORT", outbox.getLastErrorCode());
        verifyNoInteractions(tokenRepository);
    }
}

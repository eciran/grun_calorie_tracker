package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.*;
import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminSubscriptionNotificationServiceImplTest {
    @Mock NotificationPlatformPolicyRepository policyRepository;
    @Mock NotificationDefinitionRepository definitionRepository;
    @Mock NotificationOccurrenceRepository occurrenceRepository;
    @Mock NotificationOutboxRepository outboxRepository;
    @Mock NotificationDeliveryAttemptRepository attemptRepository;
    @Mock NotificationEngagementRepository engagementRepository;
    @Mock NotificationDefinitionPolicy definitionPolicy;
    @Mock AdminNotificationDefinitionService definitionService;
    @Mock AdminAuditService auditService;
    NotificationDeliveryProperties delivery = new NotificationDeliveryProperties();
    NotificationProducerMigrationProperties producerMigration = new NotificationProducerMigrationProperties();
    PushProperties push = new PushProperties();
    AdminSubscriptionNotificationServiceImpl service;
    NotificationPlatformPolicyEntity policy;

    @BeforeEach void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new AdminSubscriptionNotificationServiceImpl(policyRepository, definitionRepository,
                occurrenceRepository, outboxRepository, attemptRepository, engagementRepository,
                definitionPolicy, definitionService, auditService, delivery, producerMigration,
                new com.grun.calorietracker.service.notification.NotificationReleaseGate(delivery), push);
        policy = new NotificationPlatformPolicyEntity(); policy.setId(1L); policy.setVersion(3L);
        policy.setRequestedDeliveryEnabled(true); policy.setEmergencyStopped(false);
        policy.setUpdatedBy("owner@grun.app"); policy.setUpdatedAt(Instant.now());
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policyRepository.findWithLockById(1L)).thenReturn(Optional.of(policy));
        when(policyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test void adminApprovalCannotOverrideDeploymentKillSwitch() {
        delivery.setEnabled(false); push.setEnabled(true);
        assertThat(service.getPolicy().effectiveDeliveryEnabled()).isFalse();
        assertThat(service.getPolicy().effectiveReason()).isEqualTo("DEPLOYMENT_GATE_OFF");
        assertThat(service.deliveryAllowed()).isFalse();
    }

    @Test void policyExposesDeploymentOwnedStageCohortsAndProducerState() {
        delivery.setEnabled(true);
        delivery.setStage(NotificationReleaseStage.TEST_ACCOUNTS);
        delivery.setTestUserIds(Set.of(42L));
        delivery.setPilotUserIds(Set.of(84L));
        delivery.setLivePercentage(5);
        producerMigration.setWaterEnabled(true);
        push.setEnabled(true);

        var result = service.getPolicy();

        assertThat(result.effectiveDeliveryEnabled()).isTrue();
        assertThat(result.releaseStage()).isEqualTo("TEST_ACCOUNTS");
        assertThat(result.testAccountCount()).isEqualTo(1);
        assertThat(result.pilotAccountCount()).isEqualTo(1);
        assertThat(result.livePercentage()).isEqualTo(5);
        assertThat(result.waterProducerMigrated()).isTrue();
        assertThat(result.stepProducerMigrated()).isFalse();
    }

    @Test void emergencyStopIsImmediateAndAudited() {
        var result = service.emergencyStop("Provider anomaly", "tech@grun.app", "cid");
        assertThat(result.emergencyStopped()).isTrue();
        assertThat(result.effectiveReason()).isEqualTo("EMERGENCY_STOPPED");
        verify(auditService).record(eq("tech@grun.app"),
                eq(AdminAuditActionType.SUBSCRIPTION_NOTIFICATION_EMERGENCY_STOP), any(), any(), any(), any(), eq("cid"));
    }

    @Test void approvedPolicyUsesOptimisticVersionAndClearsStop() {
        service.publishApproved(new AdminSubscriptionNotificationPolicyRequestDto(3L, true, "Approved pilot"),
                "owner@grun.app", "cid");
        assertThat(policy.isEmergencyStopped()).isFalse();
        assertThat(policy.isRequestedDeliveryEnabled()).isTrue();
        assertThatThrownBy(() -> service.publishApproved(
                new AdminSubscriptionNotificationPolicyRequestDto(2L, true, "stale"), "owner", "cid"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("changed");
    }

    @Test void previewRejectsUntypedParametersBeforeRendering() {
        assertThatThrownBy(() -> service.preview(new AdminSubscriptionNotificationPreviewRequestDto(
                "subscription_renewed", PreferredLanguage.TR, Map.of("planName", "Pro", "secret", "tx"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("typed");
        verifyNoInteractions(definitionPolicy);
    }
}

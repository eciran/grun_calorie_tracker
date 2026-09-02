package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.NotificationDeliveryProperties;
import com.grun.calorietracker.config.NotificationProducerMigrationProperties;
import com.grun.calorietracker.config.PushProperties;
import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminSubscriptionNotificationServiceImpl implements AdminSubscriptionNotificationService {
    private static final long POLICY_ID = 1L;
    private final NotificationPlatformPolicyRepository policyRepository;
    private final NotificationDefinitionRepository definitionRepository;
    private final NotificationOccurrenceRepository occurrenceRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationDeliveryAttemptRepository attemptRepository;
    private final NotificationEngagementRepository engagementRepository;
    private final NotificationDefinitionPolicy definitionPolicy;
    private final AdminNotificationDefinitionService definitionService;
    private final AdminAuditService auditService;
    private final NotificationDeliveryProperties deliveryProperties;
    private final NotificationProducerMigrationProperties producerMigrationProperties;
    private final com.grun.calorietracker.service.notification.NotificationReleaseGate releaseGate;
    private final PushProperties pushProperties;

    @Override @Transactional(readOnly = true)
    public AdminSubscriptionNotificationPolicyDto getPolicy() { return toDto(requiredPolicy()); }

    @Override @Transactional
    public AdminSubscriptionNotificationPolicyDto publishApproved(AdminSubscriptionNotificationPolicyRequestDto request,
            String checker, String correlationId) {
        NotificationPlatformPolicyEntity policy = policyRepository.findWithLockById(POLICY_ID)
                .orElseThrow(() -> new IllegalStateException("Notification platform policy is not initialized."));
        if (!Objects.equals(policy.getVersion(), request.version()))
            throw new IllegalArgumentException("Notification policy changed. Refresh and submit a new approval request.");
        Map<String, Object> before = auditValue(policy);
        policy.setRequestedDeliveryEnabled(request.requestedDeliveryEnabled());
        policy.setEmergencyStopped(false);
        policy.setStopReason(request.reason().trim());
        policy.setUpdatedBy(checker);
        policy.setUpdatedAt(Instant.now());
        policy = policyRepository.save(policy);
        auditService.record(checker, AdminAuditActionType.SUBSCRIPTION_NOTIFICATION_POLICY_PUBLISH,
                AdminAuditTargetType.SUBSCRIPTION_NOTIFICATION_PLATFORM, String.valueOf(POLICY_ID), before,
                auditValue(policy), correlationId);
        return toDto(policy);
    }

    @Override @Transactional
    public AdminSubscriptionNotificationPolicyDto emergencyStop(String reason, String admin, String correlationId) {
        NotificationPlatformPolicyEntity policy = policyRepository.findWithLockById(POLICY_ID)
                .orElseThrow(() -> new IllegalStateException("Notification platform policy is not initialized."));
        Map<String, Object> before = auditValue(policy);
        policy.setEmergencyStopped(true);
        policy.setStopReason(reason.trim());
        policy.setUpdatedBy(admin);
        policy.setUpdatedAt(Instant.now());
        policy = policyRepository.save(policy);
        auditService.record(admin, AdminAuditActionType.SUBSCRIPTION_NOTIFICATION_EMERGENCY_STOP,
                AdminAuditTargetType.SUBSCRIPTION_NOTIFICATION_PLATFORM, String.valueOf(POLICY_ID), before,
                auditValue(policy), correlationId);
        return toDto(policy);
    }

    @Override @Transactional(readOnly = true)
    public AdminSubscriptionNotificationPreviewDto preview(AdminSubscriptionNotificationPreviewRequestDto request) {
        NotificationEventType eventType = eventType(request.definitionKey());
        if (!eventType.allowedParameters().containsAll(request.parameters().keySet())
                || !request.parameters().keySet().containsAll(eventType.requiredParameters()))
            throw new IllegalArgumentException("Preview parameters do not match the typed notification contract.");
        NotificationDefinitionEntity definition = definitionRepository.findByKey(request.definitionKey())
                .orElseThrow(() -> new IllegalArgumentException("Subscription notification definition was not found."));
        UserEntity previewUser = new UserEntity();
        previewUser.setPreferredLanguage(request.language());
        NotificationEntity notification = new NotificationEntity();
        notification.setUser(previewUser); notification.setType(request.definitionKey());
        notification.setTitle(definition.getDisplayName()); notification.setMessage(definition.getDescription());
        notification.setSeverity(definition.getSeverity()); notification.setTargetRoute(definition.getTargetRoute());
        var presentation = definitionPolicy.presentation(notification, definition, request.parameters());
        return new AdminSubscriptionNotificationPreviewDto(definition.getKey(), request.language(),
                presentation.title(), presentation.message(), presentation.severity(), presentation.targetRoute(),
                definition.getChannel(), definition.isEnabled());
    }

    @Override @Transactional(readOnly = true)
    public AdminSubscriptionNotificationLedgerPageDto ledger(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<NotificationOccurrenceEntity> result = occurrenceRepository.findByClassification(
                NotificationClassification.TRANSACTIONAL_ACCOUNT, pageable);
        List<AdminSubscriptionNotificationLedgerItemDto> content = result.getContent().stream().map(occurrence -> {
            NotificationOutboxEntity outbox = outboxRepository.findByOccurrenceId(occurrence.getId()).orElse(null);
            return new AdminSubscriptionNotificationLedgerItemDto(occurrence.getId(), occurrence.getUser().getId(),
                    occurrence.getEventType(), occurrence.getDefinitionKey(), occurrence.getSource(), occurrence.getStatus(),
                    occurrence.getReasonCode(), occurrence.getNotification() == null ? null : occurrence.getNotification().getId(),
                    outbox == null ? null : outbox.getId(), outbox == null ? null : outbox.getStatus(),
                    outbox == null ? 0 : outbox.getDispatchCount(), outbox == null ? null : outbox.getLastErrorCode(),
                    occurrence.getCreatedAt(), occurrence.getUpdatedAt());
        }).toList();
        return new AdminSubscriptionNotificationLedgerPageDto(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
    }

    @Override @Transactional(readOnly = true)
    public AdminSubscriptionNotificationMetricsDto metrics() {
        List<String> lifecycleTypes = Arrays.stream(NotificationEventType.values())
                .filter(type -> type.classification() == NotificationClassification.TRANSACTIONAL_ACCOUNT)
                .map(NotificationEventType::definitionKey).toList();
        return new AdminSubscriptionNotificationMetricsDto(
                occurrenceRepository.countByClassification(NotificationClassification.TRANSACTIONAL_ACCOUNT),
                counts(occurrenceRepository.countStatuses(NotificationClassification.TRANSACTIONAL_ACCOUNT)),
                counts(outboxRepository.countStatuses(NotificationClassification.TRANSACTIONAL_ACCOUNT)),
                counts(attemptRepository.countStatuses(NotificationClassification.TRANSACTIONAL_ACCOUNT)),
                engagementRepository.countByEngagementTypeAndNotification_TypeIn(NotificationEngagementType.OPENED, lifecycleTypes),
                engagementRepository.countByEngagementTypeAndNotification_TypeIn(NotificationEngagementType.CLICKED, lifecycleTypes),
                engagementRepository.countByEngagementTypeAndNotification_TypeIn(NotificationEngagementType.DISMISSED, lifecycleTypes));
    }

    @Override
    public void publishDefinitionApproved(Long definitionId, AdminNotificationDefinitionRequestDto request,
            String checker, String correlationId) {
        definitionService.publishProtected(definitionId, request, checker, correlationId);
    }

    @Override @Transactional(readOnly = true)
    public boolean deliveryAllowed() {
        return deliveryProperties.isEnabled() && releaseGate.anyAudienceEnabled() && pushProperties.isEnabled()
                && policyRepository.findById(POLICY_ID)
                .map(policy -> policy.isRequestedDeliveryEnabled() && !policy.isEmergencyStopped()).orElse(false);
    }

    private AdminSubscriptionNotificationPolicyDto toDto(NotificationPlatformPolicyEntity policy) {
        boolean effective = deliveryProperties.isEnabled() && releaseGate.anyAudienceEnabled() && pushProperties.isEnabled()
                && policy.isRequestedDeliveryEnabled() && !policy.isEmergencyStopped();
        String reason = policy.isEmergencyStopped() ? "EMERGENCY_STOPPED"
                : !policy.isRequestedDeliveryEnabled() ? "ADMIN_POLICY_OFF"
                : !deliveryProperties.isEnabled() ? "DEPLOYMENT_GATE_OFF"
                : !releaseGate.anyAudienceEnabled() ? releaseGate.audienceReason()
                : !pushProperties.isEnabled() ? "PUSH_PROVIDER_OFF" : "DELIVERY_ENABLED";
        return new AdminSubscriptionNotificationPolicyDto(policy.getVersion(), policy.isRequestedDeliveryEnabled(),
                policy.isEmergencyStopped(), policy.getStopReason(), deliveryProperties.isEnabled(),
                String.valueOf(deliveryProperties.getStage()), releaseGate.testAccountCount(),
                releaseGate.pilotAccountCount(), Math.max(0, Math.min(100, deliveryProperties.getLivePercentage())),
                producerMigrationProperties.isWaterEnabled(), producerMigrationProperties.isStepEnabled(),
                producerMigrationProperties.isBasicFastingEnabled(), pushProperties.isEnabled(), effective, reason,
                policy.getUpdatedBy(), policy.getUpdatedAt());
    }

    private NotificationPlatformPolicyEntity requiredPolicy() {
        return policyRepository.findById(POLICY_ID)
                .orElseThrow(() -> new IllegalStateException("Notification platform policy is not initialized."));
    }
    private NotificationEventType eventType(String key) {
        return Arrays.stream(NotificationEventType.values()).filter(type -> type.definitionKey().equals(key))
                .filter(type -> type.classification() == NotificationClassification.TRANSACTIONAL_ACCOUNT)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unsupported subscription notification type."));
    }
    private Map<String, Long> counts(List<Object[]> rows) {
        Map<String, Long> result = new LinkedHashMap<>();
        rows.forEach(row -> result.put(String.valueOf(row[0]), ((Number) row[1]).longValue()));
        return result;
    }
    private Map<String, Object> auditValue(NotificationPlatformPolicyEntity policy) {
        return Map.of("requestedDeliveryEnabled", policy.isRequestedDeliveryEnabled(),
                "emergencyStopped", policy.isEmergencyStopped(),
                "stopReason", policy.getStopReason() == null ? "" : policy.getStopReason());
    }
}

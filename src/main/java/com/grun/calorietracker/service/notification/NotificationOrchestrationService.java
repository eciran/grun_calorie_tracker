package com.grun.calorietracker.service.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.NotificationDeliveryProperties;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.NotificationDefinitionPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationOrchestrationService {
    private final UserRepository userRepository;
    private final NotificationDefinitionRepository definitionRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationOccurrenceRepository occurrenceRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationPolicyEvaluator policyEvaluator;
    private final NotificationReleaseGate releaseGate;
    private final NotificationDefinitionPolicy definitionPolicy;
    private final NotificationDeliveryProperties deliveryProperties;
    private final ObjectMapper objectMapper;
    private final Clock analyticsClock;

    @Transactional
    public NotificationOrchestrationResult enqueue(NotificationOrchestrationRequest request) {
        validate(request);
        Instant now = analyticsClock.instant();
        UserEntity user = userRepository.findByIdForUpdate(request.user().getId())
                .orElseThrow(() -> new IllegalArgumentException("Notification user was not found."));
        String definitionKey = request.eventType().definitionKey();
        String source = request.source().trim().toUpperCase(java.util.Locale.ROOT);
        String sourceEventId = request.sourceEventId().trim();
        var existing = occurrenceRepository.findBySourceAndSourceEventIdAndDefinitionKeyAndUserId(
                source, sourceEventId, definitionKey, user.getId());
        if (existing.isPresent()) return existingResult(existing.get());

        NotificationDefinitionEntity definition = definitionRepository.findByKey(definitionKey).orElse(null);
        Instant eligibleAt = request.eligibleAt() == null ? now : request.eligibleAt();
        Instant expiresAt = request.expiresAt() == null
                ? eligibleAt.plus(deliveryProperties.getDefaultTtl()) : request.expiresAt();
        if (!expiresAt.isAfter(eligibleAt)) {
            throw new IllegalArgumentException("Notification expiresAt must be after eligibleAt.");
        }

        NotificationOccurrenceEntity occurrence = baseOccurrence(request, user, definition, eligibleAt, expiresAt, now);
        if (definition == null || !definition.isEnabled()) {
            occurrence.setStatus(NotificationOccurrenceStatus.SUPPRESSED);
            occurrence.setReasonCode(definition == null ? "DEFINITION_MISSING" : "DEFINITION_DISABLED");
            occurrenceRepository.save(occurrence);
            return result(occurrence, null, null, false);
        }

        Set<NotificationDeliveryChannel> requested = applyDefinitionChannel(
                request.requestedChannels() == null || request.requestedChannels().isEmpty()
                        ? request.eventType().defaultChannels() : request.requestedChannels(), definition.getChannel());
        NotificationPolicyDecision decision = applyReleaseGate(
                policyEvaluator.evaluate(user, request.eventType(), requested, now), user.getId());
        occurrence.setReasonCode(decision.reasonCode());
        if (decision.suppressed()) {
            occurrence.setStatus(NotificationOccurrenceStatus.SUPPRESSED);
            occurrenceRepository.save(occurrence);
            return result(occurrence, null, null, false);
        }

        NotificationEntity notification = notification(request, user, definition, decision, now);
        notification = notificationRepository.save(notification);
        occurrence.setNotification(notification);

        NotificationOutboxEntity outbox = null;
        if (decision.allows(NotificationDeliveryChannel.PUSH)) {
            occurrence.setStatus(NotificationOccurrenceStatus.QUEUED);
            occurrence = occurrenceRepository.save(occurrence);
            outbox = new NotificationOutboxEntity();
            outbox.setOccurrence(occurrence);
            outbox.setNotification(notification);
            outbox.setChannel(NotificationDeliveryChannel.PUSH);
            outbox.setStatus(NotificationOutboxStatus.PENDING);
            outbox.setAvailableAt(max(eligibleAt, decision.pushAvailableAt()));
            outbox.setExpiresAt(expiresAt);
            outbox.setCreatedAt(now);
            outbox.setUpdatedAt(now);
            outbox = outboxRepository.save(outbox);
        } else {
            occurrence.setStatus(NotificationOccurrenceStatus.COMPLETED);
            occurrence = occurrenceRepository.save(occurrence);
        }
        return result(occurrence, notification, outbox, false);
    }

    private NotificationOccurrenceEntity baseOccurrence(NotificationOrchestrationRequest request, UserEntity user,
            NotificationDefinitionEntity definition, Instant eligibleAt, Instant expiresAt, Instant now) {
        NotificationOccurrenceEntity occurrence = new NotificationOccurrenceEntity();
        occurrence.setUser(user);
        occurrence.setEventType(request.eventType());
        occurrence.setClassification(request.eventType().classification());
        occurrence.setDefinitionKey(request.eventType().definitionKey());
        occurrence.setDefinitionVersion(definition == null ? null : definition.getVersion());
        occurrence.setSource(request.source().trim().toUpperCase(java.util.Locale.ROOT));
        occurrence.setSourceEventId(request.sourceEventId().trim());
        occurrence.setStatus(NotificationOccurrenceStatus.PENDING);
        occurrence.setParametersJson(writeParameters(request.parameters()));
        occurrence.setEligibleAt(eligibleAt);
        occurrence.setExpiresAt(expiresAt);
        occurrence.setCreatedAt(now);
        occurrence.setUpdatedAt(now);
        return occurrence;
    }

    private NotificationEntity notification(NotificationOrchestrationRequest request, UserEntity user,
            NotificationDefinitionEntity definition, NotificationPolicyDecision decision, Instant now) {
        NotificationEntity notification = new NotificationEntity();
        notification.setUser(user);
        notification.setType(request.eventType().definitionKey());
        notification.setTitle(request.title());
        notification.setMessage(request.message());
        notification.setSeverity(request.severity() == null ? "INFO" : request.severity());
        notification.setSource(request.source());
        notification.setTargetType(request.targetType());
        notification.setTargetId(request.targetId());
        notification.setTargetRoute(request.targetRoute());
        notification.setPrimaryAction(request.primaryAction());
        notification.setActionAmountMl(request.actionAmountMl());
        notification.setVisibleInApp(decision.allows(NotificationDeliveryChannel.IN_APP));
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        Map<String, String> parameters = request.parameters() == null ? Map.of() : request.parameters();
        NotificationDefinitionPolicy.NotificationPresentation presentation =
                definitionPolicy.presentation(notification, definition, parameters);
        notification.setTitle(presentation.title());
        notification.setMessage(presentation.message());
        notification.setSeverity(presentation.severity());
        notification.setTargetRoute(presentation.targetRoute());
        return notification;
    }

    private Set<NotificationDeliveryChannel> applyDefinitionChannel(
            Set<NotificationDeliveryChannel> requested, NotificationCampaignChannel definitionChannel) {
        EnumSet<NotificationDeliveryChannel> channels = EnumSet.copyOf(requested);
        channels.remove(NotificationDeliveryChannel.EMAIL); // Email transport is deliberately not active in Sprint 2.
        if (definitionChannel == NotificationCampaignChannel.IN_APP) channels.remove(NotificationDeliveryChannel.PUSH);
        if (definitionChannel == NotificationCampaignChannel.PUSH) channels.remove(NotificationDeliveryChannel.IN_APP);
        return channels;
    }

    private NotificationPolicyDecision applyReleaseGate(NotificationPolicyDecision decision, Long userId) {
        if (!decision.allows(NotificationDeliveryChannel.PUSH)) return decision;
        NotificationReleaseGate.ReleaseDecision release = releaseGate.evaluate(userId);
        if (release.allowed()) return decision;
        EnumSet<NotificationDeliveryChannel> allowed = decision.allowedChannels().isEmpty()
                ? EnumSet.noneOf(NotificationDeliveryChannel.class)
                : EnumSet.copyOf(decision.allowedChannels());
        allowed.remove(NotificationDeliveryChannel.PUSH);
        return new NotificationPolicyDecision(allowed, null, release.reasonCode());
    }

    private void validate(NotificationOrchestrationRequest request) {
        if (request == null || request.user() == null || request.user().getId() == null || request.eventType() == null) {
            throw new IllegalArgumentException("Notification user and event type are required.");
        }
        requireText(request.source(), "source", 80);
        requireText(request.sourceEventId(), "sourceEventId", 255);
        Map<String, String> parameters = request.parameters() == null ? Map.of() : request.parameters();
        if (!request.eventType().allowedParameters().containsAll(parameters.keySet())) {
            throw new IllegalArgumentException("Notification contains an unsupported template parameter.");
        }
        if (!parameters.keySet().containsAll(request.eventType().requiredParameters())) {
            throw new IllegalArgumentException("Notification is missing a required template parameter.");
        }
        if (parameters.size() > 12 || parameters.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getValue() == null || entry.getValue().length() > 200)) {
            throw new IllegalArgumentException("Notification template parameters are invalid.");
        }
    }

    private void requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException("Notification " + field + " is required and must be at most " + maxLength + " characters.");
        }
    }

    private String writeParameters(Map<String, String> parameters) {
        try {
            return objectMapper.writeValueAsString(parameters == null ? Map.of() : parameters);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Notification parameters cannot be serialized.", exception);
        }
    }

    private Instant max(Instant first, Instant second) {
        if (second == null) return first;
        return first.isAfter(second) ? first : second;
    }

    private NotificationOrchestrationResult existingResult(NotificationOccurrenceEntity occurrence) {
        Long outboxId = outboxRepository.findByOccurrenceId(occurrence.getId())
                .map(NotificationOutboxEntity::getId).orElse(null);
        return new NotificationOrchestrationResult(occurrence.getId(),
                occurrence.getNotification() == null ? null : occurrence.getNotification().getId(),
                outboxId, occurrence.getStatus(), occurrence.getReasonCode(), true);
    }

    private NotificationOrchestrationResult result(NotificationOccurrenceEntity occurrence,
            NotificationEntity notification, NotificationOutboxEntity outbox, boolean duplicate) {
        return new NotificationOrchestrationResult(occurrence.getId(), notification == null ? null : notification.getId(),
                outbox == null ? null : outbox.getId(), occurrence.getStatus(), occurrence.getReasonCode(), duplicate);
    }
}

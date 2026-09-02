package com.grun.calorietracker.service.notification;

import com.grun.calorietracker.config.NotificationDeliveryProperties;
import com.grun.calorietracker.config.PushProperties;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.push.PushProviderSendResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationDispatchStateService {
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationDeliveryAttemptRepository attemptRepository;
    private final NotificationDefinitionRepository definitionRepository;
    private final UserPushTokenRepository tokenRepository;
    private final NotificationPolicyEvaluator policyEvaluator;
    private final NotificationReleaseGate releaseGate;
    private final NotificationDeliveryProperties properties;
    private final PushProperties pushProperties;

    @Transactional
    public PreparedOutbox prepare(Long outboxId, String workerId, Instant now) {
        NotificationOutboxEntity outbox = outboxRepository.findByIdForUpdate(outboxId).orElse(null);
        if (outbox == null || !workerId.equals(outbox.getLeaseOwner())) return null;
        NotificationOccurrenceEntity occurrence = outbox.getOccurrence();
        if (!now.isBefore(outbox.getExpiresAt())) {
            finish(outbox, NotificationOutboxStatus.EXPIRED, NotificationOccurrenceStatus.EXPIRED,
                    "TTL_EXPIRED", now);
            return null;
        }
        if (!properties.isEnabled() || !pushProperties.isEnabled()) {
            finish(outbox, NotificationOutboxStatus.CANCELLED, terminalOccurrence(outbox),
                    "SYSTEM_DISABLED", now);
            return null;
        }
        NotificationReleaseGate.ReleaseDecision release = releaseGate.evaluate(occurrence.getUser().getId());
        if (!release.allowed()) {
            finish(outbox, NotificationOutboxStatus.CANCELLED, terminalOccurrence(outbox),
                    release.reasonCode(), now);
            return null;
        }
        var definition = definitionRepository.findByKey(occurrence.getDefinitionKey()).orElse(null);
        if (definition == null || !definition.isEnabled()
                || definition.getChannel() == NotificationCampaignChannel.IN_APP) {
            finish(outbox, NotificationOutboxStatus.CANCELLED, terminalOccurrence(outbox),
                    definition == null ? "DEFINITION_MISSING" : "DEFINITION_PUSH_DISABLED", now);
            return null;
        }
        NotificationPolicyDecision decision = policyEvaluator.evaluate(occurrence.getUser(), occurrence.getEventType(),
                Set.of(NotificationDeliveryChannel.PUSH), now);
        if (!decision.allows(NotificationDeliveryChannel.PUSH)) {
            finish(outbox, NotificationOutboxStatus.CANCELLED, terminalOccurrence(outbox),
                    decision.reasonCode(), now);
            return null;
        }
        if (decision.pushAvailableAt() != null && decision.pushAvailableAt().isAfter(now)) {
            if (!decision.pushAvailableAt().isBefore(outbox.getExpiresAt())) {
                finish(outbox, NotificationOutboxStatus.EXPIRED, terminalOccurrence(outbox),
                        "QUIET_HOURS_EXCEED_TTL", now);
            } else {
                outbox.setStatus(NotificationOutboxStatus.RETRY);
                outbox.setAvailableAt(decision.pushAvailableAt());
                outbox.setLeaseOwner(null);
                outbox.setLeaseUntil(null);
                outbox.setLastErrorCode("QUIET_HOURS_DEFERRED");
                outbox.setUpdatedAt(now);
                occurrence.setStatus(NotificationOccurrenceStatus.QUEUED);
                occurrence.setReasonCode("QUIET_HOURS_DEFERRED");
                occurrence.setUpdatedAt(now);
            }
            return null;
        }

        if (occurrence.getClassification() == NotificationClassification.BEHAVIOR_REMINDER) {
            outbox.getNotification().setVisibleInApp(true);
        }
        List<UserPushTokenEntity> tokens = tokenRepository.findByUserAndEnabledTrue(occurrence.getUser()).stream()
                .filter(token -> token.getProvider() == pushProperties.getProvider()).toList();
        if (tokens.isEmpty()) {
            finish(outbox, NotificationOutboxStatus.CANCELLED, terminalOccurrence(outbox),
                    "NO_VALID_TOKEN", now);
            return null;
        }
        List<Long> attemptIds = new ArrayList<>();
        for (UserPushTokenEntity token : tokens) {
            NotificationDeliveryAttemptEntity attempt = attemptRepository
                    .findByOutboxIdAndPushTokenId(outbox.getId(), token.getId())
                    .orElseGet(() -> newAttempt(outbox, token, now));
            if (attempt.getStatus() == NotificationDeliveryAttemptStatus.PENDING
                    || (attempt.getStatus() == NotificationDeliveryAttemptStatus.FAILED_RETRYABLE
                    && attempt.getNextAttemptAt() != null && !attempt.getNextAttemptAt().isAfter(now))) {
                attempt.setStatus(NotificationDeliveryAttemptStatus.PROCESSING);
                attempt.setAttemptCount(attempt.getAttemptCount() + 1);
                attempt.setUpdatedAt(now);
                attemptRepository.save(attempt);
                attemptIds.add(attempt.getId());
            }
        }
        if (attemptIds.isEmpty()) {
            finishFromAttempts(outbox, now);
            return null;
        }
        outbox.setUpdatedAt(now);
        outboxRepository.save(outbox);
        return new PreparedOutbox(outbox.getId(), attemptIds, outbox.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public AttemptPayload payload(Long attemptId) {
        return attemptRepository.findByIdWithPayload(attemptId)
                .map(attempt -> new AttemptPayload(attempt.getId(), attempt.getPushToken(),
                        attempt.getOutbox().getNotification(), attempt.getOutbox().getExpiresAt()))
                .orElse(null);
    }

    @Transactional
    public void recordProviderResult(Long attemptId, PushProviderSendResult result, Instant now) {
        NotificationDeliveryAttemptEntity attempt = attemptRepository.findById(attemptId).orElse(null);
        if (attempt == null || attempt.getStatus() != NotificationDeliveryAttemptStatus.PROCESSING) return;
        NotificationOutboxEntity outbox = attempt.getOutbox();
        if (result.sent()) {
            attempt.setStatus(NotificationDeliveryAttemptStatus.PROVIDER_ACCEPTED);
            attempt.setProviderMessageId(result.providerMessageId());
            attempt.setProviderAcceptedAt(now);
        } else if (result.invalidToken()) {
            attempt.setStatus(NotificationDeliveryAttemptStatus.INVALID_TOKEN);
            attempt.getPushToken().setEnabled(false);
            attempt.getPushToken().setRevokedAt(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
            tokenRepository.save(attempt.getPushToken());
        } else if (result.uncertain()) {
            attempt.setStatus(NotificationDeliveryAttemptStatus.UNKNOWN);
            outbox.setStatus(NotificationOutboxStatus.UNKNOWN);
            outbox.getOccurrence().setStatus(NotificationOccurrenceStatus.UNKNOWN);
        } else if (attempt.getAttemptCount() < properties.getMaxAttempts()
                && now.plus(backoff(attempt.getAttemptCount())).isBefore(outbox.getExpiresAt())) {
            attempt.setStatus(NotificationDeliveryAttemptStatus.FAILED_RETRYABLE);
            attempt.setNextAttemptAt(now.plus(backoff(attempt.getAttemptCount())));
            outbox.setStatus(NotificationOutboxStatus.RETRY);
            outbox.setAvailableAt(attempt.getNextAttemptAt());
        } else {
            attempt.setStatus(NotificationDeliveryAttemptStatus.FAILED_FINAL);
        }
        attempt.setErrorCode(safeError(result.errorMessage()));
        attempt.setUpdatedAt(now);
        attemptRepository.save(attempt);
        finishFromAttempts(outbox, now);
    }

    @Transactional
    public void recordReceipt(String providerMessageId, boolean delivered, String errorCode, Instant now) {
        attemptRepository.findByProviderMessageId(providerMessageId).ifPresent(attempt -> {
            if (attempt.getStatus() != NotificationDeliveryAttemptStatus.PROVIDER_ACCEPTED) return;
            attempt.setStatus(delivered ? NotificationDeliveryAttemptStatus.DELIVERED
                    : NotificationDeliveryAttemptStatus.FAILED_FINAL);
            attempt.setDeliveredAt(now);
            attempt.setErrorCode(delivered ? null : safeError(errorCode));
            attempt.setUpdatedAt(now);
            attemptRepository.save(attempt);
        });
    }

    @Transactional
    public void cancelForKillSwitch(Long outboxId, Instant now) {
        outboxRepository.findByIdForUpdate(outboxId).ifPresent(outbox -> {
            attemptRepository.findByOutboxIdOrderById(outboxId).stream()
                    .filter(attempt -> attempt.getStatus() == NotificationDeliveryAttemptStatus.PENDING
                            || attempt.getStatus() == NotificationDeliveryAttemptStatus.PROCESSING
                            || attempt.getStatus() == NotificationDeliveryAttemptStatus.FAILED_RETRYABLE)
                    .forEach(attempt -> {
                        attempt.setStatus(NotificationDeliveryAttemptStatus.SUPPRESSED);
                        attempt.setErrorCode("SYSTEM_DISABLED");
                        attempt.setUpdatedAt(now);
                        attemptRepository.save(attempt);
                    });
            finish(outbox, NotificationOutboxStatus.CANCELLED, terminalOccurrence(outbox),
                    "SYSTEM_DISABLED", now);
        });
    }

    private NotificationDeliveryAttemptEntity newAttempt(NotificationOutboxEntity outbox,
            UserPushTokenEntity token, Instant now) {
        NotificationDeliveryAttemptEntity attempt = new NotificationDeliveryAttemptEntity();
        attempt.setOutbox(outbox);
        attempt.setPushToken(token);
        attempt.setChannel(NotificationDeliveryChannel.PUSH);
        attempt.setProvider(token.getProvider());
        attempt.setStatus(NotificationDeliveryAttemptStatus.PENDING);
        attempt.setCreatedAt(now);
        attempt.setUpdatedAt(now);
        return attemptRepository.save(attempt);
    }

    private void finishFromAttempts(NotificationOutboxEntity outbox, Instant now) {
        List<NotificationDeliveryAttemptEntity> attempts = attemptRepository.findByOutboxIdOrderById(outbox.getId());
        boolean active = attempts.stream().anyMatch(value -> value.getStatus() == NotificationDeliveryAttemptStatus.PROCESSING);
        if (attempts.stream().anyMatch(value -> value.getStatus() == NotificationDeliveryAttemptStatus.UNKNOWN)) {
            outbox.setStatus(NotificationOutboxStatus.UNKNOWN);
            outbox.getOccurrence().setStatus(NotificationOccurrenceStatus.UNKNOWN);
        } else if (attempts.stream().anyMatch(value -> value.getStatus() == NotificationDeliveryAttemptStatus.PENDING
                || value.getStatus() == NotificationDeliveryAttemptStatus.PROCESSING
                || value.getStatus() == NotificationDeliveryAttemptStatus.FAILED_RETRYABLE)) {
            // Work remains for this occurrence.
        } else if (attempts.stream().anyMatch(value -> value.getStatus() == NotificationDeliveryAttemptStatus.PROVIDER_ACCEPTED
                || value.getStatus() == NotificationDeliveryAttemptStatus.DELIVERED)) {
            outbox.setStatus(NotificationOutboxStatus.COMPLETED);
            outbox.getOccurrence().setStatus(NotificationOccurrenceStatus.COMPLETED);
        } else if (!attempts.isEmpty()) {
            outbox.setStatus(NotificationOutboxStatus.FAILED_FINAL);
            outbox.getOccurrence().setStatus(terminalOccurrence(outbox));
            outbox.setLastErrorCode("ALL_ATTEMPTS_FAILED");
        }
        if (!active) {
            outbox.setLeaseOwner(null);
            outbox.setLeaseUntil(null);
        }
        outbox.setUpdatedAt(now);
        outbox.getOccurrence().setUpdatedAt(now);
        outboxRepository.save(outbox);
    }

    private void finish(NotificationOutboxEntity outbox, NotificationOutboxStatus outboxStatus,
            NotificationOccurrenceStatus occurrenceStatus, String reason, Instant now) {
        outbox.setStatus(outboxStatus);
        outbox.setLastErrorCode(reason);
        outbox.setLeaseOwner(null);
        outbox.setLeaseUntil(null);
        outbox.setUpdatedAt(now);
        outbox.getOccurrence().setStatus(occurrenceStatus);
        outbox.getOccurrence().setReasonCode(reason);
        outbox.getOccurrence().setUpdatedAt(now);
        outboxRepository.save(outbox);
    }

    private NotificationOccurrenceStatus terminalOccurrence(NotificationOutboxEntity outbox) {
        return Boolean.TRUE.equals(outbox.getNotification().getVisibleInApp())
                ? NotificationOccurrenceStatus.COMPLETED : NotificationOccurrenceStatus.SUPPRESSED;
    }

    private Duration backoff(int attemptCount) {
        long multiplier = 1L << Math.max(0, Math.min(attemptCount - 1, 4));
        return properties.getBaseRetryDelay().multipliedBy(multiplier);
    }

    private String safeError(String value) {
        if (value == null || value.isBlank()) return null;
        return value.length() > 80 ? value.substring(0, 80) : value;
    }

    public record PreparedOutbox(Long outboxId, List<Long> attemptIds, Instant expiresAt) { }
    public record AttemptPayload(Long attemptId, UserPushTokenEntity token,
                                 NotificationEntity notification, Instant expiresAt) { }
}

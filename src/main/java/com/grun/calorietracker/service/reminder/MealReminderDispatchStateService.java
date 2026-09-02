package com.grun.calorietracker.service.reminder;

import com.grun.calorietracker.config.MealReminderDeliveryProperties;
import com.grun.calorietracker.config.PushProperties;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MealReminderDispatchStateService {
    private final MealReminderOutboxRepository outboxRepository;
    private final MealReminderDeliveryAttemptRepository attemptRepository;
    private final MealReminderDailyBudgetRepository budgetRepository;
    private final UserPushTokenRepository tokenRepository;
    private final DailyMealReminderSnapshotService snapshotService;
    private final MealReminderDecisionEngine decisionEngine;
    private final MealReminderRuntimeStateFactory runtimeStateFactory;
    private final MealReminderPolicyFactory policyFactory;
    private final MealReminderDeliveryProperties properties;
    private final PushProperties pushProperties;

    @Transactional
    public PreparedOutbox revalidateAndPrepare(Long outboxId, String workerId, Instant now) {
        MealReminderOutboxEntity outbox = outboxRepository.findByIdForUpdate(outboxId).orElse(null);
        if (outbox == null || !workerId.equals(outbox.getLeaseOwner())) return null;
        MealReminderOccurrenceEntity occurrence = outbox.getOccurrence();
        UserEntity user = occurrence.getUser();
        if (!now.isBefore(outbox.getExpiresAt())) {
            cancel(outbox, MealReminderOutboxStatus.EXPIRED, "STALE_SLOT", now, true);
            return null;
        }
        MealReminderContract.Mode mode = policyFactory.current().mode();
        if (!properties.isDeliveryEnabled() || !pushProperties.isEnabled()
                || (mode != MealReminderContract.Mode.PILOT && mode != MealReminderContract.Mode.LIVE)) {
            cancel(outbox, MealReminderOutboxStatus.CANCELLED, "SYSTEM_DISABLED", now, true);
            return null;
        }

        DailyMealReminderSnapshot snapshot = snapshotService.buildBatch(List.of(user), now).get(0);
        MealReminderRuntimeState runtime = runtimeStateFactory.current(user, occurrence, now);
        MealReminderDecision refreshed = decisionEngine.evaluate(snapshot, policyFactory.current(), runtime);
        if (!refreshed.shouldSend() || refreshed.slot() != occurrence.getSlot()) {
            cancel(outbox, MealReminderOutboxStatus.CANCELLED, refreshed.reason().name(), now, true);
            return null;
        }
        occurrence.setCandidate(refreshed.candidate());
        occurrence.setMessageVariant(refreshed.message());
        occurrence.setPolicyVersion(refreshed.policyVersion());
        occurrence.setExpiresAt(refreshed.expiresAt());
        occurrence.setUpdatedAt(now);
        NotificationEntity notification = outbox.getNotification();
        notification.setTitle(refreshed.renderedCopy().title());
        notification.setMessage(refreshed.renderedCopy().body());

        List<UserPushTokenEntity> tokens = tokenRepository.findByUserAndEnabledTrue(user).stream()
                .filter(token -> token.getProvider() == pushProperties.getProvider())
                .toList();
        if (tokens.isEmpty()) {
            cancel(outbox, MealReminderOutboxStatus.NO_TOKEN, "NO_VALID_TOKEN", now, true);
            occurrence.setStatus(MealReminderOccurrenceStatus.NO_TOKEN);
            return null;
        }
        List<Long> attemptIds = new ArrayList<>();
        for (UserPushTokenEntity token : tokens) {
            MealReminderDeliveryAttemptEntity attempt = attemptRepository
                    .findByOutboxIdAndPushTokenId(outbox.getId(), token.getId())
                    .orElseGet(() -> newAttempt(outbox, token, now));
            if (attempt.getStatus() == MealReminderAttemptStatus.PENDING
                    || (attempt.getStatus() == MealReminderAttemptStatus.FAILED_RETRYABLE
                    && attempt.getNextAttemptAt() != null && !attempt.getNextAttemptAt().isAfter(now))) {
                attempt.setStatus(MealReminderAttemptStatus.PROCESSING);
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
        outbox.setExpiresAt(refreshed.expiresAt());
        outbox.setUpdatedAt(now);
        outboxRepository.save(outbox);
        return new PreparedOutbox(outbox.getId(), attemptIds, refreshed.expiresAt());
    }

    @Transactional(readOnly = true)
    public AttemptPayload payload(Long attemptId) {
        return attemptRepository.findByIdWithPayload(attemptId)
                .map(attempt -> new AttemptPayload(
                        attempt.getId(), attempt.getPushToken(), attempt.getOutbox().getNotification(),
                        attempt.getOutbox().getExpiresAt()))
                .orElse(null);
    }

    @Transactional
    public void recordProviderResult(Long attemptId, com.grun.calorietracker.service.push.PushProviderSendResult result, Instant now) {
        MealReminderDeliveryAttemptEntity attempt = attemptRepository.findById(attemptId).orElse(null);
        if (attempt == null || attempt.getStatus() != MealReminderAttemptStatus.PROCESSING) return;
        MealReminderOutboxEntity outbox = attempt.getOutbox();
        if (result.sent()) {
            attempt.setStatus(MealReminderAttemptStatus.PROVIDER_ACCEPTED);
            attempt.setProviderMessageId(result.providerMessageId());
            attempt.setProviderAcceptedAt(now);
        } else if (result.invalidToken()) {
            attempt.setStatus(MealReminderAttemptStatus.INVALID_TOKEN);
            attempt.getPushToken().setEnabled(false);
            attempt.getPushToken().setRevokedAt(java.time.LocalDateTime.ofInstant(now, java.time.ZoneOffset.UTC));
            tokenRepository.save(attempt.getPushToken());
        } else if (result.uncertain()) {
            attempt.setStatus(MealReminderAttemptStatus.UNKNOWN);
            outbox.setStatus(MealReminderOutboxStatus.UNKNOWN);
            outbox.getOccurrence().setStatus(MealReminderOccurrenceStatus.UNKNOWN);
        } else if (attempt.getAttemptCount() < properties.getMaxAttempts()
                && now.plus(backoff(attempt.getAttemptCount())).isBefore(outbox.getExpiresAt())) {
            attempt.setStatus(MealReminderAttemptStatus.FAILED_RETRYABLE);
            attempt.setNextAttemptAt(now.plus(backoff(attempt.getAttemptCount())));
            outbox.setStatus(MealReminderOutboxStatus.RETRY);
            outbox.setAvailableAt(attempt.getNextAttemptAt());
        } else {
            attempt.setStatus(MealReminderAttemptStatus.FAILED_FINAL);
        }
        attempt.setErrorCode(safeError(result.errorMessage()));
        attempt.setUpdatedAt(now);
        attemptRepository.save(attempt);
        finishFromAttempts(outbox, now);
    }

    @Transactional
    public void recordReceipt(String providerMessageId, boolean delivered, String errorCode, Instant now) {
        attemptRepository.findByProviderMessageId(providerMessageId).ifPresent(attempt -> {
            if (attempt.getStatus() != MealReminderAttemptStatus.PROVIDER_ACCEPTED) return;
            attempt.setStatus(delivered ? MealReminderAttemptStatus.RECEIPT_DELIVERED
                    : MealReminderAttemptStatus.RECEIPT_FAILED);
            attempt.setReceiptAt(now);
            attempt.setErrorCode(delivered ? null : safeError(errorCode));
            attempt.setUpdatedAt(now);
            attemptRepository.save(attempt);
        });
    }

    @Transactional
    public void cancelForKillSwitch(Long outboxId, Instant now) {
        outboxRepository.findByIdForUpdate(outboxId).ifPresent(outbox ->
                cancel(outbox, MealReminderOutboxStatus.CANCELLED, "SYSTEM_DISABLED", now, true));
    }

    private MealReminderDeliveryAttemptEntity newAttempt(
            MealReminderOutboxEntity outbox, UserPushTokenEntity token, Instant now) {
        MealReminderDeliveryAttemptEntity attempt = new MealReminderDeliveryAttemptEntity();
        attempt.setOutbox(outbox);
        attempt.setPushToken(token);
        attempt.setProvider(token.getProvider());
        attempt.setStatus(MealReminderAttemptStatus.PENDING);
        attempt.setCreatedAt(now);
        attempt.setUpdatedAt(now);
        return attemptRepository.save(attempt);
    }

    private void finishFromAttempts(MealReminderOutboxEntity outbox, Instant now) {
        List<MealReminderDeliveryAttemptEntity> attempts = attemptRepository.findByOutboxIdOrderById(outbox.getId());
        boolean processing = attempts.stream().anyMatch(value ->
                value.getStatus() == MealReminderAttemptStatus.PROCESSING);
        if (attempts.stream().anyMatch(value -> value.getStatus() == MealReminderAttemptStatus.UNKNOWN)) {
            outbox.setStatus(MealReminderOutboxStatus.UNKNOWN);
            outbox.getOccurrence().setStatus(MealReminderOccurrenceStatus.UNKNOWN);
        } else if (attempts.stream().anyMatch(value -> value.getStatus() == MealReminderAttemptStatus.PROCESSING
                || value.getStatus() == MealReminderAttemptStatus.PENDING
                || value.getStatus() == MealReminderAttemptStatus.FAILED_RETRYABLE)) {
            // More device work remains.
        } else if (attempts.stream().anyMatch(value -> value.getStatus() == MealReminderAttemptStatus.PROVIDER_ACCEPTED
                || value.getStatus() == MealReminderAttemptStatus.RECEIPT_DELIVERED
                || value.getStatus() == MealReminderAttemptStatus.RECEIPT_FAILED)) {
            outbox.setStatus(MealReminderOutboxStatus.COMPLETED);
            outbox.getOccurrence().setStatus(MealReminderOccurrenceStatus.COMPLETED);
        } else if (!attempts.isEmpty()) {
            cancel(outbox, MealReminderOutboxStatus.CANCELLED, "ALL_ATTEMPTS_FAILED", now, true);
        }
        // Keep ownership while another device attempt is still outside the transaction doing I/O.
        // Releasing here would allow another instance to claim and duplicate that same push.
        if (!processing) {
            outbox.setLeaseOwner(null);
            outbox.setLeaseUntil(null);
        }
        outbox.setUpdatedAt(now);
        outboxRepository.save(outbox);
    }

    private void cancel(MealReminderOutboxEntity outbox, MealReminderOutboxStatus status,
                        String errorCode, Instant now, boolean releaseReservation) {
        outbox.setStatus(status);
        outbox.setLastErrorCode(errorCode);
        outbox.setLeaseOwner(null);
        outbox.setLeaseUntil(null);
        outbox.setUpdatedAt(now);
        MealReminderOccurrenceEntity occurrence = outbox.getOccurrence();
        occurrence.setStatus(status == MealReminderOutboxStatus.NO_TOKEN
                ? MealReminderOccurrenceStatus.NO_TOKEN : MealReminderOccurrenceStatus.CANCELLED);
        occurrence.getNotification().setVisibleInApp(false);
        if (releaseReservation && occurrence.isReservationActive()) {
            budgetRepository.findForUpdate(occurrence.getUser().getId(), occurrence.getLocalDate())
                    .ifPresent(budget -> {
                        budget.setReservedCount(Math.max(0, budget.getReservedCount() - 1));
                        if (occurrence.getCandidate() == MealReminderDecision.Candidate.DAILY_CATCHUP) {
                            budget.setCatchupCount(Math.max(0, budget.getCatchupCount() - 1));
                        }
                        budget.setUpdatedAt(now);
                        budgetRepository.save(budget);
                    });
            occurrence.setReservationActive(false);
        }
        occurrence.setUpdatedAt(now);
        outboxRepository.save(outbox);
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

package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.grun.calorietracker.dto.RevenueCatWebhookResponseDto;
import com.grun.calorietracker.entity.SubscriptionProviderEventEntity;
import com.grun.calorietracker.enums.SubscriptionProviderEventStatus;
import com.grun.calorietracker.repository.SubscriptionProviderEventRepository;
import com.grun.calorietracker.service.RevenueCatWebhookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@Primary
@Service
public class ReliableRevenueCatWebhookService implements RevenueCatWebhookService {
    private final RevenueCatWebhookServiceImpl processor;
    private final SubscriptionProviderEventRepository events;
    private final TransactionTemplate transaction;

    public ReliableRevenueCatWebhookService(RevenueCatWebhookServiceImpl processor,
            SubscriptionProviderEventRepository events, PlatformTransactionManager manager) {
        this.processor = processor;
        this.events = events;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(30);
    }

    @Override
    public RevenueCatWebhookResponseDto processWebhook(String authorization, JsonNode payload) {
        Long id;
        try {
            id = processor.captureWebhook(authorization, payload);
        } catch (DataIntegrityViolationException concurrentInsert) {
            // A concurrent delivery may have won the unique insert. Re-read in a fresh transaction.
            id = processor.captureWebhook(authorization, payload);
        }
        return processStoredEvent(id, false);
    }

    @Override
    public RevenueCatWebhookResponseDto retryStoredEvent(Long id) {
        return processStoredEvent(id, true);
    }

    private RevenueCatWebhookResponseDto processStoredEvent(Long id, boolean operatorRetry) {
        try {
            return processStoredEventOnce(id, operatorRetry);
        } catch (ObjectOptimisticLockingFailureException transientConflict) {
            log.warn("subscription_event_optimistic_lock_retry eventId={}", id);
            try {
                return processStoredEventOnce(id, operatorRetry);
            } catch (RuntimeException repeatedFailure) {
                return recordFailure(id, repeatedFailure.getClass().getSimpleName() + ": " + repeatedFailure.getMessage());
            }
        }
    }

    private RevenueCatWebhookResponseDto processStoredEventOnce(Long id, boolean operatorRetry) {
        String failure;
        boolean[] attempted = {false};
        try {
            var result = transaction.execute(status -> {
                var event = events.findLockedById(id).orElseThrow();
                if (event.getStatus() != SubscriptionProviderEventStatus.FAILED) {
                    return new RevenueCatWebhookResponseDto(true, true, event.getProviderEventId(),
                            event.getStatus().name(), "Event already completed.");
                }
                if (!operatorRetry && (event.getNextAttemptAt() == null
                        || event.getNextAttemptAt().isAfter(LocalDateTime.now(ZoneOffset.UTC)))) {
                    return new RevenueCatWebhookResponseDto(true, true, event.getProviderEventId(),
                            "FAILED", "Event retained for retry or operator review.");
                }
                attempted[0] = true;
                var response = processor.retryStoredEvent(id);
                if ("FAILED".equals(response.getStatus())) {
                    status.setRollbackOnly();
                } else {
                    event.setNextAttemptAt(null);
                }
                return response;
            });
            if (result != null && "REQUIRES_REVIEW".equals(result.getStatus())) {
                var reviewEvent = events.findById(id).orElseThrow();
                processor.notifyAdminsAboutFailedProviderEvent(reviewEvent);
                return result;
            }
            if (result != null && (!attempted[0] || !"FAILED".equals(result.getStatus()))) {
                if (attempted[0]) processor.resolveAdminAlertsForProviderEvent(id);
                return result;
            }
            failure = result == null ? "Provider processing returned no result." : result.getMessage();
        } catch (RuntimeException ex) {
            if (ex instanceof ObjectOptimisticLockingFailureException optimisticLockingFailure) {
                throw optimisticLockingFailure;
            }
            failure = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            log.error("subscription_event_processing_failed eventId={} exception={}", id, ex.getClass().getSimpleName());
        }
        return recordFailure(id, failure);
    }

    private RevenueCatWebhookResponseDto recordFailure(Long id, String failure) {
        var recorded = transaction.execute(status -> {
            var event = events.findLockedById(id).orElseThrow();
            if (event.getStatus() != SubscriptionProviderEventStatus.FAILED) return event;
            event.setProcessingError(failure == null ? "Unknown processing failure."
                    : failure.substring(0, Math.min(1000, failure.length())));
            event.setProcessedAt(LocalDateTime.now(ZoneOffset.UTC));
            int attempts = event.getProcessingAttempts() + 1;
            event.setProcessingAttempts(attempts);
            event.setNextAttemptAt(attempts >= 8 || requiresReview(failure) ? null
                    : LocalDateTime.now(ZoneOffset.UTC).plusSeconds(Math.min(3600, 30L << Math.min(attempts - 1, 7))));
            return events.saveAndFlush(event);
        });
        if (recorded.getStatus() != SubscriptionProviderEventStatus.FAILED) {
            return new RevenueCatWebhookResponseDto(true, true, recorded.getProviderEventId(),
                    recorded.getStatus().name(), "Event already completed.");
        }
        log.warn("subscription_event_pending eventId={} attempts={} retryScheduled={}",
                id, recorded.getProcessingAttempts(), recorded.getNextAttemptAt() != null);
        if (recorded.getNextAttemptAt() == null) {
            try {
                processor.notifyAdminsAboutFailedProviderEvent(recorded);
            } catch (RuntimeException alertFailure) {
                log.error("subscription_event_alert_failed eventId={} exception={}", id, alertFailure.getClass().getSimpleName());
            }
        }
        return new RevenueCatWebhookResponseDto(true, false, recorded.getProviderEventId(), "FAILED",
                "Event retained for retry or operator review.");
    }

    private boolean requiresReview(String failure) {
        return failure != null && (failure.contains("SUBSCRIPTION_OWNERSHIP_CONFLICT")
                || failure.contains("already bound") || failure.contains("not mapped")
                || failure.contains("does not match a known user"));
    }

    @Scheduled(fixedDelayString = "${grun.revenuecat.retry-delay-ms:60000}", initialDelay = 60000)
    public void retryDueEvents() {
        for (Long id : events.findDueRetries(LocalDateTime.now(ZoneOffset.UTC), PageRequest.of(0, 20))) {
            try {
                processStoredEvent(id, false);
            } catch (RuntimeException ex) {
                log.error("subscription_event_retry_failed eventId={} exception={}", id, ex.getClass().getSimpleName());
            }
        }
    }
}

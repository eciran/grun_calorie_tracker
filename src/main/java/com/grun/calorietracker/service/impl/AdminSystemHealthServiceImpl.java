package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdminSystemHealthDto;
import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.enums.AiDraftRejectReason;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.ProductAnalyticsEventType;
import com.grun.calorietracker.enums.SubscriptionProviderEventStatus;
import com.grun.calorietracker.enums.SubscriptionStatus;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.repository.ProductAnalyticsEventRepository;
import com.grun.calorietracker.repository.SubscriptionProviderEventRepository;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.SubscriptionRepository;
import com.grun.calorietracker.service.AdminSystemHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminSystemHealthServiceImpl implements AdminSystemHealthService {

    private final DataSource dataSource;
    private final Environment environment;
    private final SubscriptionProviderEventRepository subscriptionProviderEventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final NotificationRepository notificationRepository;
    private final AiProperties aiProperties;
    private final AiRequestHistoryRepository aiRequestHistoryRepository;
    private final ProductAnalyticsEventRepository productAnalyticsEventRepository;

    @Override
    public AdminSystemHealthDto getHealth() {
        DatabaseCheck databaseCheck = checkDatabase();
        RuntimeSnapshot runtimeSnapshot = runtimeSnapshot();
        RevenueCatSnapshot revenueCatSnapshot = revenueCatSnapshot();
        SubscriptionSnapshot subscriptionSnapshot = subscriptionSnapshot();
        AiSnapshot aiSnapshot = aiSnapshot();
        ProductAnalyticsSnapshot productAnalyticsSnapshot = productAnalyticsSnapshot();
        long systemAlertsLast24h = systemAlertsLast24h();
        List<String> warnings = warnings(databaseCheck, runtimeSnapshot, revenueCatSnapshot, subscriptionSnapshot, aiSnapshot, productAnalyticsSnapshot, systemAlertsLast24h);
        String status = "UP".equals(databaseCheck.status()) && warnings.isEmpty() ? "UP" : "DEGRADED";

        return new AdminSystemHealthDto(
                status,
                environment.getProperty("spring.application.name", "grun-calorie-tracker"),
                environment.getProperty("info.app.version", "unknown"),
                activeProfiles(),
                databaseCheck.status(),
                databaseCheck.latencyMs(),
                runtimeSnapshot.uptimeMs(),
                runtimeSnapshot.availableProcessors(),
                runtimeSnapshot.heapUsedMb(),
                runtimeSnapshot.heapMaxMb(),
                revenueCatSnapshot.eventsLast24h(),
                revenueCatSnapshot.failedEvents(),
                subscriptionSnapshot.activeSubscriptions(),
                subscriptionSnapshot.exhaustedAiQuotaSubscriptions(),
                systemAlertsLast24h,
                aiProperties.isEnabled(),
                aiProperties.getProvider() == null ? null : aiProperties.getProvider().name(),
                aiProperties.getModel(),
                aiSnapshot.requestsLast24h(),
                aiSnapshot.failedRequestsLast24h(),
                aiSnapshot.failureRateLast24h(),
                aiSnapshot.draftsLast7d(),
                aiSnapshot.confirmedDraftsLast7d(),
                aiSnapshot.rejectedDraftsLast7d(),
                aiSnapshot.rejectionReasonsLast7d(),
                aiSnapshot.openDraftsLast7d(),
                aiSnapshot.confirmationRateLast7d(),
                productAnalyticsSnapshot.logFlowCompletedLast24h(),
                productAnalyticsSnapshot.averageLogFlowDurationMsLast24h(),
                productAnalyticsSnapshot.quickLogSuggestionAppliedLast24h(),
                productAnalyticsSnapshot.searchStartedLast24h(),
                warnings,
                LocalDateTime.now()
        );
    }

    private DatabaseCheck checkDatabase() {
        long startedAt = System.nanoTime();
        try (Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(2);
            return new DatabaseCheck(valid ? "UP" : "DOWN", elapsedMs(startedAt));
        } catch (Exception ex) {
            return new DatabaseCheck("DOWN", elapsedMs(startedAt));
        }
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private List<String> activeProfiles() {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            return List.of("default");
        }
        return Arrays.asList(profiles);
    }

    private RuntimeSnapshot runtimeSnapshot() {
        Runtime runtime = Runtime.getRuntime();
        long usedBytes = runtime.totalMemory() - runtime.freeMemory();
        return new RuntimeSnapshot(
                ManagementFactory.getRuntimeMXBean().getUptime(),
                runtime.availableProcessors(),
                bytesToMb(usedBytes),
                bytesToMb(runtime.maxMemory())
        );
    }

    private RevenueCatSnapshot revenueCatSnapshot() {
        return new RevenueCatSnapshot(
                subscriptionProviderEventRepository.countByReceivedAtAfter(LocalDateTime.now().minus(Duration.ofHours(24))),
                subscriptionProviderEventRepository.countByStatus(SubscriptionProviderEventStatus.FAILED)
        );
    }

    private SubscriptionSnapshot subscriptionSnapshot() {
        long active = subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE)
                + subscriptionRepository.countByStatus(SubscriptionStatus.TRIALING);
        return new SubscriptionSnapshot(active, subscriptionRepository.countActiveSubscriptionsWithExhaustedAiQuota());
    }

    private long systemAlertsLast24h() {
        return notificationRepository.countByTypeAndCreatedAtAfter("system_alert", LocalDateTime.now().minus(Duration.ofHours(24)));
    }

    private AiSnapshot aiSnapshot() {
        LocalDateTime since = LocalDateTime.now().minus(Duration.ofHours(24));
        LocalDateTime since7d = LocalDateTime.now().minus(Duration.ofDays(7));
        long requests = aiRequestHistoryRepository.countByCreatedAtAfter(since);
        long failed = aiRequestHistoryRepository.countByStatusAndCreatedAtAfter(AiRequestStatus.FAILED, since);
        long openDrafts = aiRequestHistoryRepository.countByStatusAndCreatedAtAfter(AiRequestStatus.DRAFT_CREATED, since7d);
        long confirmedDrafts = aiRequestHistoryRepository.countByStatusAndCreatedAtAfter(AiRequestStatus.CONFIRMED, since7d);
        long rejectedDrafts = aiRequestHistoryRepository.countByStatusAndCreatedAtAfter(AiRequestStatus.REJECTED, since7d);
        Map<String, Long> rejectionReasons = aiRejectionReasons(since7d);
        long drafts = openDrafts + confirmedDrafts + rejectedDrafts;
        double failureRate = requests == 0 ? 0.0 : Math.round((failed / (double) requests) * 10_000.0) / 10_000.0;
        double confirmationRate = drafts == 0 ? 0.0 : Math.round((confirmedDrafts / (double) drafts) * 10_000.0) / 10_000.0;
        return new AiSnapshot(requests, failed, failureRate, drafts, confirmedDrafts, rejectedDrafts, rejectionReasons, openDrafts, confirmationRate);
    }

    private ProductAnalyticsSnapshot productAnalyticsSnapshot() {
        LocalDateTime since = LocalDateTime.now().minus(Duration.ofHours(24));
        Double averageDuration = productAnalyticsEventRepository.averageDurationMs(ProductAnalyticsEventType.LOG_FLOW_COMPLETED, since);
        return new ProductAnalyticsSnapshot(
                productAnalyticsEventRepository.countByEventTypeAndCreatedAtAfter(ProductAnalyticsEventType.LOG_FLOW_COMPLETED, since),
                averageDuration == null ? null : Math.round(averageDuration),
                productAnalyticsEventRepository.countByEventTypeAndCreatedAtAfter(ProductAnalyticsEventType.QUICK_LOG_SUGGESTION_APPLIED, since),
                productAnalyticsEventRepository.countByEventTypeAndCreatedAtAfter(ProductAnalyticsEventType.SEARCH_STARTED, since)
        );
    }

    private Map<String, Long> aiRejectionReasons(LocalDateTime since) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (AiDraftRejectReason reason : AiDraftRejectReason.values()) {
            long count = aiRequestHistoryRepository.countByRejectionReasonAndRejectedAtAfter(reason, since);
            if (count > 0) {
                result.put(reason.name(), count);
            }
        }
        return result;
    }

    private List<String> warnings(
            DatabaseCheck databaseCheck,
            RuntimeSnapshot runtimeSnapshot,
            RevenueCatSnapshot revenueCatSnapshot,
            SubscriptionSnapshot subscriptionSnapshot,
            AiSnapshot aiSnapshot,
            ProductAnalyticsSnapshot productAnalyticsSnapshot,
            long systemAlertsLast24h
    ) {
        List<String> warnings = new ArrayList<>();
        if (!"UP".equals(databaseCheck.status())) {
            warnings.add("Database connectivity is down.");
        }
        if (databaseCheck.latencyMs() > 500) {
            warnings.add("Database latency is above 500ms.");
        }
        if (runtimeSnapshot.heapMaxMb() > 0 && runtimeSnapshot.heapUsedMb() * 100 / runtimeSnapshot.heapMaxMb() >= 85) {
            warnings.add("JVM heap usage is above 85%.");
        }
        if (revenueCatSnapshot.failedEvents() > 0) {
            warnings.add("RevenueCat has failed provider events that require review.");
        }
        if (subscriptionSnapshot.exhaustedAiQuotaSubscriptions() > 0) {
            warnings.add("Some active subscriptions have exhausted AI quota.");
        }
        if (aiSnapshot.failedRequestsLast24h() > 0) {
            warnings.add("AI draft requests failed in the last 24 hours.");
        }
        if (aiSnapshot.draftsLast7d() >= 10 && aiSnapshot.confirmationRateLast7d() < 0.2) {
            warnings.add("AI draft confirmation rate is below 20% in the last 7 days.");
        }
        if (aiSnapshot.openDraftsLast7d() >= 25) {
            warnings.add("Many AI meal drafts are still open in the last 7 days.");
        }
        if (aiSnapshot.rejectionReasonsLast7d().getOrDefault(AiDraftRejectReason.IRRELEVANT_RESULT.name(), 0L) >= 5) {
            warnings.add("AI draft irrelevant-result rejections are high in the last 7 days.");
        }
        if (productAnalyticsSnapshot.logFlowCompletedLast24h() >= 10
                && productAnalyticsSnapshot.averageLogFlowDurationMsLast24h() != null
                && productAnalyticsSnapshot.averageLogFlowDurationMsLast24h() > 30_000) {
            warnings.add("Average measured food logging duration is above 30 seconds in the last 24 hours.");
        }
        if (systemAlertsLast24h > 0) {
            warnings.add("System alerts were created in the last 24 hours.");
        }
        return warnings;
    }

    private long bytesToMb(long bytes) {
        return bytes / 1024 / 1024;
    }

    private record DatabaseCheck(String status, long latencyMs) {
    }

    private record RuntimeSnapshot(long uptimeMs, int availableProcessors, long heapUsedMb, long heapMaxMb) {
    }

    private record RevenueCatSnapshot(long eventsLast24h, long failedEvents) {
    }

    private record SubscriptionSnapshot(long activeSubscriptions, long exhaustedAiQuotaSubscriptions) {
    }

    private record AiSnapshot(
            long requestsLast24h,
            long failedRequestsLast24h,
            double failureRateLast24h,
            long draftsLast7d,
            long confirmedDraftsLast7d,
            long rejectedDraftsLast7d,
            Map<String, Long> rejectionReasonsLast7d,
            long openDraftsLast7d,
            double confirmationRateLast7d
    ) {
    }

    private record ProductAnalyticsSnapshot(
            long logFlowCompletedLast24h,
            Long averageLogFlowDurationMsLast24h,
            long quickLogSuggestionAppliedLast24h,
            long searchStartedLast24h
    ) {
    }
}

package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdminSystemHealthDto;
import com.grun.calorietracker.enums.SubscriptionProviderEventStatus;
import com.grun.calorietracker.enums.SubscriptionStatus;
import com.grun.calorietracker.repository.SubscriptionProviderEventRepository;
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
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminSystemHealthServiceImpl implements AdminSystemHealthService {

    private final DataSource dataSource;
    private final Environment environment;
    private final SubscriptionProviderEventRepository subscriptionProviderEventRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Override
    public AdminSystemHealthDto getHealth() {
        DatabaseCheck databaseCheck = checkDatabase();
        RuntimeSnapshot runtimeSnapshot = runtimeSnapshot();
        RevenueCatSnapshot revenueCatSnapshot = revenueCatSnapshot();
        SubscriptionSnapshot subscriptionSnapshot = subscriptionSnapshot();
        List<String> warnings = warnings(databaseCheck, runtimeSnapshot, revenueCatSnapshot, subscriptionSnapshot);
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

    private List<String> warnings(
            DatabaseCheck databaseCheck,
            RuntimeSnapshot runtimeSnapshot,
            RevenueCatSnapshot revenueCatSnapshot,
            SubscriptionSnapshot subscriptionSnapshot
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
}

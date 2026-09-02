package com.grun.calorietracker.config;

import com.grun.calorietracker.enums.NotificationReleaseStage;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

@Data
@ConfigurationProperties(prefix = "grun.notifications.delivery")
public class NotificationDeliveryProperties {
    /** Independent kill switch. A domain event or admin definition cannot override it. */
    private boolean enabled = false;
    /** Deployment-owned rollout stage. Admin policy cannot advance this value. */
    private NotificationReleaseStage stage = NotificationReleaseStage.OFF;
    /** Named internal accounts eligible during TEST_ACCOUNTS and later stages. */
    private Set<Long> testUserIds = new LinkedHashSet<>();
    /** Named pilot accounts, in addition to test accounts, eligible during PILOT. */
    private Set<Long> pilotUserIds = new LinkedHashSet<>();
    /** Stable user-id cohort percentage used only in LIVE. Zero is fail-closed. */
    private int livePercentage = 0;
    private int outboxBatchSize = 100;
    private Duration lease = Duration.ofMinutes(2);
    private Duration baseRetryDelay = Duration.ofMinutes(2);
    private Duration defaultTtl = Duration.ofHours(24);
    private int maxAttempts = 3;
}

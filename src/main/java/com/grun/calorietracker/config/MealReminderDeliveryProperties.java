package com.grun.calorietracker.config;

import com.grun.calorietracker.service.reminder.MealReminderContract;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

@Data
@ConfigurationProperties(prefix = "grun.meal-reminders")
public class MealReminderDeliveryProperties {
    /** Independent deployment kill switch. Admin policy cannot override this. */
    private boolean deliveryEnabled = false;
    private MealReminderContract.Mode mode = MealReminderContract.Mode.OFF;
    private String policyVersion = "meal-reminder-policy-v1";
    private boolean kcalEnabled = true;
    private int candidateBatchSize = 100;
    private int outboxBatchSize = 100;
    private Duration scheduleLease = Duration.ofMinutes(2);
    private Duration outboxLease = Duration.ofMinutes(2);
    private Duration baseRetryDelay = Duration.ofMinutes(2);
    private int maxAttempts = 3;
    private Duration deterministicJitter = Duration.ofSeconds(45);
    private Set<Long> pilotUserIds = new HashSet<>();
}

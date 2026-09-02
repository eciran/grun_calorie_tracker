package com.grun.calorietracker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "grun.notifications.producer-migration")
public class NotificationProducerMigrationProperties {
    /** Keep false until the shared dispatcher and governance gates are enabled for this producer. */
    private boolean waterEnabled = false;
    private boolean stepEnabled = false;
    private boolean basicFastingEnabled = false;
}

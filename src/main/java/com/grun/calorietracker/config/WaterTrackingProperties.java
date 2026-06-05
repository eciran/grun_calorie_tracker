package com.grun.calorietracker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "grun.water")
public class WaterTrackingProperties {
    private int defaultDailyTargetMl = 2500;
    private Reminders reminders = new Reminders();

    @Data
    public static class Reminders {
        private boolean enabled = true;
        private long scanIntervalMs = 300000;
    }
}

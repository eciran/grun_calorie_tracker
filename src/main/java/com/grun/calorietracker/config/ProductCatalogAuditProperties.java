package com.grun.calorietracker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalTime;

@Data
@Component
@org.springframework.validation.annotation.Validated
@ConfigurationProperties(prefix = "grun.product-catalog.audit")
public class ProductCatalogAuditProperties {
    private boolean enabled = false;
    @jakarta.validation.constraints.Min(2)
    @jakarta.validation.constraints.Max(1000)
    private int batchSize = 250;
    private Duration timeBudget = Duration.ofSeconds(15);
    private Duration lease = Duration.ofMinutes(2);
    @jakarta.validation.constraints.Min(1)
    @jakarta.validation.constraints.Max(5)
    private int maxAttempts = 3;
    private Duration retryDelay = Duration.ofMinutes(2);
    private Duration repeatInterval = Duration.ofDays(1);
    private String zone = "Europe/Dublin";
    private LocalTime downtimeStart = LocalTime.of(1, 30);
    private LocalTime downtimeEnd = LocalTime.of(5, 0);

    @jakarta.validation.constraints.AssertTrue(message = "Audit durations must be positive; time budget <=30s, lease 60-600s, retry <=1h")
    public boolean isDurationConfigurationValid() {
        return timeBudget != null && timeBudget.toSeconds() >= 1 && timeBudget.toSeconds() <= 30
                && lease != null && lease.toSeconds() >= 60 && lease.toSeconds() <= 600
                && retryDelay != null && retryDelay.toSeconds() >= 1 && retryDelay.toSeconds() <= 3600
                && repeatInterval != null && repeatInterval.toSeconds() >= 60;
    }

    @jakarta.validation.constraints.AssertTrue(message = "Audit timezone and downtime bounds must be valid")
    public boolean isScheduleConfigurationValid() {
        try { java.time.ZoneId.of(zone); return downtimeStart != null && downtimeEnd != null; }
        catch (RuntimeException exception) { return false; }
    }
}

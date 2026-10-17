package com.grun.calorietracker.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Data
@Validated
@ConfigurationProperties("grun.product-cache")
public class FoodProductCacheProperties {
    private boolean enabled = true;
    @Min(1024) @Max(134_217_728)
    private long maxPayloadBudgetBytes = 67_108_864;
    @Valid @NotNull
    private Policy search = new Policy(Duration.ofSeconds(30), 256, 65_536);
    @Valid @NotNull
    private Policy detail = new Policy(Duration.ofMinutes(5), 512, 16_384);
    @NotNull private Duration negativeTtl = Duration.ofSeconds(5);
    @Valid @NotNull private Coalescing coalescing = new Coalescing();

    @AssertTrue(message = "Product negative TTL must be between zero and 30 seconds")
    public boolean isNegativeTtlBounded() {
        return negativeTtl != null && !negativeTtl.isNegative()
                && negativeTtl.compareTo(Duration.ofSeconds(30)) <= 0;
    }

    @Data
    public static class Coalescing {
        private boolean enabled = true;
        @Min(1) @Max(1024) private int maxConcurrentLoads = 128;
        @NotNull private Duration waitTimeout = Duration.ofSeconds(1);
        @AssertTrue(message = "Product coalescing wait must be between 10ms and 5s")
        public boolean isWaitBounded() {
            return waitTimeout != null && waitTimeout.compareTo(Duration.ofMillis(10)) >= 0
                    && waitTimeout.compareTo(Duration.ofSeconds(5)) <= 0;
        }
    }

    @AssertTrue(message = "Product cache entry limits exceed the configured payload budget")
    public boolean isPayloadBudgetBounded() {
        return search != null && detail != null
                && (long) search.maxEntries * search.maxValueBytes
                   + 2L * detail.maxEntries * detail.maxValueBytes <= maxPayloadBudgetBytes;
    }

    @Data
    public static class Policy {
        @NotNull private Duration ttl;
        @Min(1) @Max(10_000) private int maxEntries;
        @Min(1024) @Max(1_048_576) private int maxValueBytes;
        public Policy() { }
        public Policy(Duration ttl, int maxEntries, int maxValueBytes) {
            this.ttl = ttl; this.maxEntries = maxEntries; this.maxValueBytes = maxValueBytes;
        }
        @AssertTrue(message = "Product cache TTL must be between 1 second and 15 minutes")
        public boolean isTtlBounded() {
            return ttl != null && ttl.compareTo(Duration.ofSeconds(1)) >= 0
                    && ttl.compareTo(Duration.ofMinutes(15)) <= 0;
        }
    }
}

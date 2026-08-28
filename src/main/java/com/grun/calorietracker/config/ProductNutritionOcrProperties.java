package com.grun.calorietracker.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "grun.product-ocr")
public class ProductNutritionOcrProperties {
    private boolean cloudEnabled = false;
    @NotBlank
    private String geminiModel = "gemini-2.5-flash";
    @DecimalMin("0.0") @DecimalMax("1.0")
    private double fallbackThreshold = 0.92;
    @Pattern(regexp = "LOW|MEDIUM|HIGH")
    private String mediaResolution = "HIGH";
    @Min(1) @Max(2)
    private int maxAttempts = 1;
    private boolean targetedSecondPassEnabled = false;
    @Pattern(regexp = "OFF|INTERNAL|SMALL_PILOT|WIDE_PILOT")
    private String rolloutStage = "OFF";
    @Min(0) @Max(100)
    private int rolloutPercentage = 0;
    private Set<Long> internalUserIds = Set.of();
    private Duration cacheTtl = Duration.ofMinutes(15);
    @Min(100) @Max(20_000)
    private int cacheMaxEntries = 5_000;
    @DecimalMin("0.0")
    private double estimatedRequestCostUsd = 0;
}

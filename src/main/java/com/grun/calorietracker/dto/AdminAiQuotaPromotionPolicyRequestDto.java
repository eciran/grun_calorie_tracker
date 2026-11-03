package com.grun.calorietracker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminAiQuotaPromotionPolicyRequestDto(
        @NotNull Long version,
        @NotNull Boolean enabled,
        @NotNull @Min(1) @Max(720) Integer minimumIntervalHours,
        @NotNull @Min(1) @Max(24) Integer maxImpressions24h,
        @NotNull @Min(1) @Max(720) Integer dismissCooldownHours,
        @NotNull @Min(0) @Max(100) Integer rolloutPercentage,
        @NotBlank @Size(max = 500) String changeReason) { }

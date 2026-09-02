package com.grun.calorietracker.dto;

import jakarta.validation.constraints.*;

public record AdminFreePromotionPolicyRequestDto(@NotNull Long version, @NotNull Boolean enabled,
        @NotNull @Min(1) @Max(720) Integer minimumIntervalHours,
        @NotNull @Min(1) @Max(24) Integer maxImpressions24h,
        @NotNull @Min(1) @Max(720) Integer dismissCooldownHours,
        @NotNull @Min(1) @Max(100) Integer minimumSessionNumber,
        @NotNull @Min(0) @Max(100) Integer rolloutPercentage,
        @NotBlank @Size(max = 500) String changeReason) { }

package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Manual step entry when a connected provider is unavailable or needs correction.")
public class StepManualLogRequestDto {
    @NotNull(message = "{validation.steps.steps.required}")
    @Positive(message = "{validation.steps.steps.positive}")
    @Max(value = 100000, message = "{validation.steps.steps.max}")
    @Schema(description = "Step count to add as a manual metric. Maximum 100000 per manual entry.", example = "1200", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer steps;

    @PositiveOrZero(message = "{validation.steps.distance.positive-or-zero}")
    @DecimalMax(value = "100000.0", message = "{validation.steps.distance.max}")
    @Schema(description = "Optional distance in meters.", example = "850.0")
    private Double distanceMeters;

    @PositiveOrZero(message = "{validation.steps.calories.positive-or-zero}")
    @DecimalMax(value = "10000.0", message = "{validation.steps.calories.max}")
    @Schema(description = "Optional calories burned estimate.", example = "45.0")
    private Double caloriesBurned;

    @NotNull(message = "{validation.steps.recorded-at.required}")
    @Schema(description = "Local datetime for the manual step entry.", example = "2026-06-12T18:30:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime recordedAt;
}
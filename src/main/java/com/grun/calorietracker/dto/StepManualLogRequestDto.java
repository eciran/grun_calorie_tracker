package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Manual step entry when a connected provider is unavailable or needs correction.")
public class StepManualLogRequestDto {
    @NotNull(message = "{validation.steps.steps.required}")
    @Positive(message = "{validation.steps.steps.positive}")
    @Schema(description = "Step count to add as a manual metric.", example = "1200", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer steps;

    @Schema(description = "Optional distance in meters.", example = "850.0")
    private Double distanceMeters;

    @Schema(description = "Optional calories burned estimate.", example = "45.0")
    private Double caloriesBurned;

    @NotNull(message = "{validation.steps.recorded-at.required}")
    @Schema(description = "Local datetime for the manual step entry.", example = "2026-06-12T18:30:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime recordedAt;
}

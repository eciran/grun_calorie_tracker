package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Normalized health metric payload sent by the mobile app after local provider permission is granted.")
public class HealthMetricSyncRequestDto {

    @Size(max = 255)
    @Schema(description = "Provider metric id used for idempotent sync. Strongly recommended.", example = "apple-steps-2026-05-26")
    private String externalId;

    @PositiveOrZero
    @Schema(description = "Step count for the recorded interval.", example = "8400")
    private Integer steps;

    @PositiveOrZero
    @Schema(description = "Heart rate sample in bpm.", example = "72")
    private Integer heartRate;

    @PositiveOrZero
    @Schema(description = "Sleep duration in hours.", example = "7.5")
    private Double sleepHours;

    @PositiveOrZero
    @Schema(description = "Active calories burned for the interval.", example = "420.5")
    private Double caloriesBurned;

    @PositiveOrZero
    @Schema(description = "Distance in meters for the interval.", example = "5200.0")
    private Double distanceMeters;

    @NotNull
    @Schema(description = "Metric timestamp in device-local normalized time.", example = "2026-05-26T08:30:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime recordedAt;
}

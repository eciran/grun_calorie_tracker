package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.ExerciseLogMeasurementType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Provider workout payload normalized by the mobile app before importing as an exercise log.")
public class HealthWorkoutSyncRequestDto {

    @NotBlank(message = "Provider workout id is required.")
    @Size(max = 255)
    @Schema(description = "Stable workout id from the provider for idempotency.", example = "apple-workout-123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String externalId;

    @NotBlank(message = "Provider activity type is required.")
    @Size(max = 120)
    @Schema(description = "Provider-specific workout/activity type.", example = "HKWorkoutActivityTypeRunning", requiredMode = Schema.RequiredMode.REQUIRED)
    private String providerActivityType;

    @Schema(description = "Optional explicit measurement type. Defaults from the mapped exercise when omitted.", example = "DISTANCE")
    private ExerciseLogMeasurementType measurementType;

    @Positive
    @Schema(description = "Workout duration in minutes.", example = "42")
    private Integer durationMinutes;

    @Positive
    @Schema(description = "Repetition count when provider supports rep-based workouts.", example = "30")
    private Integer reps;

    @Positive
    @Schema(description = "Set count when provider supports set-based workouts.", example = "3")
    private Integer setCount;

    @Positive
    @Schema(description = "External load in kilograms.", example = "40.0")
    private Double weightKg;

    @Positive
    @Schema(description = "Distance in kilometers.", example = "5.2")
    private Double distanceKm;

    @NotNull(message = "Calories burned is required.")
    @PositiveOrZero
    @Schema(description = "Active calories burned reported by provider. If zero, mobile should prefer provider energy estimate when available.", example = "420.0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double caloriesBurned;

    @NotNull(message = "Workout start time is required.")
    @Schema(description = "Workout start time in device-local normalized time.", example = "2026-07-02T18:30:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime startedAt;

    @Schema(description = "Optional provider/device metadata for audit/debugging.", example = "{\"source\":\"Apple Watch\"}")
    private String extraData;
}

package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.ExerciseLogMeasurementType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Exercise diary entry for a user.")
public class ExerciseLogsDto {

    @Schema(description = "Exercise log id.", example = "1")
    private Long id;

    @NotNull(message = "{validation.exercise-log.exercise-item-id.required}")
    @Positive(message = "{validation.exercise-log.exercise-item-id.positive}")
    @Schema(description = "Linked exercise item id.", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long exerciseItemId;

    @Schema(description = "Exercise display name captured for the log.", example = "Running")
    private String exerciseItemName;

    @Positive(message = "{validation.exercise-log.duration-minutes.positive}")
    @Schema(description = "Exercise duration in minutes. Required for duration-based activities; optional for rep/set based activities.", example = "45")
    private Integer durationMinutes;

    @Schema(description = "How this exercise log is primarily measured.", example = "SETS_REPS", allowableValues = {"DURATION", "REPS", "SETS_REPS", "WEIGHT_REPS", "DISTANCE", "MIXED"})
    private ExerciseLogMeasurementType measurementType;

    @Positive(message = "{validation.exercise-log.set-count.positive}")
    @Schema(description = "Number of sets for strength/bodyweight exercises.", example = "4")
    private Integer setCount;

    @Positive(message = "{validation.exercise-log.reps.positive}")
    @Schema(description = "Total repetitions or repetitions per set, depending on mobile UI context.", example = "20")
    private Integer reps;

    @Positive(message = "{validation.exercise-log.weight-kg.positive}")
    @Schema(description = "External load in kilograms for weighted exercises.", example = "60.0")
    private Double weightKg;

    @Positive(message = "{validation.exercise-log.distance-km.positive}")
    @Schema(description = "Distance in kilometers for cardio activities.", example = "5.2")
    private Double distanceKm;

    @NotNull(message = "{validation.exercise-log.calories-burned.required}")
    @Positive(message = "{validation.exercise-log.calories-burned.positive}")
    @Schema(description = "Estimated calories burned.", example = "420.0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double caloriesBurned;

    @NotNull(message = "{validation.exercise-log.log-date.required}")
    @Schema(description = "Date and time when the exercise was logged.", example = "2026-05-11T18:30:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime logDate;

    @Schema(description = "Source system for externally imported exercise logs.", example = "MANUAL")
    private String source;

    @Schema(description = "External source id used for duplicate protection.", example = "apple-health-123")
    private String externalId;

    @Schema(description = "Optional raw metadata from the external source.", example = "{\"distanceKm\":5.2}")
    private String extraData;

}

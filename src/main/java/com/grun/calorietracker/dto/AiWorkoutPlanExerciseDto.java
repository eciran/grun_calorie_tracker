package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.ExerciseLogMeasurementType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
@Schema(description = "AI workout plan exercise item. It should reference a verified exercise catalog item when possible.")
public class AiWorkoutPlanExerciseDto {
    @Schema(description = "Linked exercise catalog item id.", example = "12")
    private Long exerciseItemId;

    @NotBlank
    @Schema(description = "Exercise display name.", example = "Push-Up")
    private String name;

    @Schema(description = "Measurement style expected by the exercise.", example = "SETS_REPS")
    private ExerciseLogMeasurementType measurementType;

    @Positive
    private Integer setCount;

    @Positive
    private Integer reps;

    @Positive
    private Integer durationMinutes;

    @Positive
    private Double distanceKm;

    @Positive
    private Double weightKg;

    @Schema(description = "Rest period after this exercise.", example = "60 sec")
    private String rest;

    @Schema(description = "Why this exercise was included.")
    private String rationale;

    @Schema(description = "Safety note for this movement.")
    private String safetyNote;

    @Positive
    @Schema(description = "Rest period after this exercise in seconds.", example = "60")
    private Integer restSeconds;

    @Schema(description = "Exercise intensity cue.", example = "MODERATE")
    private String intensity;

    @Schema(description = "Simple progression guidance for the user.")
    private String progressionNote;

    @Schema(description = "Primary target muscle group for display/filtering.", example = "CHEST")
    private String targetMuscleGroup;

    @Schema(description = "Equipment expected for this exercise.", example = "DUMBBELLS")
    private String equipmentUsed;

    @Schema(description = "Whether this exercise needs user/admin review because it was not confidently matched to the catalog.", example = "true")
    private Boolean reviewRequired;
}

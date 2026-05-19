package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.ExerciseDifficulty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Exercise catalog item used for exercise logs.")
public class ExerciseItemDto {

    @Schema(description = "Exercise item id.", example = "1")
    private Long id;

    @NotBlank(message = "{validation.exercise-item.name.required}")
    @Schema(description = "Exercise display name.", example = "Running", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "{validation.exercise-item.met-code.required}")
    @Schema(description = "MET code or internal activity code.", example = "RUNNING_GENERAL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String metCode;

    @NotNull(message = "{validation.exercise-item.calories-per-minute.required}")
    @Positive(message = "{validation.exercise-item.calories-per-minute.positive}")
    @Schema(description = "Estimated calories burned per minute.", example = "10.5", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double caloriesPerMinute;

    @Schema(description = "Exercise description.", example = "General outdoor running activity.")
    private String description;

    @Schema(description = "Optional icon URL for mobile display.", example = "https://example.com/icons/running.png")
    private String iconUrl;

    @Schema(description = "Primary target muscle group.", example = "Quadriceps")
    private String primaryMuscleGroup;

    @Schema(description = "Comma-separated secondary muscle groups.", example = "Glutes, Hamstrings, Core")
    private String secondaryMuscleGroups;

    @Schema(description = "Equipment needed for this exercise.", example = "Dumbbell")
    private String equipment;

    @Schema(description = "Exercise difficulty level.", example = "BEGINNER")
    private ExerciseDifficulty difficulty;

    @Schema(description = "Step-by-step execution instructions.", example = "Stand tall, brace your core, lower under control, then return to standing.")
    private String instructions;

    @Schema(description = "Safety notes and form warnings.", example = "Keep knees aligned with toes and avoid rounding the lower back.")
    private String safetyNotes;

    @Schema(description = "Thumbnail image URL for catalog display.", example = "https://cdn.grun.app/exercises/squat-thumb.jpg")
    private String thumbnailUrl;

    @Schema(description = "Video URL for exercise preview.", example = "https://cdn.grun.app/exercises/squat.mp4")
    private String videoUrl;

    @Schema(description = "Animation or GIF URL for quick movement preview.", example = "https://cdn.grun.app/exercises/squat.gif")
    private String animationUrl;

    @Schema(description = "Whether this exercise can be used by future AI workout planner recommendations.", example = "true")
    private Boolean aiEligible;

    @Schema(description = "Whether this exercise is active in the catalog.", example = "true")
    private Boolean active;
}

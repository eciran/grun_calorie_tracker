package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Controlled AI workout plan request. The response is a draft and must be confirmed before becoming an active plan.")
public class AiWorkoutPlanDraftRequestDto {
    @Size(max = 120)
    @Schema(description = "User's training goal.", example = "Lose fat while keeping muscle")
    private String goal;

    @Size(max = 40)
    @Schema(description = "Training level.", example = "BEGINNER")
    private String level;

    @Min(1)
    @Max(7)
    @Schema(description = "Available training days per week.", example = "4")
    private Integer daysPerWeek;

    @Min(10)
    @Max(180)
    @Schema(description = "Available minutes per session.", example = "45")
    private Integer minutesPerSession;

    @Size(max = 12)
    @Schema(description = "Available equipment.", example = "[\"DUMBBELLS\", \"BENCH\"]")
    private List<@Size(max = 60) String> equipment;

    @Size(max = 12)
    @Schema(description = "Muscle groups or movements to prioritize.")
    private List<@Size(max = 60) String> focusAreas;

    @Size(max = 12)
    @Schema(description = "Exercise ids the user wants to avoid.")
    private List<Long> excludedExerciseItemIds;

    @Size(max = 500)
    @Schema(description = "Relevant injury or limitation notes. Do not send medical records.")
    private String limitationNotes;

    @Size(max = 12)
    @Schema(description = "Preferred output language.", example = "en")
    private String language;

    @Schema(description = "Backend-built user profile context for provider calls. Clients should not set this field.", accessMode = Schema.AccessMode.READ_ONLY)
    private Object userContext;

    @Schema(description = "Backend-built AI-eligible exercise catalog context for provider calls. Clients should not set this field.", accessMode = Schema.AccessMode.READ_ONLY)
    private Object exerciseCatalogContext;
}

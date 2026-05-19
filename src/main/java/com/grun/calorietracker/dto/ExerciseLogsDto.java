package com.grun.calorietracker.dto;

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

    @NotNull(message = "{validation.exercise-log.duration-minutes.required}")
    @Positive(message = "{validation.exercise-log.duration-minutes.positive}")
    @Schema(description = "Exercise duration in minutes.", example = "45", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer durationMinutes;

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

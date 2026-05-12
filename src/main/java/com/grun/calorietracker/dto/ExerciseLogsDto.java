package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Exercise diary entry for a user.")
public class ExerciseLogsDto {

    @Schema(description = "Exercise log id.", example = "1")
    private Long id;

    @Schema(description = "Linked exercise item id.", example = "3")
    private Long exerciseItemId;

    @Schema(description = "Exercise display name captured for the log.", example = "Running")
    private String exerciseItemName;

    @Schema(description = "Exercise duration in minutes.", example = "45")
    private Integer durationMinutes;

    @Schema(description = "Estimated calories burned.", example = "420.0")
    private Double caloriesBurned;

    @Schema(description = "Date and time when the exercise was logged.", example = "2026-05-11T18:30:00")
    private LocalDateTime logDate;

    @Schema(description = "Source system for externally imported exercise logs.", example = "MANUAL")
    private String source;

    @Schema(description = "External source id used for duplicate protection.", example = "apple-health-123")
    private String externalId;

    @Schema(description = "Optional raw metadata from the external source.", example = "{\"distanceKm\":5.2}")
    private String extraData;

}

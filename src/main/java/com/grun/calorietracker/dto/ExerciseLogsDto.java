package com.grun.calorietracker.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ExerciseLogsDto {

    private Long id;

    @NotNull(message = "Exercise item id is required")
    private Long exerciseItemId;

    private String exerciseItemName;

    @NotNull(message = "Duration is required")
    @DecimalMin(value = "1", inclusive = true, message = "Duration must be greater than 0")
    private Integer durationMinutes;

    @NotNull(message = "Calories burned is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Calories burned cannot be negative")
    private Double caloriesBurned;

    private LocalDateTime logDate;

    private String source;

    private String externalId;

    private String extraData;
}
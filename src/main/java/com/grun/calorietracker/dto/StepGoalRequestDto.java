package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalTime;

@Data
@Schema(description = "Request to update daily step goal settings.")
public class StepGoalRequestDto {
    @Min(value = 1000, message = "{validation.steps.target.min}")
    @Max(value = 50000, message = "{validation.steps.target.max}")
    @Schema(description = "Daily target steps.", example = "10000")
    private Integer targetSteps;

    @Schema(description = "Whether step reminder notifications are enabled.", example = "true")
    private Boolean reminderEnabled;

    @Schema(description = "Local time when the system should check whether a step reminder is needed.", example = "20:00")
    private LocalTime reminderTime;

    @Min(value = 1, message = "{validation.steps.reminderThreshold.min}")
    @Max(value = 99, message = "{validation.steps.reminderThreshold.max}")
    @Schema(description = "Reminder is sent only when progress is below this percent at reminder time.", example = "70")
    private Integer reminderThresholdPercent;
}

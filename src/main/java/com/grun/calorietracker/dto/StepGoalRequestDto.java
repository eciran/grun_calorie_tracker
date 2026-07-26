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

    @Schema(description = "Legacy single reminder time retained for backward compatibility.", example = "20:00")
    private LocalTime reminderTime;

    @Min(value = 30, message = "{validation.steps.reminderInterval.min}")
    @Max(value = 180, message = "{validation.steps.reminderInterval.max}")
    @Schema(description = "Minutes between step reminders during active hours.", example = "120")
    private Integer reminderIntervalMinutes;

    @Schema(description = "Local time when recurring step reminders become active.", example = "09:00")
    private LocalTime reminderStartTime;

    @Schema(description = "Local time when recurring step reminders stop.", example = "21:00")
    private LocalTime reminderEndTime;

    @Min(value = 1, message = "{validation.steps.reminderThreshold.min}")
    @Max(value = 99, message = "{validation.steps.reminderThreshold.max}")
    @Schema(description = "Legacy reminder threshold retained for backward compatibility.", example = "70")
    private Integer reminderThresholdPercent;
}

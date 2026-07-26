package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Schema(description = "User step goal settings.")
public class StepGoalDto {
    @Schema(description = "Daily target steps.", example = "10000")
    private Integer targetSteps;

    @Schema(description = "Whether step reminder notifications are enabled.", example = "true")
    private Boolean reminderEnabled;

    @Schema(description = "Legacy single reminder time retained for backward compatibility.", example = "20:00")
    private LocalTime reminderTime;

    @Schema(description = "Minutes between step reminders during active hours.", example = "120")
    private Integer reminderIntervalMinutes;

    @Schema(description = "Local time when recurring step reminders become active.", example = "09:00")
    private LocalTime reminderStartTime;

    @Schema(description = "Local time when recurring step reminders stop.", example = "21:00")
    private LocalTime reminderEndTime;

    @Schema(description = "Legacy reminder threshold retained for backward compatibility.", example = "70")
    private Integer reminderThresholdPercent;

    @Schema(description = "Last step reminder notification creation time in user's local time.")
    private LocalDateTime lastReminderAt;
}

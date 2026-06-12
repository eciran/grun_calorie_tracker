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

    @Schema(description = "Local time when the system should check whether a step reminder is needed.", example = "20:00")
    private LocalTime reminderTime;

    @Schema(description = "Reminder is sent only when progress is below this percent at reminder time.", example = "70")
    private Integer reminderThresholdPercent;

    @Schema(description = "Last step reminder notification creation time in user's local time.")
    private LocalDateTime lastReminderAt;
}

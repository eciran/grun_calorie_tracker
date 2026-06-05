package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Schema(description = "Water reminder preference for the authenticated user.")
public class WaterReminderSettingsDto {
    @Schema(description = "Settings id.", example = "1")
    private Long id;

    @Schema(description = "Whether water reminders are enabled.", example = "true")
    private Boolean enabled;

    @Schema(description = "Reminder interval in minutes.", example = "120")
    private Integer intervalMinutes;

    @Schema(description = "Daily reminder window start time.", example = "09:00:00")
    private LocalTime startTime;

    @Schema(description = "Daily reminder window end time.", example = "21:00:00")
    private LocalTime endTime;

    @Schema(description = "Last time a reminder notification was created.", example = "2026-06-05T12:00:00")
    private LocalDateTime lastReminderAt;

    @Schema(description = "Server update time.", example = "2026-06-05T12:00:00")
    private LocalDateTime updatedAt;
}

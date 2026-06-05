package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

@Data
@Schema(description = "Water reminder preference update request.")
public class WaterReminderSettingsRequestDto {

    @NotNull(message = "{validation.water-reminder.enabled.required}")
    @Schema(description = "Whether water reminders are enabled.", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean enabled;

    @NotNull(message = "{validation.water-reminder.interval.required}")
    @Min(value = 30, message = "{validation.water-reminder.interval.min}")
    @Max(value = 240, message = "{validation.water-reminder.interval.max}")
    @Schema(description = "Reminder interval in minutes. Allowed range is 30-240.", example = "120", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer intervalMinutes;

    @NotNull(message = "{validation.water-reminder.start-time.required}")
    @Schema(description = "Daily reminder window start time in server/local app time.", example = "09:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalTime startTime;

    @NotNull(message = "{validation.water-reminder.end-time.required}")
    @Schema(description = "Daily reminder window end time in server/local app time.", example = "21:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalTime endTime;
}

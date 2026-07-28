package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "User-specific advanced fasting reminder preferences.")
public class AdvancedFastingReminderSettingsDto {
    @NotNull private Boolean enabled;
    @NotNull private Boolean preStartEnabled;
    @NotNull private Boolean startEnabled;
    @NotNull private Boolean nearingCompletionEnabled;
    @NotNull private Boolean completionEnabled;
    @NotNull private Boolean missedPlanEnabled;
    @NotNull @Min(5) @Max(180) private Integer preStartMinutes;
    @NotNull @Min(5) @Max(120) private Integer nearingCompletionMinutes;
}
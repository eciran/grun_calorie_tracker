package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FastingPlanType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalTime;

@Data
@Schema(description = "Request to create or update the authenticated user's fasting plan.")
public class FastingPlanRequestDto {
    @NotNull(message = "{validation.fasting-plan.type.required}")
    @Schema(description = "Selected fasting plan type.", example = "FASTING_16_8", requiredMode = Schema.RequiredMode.REQUIRED)
    private FastingPlanType planType;

    @NotNull(message = "{validation.fasting-plan.fasting-hours.required}")
    @Min(value = 1, message = "{validation.fasting-plan.fasting-hours.min}")
    @Max(value = 48, message = "{validation.fasting-plan.fasting-hours.max}")
    @Schema(description = "Target fasting duration in hours.", example = "16", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer fastingHours;

    @NotNull(message = "{validation.fasting-plan.eating-window-hours.required}")
    @Min(value = 1, message = "{validation.fasting-plan.eating-window-hours.min}")
    @Max(value = 23, message = "{validation.fasting-plan.eating-window-hours.max}")
    @Schema(description = "Eating window duration in hours.", example = "8", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer eatingWindowHours;

    @NotNull(message = "{validation.fasting-plan.start-time.required}")
    @Schema(description = "Preferred daily fasting start time.", example = "20:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalTime preferredStartTime;

    @NotNull(message = "{validation.fasting-plan.active.required}")
    @Schema(description = "Whether this plan is active.", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean active;

    @NotNull(message = "{validation.fasting-plan.reminder-enabled.required}")
    @Schema(description = "Whether fasting reminders are enabled.", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean reminderEnabled;

    @Size(max = 500, message = "{validation.fasting.note.size}")
    @Schema(description = "Optional user note for this fasting plan.", example = "Weekday fasting plan.")
    private String note;
}

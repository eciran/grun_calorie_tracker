package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FastingPlanType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Schema(description = "User fasting plan preference.")
public class FastingPlanDto {
    @Schema(description = "Plan id.", example = "1")
    private Long id;
    @Schema(description = "Selected fasting plan type.", example = "FASTING_16_8")
    private FastingPlanType planType;
    @Schema(description = "Target fasting duration in hours.", example = "16")
    private Integer fastingHours;
    @Schema(description = "Eating window duration in hours.", example = "8")
    private Integer eatingWindowHours;
    @Schema(description = "Preferred daily fasting start time.", example = "20:00:00")
    private LocalTime preferredStartTime;
    @Schema(description = "Whether this plan is active.", example = "true")
    private Boolean active;
    @Schema(description = "Whether fasting reminders are enabled for future notification delivery.", example = "true")
    private Boolean reminderEnabled;
    @Schema(description = "Optional user note for the plan.", example = "Weekday fasting plan.")
    private String note;
    @Schema(description = "Last update timestamp.", example = "2026-06-05T18:30:00")
    private LocalDateTime updatedAt;
}

package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Request to update the authenticated user's daily hydration goal.")
public class WaterGoalRequestDto {
    @NotNull(message = "{validation.water-goal.target.required}")
    @Min(value = 500, message = "{validation.water-goal.target.min}")
    @Max(value = 10000, message = "{validation.water-goal.target.max}")
    @Schema(description = "Daily water target in milliliters. Allowed range is 500-10000.", example = "2500", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer targetMl;
}

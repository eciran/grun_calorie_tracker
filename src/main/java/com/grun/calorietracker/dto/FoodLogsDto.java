package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Food diary entry for a user.")
public class FoodLogsDto {
    @Schema(description = "Food log id.", example = "1")
    private Long id;

    @Schema(description = "Linked food product id.", example = "12")
    private Long foodItemId;

    @Schema(description = "Food display name captured for the log.", example = "Greek yogurt")
    private String foodName;

    @Schema(description = "Portion multiplier or portion amount used by the service.", example = "1.0")
    private Double portionSize;

    @Schema(description = "Meal category.", example = "BREAKFAST")
    private String mealType;

    @Schema(description = "Date and time when the food was logged.", example = "2026-05-11T08:30:00")
    private LocalDateTime logDate;
}

package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodPortionUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Food diary entry for a user.")
public class FoodLogsDto {
    @Schema(description = "Food log id.", example = "1")
    private Long id;

    @NotNull(message = "{validation.food-log.food-item-id.required}")
    @Positive(message = "{validation.food-log.food-item-id.positive}")
    @Schema(description = "Linked food product id.", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long foodItemId;

    @Schema(description = "Food display name captured for the log.", example = "Greek yogurt")
    private String foodName;

    @NotNull(message = "{validation.food-log.portion-size.required}")
    @Positive(message = "{validation.food-log.portion-size.positive}")
    @Schema(description = "Portion multiplier or portion amount used by the service.", example = "1.0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double portionSize;

    @Schema(description = "Unit for portionSize. If omitted, GRAM is used for backward compatibility.", example = "GRAM", allowableValues = {"GRAM", "MILLILITER", "SERVING", "PIECE"})
    private FoodPortionUnit portionUnit;

    @Schema(description = "Portion converted to grams for nutrition calculations.", example = "100.0", accessMode = Schema.AccessMode.READ_ONLY)
    private Double normalizedPortionGrams;

    @Schema(description = "Meal category.", example = "BREAKFAST", allowableValues = {"BREAKFAST", "LUNCH", "DINNER", "SNACK"})
    private String mealType;

    @NotNull(message = "{validation.food-log.log-date.required}")
    @Schema(description = "Date and time when the food was logged.", example = "2026-05-11T08:30:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime logDate;
}

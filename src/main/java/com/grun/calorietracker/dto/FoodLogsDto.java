package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodPortionUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
    @Schema(description = "User-entered amount. For GRAM this is grams, for MILLILITER this is milliliters, and for SERVING/PIECE this is the count multiplied by the product serving size.", example = "150.0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double portionSize;

    @Schema(description = "Unit for portionSize. If omitted, GRAM is used. GRAM and MILLILITER use the entered amount directly for nutrition calculation; SERVING and PIECE multiply by servingSizeGrams.", example = "GRAM", allowableValues = {"GRAM", "MILLILITER", "SERVING", "PIECE"})
    private FoodPortionUnit portionUnit;

    @Schema(description = "Portion converted to grams for nutrition calculations.", example = "100.0", accessMode = Schema.AccessMode.READ_ONLY)
    private Double normalizedPortionGrams;

    @NotBlank(message = "{validation.food-log.meal-type.required}")
    @Pattern(regexp = "(?i)BREAKFAST|LUNCH|DINNER|SNACK", message = "{validation.food-log.meal-type.invalid}")
    @Schema(description = "Meal category.", example = "BREAKFAST", allowableValues = {"BREAKFAST", "LUNCH", "DINNER", "SNACK"}, requiredMode = Schema.RequiredMode.REQUIRED)
    private String mealType;

    @NotNull(message = "{validation.food-log.log-date.required}")
    @Schema(description = "Date and time when the food was logged.", example = "2026-05-11T08:30:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime logDate;
}

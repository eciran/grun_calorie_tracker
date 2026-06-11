package com.grun.calorietracker.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.grun.calorietracker.enums.FoodLogSource;
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
    @JsonAlias("foodId")
    @Schema(description = "Linked food product id. Mobile may send either foodItemId or foodId; responses use foodItemId.", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long foodItemId;

    @Schema(description = "Food display name captured for the log.", example = "Greek yogurt")
    private String foodName;

    @NotNull(message = "{validation.food-log.portion-size.required}")
    @Positive(message = "{validation.food-log.portion-size.positive}")
    @Schema(description = "User-entered amount. For GRAM this is grams, for MILLILITER this is milliliters, and for SERVING/PIECE this is the count multiplied by the product serving size.", example = "150.0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double portionSize;

    @Schema(description = "Unit for portionSize. If omitted, GRAM is used. GRAM and MILLILITER use the entered amount directly for nutrition calculation; SERVING and PIECE multiply by servingSizeGrams.", example = "GRAM", allowableValues = {"GRAM", "MILLILITER", "SERVING", "PIECE"})
    private FoodPortionUnit portionUnit;

    @Schema(description = "Optional product-specific serving option id. When supplied, the log uses this serving option's gram/ml conversion instead of the product's legacy servingSizeGrams.", example = "5")
    private Long servingOptionId;

    @Schema(description = "Display label of the serving option used for this log.", example = "1 slice", accessMode = Schema.AccessMode.READ_ONLY)
    private String servingOptionLabel;

    @Schema(description = "Portion converted to grams for nutrition calculations.", example = "100.0", accessMode = Schema.AccessMode.READ_ONLY)
    private Double normalizedPortionGrams;

    @Schema(description = "Calories captured at log time for the entered portion.", example = "155.0", accessMode = Schema.AccessMode.READ_ONLY)
    private Double snapshotCalories;

    @Schema(description = "Protein captured at log time for the entered portion.", example = "13.0", accessMode = Schema.AccessMode.READ_ONLY)
    private Double snapshotProtein;

    @Schema(description = "Carbohydrates captured at log time for the entered portion.", example = "1.1", accessMode = Schema.AccessMode.READ_ONLY)
    private Double snapshotCarbs;

    @Schema(description = "Fat captured at log time for the entered portion.", example = "11.0", accessMode = Schema.AccessMode.READ_ONLY)
    private Double snapshotFat;

    @Schema(description = "Fiber captured at log time for the entered portion.", example = "4.2", accessMode = Schema.AccessMode.READ_ONLY)
    private Double snapshotFiber;

    @Schema(description = "Sugar captured at log time for the entered portion.", example = "8.0", accessMode = Schema.AccessMode.READ_ONLY)
    private Double snapshotSugar;

    @Schema(description = "Saturated fat captured at log time for the entered portion.", example = "3.1", accessMode = Schema.AccessMode.READ_ONLY)
    private Double snapshotSaturatedFat;

    @Schema(description = "Sodium captured at log time for the entered portion in mg.", example = "210.0", accessMode = Schema.AccessMode.READ_ONLY)
    private Double snapshotSodium;

    @Schema(description = "Potassium captured at log time for the entered portion in mg.", example = "380.0", accessMode = Schema.AccessMode.READ_ONLY)
    private Double snapshotPotassium;

    @Schema(description = "Cholesterol captured at log time for the entered portion in mg.", example = "45.0", accessMode = Schema.AccessMode.READ_ONLY)
    private Double snapshotCholesterol;

    @Schema(description = "Calcium captured at log time for the entered portion in mg.", example = "120.0", accessMode = Schema.AccessMode.READ_ONLY)
    private Double snapshotCalcium;

    @Schema(description = "Iron captured at log time for the entered portion in mg.", example = "2.1", accessMode = Schema.AccessMode.READ_ONLY)
    private Double snapshotIron;

    @Schema(description = "Magnesium captured at log time for the entered portion in mg.", example = "35.0", accessMode = Schema.AccessMode.READ_ONLY)
    private Double snapshotMagnesium;

    @Schema(description = "Zinc captured at log time for the entered portion in mg.", example = "1.2", accessMode = Schema.AccessMode.READ_ONLY)
    private Double snapshotZinc;

    @Schema(description = "Vitamin A captured at log time for the entered portion in micrograms.", example = "250.0", accessMode = Schema.AccessMode.READ_ONLY)
    private Double snapshotVitaminA;

    @Schema(description = "Vitamin C captured at log time for the entered portion in mg.", example = "18.0", accessMode = Schema.AccessMode.READ_ONLY)
    private Double snapshotVitaminC;

    @Schema(description = "Vitamin D captured at log time for the entered portion in micrograms.", example = "4.0", accessMode = Schema.AccessMode.READ_ONLY)
    private Double snapshotVitaminD;

    @Schema(description = "Vitamin E captured at log time for the entered portion in mg.", example = "2.5", accessMode = Schema.AccessMode.READ_ONLY)
    private Double snapshotVitaminE;

    @Schema(description = "Vitamin B12 captured at log time for the entered portion in micrograms.", example = "1.1", accessMode = Schema.AccessMode.READ_ONLY)
    private Double snapshotVitaminB12;

    @Schema(description = "How this food log was created.", example = "SEARCH")
    private FoodLogSource source;

    @NotBlank(message = "{validation.food-log.meal-type.required}")
    @Pattern(regexp = "(?i)BREAKFAST|LUNCH|DINNER|SNACK", message = "{validation.food-log.meal-type.invalid}")
    @Schema(description = "Meal category.", example = "BREAKFAST", allowableValues = {"BREAKFAST", "LUNCH", "DINNER", "SNACK"}, requiredMode = Schema.RequiredMode.REQUIRED)
    private String mealType;

    @NotNull(message = "{validation.food-log.log-date.required}")
    @Schema(description = "Date and time when the food was logged.", example = "2026-05-11T08:30:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime logDate;
}

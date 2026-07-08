package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodPortionUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "User-reviewed AI draft item to write into the food diary.")
public class AiMealDraftConfirmItemRequestDto {
    @Positive(message = "{validation.food-log.food-item-id.positive}")
    @Schema(description = "Food catalog id selected by the user after reviewing the AI suggestion. Required only for matched-product confirmations.", example = "12")
    private Long foodItemId;

    @NotNull(message = "{validation.food-log.portion-size.required}")
    @Positive(message = "{validation.food-log.portion-size.positive}")
    @Schema(description = "Final user-approved amount.", example = "150.0")
    private Double portionSize;

    @Schema(description = "Final user-approved unit.", example = "GRAM")
    private FoodPortionUnit portionUnit;

    @NotBlank(message = "{validation.food-log.meal-type.required}")
    @Pattern(regexp = "(?i)BREAKFAST|LUNCH|DINNER|SNACK", message = "{validation.food-log.meal-type.invalid}")
    @Schema(description = "Final user-approved meal type.", example = "LUNCH")
    private String mealType;

    @NotNull(message = "{validation.food-log.log-date.required}")
    @Schema(description = "Final user-approved diary timestamp.", example = "2026-06-01T13:30:00")
    private LocalDateTime logDate;

    @Size(max = 255)
    @Schema(description = "User-approved display name for an AI estimate when no catalog product is matched.", example = "Ham and cheese sandwich")
    private String estimatedFoodName;

    @PositiveOrZero
    @Schema(description = "User-approved estimated calories for an unmatched AI item.", example = "350.0")
    private Double estimatedCalories;

    @PositiveOrZero
    @Schema(description = "User-approved estimated protein grams for an unmatched AI item.", example = "20.0")
    private Double estimatedProtein;

    @PositiveOrZero
    @Schema(description = "User-approved estimated carbohydrate grams for an unmatched AI item.", example = "30.0")
    private Double estimatedCarbs;

    @PositiveOrZero
    @Schema(description = "User-approved estimated fat grams for an unmatched AI item.", example = "16.0")
    private Double estimatedFat;

    @Schema(description = "AI confidence for this estimate from 0 to 1.", example = "0.72")
    private Double confidence;
}


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
@Schema(description = "User-reviewed AI draft item to write into the food diary.")
public class AiMealDraftConfirmItemRequestDto {
    @NotNull(message = "{validation.food-log.food-item-id.required}")
    @Positive(message = "{validation.food-log.food-item-id.positive}")
    @Schema(description = "Food catalog id selected by the user after reviewing the AI suggestion.", example = "12")
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
}

package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Copies one meal's food logs from a source day to a target day.")
public class FoodLogCopyMealRequestDto {

    @NotNull(message = "{validation.food-log.copy.source-date.required}")
    @Schema(description = "Day to copy from.", example = "2026-05-21", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate sourceDate;

    @NotNull(message = "{validation.food-log.copy.target-date.required}")
    @Schema(description = "Day to copy into.", example = "2026-05-22", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate targetDate;

    @NotBlank(message = "{validation.food-log.meal-type.required}")
    @Pattern(regexp = "(?i)BREAKFAST|LUNCH|DINNER|SNACK", message = "{validation.food-log.meal-type.invalid}")
    @Schema(description = "Meal category to copy.", example = "BREAKFAST", requiredMode = Schema.RequiredMode.REQUIRED)
    private String mealType;
}

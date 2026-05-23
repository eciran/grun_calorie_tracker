package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Adds a saved meal template to a target diary day.")
public class MealTemplateApplyRequestDto {

    @NotNull(message = "{validation.meal-template.target-date.required}")
    @Schema(example = "2026-05-22")
    private LocalDate targetDate;

    @Pattern(regexp = "(?i)BREAKFAST|LUNCH|DINNER|SNACK", message = "{validation.food-log.meal-type.invalid}")
    @Schema(description = "Optional target meal type. Defaults to the template meal type.", example = "BREAKFAST")
    private String mealType;
}

package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Creates a saved meal template from an existing logged meal.")
public class MealTemplateCreateRequestDto {

    @NotBlank(message = "{validation.meal-template.name.required}")
    @Schema(example = "Workday breakfast")
    private String name;

    @NotNull(message = "{validation.meal-template.source-date.required}")
    @Schema(example = "2026-05-21")
    private LocalDate sourceDate;

    @NotBlank(message = "{validation.food-log.meal-type.required}")
    @Pattern(regexp = "(?i)BREAKFAST|LUNCH|DINNER|SNACK", message = "{validation.food-log.meal-type.invalid}")
    @Schema(example = "BREAKFAST")
    private String mealType;
}

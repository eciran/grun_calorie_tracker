package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Replaces editable meal template metadata and item list.")
public class MealTemplateUpdateRequestDto {

    @NotBlank(message = "{validation.meal-template.name.required}")
    @Schema(example = "Workday breakfast")
    private String name;

    @NotBlank(message = "{validation.food-log.meal-type.required}")
    @Pattern(regexp = "(?i)BREAKFAST|LUNCH|DINNER|SNACK", message = "{validation.food-log.meal-type.invalid}")
    @Schema(example = "BREAKFAST")
    private String mealType;

    @NotEmpty(message = "{validation.meal-template.items.required}")
    @Valid
    @Schema(description = "Replacement template food items.")
    private List<MealTemplateItemRequestDto> items;
}

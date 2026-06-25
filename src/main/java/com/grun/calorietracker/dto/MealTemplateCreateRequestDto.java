package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Schema(description = "Creates a saved meal template either from an existing logged meal or from an explicit editable item list.")
public class MealTemplateCreateRequestDto {

    @NotBlank(message = "{validation.meal-template.name.required}")
    @Schema(example = "Workday breakfast")
    private String name;

    @Schema(description = "Source diary date when saving a template from an existing logged meal. Required only when items is empty.", example = "2026-05-21")
    private LocalDate sourceDate;

    @NotBlank(message = "{validation.food-log.meal-type.required}")
    @Pattern(regexp = "(?i)BREAKFAST|LUNCH|DINNER|SNACK", message = "{validation.food-log.meal-type.invalid}")
    @Schema(example = "BREAKFAST")
    private String mealType;

    @Valid
    @Schema(description = "Optional explicit food item list for creating a template directly from product selection. When supplied, sourceDate is not required.")
    private List<MealTemplateItemRequestDto> items;
}
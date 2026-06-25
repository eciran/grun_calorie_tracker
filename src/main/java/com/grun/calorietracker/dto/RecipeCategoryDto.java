package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.RecipeCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "Recipe category metadata for public recipe filters.")
public class RecipeCategoryDto {
    @Schema(description = "Stable enum value used in API filters.", example = "HIGH_PROTEIN")
    private RecipeCategory code;

    @Schema(description = "Default English display label.", example = "High protein")
    private String label;
}

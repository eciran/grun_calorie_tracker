package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Ordered cooking/preparation step returned with a recipe.")
public class RecipeStepDto {
    @Schema(description = "One-based display order.", example = "1")
    private Integer stepNumber;

    @Schema(description = "Step instruction text.", example = "Heat olive oil in a pan and add chopped onion.")
    private String instruction;
}
package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "One ordered cooking/preparation step for a recipe.")
public class RecipeStepRequestDto {
    @NotBlank
    @Size(max = 1000)
    @Schema(description = "Step instruction text.", example = "Heat olive oil in a pan and add chopped onion.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String instruction;
}
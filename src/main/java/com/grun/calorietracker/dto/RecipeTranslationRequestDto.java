package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.PreferredLanguage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Localized public recipe copy. Nutrition, ingredients, media and engagement remain shared by the parent recipe.")
public class RecipeTranslationRequestDto {
    @NotNull
    private PreferredLanguage language;

    @NotBlank
    @Size(max = 160)
    private String name;

    @Size(max = 1000)
    private String description;

    @Valid
    @Size(max = 30)
    private List<RecipeStepRequestDto> cookingSteps;
}

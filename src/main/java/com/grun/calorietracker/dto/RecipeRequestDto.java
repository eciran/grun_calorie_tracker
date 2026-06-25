package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.RecipeCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
@Schema(description = "Create or update request for a private user recipe.")
public class RecipeRequestDto {
    @NotBlank
    @Size(max = 160)
    @Schema(description = "Recipe name.", example = "Homemade lentil soup", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Size(max = 1000)
    @Schema(description = "Optional recipe description or short preparation note.", example = "Simple homemade soup prepared with red lentils.")
    private String description;

    @Schema(description = "Default meal type for logging.", example = "LUNCH", allowableValues = {"BREAKFAST", "LUNCH", "DINNER", "SNACK"})
    private String mealType;

    @Schema(description = "Market/region hint for future public recipe library filtering.", example = "TR")
    private MarketRegion marketRegion;

    @Size(max = 12)
    @Schema(description = "Recipe language code.", example = "tr")
    private String language;

    @Schema(description = "Display image URL.")
    private String imageUrl;

    @Positive
    @Schema(description = "Cooked final recipe yield in grams. If omitted, sum of ingredient grams is used.", example = "1200.0")
    private Double totalYieldGrams;

    @Positive
    @Schema(description = "Default serving amount in grams.", example = "300.0")
    private Double defaultServingGrams;

    @Positive
    @Schema(description = "Optional serving count.", example = "4")
    private Integer servingCount;

    @Schema(description = "Standard categories used for public recipe discovery.", example = "[\"VEGAN\", \"HIGH_PROTEIN\"]")
    private Set<RecipeCategory> categories;

    @Valid
    @NotEmpty
    @Schema(description = "Recipe ingredients.", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<RecipeIngredientRequestDto> ingredients;

    @Valid
    @Size(max = 30)
    @Schema(description = "Ordered cooking/preparation steps displayed by the mobile recipe detail screen.")
    private List<RecipeStepRequestDto> cookingSteps;
}

package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.MarketRegion;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "AI recipe generation request. The response is a draft and must be reviewed before a recipe is created.")
public class AiRecipeDraftRequestDto {
    @Size(max = 500)
    @Schema(description = "User goal or prompt for the recipe.", example = "High protein Turkish style dinner with chicken and rice.")
    private String prompt;

    @Schema(description = "Preferred meal type.", example = "DINNER", allowableValues = {"BREAKFAST", "LUNCH", "DINNER", "SNACK"})
    private String mealType;

    @Schema(description = "Market/region hint for local ingredients.", example = "TR")
    private MarketRegion marketRegion;

    @Size(max = 12)
    @Schema(description = "Preferred output language.", example = "tr")
    private String language;

    @Positive
    @Max(20)
    @Schema(description = "Desired serving count.", example = "2")
    private Integer servingCount;

    @Positive
    @Schema(description = "Approximate target calories per serving.", example = "650")
    private Double targetCaloriesPerServing;

    @Size(max = 12)
    @Schema(description = "Diet preferences such as HIGH_PROTEIN, VEGETARIAN, LOW_CARB.")
    private List<@Size(max = 60) String> dietaryPreferences;

    @Size(max = 12)
    @Schema(description = "Allergens or ingredients to avoid.")
    private List<@Size(max = 60) String> excludedIngredients;

    @Size(max = 20)
    @Schema(description = "Ingredients the user already has available.")
    private List<@Size(max = 80) String> availableIngredients;
}

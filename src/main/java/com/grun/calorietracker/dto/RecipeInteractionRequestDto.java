package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
@Schema(description = "Recipe save/favorite/rating update. Omitted fields keep their current value.")
public class RecipeInteractionRequestDto {
    @Schema(description = "Whether the recipe is saved by the user.", example = "true")
    private Boolean saved;

    @Schema(description = "Whether the recipe is favorited by the user.", example = "true")
    private Boolean favorite;

    @Min(1)
    @Max(5)
    @Schema(description = "User rating from 1 to 5. Null keeps or clears depending on clearRating.", example = "5")
    private Integer rating;

    @Schema(description = "Clears the current user rating when true.", example = "false")
    private Boolean clearRating;
}

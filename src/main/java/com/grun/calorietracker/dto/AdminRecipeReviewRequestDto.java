package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.RecipeAllergen;
import com.grun.calorietracker.enums.RecipeCategory;
import com.grun.calorietracker.enums.RecipeVisibility;
import com.grun.calorietracker.enums.VerificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
@Schema(description = "Admin recipe review update request.")
public class AdminRecipeReviewRequestDto {
    @Schema(description = "Admin verification decision for the recipe.", example = "VERIFIED")
    private VerificationStatus verificationStatus;

    @Schema(description = "Admin visibility decision for the recipe.", example = "PUBLIC_ADMIN")
    private RecipeVisibility visibility;

    @Schema(description = "Whether the recipe should be archived by admin moderation.", example = "false")
    private Boolean archived;

    @Schema(description = "Admin-controlled public discovery categories.", example = "[\"HIGH_PROTEIN\", \"MEAL_PREP\"]")
    private Set<RecipeCategory> categories;

    @Schema(description = "Admin-reviewed allergens displayed on public recipe detail.", example = "[\"MILK\", \"TREE_NUTS\"]")
    private Set<RecipeAllergen> allergens;
    @Size(max = 1024)
    @Schema(description = "Reviewed display image URL for the recipe.")
    private String imageUrl;

    @Schema(description = "Image source after admin review.", example = "ADMIN_UPLOAD")
    private ImageSource imageSource;

    @Schema(description = "Image moderation decision.", example = "APPROVED")
    private ImageStatus imageStatus;

    @Size(max = 1000)
    @Schema(description = "Internal admin note explaining the decision.", example = "Recipe looks valid and nutrition calculation is plausible.")
    private String reviewNote;
}

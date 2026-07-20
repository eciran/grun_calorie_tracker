package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.RecipeAllergen;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Data
@Schema(description = "User-owned nutrition safety and planning preferences.")
public class UserNutritionPreferenceDto {

    @Size(max = 15)
    private Set<RecipeAllergen> allergens = new LinkedHashSet<>();

    @Size(max = 30)
    private List<@Size(max = 80) String> excludedFoods = new ArrayList<>();

    @Size(max = 20)
    private List<@Size(max = 80) String> dietaryPreferences = new ArrayList<>();

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime updatedAt;
}

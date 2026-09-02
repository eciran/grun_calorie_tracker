package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodPreparationState;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Admin-created shared catalog product with per-100g nutrition and source provenance.")
public class AdminFoodProductCreateRequestDto {
    @NotBlank @Size(max = 255)
    private String name;

    @NotNull
    private FoodCatalogType catalogType;

    @NotNull
    private MarketRegion marketRegion;

    @NotNull
    private FoodPreparationState preparationState;

    @Size(max = 255)
    private String brand;

    @NotNull @PositiveOrZero
    private Double calories;

    @PositiveOrZero private Double protein;
    @PositiveOrZero private Double carbs;
    @PositiveOrZero private Double fat;
    @PositiveOrZero private Double fiber;
    @PositiveOrZero private Double sugar;

    @NotBlank @Size(max = 160)
    private String sourceName;

    @NotBlank @Size(max = 1000)
    @Pattern(regexp = "https?://.+", message = "sourceUrl must be an http or https URL")
    private String sourceUrl;

    @Size(max = 500)
    private String reviewNote;

    @Size(max = 255)
    private String searchAlias;

    private PreferredLanguage searchAliasLanguage;

    private Boolean allowPotentialDuplicate;

    private Boolean confirmNutritionWarnings;
}

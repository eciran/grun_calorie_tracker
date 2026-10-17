package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodServingOptionQualityStatus;
import com.grun.calorietracker.enums.FoodServingOptionSource;
import com.grun.calorietracker.enums.FoodServingOptionUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Product-specific serving option with explicit mass/volume conversions for the product's nutrition reference dimension.")
public class FoodServingOptionDto {

    @Schema(description = "Serving option id.", example = "5")
    private Long id;

    @Schema(description = "Food product id this serving option belongs to.", example = "12")
    private Long foodItemId;

    @Schema(description = "Display label shown to users.", example = "1 slice")
    private String label;

    @Schema(description = "Serving option unit type.", example = "SLICE")
    private FoodServingOptionUnit unitType;

    @Schema(description = "Quantity represented by this option label.", example = "1.0")
    private Double quantity;

    @Schema(description = "Equivalent gram weight used for nutrition calculation.", example = "28.0")
    private Double gramWeight;

    @Schema(description = "Equivalent milliliter volume when the option is liquid-based.", example = "330.0")
    private Double mlVolume;

    @Schema(description = "Whether this is the default product serving option.", example = "true")
    private Boolean defaultOption;

    @Schema(description = "Source of the serving option.", example = "ADMIN")
    private FoodServingOptionSource source;

    @Schema(description = "Review quality of this serving option.", example = "VERIFIED")
    private FoodServingOptionQualityStatus qualityStatus;

    @Schema(description = "Whether the option is suitable for display in user-facing serving selectors.", example = "true")
    private Boolean userVisible;

    @Schema(description = "Input units allowed with this servingOptionId. SERVING/semantic units are option counts; GRAM/MILLILITER are absolute quantities and require an explicit mass-volume pair.")
    private java.util.List<com.grun.calorietracker.enums.FoodPortionUnit> allowedInputUnits = java.util.List.of();
}

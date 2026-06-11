package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodServingOptionQualityStatus;
import com.grun.calorietracker.enums.FoodServingOptionSource;
import com.grun.calorietracker.enums.FoodServingOptionUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Product-specific serving option. These options convert user-friendly units such as slice, cup, or bottle into grams for nutrition calculation.")
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
}

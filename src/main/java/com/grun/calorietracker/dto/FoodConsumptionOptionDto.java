package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodConsumptionOptionType;
import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.FoodServingOptionUnit;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One user-facing ready amount. Serving options and reviewed package sizes share this contract.")
public record FoodConsumptionOptionDto(
        String key,
        String label,
        FoodConsumptionOptionType type,
        FoodServingOptionUnit semanticUnit,
        Double amount,
        FoodPortionUnit portionUnit,
        Double gramWeight,
        Double mlVolume,
        Long servingOptionId,
        Long foodItemId,
        String barcode,
        boolean defaultOption,
        boolean selectedOption
) {
}

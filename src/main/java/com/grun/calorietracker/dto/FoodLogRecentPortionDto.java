package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodLogSource;
import com.grun.calorietracker.enums.FoodPortionUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Recently used portion for one food product.")
public class FoodLogRecentPortionDto {
    private Double portionSize;
    private FoodPortionUnit portionUnit;
    private Long servingOptionId;
    private String servingOptionLabel;
    private Double normalizedPortionGrams;
    private FoodLogSource source;
}

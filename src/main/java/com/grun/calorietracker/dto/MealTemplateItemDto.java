package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodPortionUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "One food item stored in a saved meal template.")
public class MealTemplateItemDto {

    private Long foodItemId;
    private String foodName;
    private Double portionSize;
    private FoodPortionUnit portionUnit;
    private Double normalizedPortionGrams;
}

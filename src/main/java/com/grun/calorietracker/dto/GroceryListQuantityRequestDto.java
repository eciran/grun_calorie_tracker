package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.GroceryCategory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class GroceryListQuantityRequestDto {
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @DecimalMax("100000")
    private Double displayQuantity;

    @NotNull
    private FoodPortionUnit displayUnit;

    @DecimalMin("0.0")
    @DecimalMax("1000000")
    private Double normalizedGrams;

    private GroceryCategory category;

    @NotNull
    @PositiveOrZero
    private Long expectedVersion;
}

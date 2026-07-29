package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.GroceryCategory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GroceryListManualItemRequestDto {
    @NotBlank
    @Size(max = 160)
    private String displayName;

    @NotNull
    private GroceryCategory category;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @DecimalMax("100000")
    private Double displayQuantity;

    @NotNull
    private FoodPortionUnit displayUnit;

    @DecimalMin("0.0")
    @DecimalMax("1000000")
    private Double normalizedGrams;
}

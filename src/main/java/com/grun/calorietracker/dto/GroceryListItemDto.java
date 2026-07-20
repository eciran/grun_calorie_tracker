package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodPortionUnit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroceryListItemDto {
    private Long foodItemId;
    private String name;
    private Double totalGrams;
    private Double totalQuantity;
    private FoodPortionUnit quantityUnit;
    private Integer plannedUses;
}

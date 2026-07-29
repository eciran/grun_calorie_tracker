package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.GroceryCategory;
import com.grun.calorietracker.enums.GroceryListItemSource;
import lombok.Data;

@Data
public class GroceryListItemResponseDto {
    private Long id;
    private Long foodItemId;
    private String displayName;
    private GroceryCategory category;
    private GroceryListItemSource source;
    private Double displayQuantity;
    private FoodPortionUnit displayUnit;
    private Double normalizedGrams;
    private Integer plannedUses;
    private Boolean purchased;
    private Boolean excluded;
    private Boolean sourceRemoved;
    private Boolean quantityOverridden;
    private Long version;
}

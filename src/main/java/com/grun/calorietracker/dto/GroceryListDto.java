package com.grun.calorietracker.dto;

import lombok.Data;

import java.util.List;

@Data
public class GroceryListDto {
    private Long mealPlanId;
    private String mealPlanName;
    private List<GroceryListItemDto> items;
}

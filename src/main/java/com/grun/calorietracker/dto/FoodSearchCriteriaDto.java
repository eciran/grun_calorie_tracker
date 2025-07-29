package com.grun.calorietracker.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FoodSearchCriteriaDto {
    private String query;
    private String brand;
    private String category;
    private Double minCalories;
    private Double maxCalories;
    private String sortBy;
    private String sortOrder;
    private String nutriScore;
}
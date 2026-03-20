package com.grun.calorietracker.dto;

import jakarta.validation.constraints.DecimalMin;
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
    @DecimalMin(value = "0.0", inclusive = true, message = "Minimum calories cannot be negative")
    private Double minCalories;

    @DecimalMin(value = "0.0", inclusive = true, message = "Maximum calories cannot be negative")
    private Double maxCalories;
    private String sortBy;
    private String sortOrder;
    private String nutriScore;
}
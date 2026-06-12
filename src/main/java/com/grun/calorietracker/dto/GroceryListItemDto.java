package com.grun.calorietracker.dto;

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
    private Integer plannedUses;
}

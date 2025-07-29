package com.grun.calorietracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FoodProductDto {

    private String barcode;
    private String productName;
    private String brand;
    private String imageUrl;
    private Double calories;
    private Double protein;
    private Double fat;
    private Double carbs;
    private Double fiber;
    private Double sugar;
    private Double sodium;
    private Double servingSize;
    private String ingredientsText;
    private String allergens;
    private String nutriScore;
}

package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.FoodPreparationState;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiProductQualityValidationRequestDto {
    private Long productId;
    private String name;
    private String brand;
    private String barcode;
    private String sourceKey;
    private MarketRegion marketRegion;
    private FoodDataSource dataSource;
    private FoodCatalogType catalogType;
    private VerificationStatus verificationStatus;
    private ImageStatus imageStatus;
    private FoodPreparationState preparationState;
    private String allergens;
    private String nutriScore;
    private Double calories;
    private Double protein;
    private Double fat;
    private Double carbs;
    private Double fiber;
    private Double sugar;
    private Double sodium;
    private Double potassium;
    private Double cholesterol;
    private Double calcium;
    private Double iron;
    private Double magnesium;
    private Double zinc;
    private Double vitaminA;
    private Double vitaminC;
    private Double vitaminD;
    private Double vitaminE;
    private Double vitaminB12;
    private Double saturatedFat;
    private Double transFat;
    private Double sugarAlcohol;
    private Double servingSizeGrams;
    private String servingUnit;
}

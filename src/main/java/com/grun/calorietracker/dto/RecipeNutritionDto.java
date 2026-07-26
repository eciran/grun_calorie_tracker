package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Complete estimated nutrition snapshot. Macro values, fiber, sugar and fat are grams; sodium, potassium, cholesterol, calcium, iron, magnesium and zinc are milligrams; vitamins A and D are micrograms, vitamin C and E are milligrams, and vitamin B12 is micrograms.")
public class RecipeNutritionDto {
    @Schema(description = "Energy in kilocalories for the stated portion or nutrition basis.")
    private Double calories;
    @Schema(description = "Protein in grams.")
    private Double protein;
    @Schema(description = "Carbohydrate in grams.")
    private Double carbs;
    @Schema(description = "Fat in grams.")
    private Double fat;
    @Schema(description = "Fiber in grams.")
    private Double fiber;
    @Schema(description = "Sugar in grams.")
    private Double sugar;
    @Schema(description = "Saturated fat in grams.")
    private Double saturatedFat;
    @Schema(description = "Sodium in milligrams.")
    private Double sodium;
    @Schema(description = "Potassium in milligrams.")
    private Double potassium;
    @Schema(description = "Cholesterol in milligrams.")
    private Double cholesterol;
    @Schema(description = "Calcium in milligrams.")
    private Double calcium;
    @Schema(description = "Iron in milligrams.")
    private Double iron;
    @Schema(description = "Magnesium in milligrams.")
    private Double magnesium;
    @Schema(description = "Zinc in milligrams.")
    private Double zinc;
    @Schema(description = "Vitamin A in micrograms RAE.")
    private Double vitaminA;
    @Schema(description = "Vitamin C in milligrams.")
    private Double vitaminC;
    @Schema(description = "Vitamin D in micrograms.")
    private Double vitaminD;
    @Schema(description = "Vitamin E in milligrams.")
    private Double vitaminE;
    @Schema(description = "Vitamin B12 in micrograms.")
    private Double vitaminB12;
}

package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Aggregated nutrition details. Null means the value was unavailable in source data and must not be interpreted as zero.")
public class MicronutrientTotalsDto {
    @Schema(description = "Fiber in grams.")
    private Double fiber;
    @Schema(description = "Total sugar in grams. This is not equivalent to free sugar.")
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
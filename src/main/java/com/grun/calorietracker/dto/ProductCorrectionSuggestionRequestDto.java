package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "User suggestion for correcting product nutrition data.")
public class ProductCorrectionSuggestionRequestDto {
    @PositiveOrZero
    private Double suggestedCalories;
    @PositiveOrZero
    private Double suggestedProtein;
    @PositiveOrZero
    private Double suggestedCarbs;
    @PositiveOrZero
    private Double suggestedFat;
    @Size(max = 500)
    private String note;
    @Size(max = 500)
    private String imageUrl;
}

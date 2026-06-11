package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.ProductCorrectionStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductCorrectionSuggestionDto {
    private Long id;
    private Long foodItemId;
    private Double suggestedCalories;
    private Double suggestedProtein;
    private Double suggestedCarbs;
    private Double suggestedFat;
    private String note;
    private String imageUrl;
    private ProductCorrectionStatus status;
    private LocalDateTime createdAt;
}

package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.ProductQualityScanItemStatus;
import com.grun.calorietracker.enums.ProductQualitySuggestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Product-level result item for a quality scan run.")
public class ProductQualityScanRunItemDto {
    private Long id;
    private Long foodItemId;
    private String productName;
    private String brand;
    private ProductQualityScanItemStatus status;
    private ProductQualitySuggestionType suggestionType;
    private String fieldName;
    private String suggestedValue;
    private String reason;
    private Integer confidenceScore;
    private String note;
}
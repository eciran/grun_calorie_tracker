package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.ProductQualitySuggestionSource;
import com.grun.calorietracker.enums.ProductQualitySuggestionStatus;
import com.grun.calorietracker.enums.ProductQualitySuggestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Admin-reviewable product quality improvement suggestion.")
public class ProductQualitySuggestionDto {
    private Long id;
    private Long foodItemId;
    private String productName;
    private String brand;
    private ProductQualitySuggestionType suggestionType;
    private ProductQualitySuggestionSource source;
    private ProductQualitySuggestionStatus status;
    private Integer confidenceScore;
    private String fieldName;
    private String currentValue;
    private String suggestedValue;
    private String reason;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    private String reviewedBy;

    public ProductQualitySuggestionDto(
            Long id,
            Long foodItemId,
            String productName,
            String brand,
            ProductQualitySuggestionType suggestionType,
            ProductQualitySuggestionSource source,
            ProductQualitySuggestionStatus status,
            Integer confidenceScore,
            String currentValue,
            String suggestedValue,
            String reason,
            LocalDateTime createdAt,
            LocalDateTime reviewedAt,
            String reviewedBy
    ) {
        this(
                id,
                foodItemId,
                productName,
                brand,
                suggestionType,
                source,
                status,
                confidenceScore,
                null,
                currentValue,
                suggestedValue,
                reason,
                createdAt,
                reviewedAt,
                reviewedBy
        );
    }
}





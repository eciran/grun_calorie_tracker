package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result of AI-assisted selected product quality validation.")
public class AdminProductQualityAiValidationResultDto {
    private Long scanRunId;
    private int requestedProducts;
    private int validatedProducts;
    private int createdSuggestions;
    private int skippedExistingSuggestions;
    private int skippedPreviouslyValidatedProducts;
    private int effectiveLimit;
}

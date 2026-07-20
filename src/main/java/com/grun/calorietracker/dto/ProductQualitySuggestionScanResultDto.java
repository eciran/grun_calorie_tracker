package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result of scanning products for quality improvement suggestions.")
public class ProductQualitySuggestionScanResultDto {
    private Long scanRunId;
    private int scannedProducts;
    private int createdSuggestions;
    private int skippedExistingSuggestions;
    private int skippedPreviouslyValidatedProducts;
    private int validatedProducts;
    private int effectiveLimit;
    private boolean forceRescan;

    public ProductQualitySuggestionScanResultDto(int scannedProducts, int createdSuggestions, int skippedExistingSuggestions) {
        this(null, scannedProducts, createdSuggestions, skippedExistingSuggestions, 0, 0, scannedProducts, false);
    }
}

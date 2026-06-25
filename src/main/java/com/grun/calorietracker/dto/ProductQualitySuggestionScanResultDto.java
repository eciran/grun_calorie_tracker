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
    private int scannedProducts;
    private int createdSuggestions;
    private int skippedExistingSuggestions;
}

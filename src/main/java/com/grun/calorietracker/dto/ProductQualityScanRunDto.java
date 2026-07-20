package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.ProductQualityScanStatus;
import com.grun.calorietracker.enums.ProductQualityScanTriggerType;
import com.grun.calorietracker.enums.ProductQualitySuggestionSource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Product quality scan run history item.")
public class ProductQualityScanRunDto {
    private Long id;
    private ProductQualitySuggestionSource source;
    private ProductQualityScanTriggerType triggerType;
    private ProductQualityScanStatus status;
    private MarketRegion marketRegion;
    private int requestedLimit;
    private int effectiveLimit;
    private boolean forceRescan;
    private int scannedProducts;
    private int createdSuggestions;
    private int skippedExistingSuggestions;
    private int skippedPreviouslyValidatedProducts;
    private int validatedProducts;
    private String triggeredBy;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;
}

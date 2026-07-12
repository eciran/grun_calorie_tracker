package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.ProductQualitySuggestionDto;
import com.grun.calorietracker.dto.ProductQualitySuggestionPageDto;
import com.grun.calorietracker.dto.ProductQualitySuggestionScanResultDto;
import com.grun.calorietracker.dto.ProductQualityAiSettingsDto;
import com.grun.calorietracker.dto.ProductQualityAiSettingsUpdateRequestDto;
import com.grun.calorietracker.dto.AdminProductQualityAiValidationRequestDto;
import com.grun.calorietracker.dto.AdminProductQualityAiValidationResultDto;
import com.grun.calorietracker.dto.ProductQualityScanRunPageDto;
import com.grun.calorietracker.dto.ProductQualityScanRunDetailDto;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.ProductQualityScanTriggerType;
import com.grun.calorietracker.enums.ProductQualitySuggestionStatus;

public interface ProductQualitySuggestionService {

    ProductQualitySuggestionScanResultDto scanSuggestions(MarketRegion marketRegion, int limit, boolean forceRescan, ProductQualityScanTriggerType triggerType, String triggeredBy);

    default ProductQualitySuggestionScanResultDto scanSuggestions(MarketRegion marketRegion, int limit) {
        return scanSuggestions(marketRegion, limit, false, ProductQualityScanTriggerType.MANUAL, null);
    }

    ProductQualitySuggestionPageDto getSuggestions(ProductQualitySuggestionStatus status, int page, int size);

    ProductQualityScanRunPageDto getScanRuns(int page, int size);

    ProductQualityScanRunDetailDto getScanRunDetail(Long scanRunId);

    AdminProductQualityAiValidationResultDto validateSelectedWithAi(AdminProductQualityAiValidationRequestDto request, String triggeredBy);

    ProductQualityAiSettingsDto getAiSettings();

    ProductQualityAiSettingsDto updateAiSettings(ProductQualityAiSettingsUpdateRequestDto request, String updatedBy);

    ProductQualitySuggestionDto acceptSuggestion(Long suggestionId, String reviewedBy);

    ProductQualitySuggestionDto rejectSuggestion(Long suggestionId, String reviewedBy);
}


package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.ProductQualitySuggestionDto;
import com.grun.calorietracker.dto.ProductQualitySuggestionPageDto;
import com.grun.calorietracker.dto.ProductQualitySuggestionScanResultDto;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.ProductQualitySuggestionStatus;

public interface ProductQualitySuggestionService {

    ProductQualitySuggestionScanResultDto scanSuggestions(MarketRegion marketRegion, int limit);

    ProductQualitySuggestionPageDto getSuggestions(ProductQualitySuggestionStatus status, int page, int size);

    ProductQualitySuggestionDto acceptSuggestion(Long suggestionId, String reviewedBy);

    ProductQualitySuggestionDto rejectSuggestion(Long suggestionId, String reviewedBy);
}
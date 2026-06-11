package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.ProductCorrectionSuggestionDto;
import com.grun.calorietracker.dto.ProductCorrectionSuggestionRequestDto;

public interface ProductCorrectionSuggestionService {

    ProductCorrectionSuggestionDto suggestCorrection(Long foodItemId, String email, ProductCorrectionSuggestionRequestDto request);
}

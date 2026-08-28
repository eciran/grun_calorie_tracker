package com.grun.calorietracker.service;

import com.grun.calorietracker.service.model.ProductNutritionOcrFallbackRequest;
import com.grun.calorietracker.service.model.ProductNutritionOcrFallbackResult;

import java.util.Optional;

public interface ProductNutritionOcrFallbackService {
    Optional<ProductNutritionOcrFallbackResult> analyzeIfNeeded(ProductNutritionOcrFallbackRequest request);
}

package com.grun.calorietracker.service;

import com.grun.calorietracker.service.model.ProductNutritionOcrFallbackRequest;
import com.grun.calorietracker.service.model.ProductNutritionOcrFallbackResult;
import com.grun.calorietracker.service.model.ProductNutritionOcrEvidence;

public interface ProductNutritionOcrProvider {
    String providerId();

    ProductNutritionOcrFallbackResult analyze(
            ProductNutritionOcrFallbackRequest request,
            ProductNutritionOcrEvidence evidence);
}

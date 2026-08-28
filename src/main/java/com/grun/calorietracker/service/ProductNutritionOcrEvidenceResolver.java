package com.grun.calorietracker.service;

import com.grun.calorietracker.service.model.ProductNutritionOcrEvidence;
import com.grun.calorietracker.service.model.ProductNutritionOcrFallbackRequest;

public interface ProductNutritionOcrEvidenceResolver {
    ProductNutritionOcrEvidence resolve(ProductNutritionOcrFallbackRequest request);
}

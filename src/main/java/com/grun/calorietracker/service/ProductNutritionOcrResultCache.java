package com.grun.calorietracker.service;

import com.grun.calorietracker.service.model.ProductNutritionOcrEvidence;
import com.grun.calorietracker.service.model.ProductNutritionOcrFallbackRequest;
import com.grun.calorietracker.service.model.ProductNutritionOcrFallbackResult;

import java.util.Optional;

public interface ProductNutritionOcrResultCache {
    Optional<ProductNutritionOcrFallbackResult> get(
            ProductNutritionOcrFallbackRequest request, ProductNutritionOcrEvidence evidence, String model);

    void put(ProductNutritionOcrFallbackRequest request, ProductNutritionOcrEvidence evidence,
             String model, ProductNutritionOcrFallbackResult result);
}

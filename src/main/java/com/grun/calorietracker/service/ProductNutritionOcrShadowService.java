package com.grun.calorietracker.service;

import com.grun.calorietracker.service.model.ProductNutritionOcrShadowRequest;
import com.grun.calorietracker.service.model.ProductNutritionOcrShadowResult;

public interface ProductNutritionOcrShadowService {
    ProductNutritionOcrShadowResult compare(ProductNutritionOcrShadowRequest request);
}

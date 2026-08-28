package com.grun.calorietracker.service;

import com.grun.calorietracker.service.model.ProductNutritionOcrReconciliation;

import java.util.Map;

public interface ProductNutritionOcrReconciler {
    ProductNutritionOcrReconciliation reconcile(Map<String, Object> localFields, Map<String, Object> geminiFields);
}

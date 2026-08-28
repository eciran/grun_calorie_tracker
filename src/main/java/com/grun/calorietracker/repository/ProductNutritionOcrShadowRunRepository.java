package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.ProductNutritionOcrShadowRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductNutritionOcrShadowRunRepository
        extends JpaRepository<ProductNutritionOcrShadowRunEntity, Long> {
}

package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.ProductNutritionOcrShadowRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductNutritionOcrShadowRunRepository
        extends JpaRepository<ProductNutritionOcrShadowRunEntity, Long> {
    List<ProductNutritionOcrShadowRunEntity> findByReviewCaseIdOrderByCreatedAtDescIdDesc(Long reviewCaseId);
}

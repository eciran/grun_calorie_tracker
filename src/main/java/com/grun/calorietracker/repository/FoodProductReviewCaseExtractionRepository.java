package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodProductReviewCaseExtractionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FoodProductReviewCaseExtractionRepository
        extends JpaRepository<FoodProductReviewCaseExtractionEntity, Long> {
    Optional<FoodProductReviewCaseExtractionEntity> findByReviewCaseId(Long reviewCaseId);
}

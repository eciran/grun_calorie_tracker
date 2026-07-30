package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodProductReviewCaseEntity;
import com.grun.calorietracker.enums.FoodProductReviewCaseSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FoodProductReviewCaseRepository extends JpaRepository<FoodProductReviewCaseEntity, Long> {
    Optional<FoodProductReviewCaseEntity> findByIdempotencyKey(String idempotencyKey);
    Optional<FoodProductReviewCaseEntity> findBySourceAndSourceReference(
            FoodProductReviewCaseSource source,
            String sourceReference
    );
}

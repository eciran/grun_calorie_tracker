package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodProductReviewAuditEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodProductReviewAuditRepository extends JpaRepository<FoodProductReviewAuditEntity, Long> {
    Page<FoodProductReviewAuditEntity> findByFoodItemId(Long foodItemId, Pageable pageable);
}

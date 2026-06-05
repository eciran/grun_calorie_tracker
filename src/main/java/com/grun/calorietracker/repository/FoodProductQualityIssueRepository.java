package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodProductQualityIssueEntity;
import com.grun.calorietracker.enums.FoodProductQualityIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FoodProductQualityIssueRepository extends JpaRepository<FoodProductQualityIssueEntity, Long> {

    List<FoodProductQualityIssueEntity> findByFoodItemIdAndResolvedFalse(Long foodItemId);

    List<FoodProductQualityIssueEntity> findByFoodItemIdOrderByResolvedAscLastDetectedAtDesc(Long foodItemId);

    Optional<FoodProductQualityIssueEntity> findByFoodItemIdAndIssueTypeAndResolvedFalse(
            Long foodItemId,
            FoodProductQualityIssue issueType
    );
}

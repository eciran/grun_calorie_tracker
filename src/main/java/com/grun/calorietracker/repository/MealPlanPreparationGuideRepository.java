package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.AiRequestHistoryEntity;
import com.grun.calorietracker.entity.MealPlanItemEntity;
import com.grun.calorietracker.entity.MealPlanPreparationGuideEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MealPlanPreparationGuideRepository
        extends JpaRepository<MealPlanPreparationGuideEntity, Long> {
    Optional<MealPlanPreparationGuideEntity> findTopByMealPlanItemOrderByVersionDesc(
            MealPlanItemEntity mealPlanItem);
    Optional<MealPlanPreparationGuideEntity> findBySourceAiRequest(
            AiRequestHistoryEntity sourceAiRequest);
}

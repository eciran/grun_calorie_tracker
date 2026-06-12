package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.MealPlanEntity;
import com.grun.calorietracker.entity.MealPlanItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MealPlanItemRepository extends JpaRepository<MealPlanItemEntity, Long> {
    List<MealPlanItemEntity> findByMealPlanOrderByPlanDateAscMealTypeAscItemOrderAscIdAsc(MealPlanEntity mealPlan);
}

package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.MealTemplateItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealTemplateItemRepository extends JpaRepository<MealTemplateItemEntity, Long> {
    boolean existsByFoodItem(FoodItemEntity foodItem);
}

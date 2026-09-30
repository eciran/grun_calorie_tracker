package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodItemCategoryEntity;
import com.grun.calorietracker.entity.FoodItemCategoryId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface FoodItemCategoryRepository extends JpaRepository<FoodItemCategoryEntity, FoodItemCategoryId> {
    long countByPrimaryCategoryTrue();
    List<FoodItemCategoryEntity> findByFoodItemIdIn(Collection<Long> foodItemIds);
}

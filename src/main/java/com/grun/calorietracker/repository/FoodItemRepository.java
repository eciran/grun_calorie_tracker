package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodItemRepository extends JpaRepository<FoodItemEntity, Long> {
}

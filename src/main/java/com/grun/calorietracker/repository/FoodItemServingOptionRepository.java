package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodItemServingOptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FoodItemServingOptionRepository extends JpaRepository<FoodItemServingOptionEntity, Long> {

    List<FoodItemServingOptionEntity> findByFoodItemOrderByIsDefaultDescLabelAsc(FoodItemEntity foodItem);

    Optional<FoodItemServingOptionEntity> findByIdAndFoodItem(Long id, FoodItemEntity foodItem);
}

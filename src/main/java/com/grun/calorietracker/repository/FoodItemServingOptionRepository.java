package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodItemServingOptionEntity;
import com.grun.calorietracker.enums.FoodServingOptionQualityStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FoodItemServingOptionRepository extends JpaRepository<FoodItemServingOptionEntity, Long> {

    List<FoodItemServingOptionEntity> findByFoodItemOrderByIsDefaultDescLabelAsc(FoodItemEntity foodItem);

    List<FoodItemServingOptionEntity> findByFoodItemAndQualityStatusOrderByIsDefaultDescLabelAsc(
            FoodItemEntity foodItem,
            FoodServingOptionQualityStatus qualityStatus
    );

    Optional<FoodItemServingOptionEntity> findByIdAndFoodItem(Long id, FoodItemEntity foodItem);

    Optional<FoodItemServingOptionEntity> findByIdAndFoodItemAndQualityStatus(
            Long id,
            FoodItemEntity foodItem,
            FoodServingOptionQualityStatus qualityStatus
    );

    List<FoodItemServingOptionEntity> findByFoodItemId(Long foodItemId);

    List<FoodItemServingOptionEntity> findByFoodItemIdInOrderByFoodItemIdAscIsDefaultDescLabelAsc(
            Collection<Long> foodItemIds
    );

    List<FoodItemServingOptionEntity> findByFoodItemIdInAndQualityStatusOrderByFoodItemIdAscIsDefaultDescLabelAsc(
            Collection<Long> foodItemIds,
            FoodServingOptionQualityStatus qualityStatus
    );
}

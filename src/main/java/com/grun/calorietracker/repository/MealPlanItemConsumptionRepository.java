package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.MealPlanItemConsumptionEntity;
import com.grun.calorietracker.entity.MealPlanItemEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MealPlanItemConsumptionRepository extends JpaRepository<MealPlanItemConsumptionEntity, Long> {
    Optional<MealPlanItemConsumptionEntity> findByIdAndUser(Long id, UserEntity user);
    Optional<MealPlanItemConsumptionEntity> findByUserAndIdempotencyKey(UserEntity user, String idempotencyKey);
    Optional<MealPlanItemConsumptionEntity> findByMealPlanItemAndUser(MealPlanItemEntity mealPlanItem, UserEntity user);
    List<MealPlanItemConsumptionEntity> findByMealPlanItemMealPlanIdAndUserOrderByCreatedAtDesc(Long mealPlanId, UserEntity user);
    long deleteByUser(UserEntity user);
}

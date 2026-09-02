package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.MealPlanItemConsumptionEntity;
import com.grun.calorietracker.entity.MealPlanItemEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.MealPlanItemConsumptionStatus;
import com.grun.calorietracker.repository.projection.MealReminderUnresolvedPlanProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MealPlanItemConsumptionRepository extends JpaRepository<MealPlanItemConsumptionEntity, Long> {
    @Query("""
            select consumption.user.id as userId, upper(consumption.mealPlanItem.mealType) as mealType
            from MealPlanItemConsumptionEntity consumption
            where consumption.user.id in :userIds
              and consumption.mealPlanItem.planDate = :localDate
              and consumption.status in :statuses
              and consumption.foodLog is null
              and consumption.recipeLog is null
            group by consumption.user.id, upper(consumption.mealPlanItem.mealType)
            """)
    List<MealReminderUnresolvedPlanProjection> findUnresolvedReminderConsumptions(
            @Param("userIds") List<Long> userIds,
            @Param("localDate") LocalDate localDate,
            @Param("statuses") List<MealPlanItemConsumptionStatus> statuses);

    Optional<MealPlanItemConsumptionEntity> findByIdAndUser(Long id, UserEntity user);
    Optional<MealPlanItemConsumptionEntity> findByUserAndIdempotencyKey(UserEntity user, String idempotencyKey);
    Optional<MealPlanItemConsumptionEntity> findByMealPlanItemAndUser(MealPlanItemEntity mealPlanItem, UserEntity user);
    List<MealPlanItemConsumptionEntity> findByMealPlanItemMealPlanIdAndUserOrderByCreatedAtDesc(Long mealPlanId, UserEntity user);
    long deleteByUser(UserEntity user);
}

package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.MealPlanEntity;
import com.grun.calorietracker.entity.MealPlanItemEntity;
import com.grun.calorietracker.entity.UserEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MealPlanItemRepository extends JpaRepository<MealPlanItemEntity, Long> {
    List<MealPlanItemEntity> findByMealPlanOrderByPlanDateAscMealTypeAscItemOrderAscIdAsc(MealPlanEntity mealPlan);

    @Query("select i from MealPlanItemEntity i join i.mealPlan p where i.id = :itemId and p.id = :planId and p.user = :user")
    Optional<MealPlanItemEntity> findOwned(@Param("itemId") Long itemId, @Param("planId") Long planId, @Param("user") UserEntity user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from MealPlanItemEntity i join i.mealPlan p where i.id = :itemId and p.id = :planId and p.user = :user")
    Optional<MealPlanItemEntity> findOwnedForUpdate(@Param("itemId") Long itemId, @Param("planId") Long planId, @Param("user") UserEntity user);
}

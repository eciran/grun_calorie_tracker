package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.GroceryListEntity;
import com.grun.calorietracker.entity.MealPlanEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.GroceryListStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroceryListRepository extends JpaRepository<GroceryListEntity, Long> {

    Optional<GroceryListEntity> findByIdAndUser(Long id, UserEntity user);

    Optional<GroceryListEntity> findByUserAndSourceMealPlanAndStatus(
            UserEntity user,
            MealPlanEntity sourceMealPlan,
            GroceryListStatus status
    );

    List<GroceryListEntity> findByUserOrderByUpdatedAtDesc(UserEntity user);

    long countByUserAndStatus(UserEntity user, GroceryListStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select groceryList from GroceryListEntity groceryList "
            + "where groceryList.id = :id and groceryList.user = :user")
    Optional<GroceryListEntity> findOwnedForUpdate(@Param("id") Long id, @Param("user") UserEntity user);

    long deleteByUser(UserEntity user);
}
